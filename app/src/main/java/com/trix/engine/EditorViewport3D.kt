package com.trix.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.Renderer3D.draw3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class Ed3D {
    var yaw by mutableFloatStateOf(25f)
    var pitch by mutableFloatStateOf(-28f)
    var dist by mutableFloatStateOf(14f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(1f)
    var tz by mutableFloatStateOf(0f)
}

private class Cam3(val x: Double, val y: Double, val z: Double)

@Composable
private fun Btn3(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC1B1F3B)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
fun SceneViewport3D(st: EditorState, modifier: Modifier = Modifier) {
    val ed = remember { Ed3D() }
    val dims = remember { floatArrayOf(1f, 1f) }
    val fov = 60.0

    fun camPos(): Cam3 {
        val yr = Math.toRadians(ed.yaw.toDouble()); val pr = Math.toRadians(ed.pitch.toDouble())
        val fx = sin(yr) * cos(pr); val fy = sin(pr); val fz = cos(yr) * cos(pr)
        return Cam3(ed.tx - fx * ed.dist, ed.ty - fy * ed.dist, ed.tz - fz * ed.dist)
    }
    fun view(): View3D {
        val c = camPos()
        return View3D(c.x, c.y, c.z, ed.yaw.toDouble(), ed.pitch.toDouble(), fov, dims[0], dims[1])
    }

    fun pick(p: Offset): GameObject? {
        val v = view()
        var best: GameObject? = null
        var bestR = Double.MAX_VALUE
        for (o in st.objects) {
            if (o.type != "mesh") continue
            val pc = DoubleArray(3); v.toCam(o.x, o.y, o.z, pc)
            if (pc[2] < 0.1) continue
            val s = v.screen(pc)
            val r = Math.max(o.w, Math.max(o.h, o.d)) * 0.5 * v.f / pc[2] + 14
            if ((s - p).getDistance() < r && r < bestR) { best = o; bestR = r }
        }
        return best
    }

    /** interseção do raio do pixel com o plano y = planeY */
    fun onPlane(p: Offset, planeY: Double): DoubleArray? {
        val v = view()
        val d = v.ray(p.x, p.y)
        if (Math.abs(d[1]) < 1e-6) return null
        val t = (planeY - v.camY) / d[1]
        if (t < 0) return null
        return doubleArrayOf(v.camX + d[0] * t, v.camZ + d[2] * t)
    }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().background(Dark).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val hit = pick(start)
                    if (hit != null) st.sel = hit
                    val plane = if (hit != null) onPlane(start, hit.y) else null
                    val offX = if (hit != null && plane != null) hit.x - plane[0] else 0.0
                    val offZ = if (hit != null && plane != null) hit.z - plane[1] else 0.0
                    var moved = false
                    var multi = false
                    do {
                        val ev = awaitPointerEvent()
                        val count = ev.changes.count { it.pressed }
                        if (count >= 2) {
                            multi = true
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            ed.dist = (ed.dist / z).coerceIn(2f, 120f)
                            val yr = Math.toRadians(ed.yaw.toDouble())
                            val f = (dims[1] / 2.0) / tan(Math.toRadians(fov) / 2.0)
                            val s = (ed.dist / f).toFloat()
                            ed.tx -= (cos(yr) * pan.x * s).toFloat(); ed.tz += (sin(yr) * pan.x * s).toFloat()
                            ed.ty += pan.y * s
                            ev.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val c = ev.changes.firstOrNull { it.pressed }
                            if (c != null) {
                                if (!moved && (c.position - start).getDistance() > 12f) moved = true
                                if (moved) {
                                    if (hit != null && plane != null) {
                                        val q = onPlane(c.position, hit.y)
                                        if (q != null) {
                                            var nx = q[0] + offX; var nz = q[1] + offZ
                                            if (st.snap) { nx = Math.round(nx * 2) / 2.0; nz = Math.round(nz * 2) / 2.0 }
                                            hit.x = nx; hit.z = nz
                                            st.edited()
                                        }
                                    } else {
                                        val d = c.positionChange()
                                        ed.yaw += d.x * 0.4f
                                        ed.pitch = (ed.pitch - d.y * 0.4f).coerceIn(-89f, 89f)
                                    }
                                    c.consume()
                                }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (!moved && !multi && hit == null) st.sel = null
                }
            }
        ) {
            val rv = st.rev
            dims[0] = size.width; dims[1] = size.height
            drawRect(Color(st.settings.bg), Offset.Zero, size)
            val c = camPos()
            draw3D(st.objects, c.x, c.y, c.z, ed.yaw.toDouble(), ed.pitch.toDouble(), fov, st.settings.showGrid, st.sel)
        }
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn3("⌖") { ed.yaw = 25f; ed.pitch = -28f; ed.dist = 14f; ed.tx = 0f; ed.ty = 1f; ed.tz = 0f }
            Btn3("＋") { ed.dist = (ed.dist / 1.3f).coerceAtLeast(2f) }
            Btn3("－") { ed.dist = (ed.dist * 1.3f).coerceAtMost(120f) }
        }
        Text(
            "arraste o fundo = girar • objeto = mover no chão • 2 dedos = zoom/pan",
            fontSize = 10.sp, color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        )
    }
}
