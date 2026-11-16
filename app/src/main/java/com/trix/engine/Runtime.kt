package com.trix.engine

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError
import java.io.File

/** Transformação mundo -> tela. */
class ViewXf {
    var cw = 1f; var ch = 1f
    var sc = 1f; var ox = 0f; var oy = 0f
}

class TouchProps : Props {
    var x = 0.0; var y = 0.0; var down = false; var tapped = false
    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "down" -> down; "tapped" -> tapped
        else -> throw TrixError("touch não tem '$n' (use x, y, down, tapped)")
    }
    override fun set(n: String, v: Any?) { throw TrixError("touch é somente leitura") }
}

class ScreenProps(private val w: Double, private val h: Double) : Props {
    override fun get(n: String): Any? = when (n) {
        "w" -> w; "h" -> h
        else -> throw TrixError("screen não tem '$n' (use w, h)")
    }
    override fun set(n: String, v: Any?) { throw TrixError("screen é somente leitura") }
}

/** Variáveis compartilhadas entre todos os scripts: game.pontos = 0 */
class GameVars : Props {
    val m = HashMap<String, Any?>()
    override fun get(n: String): Any? = m[n]
    override fun set(n: String, v: Any?) { m[n] = v }
}

/** Botões virtuais: input.left/right/up/down/a/b e input.a_pressed/b_pressed. */
class InputProps : Props {
    var left = false; var right = false; var up = false; var down = false
    var a = false; var b = false
    private var pa = false; private var pb = false
    private var aP = false; private var bP = false
    fun beginFrame() { aP = a && !pa; bP = b && !pb; pa = a; pb = b }
    override fun get(n: String): Any? = when (n) {
        "left" -> left; "right" -> right; "up" -> up; "down" -> down; "a" -> a; "b" -> b
        "a_pressed" -> aP; "b_pressed" -> bP
        else -> throw TrixError("input não tem '$n' (left, right, up, down, a, b, a_pressed, b_pressed)")
    }
    override fun set(n: String, v: Any?) { throw TrixError("input é somente leitura") }
}

class CameraProps(var x: Double, var y: Double) : Props {
    var zoom = 1.0
    var target: GameObject? = null
    var smooth = 0.0
    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "zoom" -> zoom
        else -> throw TrixError("camera não tem '$n' (x, y, zoom)")
    }
    override fun set(n: String, v: Any?) {
        val d = (v as? Double) ?: throw TrixError("camera.$n precisa ser número")
        when (n) {
            "x" -> x = d; "y" -> y = d; "zoom" -> zoom = d.coerceIn(0.1, 10.0)
            else -> throw TrixError("camera não tem '$n' (x, y, zoom)")
        }
    }
    fun update(dt: Double) {
        val t = target ?: return
        if (smooth <= 0.0) { x = t.x; y = t.y }
        else {
            val k = 1.0 - Math.exp(-smooth * dt)
            x += (t.x - x) * k; y += (t.y - y) * k
        }
    }
}

/** Cache de imagens da pasta assets do projeto. */
object Images {
    var dir: File? = null
    private val cache = HashMap<String, ImageBitmap?>()
    private val samples = HashMap<String, Int>()

    fun get(name: String): ImageBitmap? {
        if (name.isEmpty()) return null
        val d = dir ?: return null
        val key = File(d, name).path
        if (!cache.containsKey(key)) {
            cache[key] = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(key, o)
                var s = 1
                while (o.outWidth / s > 2048 || o.outHeight / s > 2048) s *= 2
                samples[key] = s
                BitmapFactory.decodeFile(key, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            } catch (e: Throwable) { null }
        }
        return cache[key]
    }

    fun sample(name: String): Int {
        val d = dir ?: return 1
        return samples[File(d, name).path] ?: 1
    }

    fun size(name: String): IntSize? = get(name)?.let { IntSize(it.width * sample(name), it.height * sample(name)) }
}

