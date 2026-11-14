package com.trix.engine

/**
 * Física de plataforma da Trix Engine.
 *  - body = "static"  : sólido (caixa, rampa/wedge, colisão, tilemap)
 *  - body = "dynamic" : colide com os sólidos, tem grounded / ground_angle / wall / ceiling
 *  - stick = true     : o corpo "cola" no chão e segue rampas (ideal para física estilo Sonic)
 */
private class Sol(val l: Double, val t: Double, val r: Double, val b: Double, val wedge: Int)
private class Surf(val y: Double, val angle: Double)

object Physics {

    fun step(objs: List<GameObject>, dt: Double, g: Double) {
        val solids = ArrayList<GameObject>()
        val maps = ArrayList<GameObject>()
        for (o in objs) {
            if (o.type == "tilemap") { if (o.visible) maps.add(o); continue }
            if (o.body == "static" && !o.trigger && (o.type == "collision" || o.visible)) solids.add(o)
        }
        for (o in objs) {
            o.updateAnim(dt)
            if (o.type == "tilemap") continue
            if (o.body == "dynamic") moveBody(o, dt, g, solids, maps)
            else {
                if (o.gravity) o.vy += g * dt
                o.x += o.vx * dt
                o.y += o.vy * dt
            }
        }
    }

    private fun gather(x0: Double, y0: Double, x1: Double, y1: Double, solids: List<GameObject>, maps: List<GameObject>): ArrayList<Sol> {
        val out = ArrayList<Sol>()
        for (s in solids) {
            val l = s.x - s.w / 2; val r = s.x + s.w / 2
            val t = s.y - s.h / 2; val b = s.y + s.h / 2
            if (r < x0 || l > x1 || b < y0 || t > y1) continue
            out.add(Sol(l, t, r, b, if (s.shape == "wedge") (if (s.flip) -1 else 1) else 0))
        }
        for (m in maps) {
            val ts = m.tileSize
            if (ts <= 0.0) continue
            val c0 = Math.floor((x0 - m.x) / ts).toInt().coerceAtLeast(0)
            val c1 = Math.floor((x1 - m.x) / ts).toInt().coerceAtMost(m.cols - 1)
            val r0 = Math.floor((y0 - m.y) / ts).toInt().coerceAtLeast(0)
            val r1 = Math.floor((y1 - m.y) / ts).toInt().coerceAtMost(m.rows - 1)
            for (r in r0..r1) {
                for (c in c0..c1) {
                    if (TileSet.solid(m.tiles[r * m.cols + c])) {
                        out.add(Sol(m.x + c * ts, m.y + r * ts, m.x + (c + 1) * ts, m.y + (r + 1) * ts, 0))
                    }
                }
            }
        }
        return out
    }

    private fun surfaceY(s: Sol, x: Double): Double {
        if (s.wedge == 0) return s.t
        val w = s.r - s.l
        val f = if (w <= 0.0) 0.0 else ((x - s.l) / w).coerceIn(0.0, 1.0)
        val k = if (s.wedge == 1) f else 1.0 - f
        return s.b - (s.b - s.t) * k
    }

    private fun best(xs: DoubleArray, minY: Double, maxY: Double, sols: List<Sol>): Surf? {
        var by = Double.MAX_VALUE
        var ang = 0.0
        var found = false
        for (x in xs) {
            for (s in sols) {
                if (x < s.l || x > s.r) continue
                val y = surfaceY(s, x)
                if (y >= minY && y <= maxY && y < by) {
                    by = y; found = true
                    ang = if (s.wedge == 0) 0.0 else s.wedge * Math.toDegrees(Math.atan2(s.b - s.t, s.r - s.l))
                }
            }
        }
        return if (found) Surf(by, ang) else null
    }

    private fun moveBody(o: GameObject, dt: Double, g: Double, solids: List<GameObject>, maps: List<GameObject>) {
        val hw = o.w / 2
        val hh = o.h / 2
        val follow = o.grounded && o.stick
        o.wall = 0
        o.ceiling = false
        if (o.gravity && !follow) o.vy += g * dt
        if (follow) o.vy = 0.0

        val up = Math.max(o.stepUp, Math.abs(o.vx * dt) * 2.4 + 2.0)
        val prevFoot = o.y + hh
        var nx = o.x + o.vx * dt
        val region = gather(
            Math.min(o.x, nx) - hw - 2, o.y - hh - Math.abs(o.vy * dt) - 4,
            Math.max(o.x, nx) + hw + 2, prevFoot + Math.abs(o.vy * dt) + up + 8,
            solids, maps
        )

        // ---- horizontal
        if (o.vx != 0.0) {
            for (s in region) {
                if (!(s.t < prevFoot - up && s.b > o.y - hh + 1)) continue
                if (o.vx > 0 && s.wedge != 1) {
                    if (o.x + hw <= s.l + 0.5 && nx + hw > s.l) { nx = s.l - hw; o.wall = 1 }
                } else if (o.vx < 0 && s.wedge != -1) {
                    if (o.x - hw >= s.r - 0.5 && nx - hw < s.r) { nx = s.r + hw; o.wall = -1 }
                }
            }
            if (o.wall != 0) o.vx = 0.0
        }
        o.x = nx

        // ---- vertical
        val xs = if (o.shape == "circle") doubleArrayOf(o.x) else doubleArrayOf(o.x - hw * 0.8, o.x, o.x + hw * 0.8)
        val ny = o.y + o.vy * dt
        var grounded = false
        var angle = 0.0
        if (follow) {
            val s = best(xs, prevFoot - up, prevFoot + up, region)
            if (s != null) { o.y = s.y - hh; grounded = true; angle = s.angle } else o.y = ny
        } else if (o.vy >= 0) {
            val foot = ny + hh
            val s = best(xs, prevFoot - up, foot + 0.01, region)
            if (s != null) {
                o.y = s.y - hh; grounded = true; angle = s.angle
                if (o.bounce > 0 && o.vy > 60) { o.vy = -o.vy * o.bounce; grounded = false } else o.vy = 0.0
            } else o.y = ny
        } else {
            o.y = ny
            val top = o.y - hh
            val prevTop = prevFoot - o.h
            for (s in region) {
                if (s.wedge != 0) continue
                if (s.l < o.x + hw - 1 && s.r > o.x - hw + 1 && s.b > top && s.b < prevTop + 4) {
                    o.y = s.b + hh; o.vy = 0.0; o.ceiling = true
                    break
                }
            }
        }
        o.grounded = grounded
        o.groundAngle = if (grounded) angle else 0.0
    }
}
