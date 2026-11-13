package com.trix.engine

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError

class ViewXf { var sc = 1f; var ox = 0f; var oy = 0f }

class TouchProps : Props {
    var x = 0.0; var y = 0.0; var down = false; var tapped = false
    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "down" -> down; "tapped" -> tapped
        else -> throw TrixError("touch não tem '$n' (use x, y, down, tapped)")
    }
    override fun set(n: String, v: Any?) { throw TrixError("touch é somente leitura") }
}

object ScreenProps : Props {
    override fun get(n: String): Any? = when (n) {
        "w" -> WW.toDouble(); "h" -> WH.toDouble()
        else -> throw TrixError("screen não tem '$n' (use w, h)")
    }
    override fun set(n: String, v: Any?) { throw TrixError("screen é somente leitura") }
}

fun DrawScope.drawObjects(list: List<GameObject>, sc: Float, ox: Float, oy: Float) {
    for (o in list) {
        if (!o.visible) continue
        val cx = ox + o.x.toFloat() * sc
        val cy = oy + o.y.toFloat() * sc
        val col = Color(o.color)
        when (o.type) {
            "circle" -> drawOval(
                col,
                Offset(cx - o.w.toFloat() * sc / 2, cy - o.h.toFloat() * sc / 2),
                Size(o.w.toFloat() * sc, o.h.toFloat() * sc)
            )
            "text" -> drawIntoCanvas { c ->
                val p = Paint().apply {
                    color = o.color
                    textSize = o.h.toFloat() * sc
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                c.nativeCanvas.drawText(o.text, cx, cy + p.textSize * 0.35f, p)
            }
            else -> drawRect(
                col,
                Offset(cx - o.w.toFloat() * sc / 2, cy - o.h.toFloat() * sc / 2),
                Size(o.w.toFloat() * sc, o.h.toFloat() * sc)
            )
        }
    }
}
