package com.trix.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class EdView {
    var cx by mutableFloatStateOf(400f)
    var cy by mutableFloatStateOf(225f)
    var zoom by mutableFloatStateOf(1f)
}

private fun resizable(o: GameObject) = o.type != "text" && o.type != "tilemap"

private fun handlePoints(o: GameObject, xf: ViewXf): List<Offset> {
    val l = xf.ox + (o.x - o.w / 2).toFloat() * xf.sc
    val r = xf.ox + (o.x + o.w / 2).toFloat() * xf.sc
    val t = xf.oy + (o.y - o.h / 2).toFloat() * xf.sc
    val b = xf.oy + (o.y + o.h / 2).toFloat() * xf.sc
    val mx = (l + r) / 2
    val my = (t + b) / 2
    return listOf(Offset(l, t), Offset(mx, t), Offset(r, t), Offset(r, my), Offset(r, b), Offset(mx, b), Offset(l, b), Offset(l, my))
}

private fun handleAt(o: GameObject, p: Offset, xf: ViewXf): Int {
    if (!resizable(o)) return -1
    var best = -1
    var bd = 30f
    handlePoints(o, xf).forEachIndexed { i, h ->
        val d = (h - p).getDistance()
        if (d < bd) { bd = d; best = i }
    }
    return best
}

private fun hitTest(list: List<GameObject>, xf: ViewXf, p: Offset): GameObject? {
    val wx = (p.x - xf.ox) / xf.sc
    val wy = (p.y - xf.oy) / xf.sc
    val pad = 8.0 / xf.sc
    for (i in list.indices.reversed()) {
        val o = list[i]
        val l = o.left(); val t = o.top()
        if (wx >= l - pad && wx <= l + o.bw() + pad && wy >= t - pad && wy <= t + o.bh() + pad) {
            // tilemap: só seleciona se tocar numa área da grade
            return o
        }
    }
    return null
}

private fun containsPoint(o: GameObject, xf: ViewXf, p: Offset): Boolean {
    val wx = (p.x - xf.ox) / xf.sc
    val wy = (p.y - xf.oy) / xf.sc
    return wx >= o.left() && wx <= o.left() + o.bw() && wy >= o.top() && wy <= o.top() + o.bh()
}

@Composable
private fun MiniBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip8().background(Color(0xCC1B1F3B)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 14.sp, color = Color.White) }
}

private fun Modifier.clip8() = this.then(Modifier.clip(RoundedCornerShape(8.dp)))

