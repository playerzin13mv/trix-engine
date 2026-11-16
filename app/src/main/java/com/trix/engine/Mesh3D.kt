package com.trix.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError
import kotlin.math.*

/** Câmera 3D do jogo: x y z (posição), yaw/pitch/fov em graus. */
class Camera3D(var x: Double = 0.0, var y: Double = 5.0, var z: Double = -10.0,
               var yaw: Double = 0.0, var pitch: Double = -20.0, var fov: Double = 60.0) : Props {
    var target: GameObject? = null
    var dist = 8.0
    var height = 4.0
    var smooth = 0.0

    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "z" -> z; "yaw" -> yaw; "pitch" -> pitch; "fov" -> fov
        else -> throw TrixError("camera não tem '$n' (x, y, z, yaw, pitch, fov)")
    }
    override fun set(n: String, v: Any?) {
        val d = (v as? Double) ?: throw TrixError("camera.$n precisa ser número")
        when (n) {
            "x" -> x = d; "y" -> y = d; "z" -> z = d; "yaw" -> yaw = d; "pitch" -> pitch = d
            "fov" -> fov = d.coerceIn(10.0, 140.0)
            else -> throw TrixError("camera não tem '$n' (x, y, z, yaw, pitch, fov)")
        }
    }
    fun update(dt: Double) {
        val t = target ?: return
        val yr = Math.toRadians(yaw)
        val tx = t.x - sin(yr) * dist
        val ty = t.y + height
        val tz = t.z - cos(yr) * dist
        if (smooth <= 0.0) { x = tx; y = ty; z = tz }
        else {
            val k = 1.0 - exp(-smooth * dt)
            x += (tx - x) * k; y += (ty - y) * k; z += (tz - z) * k
        }
        pitch = -Math.toDegrees(atan2(height, dist))
    }
}

class Mesh(val tris: FloatArray, val normals: FloatArray, val doubleSided: Boolean)

object Meshes {
    private val cache = HashMap<String, Mesh>()
    fun get(shape: String): Mesh = cache.getOrPut(shape) {
        when (shape) {
            "sphere" -> sphere(10, 8)
            "cylinder" -> cylinder(14)
            "pyramid" -> pyramid()
            "plane" -> plane()
            else -> box()
        }
    }

    private class B {
        val t = ArrayList<Float>()
        val n = ArrayList<Float>()
        fun tri(a: FloatArray, b: FloatArray, c: FloatArray) {
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val cx = (a[0] + b[0] + c[0]) / 3; val cy = (a[1] + b[1] + c[1]) / 3; val cz = (a[2] + b[2] + c[2]) / 3
            if (nx * cx + ny * cy + nz * cz < 0) { nx = -nx; ny = -ny; nz = -nz }   // sempre para fora
            val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it == 0f) 1f else it }
            for (p in listOf(a, b, c)) { t.add(p[0]); t.add(p[1]); t.add(p[2]) }
            n.add(nx / l); n.add(ny / l); n.add(nz / l)
        }
        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) { tri(a, b, c); tri(a, c, d) }
        fun build(double: Boolean) = Mesh(t.toFloatArray(), n.toFloatArray(), double)
    }

    private fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    private fun box(): Mesh {
        val b = B(); val h = 0.5f
        b.quad(v(-h, -h, h), v(h, -h, h), v(h, h, h), v(-h, h, h))
        b.quad(v(h, -h, -h), v(-h, -h, -h), v(-h, h, -h), v(h, h, -h))
        b.quad(v(h, -h, h), v(h, -h, -h), v(h, h, -h), v(h, h, h))
        b.quad(v(-h, -h, -h), v(-h, -h, h), v(-h, h, h), v(-h, h, -h))
        b.quad(v(-h, h, h), v(h, h, h), v(h, h, -h), v(-h, h, -h))
        b.quad(v(-h, -h, -h), v(h, -h, -h), v(h, -h, h), v(-h, -h, h))
        return b.build(false)
    }

    private fun plane(): Mesh {
        val b = B(); val h = 0.5f
        b.quad(v(-h, 0f, -h), v(h, 0f, -h), v(h, 0f, h), v(-h, 0f, h))
        val m = b.build(true)
        for (i in 0 until m.normals.size step 3) { m.normals[i] = 0f; m.normals[i + 1] = 1f; m.normals[i + 2] = 0f }
        return m
    }

    private fun pyramid(): Mesh {
        val b = B(); val h = 0.5f
        val top = v(0f, h, 0f)
        val p0 = v(-h, -h, -h); val p1 = v(h, -h, -h); val p2 = v(h, -h, h); val p3 = v(-h, -h, h)
        b.tri(p0, p1, top); b.tri(p1, p2, top); b.tri(p2, p3, top); b.tri(p3, p0, top)
        b.quad(p0, p1, p2, p3)
        return b.build(false)
    }

    private fun sphere(slices: Int, stacks: Int): Mesh {
        val b = B()
        fun p(i: Int, j: Int): FloatArray {
            val phi = Math.PI * i / stacks
            val th = 2 * Math.PI * j / slices
            return v((0.5 * sin(phi) * cos(th)).toFloat(), (0.5 * cos(phi)).toFloat(), (0.5 * sin(phi) * sin(th)).toFloat())
        }
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = p(i, j); val bb = p(i, j + 1); val c = p(i + 1, j + 1); val d = p(i + 1, j)
            if (i == 0) b.tri(a, c, d) else if (i == stacks - 1) b.tri(a, bb, d) else b.quad(a, bb, c, d)
        }
        return b.build(false)
    }

    private fun cylinder(n: Int): Mesh {
        val b = B()
        fun p(j: Int, y: Float): FloatArray {
            val th = 2 * Math.PI * j / n
            return v((0.5 * cos(th)).toFloat(), y, (0.5 * sin(th)).toFloat())
        }
        val top = v(0f, 0.5f, 0f); val bot = v(0f, -0.5f, 0f)
        for (j in 0 until n) {
            b.quad(p(j, -0.5f), p(j + 1, -0.5f), p(j + 1, 0.5f), p(j, 0.5f))
            b.tri(top, p(j, 0.5f), p(j + 1, 0.5f))
            b.tri(bot, p(j, -0.5f), p(j + 1, -0.5f))
        }
        return b.build(false)
    }
}