object Audio {
    fun tone(freq: Double, ms: Int) {
        Thread {
            try {
                val sr = 22050
                val n = sr * ms / 1000
                if (n <= 0) return@Thread
                val buf = ShortArray(n) { i ->
                    (Math.sin(2 * Math.PI * freq * i / sr) * Short.MAX_VALUE * 0.4 * (1.0 - i.toDouble() / n)).toInt().toShort()
                }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(buf, 0, n)
                t.play()
                Thread.sleep(ms + 60L)
                t.release()
            } catch (e: Exception) { }
        }.start()
    }
}

private fun DrawScope.drawTilemap(o: GameObject, sc: Float, ox: Float, oy: Float) {
    val ts = o.tileSize.toFloat()
    if (ts <= 0f) return
    val px = ts * sc
    val c0 = Math.floor(((-ox) / sc - o.x) / ts).toInt().coerceIn(0, o.cols)
    val c1 = Math.floor(((size.width - ox) / sc - o.x) / ts).toInt().coerceIn(-1, o.cols - 1)
    val r0 = Math.floor(((-oy) / sc - o.y) / ts).toInt().coerceIn(0, o.rows)
    val r1 = Math.floor(((size.height - oy) / sc - o.y) / ts).toInt().coerceIn(-1, o.rows - 1)
    for (r in r0..r1) {
        for (c in c0..c1) {
            val id = o.tiles[r * o.cols + c]
            if (id <= 0 || id >= TileSet.count) continue
            val x = ox + (o.x.toFloat() + c * ts) * sc
            val y = oy + (o.y.toFloat() + r * ts) * sc
            var col = Color(TileSet.colors[id])
            if (id == 9) col = col.copy(alpha = 0.55f)
            drawRect(col, Offset(x, y), Size(px + 0.5f, px + 0.5f))
            if (px >= 6f) {
                drawRect(Color(0x33000000), Offset(x, y), Size(px, px), style = Stroke(1f))
                if (id == 7) drawRect(Color(0x66FFFFFF), Offset(x + px * 0.3f, y + px * 0.3f), Size(px * 0.4f, px * 0.4f))
            }
        }
    }
}

private fun DrawScope.drawAnimFrame(o: GameObject, cx: Float, cy: Float, pw: Float, ph: Float, a: Float) {
    val idx = o.frameIndex()
    val dOff = IntOffset((cx - pw / 2).toInt(), (cy - ph / 2).toInt())
    val dSize = IntSize(pw.toInt().coerceAtLeast(1), ph.toInt().coerceAtLeast(1))
    if (o.animMode == "files") {
        val img = Images.get(o.frameFiles.getOrNull(idx) ?: "")
        if (img != null) { drawImage(img, dstOffset = dOff, dstSize = dSize, alpha = a, filterQuality = FilterQuality.None); return }
    } else {
        val img = Images.get(o.sheet)
        if (img != null) {
            val smp = Images.sample(o.sheet)
            val fw = (o.fw / smp).toInt().coerceAtLeast(1)
            val fh = (o.fh / smp).toInt().coerceAtLeast(1)
            val colsN = Math.max(1, img.width / fw)
            val col = idx % colsN
            val row = idx / colsN
            if ((row + 1) * fh <= img.height) {
                drawImage(
                    img, srcOffset = IntOffset(col * fw, row * fh), srcSize = IntSize(fw, fh),
                    dstOffset = dOff, dstSize = dSize, alpha = a, filterQuality = FilterQuality.None
                )
                return
            }
        }
    }
    drawRect(Color(0x33FFFFFF), Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
    drawRect(Color(0xAAFACC15), Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph), style = Stroke(2f))
}

/**
 * fixedPass: -1 = tudo, 0 = só objetos do mundo, 1 = só objetos "fixos na tela" (HUD).
 * debug: desenha formas de colisão / corpos.
 */