@Composable
fun SceneViewport(st: EditorState, modifier: Modifier = Modifier) {
    val xf = remember { ViewXf() }
    val view = remember { EdView().also { it.cx = st.settings.worldW / 2f; it.cy = st.settings.worldH / 2f } }

    fun fit() {
        val ww = st.settings.worldW.toFloat()
        val wh = st.settings.worldH.toFloat()
        val base = minOf(xf.cw / ww, xf.ch / wh) * 0.9f
        xf.sc = base * view.zoom
        xf.ox = xf.cw / 2f - view.cx * xf.sc
        xf.oy = xf.ch / 2f - view.cy * xf.sc
    }

    fun paintAt(m: GameObject, p: Offset) {
        val wx = (p.x - xf.ox) / xf.sc
        val wy = (p.y - xf.oy) / xf.sc
        val c = Math.floor((wx - m.x) / m.tileSize).toInt()
        val r = Math.floor((wy - m.y) / m.tileSize).toInt()
        if (c in 0 until m.cols && r in 0 until m.rows && m.tiles[r * m.cols + c] != st.brush) {
            m.tiles[r * m.cols + c] = st.brush
            st.edited()
        }
    }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().background(Dark).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fit()
                    val start = down.position
                    val sel0 = st.sel
                    val handle = if (sel0 != null) handleAt(sel0, start, xf) else -1
                    val hit = if (handle < 0) hitTest(st.objects, xf, start) else null
                    val paintMap: GameObject? =
                        if (handle < 0 && st.paint) sel0?.takeIf { it.type == "tilemap" && containsPoint(it, xf, start) } else null
                    val mode = if (handle >= 0) 1 else if (paintMap != null) 3 else if (hit != null) 2 else 0
                    val target: GameObject? = if (mode == 1) sel0 else if (mode == 2) hit else null
                    if (mode == 2) st.sel = hit

                    var rx = 0.0; var ry = 0.0
                    var rl = 0.0; var rr = 0.0; var rt = 0.0; var rb = 0.0
                    if (target != null) {
                        rx = target.x; ry = target.y
                        rl = target.x - target.w / 2; rr = target.x + target.w / 2
                        rt = target.y - target.h / 2; rb = target.y + target.h / 2
                    }
                    val g = st.settings.grid.toDouble().coerceAtLeast(1.0)
                    fun sn(v: Double): Double = if (st.snap) Math.round(v / g) * g else v

                    if (mode == 3 && paintMap != null) paintAt(paintMap, start)
                    var moved = false
                    var multi = false
                    do {
                        val ev = awaitPointerEvent()
                        val count = ev.changes.count { it.pressed }
                        if (count >= 2) {
                            multi = true
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val cen = ev.calculateCentroid()
                            if (cen.isSpecified) {
                                val wx = (cen.x - xf.ox) / xf.sc
                                val wy = (cen.y - xf.oy) / xf.sc
                                view.zoom = (view.zoom * zoom).coerceIn(0.1f, 10f)
                                fit()
                                view.cx = wx - (cen.x - xf.cw / 2f) / xf.sc - pan.x / xf.sc
                                view.cy = wy - (cen.y - xf.ch / 2f) / xf.sc - pan.y / xf.sc
                                fit()
                            }
                            ev.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val c = ev.changes.firstOrNull { it.pressed }
                            if (c != null) {
                                if (!moved && (c.position - start).getDistance() > 12f) moved = true
                                if (mode == 3 && paintMap != null) {
                                    paintAt(paintMap, c.position); c.consume()
                                } else if (moved) {
                                    val d = c.positionChange()
                                    when (mode) {
                                        0 -> { view.cx -= d.x / xf.sc; view.cy -= d.y / xf.sc; fit() }
                                        2 -> if (target != null) {
                                            rx += d.x / xf.sc; ry += d.y / xf.sc
                                            target.x = sn(rx); target.y = sn(ry)
                                            st.edited()
                                        }
                                        1 -> if (target != null) {
                                            val dx = (d.x / xf.sc).toDouble()
                                            val dy = (d.y / xf.sc).toDouble()
                                            if (handle == 0 || handle == 6 || handle == 7) rl += dx
                                            if (handle == 2 || handle == 3 || handle == 4) rr += dx
                                            if (handle == 0 || handle == 1 || handle == 2) rt += dy
                                            if (handle == 4 || handle == 5 || handle == 6) rb += dy
                                            var l = sn(rl); var r = sn(rr); var tp = sn(rt); var bt = sn(rb)
                                            if (r - l < 4.0) { if (handle == 0 || handle == 6 || handle == 7) l = r - 4.0 else r = l + 4.0 }
                                            if (bt - tp < 4.0) { if (handle == 0 || handle == 1 || handle == 2) tp = bt - 4.0 else bt = tp + 4.0 }
                                            target.w = r - l; target.h = bt - tp
                                            target.x = (l + r) / 2; target.y = (tp + bt) / 2
                                            st.edited()
                                        }
                                    }
                                    c.consume()
                                }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (!moved && !multi && mode == 0) st.sel = null
                }
            }
        ) {
            val rv = st.rev
            val s = st.settings
            xf.cw = size.width; xf.ch = size.height
            fit()
            val sc = xf.sc; val ox = xf.ox; val oy = xf.oy
            val ww = s.worldW.toFloat(); val wh = s.worldH.toFloat()

            // quadro da tela do jogo
            drawRect(Color(s.bg), Offset(ox, oy), Size(ww * sc, wh * sc))

            if (s.showGrid) {
                val x0 = (-ox) / sc; val x1 = (size.width - ox) / sc
                val y0 = (-oy) / sc; val y1 = (size.height - oy) / sc
                val gs = s.grid.coerceAtLeast(1)
                if (gs * sc >= 8f) {
                    var gx = (Math.floor(x0 / gs) * gs).toInt()
                    while (gx <= x1) { drawLine(Color(0x0DFFFFFF), Offset(ox + gx * sc, 0f), Offset(ox + gx * sc, size.height)); gx += gs }
                    var gy = (Math.floor(y0 / gs) * gs).toInt()
                    while (gy <= y1) { drawLine(Color(0x0DFFFFFF), Offset(0f, oy + gy * sc), Offset(size.width, oy + gy * sc)); gy += gs }
                }
                val major = if (sc < 0.25f) 400 else if (sc < 0.6f) 100 else 50
                var mx = (Math.floor(x0 / major) * major).toInt()
                while (mx <= x1) { drawLine(Color(0x1CFFFFFF), Offset(ox + mx * sc, 0f), Offset(ox + mx * sc, size.height)); mx += major }
                var my = (Math.floor(y0 / major) * major).toInt()
                while (my <= y1) { drawLine(Color(0x1CFFFFFF), Offset(0f, oy + my * sc), Offset(size.width, oy + my * sc)); my += major }
            }

            drawObjects(st.objects, sc, ox, oy, -1, true)
            drawRect(Color(0x99FFFFFF), Offset(ox, oy), Size(ww * sc, wh * sc), style = Stroke(2f))

            st.sel?.let { o ->
                val bl = ox + o.left().toFloat() * sc
                val bt2 = oy + o.top().toFloat() * sc
                drawRect(Cyan, Offset(bl, bt2), Size(o.bw().toFloat() * sc, o.bh().toFloat() * sc), style = Stroke(3f))
                if (resizable(o)) {
                    handlePoints(o, xf).forEach { h ->
                        drawRect(Color.White, Offset(h.x - 7f, h.y - 7f), Size(14f, 14f))
                        drawRect(Cyan, Offset(h.x - 7f, h.y - 7f), Size(14f, 14f), style = Stroke(2f))
                    }
                }
            }
        }

        // ferramentas sobre a viewport
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniBtn("⌖") { view.cx = st.settings.worldW / 2f; view.cy = st.settings.worldH / 2f; view.zoom = 1f }
            MiniBtn("＋") { view.zoom = (view.zoom * 1.3f).coerceAtMost(10f) }
            MiniBtn("－") { view.zoom = (view.zoom / 1.3f).coerceAtLeast(0.1f) }
            MiniBtn("${(view.zoom * 100).toInt()}%") { }
        }
        val s0 = st.sel
        if (s0 != null && s0.type == "tilemap") {
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp).clip8().background(Color(0xCC1B1F3B))
                    .horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(selected = st.paint, onClick = { st.paint = !st.paint }, label = { Text("Pincel") })
                for (id in 0 until TileSet.count) {
                    val col = if (id == 0) Color(0xFF334155) else Color(TileSet.colors[id])
                    Box(
                        Modifier.size(30.dp).background(col)
                            .border(if (st.brush == id) 3.dp else 1.dp, if (st.brush == id) Color.White else Color(0x66FFFFFF))
                            .clickable { st.brush = id; st.paint = true },
                        contentAlignment = Alignment.Center
                    ) { if (id == 0) Text("✕", fontSize = 12.sp) }
                }
            }
        }
        Text(
            "mover • arrastar o fundo = pan • 2 dedos = zoom",
            fontSize = 10.sp, color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        )
    }
}