/** Visão: transforma mundo -> câmera -> tela. */
class View3D(val camX: Double, val camY: Double, val camZ: Double, yaw: Double, pitch: Double, fov: Double, val w: Float, val h: Float) {
    private val cY = cos(Math.toRadians(yaw)); private val sY = sin(Math.toRadians(yaw))
    private val cP = cos(Math.toRadians(pitch)); private val sP = sin(Math.toRadians(pitch))
    val f = (h / 2.0) / tan(Math.toRadians(fov) / 2.0)

    /** out = (x, y, z) no espaço da câmera */
    fun toCam(X: Double, Y: Double, Z: Double, out: DoubleArray) {
        val dx = X - camX; val dy = Y - camY; val dz = Z - camZ
        val xc = dx * cY - dz * sY
        val zc = dx * sY + dz * cY
        out[0] = xc
        out[1] = dy * cP - zc * sP
        out[2] = dy * sP + zc * cP
    }

    fun screen(c: DoubleArray): Offset = Offset((w / 2 + c[0] / c[2] * f).toFloat(), (h / 2 - c[1] / c[2] * f).toFloat())

    fun project(X: Double, Y: Double, Z: Double): Offset? {
        val c = DoubleArray(3); toCam(X, Y, Z, c)
        return if (c[2] < 0.05) null else screen(c)
    }

    /** raio (origem na câmera, direção no mundo) para o pixel (px,py). */
    fun ray(px: Float, py: Float): DoubleArray {
        val xc = (px - w / 2) / f; val yc = -(py - h / 2) / f; val zc = 1.0
        val y1 = yc * cP + zc * sP
        val z1 = -yc * sP + zc * cP
        return doubleArrayOf(xc * cY + z1 * sY, y1, -xc * sY + z1 * cY)
    }
}

object Renderer3D {
    private class Tri(val xs: FloatArray, val ys: FloatArray, val n: Int, val depth: Float, val color: Int)

    private fun rotate(v: DoubleArray, rx: Double, ry: Double, rz: Double) {
        // Rz, depois Rx, depois Ry
        var x = v[0]; var y = v[1]; var z = v[2]
        val cz = cos(rz); val sz = sin(rz)
        val x1 = x * cz - y * sz; val y1 = x * sz + y * cz
        x = x1; y = y1
        val cx = cos(rx); val sx = sin(rx)
        val y2 = y * cx - z * sx; val z2 = y * sx + z * cx
        y = y2; z = z2
        val cy = cos(ry); val sy = sin(ry)
        val x3 = x * cy + z * sy; val z3 = -x * sy + z * cy
        v[0] = x3; v[1] = y; v[2] = z3
    }