fun DrawScope.drawObjects(list: List<GameObject>, sc: Float, ox: Float, oy: Float, fixedPass: Int = -1, debug: Boolean = false) {
    val vw = size.width
    val vh = size.height
    for (o in list) {
        if (o.type == "mesh") continue
        if (fixedPass == 0 && o.fixed) continue
        if (fixedPass == 1 && !o.fixed) continue
        if (o.type == "tilemap") {
            if (o.visible) drawTilemap(o, sc, ox, oy)
            continue
        }
        val cx = ox + o.x.toFloat() * sc
        val cy = oy + o.y.toFloat() * sc
        val pw = o.w.toFloat() * sc
        val ph = o.h.toFloat() * sc
        val ext = Math.max(pw, Math.max(ph, o.bw().toFloat() * sc)) + 4f
        if (cx + ext < 0 || cx - ext > vw || cy + ext < 0 || cy - ext > vh) continue

        if (o.type == "collision") {
            if (debug) {
                val col = if (o.trigger) Color(0xFFFACC15) else Color(0xFF22C55E)
                if (o.shape == "wedge") {
                    drawPath(wedgePath(cx, cy, pw, ph, o.flip), col.copy(alpha = 0.25f))
                    drawPath(wedgePath(cx, cy, pw, ph, o.flip), col, style = Stroke(2f))
                } else {
                    drawRect(col.copy(alpha = 0.25f), Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
                    drawRect(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph), style = Stroke(2f))
                }
            }
            continue
        }
        if (!o.visible) continue

        val a = o.alpha.toFloat().coerceIn(0f, 1f)
        val col = Color(o.color).copy(alpha = a)
        val img = if (o.type == "part" || o.type == "sprite") Images.get(o.image) else null

        val body: DrawScope.() -> Unit = {
            when {
                img != null -> drawImage(
                    img,
                    dstOffset = IntOffset((cx - pw / 2).toInt(), (cy - ph / 2).toInt()),
                    dstSize = IntSize(pw.toInt().coerceAtLeast(1), ph.toInt().coerceAtLeast(1)),
                    alpha = a, filterQuality = FilterQuality.None
                )
                o.type == "anim" -> drawAnimFrame(o, cx, cy, pw, ph, a)
                o.type == "text" -> drawIntoCanvas { c ->
                    val p = Paint().apply {
                        color = o.color
                        alpha = (a * 255).toInt()
                        textSize = o.h.toFloat() * sc
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    c.nativeCanvas.drawText(o.text, cx, cy + p.textSize * 0.35f, p)
                }
                o.type == "sprite" -> {
                    drawRect(Color(0x22FFFFFF), Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
                    drawRect(Color(0x99FFFFFF), Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph), style = Stroke(2f))
                }
                else -> when (o.shape) {
                    "circle" -> drawOval(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
                    "wedge" -> drawPath(wedgePath(cx, cy, pw, ph, o.flip), col)
                    "capsule" -> drawRoundRect(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph), CornerRadius(Math.min(pw, ph) / 2))
                    "triangle" -> drawPath(Path().apply {
                        moveTo(cx, cy - ph / 2); lineTo(cx + pw / 2, cy + ph / 2); lineTo(cx - pw / 2, cy + ph / 2); close()
                    }, col)
                    else -> drawRect(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
                }
            }
        }
        rotate(o.rotation.toFloat(), Offset(cx, cy)) {
            if (o.flipX) scale(-1f, 1f, Offset(cx, cy)) { body() } else body()
        }

        if (debug && o.body != "none") {
            val c2 = if (o.body == "dynamic") Color(0xFF22D3EE) else Color(0xFF22C55E)
            drawRect(c2, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph), style = Stroke(1.5f))
        }
    }
}

fun wedgePath(cx: Float, cy: Float, pw: Float, ph: Float, flip: Boolean): Path = Path().apply {
    val l = cx - pw / 2; val r = cx + pw / 2; val t = cy - ph / 2; val b = cy + ph / 2
    moveTo(l, b); lineTo(r, b)
    if (flip) lineTo(l, t) else lineTo(r, t)
    close()
}
