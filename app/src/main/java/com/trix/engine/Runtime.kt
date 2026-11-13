package com.trix.engine

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError
import java.io.File

/** Transformação mundo -> tela (letterbox). */
class ViewXf {
    var cw = 1f; var ch = 1f
    var sc = 1f; var ox = 0f; var oy = 0f
    fun fit(ww: Float, wh: Float, k: Float = 1f) {
        sc = minOf(cw / ww, ch / wh) * k
        ox = (cw - ww * sc) / 2f
        oy = (ch - wh * sc) / 2f
    }
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

/** Cache de imagens da pasta assets do projeto. */
object Images {
    var dir: File? = null
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(name: String): ImageBitmap? {
        if (name.isEmpty()) return null
        val d = dir ?: return null
        val f = File(d, name)
        val key = f.path
        if (!cache.containsKey(key)) {
            cache[key] = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(key, o)
                var s = 1
                while (o.outWidth / s > 1024 || o.outHeight / s > 1024) s *= 2
                BitmapFactory.decodeFile(key, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            } catch (e: Throwable) { null }
        }
        return cache[key]
    }
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

fun DrawScope.drawObjects(list: List<GameObject>, sc: Float, ox: Float, oy: Float) {
    for (o in list) {
        if (!o.visible) continue
        val cx = ox + o.x.toFloat() * sc
        val cy = oy + o.y.toFloat() * sc
        val a = o.alpha.toFloat().coerceIn(0f, 1f)
        val col = Color(o.color).copy(alpha = a)
        val pw = o.w.toFloat() * sc
        val ph = o.h.toFloat() * sc
        val img = if (o.type != "text") Images.get(o.image) else null
        rotate(o.rotation.toFloat(), Offset(cx, cy)) {
            when {
                img != null -> drawImage(
                    img,
                    dstOffset = IntOffset((cx - pw / 2).toInt(), (cy - ph / 2).toInt()),
                    dstSize = IntSize(pw.toInt().coerceAtLeast(1), ph.toInt().coerceAtLeast(1)),
                    alpha = a
                )
                o.type == "circle" -> drawOval(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
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
                else -> drawRect(col, Offset(cx - pw / 2, cy - ph / 2), Size(pw, ph))
            }
        }
    }
}