    /** Corta o segmento no plano z = near. */
    private fun drawLine3(ds: DrawScope, v: View3D, a: DoubleArray, b: DoubleArray, color: Color, width: Float) {
        val near = 0.1
        var p = a; var q = b
        if (p[2] < near && q[2] < near) return
        if (p[2] < near) { val t = (near - p[2]) / (q[2] - p[2]); p = doubleArrayOf(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, near) }
        else if (q[2] < near) { val t = (near - q[2]) / (p[2] - q[2]); q = doubleArrayOf(q[0] + (p[0] - q[0]) * t, q[1] + (p[1] - q[1]) * t, near) }
        ds.drawLine(color, v.screen(p), v.screen(q), strokeWidth = width)
    }

    fun DrawScope.draw3D(
        objs: List<GameObject>, camX: Double, camY: Double, camZ: Double, yaw: Double, pitch: Double, fov: Double,
        grid: Boolean, sel: GameObject?
    ) {
        val v = View3D(camX, camY, camZ, yaw, pitch, fov, size.width, size.height)
        val tris = ArrayList<Tri>()
        val L = doubleArrayOf(0.45, 0.85, -0.35).let { val l = sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]); doubleArrayOf(it[0] / l, it[1] / l, it[2] / l) }
        val c0 = DoubleArray(3); val c1 = DoubleArray(3); val c2 = DoubleArray(3)
        val near = 0.1

        for (o in objs) {
            if (o.type != "mesh" || !o.visible) continue
            val m = Meshes.get(o.shape)
            val rxr = Math.toRadians(o.rx); val ryr = Math.toRadians(o.ry); val rzr = Math.toRadians(o.rotation)
            val col = o.color
            val cr = (col shr 16) and 0xFF; val cg = (col shr 8) and 0xFF; val cb = col and 0xFF
            val p0 = DoubleArray(3); val p1 = DoubleArray(3); val p2 = DoubleArray(3); val nn = DoubleArray(3)
            for (i in 0 until m.tris.size / 9) {
                val b = i * 9
                p0[0] = m.tris[b] * o.w; p0[1] = m.tris[b + 1] * o.h; p0[2] = m.tris[b + 2] * o.d
                p1[0] = m.tris[b + 3] * o.w; p1[1] = m.tris[b + 4] * o.h; p1[2] = m.tris[b + 5] * o.d
                p2[0] = m.tris[b + 6] * o.w; p2[1] = m.tris[b + 7] * o.h; p2[2] = m.tris[b + 8] * o.d
                nn[0] = m.normals[i * 3].toDouble(); nn[1] = m.normals[i * 3 + 1].toDouble(); nn[2] = m.normals[i * 3 + 2].toDouble()
                rotate(p0, rxr, ryr, rzr); rotate(p1, rxr, ryr, rzr); rotate(p2, rxr, ryr, rzr); rotate(nn, rxr, ryr, rzr)
                p0[0] += o.x; p0[1] += o.y; p0[2] += o.z
                p1[0] += o.x; p1[1] += o.y; p1[2] += o.z
                p2[0] += o.x; p2[1] += o.y; p2[2] += o.z
                val mx = (p0[0] + p1[0] + p2[0]) / 3; val my = (p0[1] + p1[1] + p2[1]) / 3; val mz = (p0[2] + p1[2] + p2[2]) / 3
                var facing = nn[0] * (camX - mx) + nn[1] * (camY - my) + nn[2] * (camZ - mz)
                if (facing <= 0) {
                    if (!m.doubleSided) continue
                    nn[0] = -nn[0]; nn[1] = -nn[1]; nn[2] = -nn[2]
                }
                val ndl = max(0.0, nn[0] * L[0] + nn[1] * L[1] + nn[2] * L[2])
                val light = 0.35 + 0.65 * ndl
                val color = (0xFF shl 24) or ((cr * light).toInt().coerceIn(0, 255) shl 16) or ((cg * light).toInt().coerceIn(0, 255) shl 8) or (cb * light).toInt().coerceIn(0, 255)

                v.toCam(p0[0], p0[1], p0[2], c0); v.toCam(p1[0], p1[1], p1[2], c1); v.toCam(p2[0], p2[1], p2[2], c2)
                val poly = clipNear(arrayOf(c0.copyOf(), c1.copyOf(), c2.copyOf()), near)
                if (poly.size < 3) continue
                val xs = FloatArray(poly.size); val ys = FloatArray(poly.size)
                var dsum = 0.0
                for ((k, pt) in poly.withIndex()) { val s = v.screen(pt); xs[k] = s.x; ys[k] = s.y; dsum += pt[2] }
                tris.add(Tri(xs, ys, poly.size, (dsum / poly.size).toFloat(), color))
            }
        }

        tris.sortByDescending { it.depth }
        val path = Path()
        for (t in tris) {
            path.reset()
            path.moveTo(t.xs[0], t.ys[0])
            for (k in 1 until t.n) path.lineTo(t.xs[k], t.ys[k])
            path.close()
            drawPath(path, Color(t.color))
            drawPath(path, Color(t.color), style = Stroke(1f))
        }

        if (grid) {
            val a = DoubleArray(3); val b = DoubleArray(3)
            for (i in -20..20) {
                val major = i % 5 == 0
                val colr = if (i == 0) Color(0x99FFFFFF) else if (major) Color(0x44FFFFFF) else Color(0x1EFFFFFF)
                v.toCam(i.toDouble(), 0.0, -20.0, a); v.toCam(i.toDouble(), 0.0, 20.0, b)
                drawLine3(this, v, a, b, colr, 1f)
                v.toCam(-20.0, 0.0, i.toDouble(), a); v.toCam(20.0, 0.0, i.toDouble(), b)
                drawLine3(this, v, a, b, colr, 1f)
            }
        }

        if (sel != null && sel.type == "mesh") {
            val corners = Array(8) { DoubleArray(3) }
            val rxr = Math.toRadians(sel.rx); val ryr = Math.toRadians(sel.ry); val rzr = Math.toRadians(sel.rotation)
            for (i in 0 until 8) {
                val p = doubleArrayOf(if (i and 1 == 0) -0.5 * sel.w else 0.5 * sel.w, if (i and 2 == 0) -0.5 * sel.h else 0.5 * sel.h, if (i and 4 == 0) -0.5 * sel.d else 0.5 * sel.d)
                rotate(p, rxr, ryr, rzr)
                v.toCam(p[0] + sel.x, p[1] + sel.y, p[2] + sel.z, corners[i])
            }
            val edges = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 1 to 3, 4 to 6, 5 to 7, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
            for ((a, b) in edges) drawLine3(this, v, corners[a], corners[b], Cyan, 3f)
        }
    }

    private fun clipNear(p: Array<DoubleArray>, near: Double): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            val ain = a[2] >= near; val bin = b[2] >= near
            if (ain) out.add(a)
            if (ain != bin) {
                val t = (near - a[2]) / (b[2] - a[2])
                out.add(doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, near))
            }
        }
        return out
    }
}

