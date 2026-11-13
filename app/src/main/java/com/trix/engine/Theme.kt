package com.trix.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Cyan = Color(0xFF22D3EE)
val Purple = Color(0xFFA855F7)
val Bg = Color(0xFF0F1222)
val Surf = Color(0xFF1B1F3B)
val Surf2 = Color(0xFF252A4D)
val Dark = Color(0xFF0A0C18)

@Composable
fun TrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Color.Black,
            secondary = Purple, onSecondary = Color.White,
            background = Bg, onBackground = Color.White,
            surface = Surf, onSurface = Color.White,
            surfaceVariant = Surf2, onSurfaceVariant = Color(0xFFCBD5E1),
            primaryContainer = Surf2, onPrimaryContainer = Cyan,
            secondaryContainer = Surf2, onSecondaryContainer = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White, content = content)
    }
}

/** Mesmo desenho do ícone do app. */
@Composable
fun TrixLogo(dp: Dp = 64.dp) {
    Canvas(Modifier.size(dp)) {
        val k = this.size.minDimension / 60f
        fun p(x: Float, y: Float) = Offset((x - 24f) * k, (y - 24f) * k)
        val hex = Path().apply {
            moveTo(p(54f, 26f).x, p(54f, 26f).y); lineTo(p(78f, 40f).x, p(78f, 40f).y)
            lineTo(p(78f, 68f).x, p(78f, 68f).y); lineTo(p(54f, 82f).x, p(54f, 82f).y)
            lineTo(p(30f, 68f).x, p(30f, 68f).y); lineTo(p(30f, 40f).x, p(30f, 40f).y); close()
        }
        drawPath(hex, Surf)
        drawPath(hex, Brush.linearGradient(listOf(Cyan, Purple), start = p(30f, 26f), end = p(78f, 82f)),
            style = Stroke(width = 3.5f * k, join = StrokeJoin.Round))
        drawRect(Color.White, p(42f, 42f), Size(24f * k, 6f * k))
        drawRect(Color.White, p(51f, 48f), Size(6f * k, 22f * k))
        val tri = Path().apply {
            moveTo(p(60f, 56f).x, p(60f, 56f).y); lineTo(p(72f, 63f).x, p(72f, 63f).y)
            lineTo(p(60f, 70f).x, p(60f, 70f).y); close()
        }
        drawPath(tri, Cyan)
    }
}