/** Física 3D simples: caixas alinhadas aos eixos (AABB). Y é para cima. */
object Physics3D {
    private fun th(o: GameObject) = if (o.shape == "plane") 0.1 else o.h

    private fun overlapXZ(a: GameObject, b: GameObject) =
        Math.abs(a.x - b.x) * 2 < a.w + b.w - 1e-4 && Math.abs(a.z - b.z) * 2 < a.d + b.d - 1e-4

    private fun overlapY(a: GameObject, b: GameObject) = Math.abs(a.y - b.y) * 2 < th(a) + th(b) - 1e-4

    fun step(objs: List<GameObject>, dt: Double, g: Double) {
        val solids = objs.filter { it.type == "mesh" && it.body == "static" && !it.trigger }
        for (o in objs) {
            if (o.type != "mesh") continue
            if (o.body != "dynamic") {
                if (o.gravity) o.vy -= g * dt
                o.x += o.vx * dt; o.y += o.vy * dt; o.z += o.vz * dt
                continue
            }
            if (o.gravity) o.vy -= g * dt
            o.wall = 0
            o.grounded = false

            o.x += o.vx * dt
            for (s in solids) if (overlapXZ(o, s) && overlapY(o, s)) {
                o.x = if (o.vx > 0) s.x - s.w / 2 - o.w / 2 else s.x + s.w / 2 + o.w / 2
                o.vx = 0.0; o.wall = 1
            }
            o.z += o.vz * dt
            for (s in solids) if (overlapXZ(o, s) && overlapY(o, s)) {
                o.z = if (o.vz > 0) s.z - s.d / 2 - o.d / 2 else s.z + s.d / 2 + o.d / 2
                o.vz = 0.0; o.wall = 1
            }

            val prevBottom = o.y - th(o) / 2
            val prevTop = o.y + th(o) / 2
            o.y += o.vy * dt
            for (s in solids) {
                if (!overlapXZ(o, s)) continue
                val top = s.y + th(s) / 2
                val bottom = s.y - th(s) / 2
                if (o.vy <= 0 && prevBottom >= top - 0.02 && o.y - th(o) / 2 < top) {
                    o.y = top + th(o) / 2; o.vy = 0.0; o.grounded = true
                } else if (o.vy > 0 && prevTop <= bottom + 0.02 && o.y + th(o) / 2 > bottom) {
                    o.y = bottom - th(o) / 2; o.vy = 0.0
                }
            }
        }
    }
}
