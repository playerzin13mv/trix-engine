package com.trix.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.lang.Interpreter
import com.trix.engine.lang.TrixError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

private fun vibrate(ctx: Context, ms: Long) {
    try {
        ctx.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) { }
}

@Composable
fun PlayScreen(scene: List<GameObject>, source: String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val objs = remember { scene.map { it.copy() } }
    val logs = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    val xf = remember { ViewXf() }
    val touch = remember { TouchProps() }

    BackHandler { onExit() }

    LaunchedEffect(Unit) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
        try {
            val vm = Interpreter { l -> logs.add(l); while (logs.size > 6) logs.removeAt(0) }
            objs.forEach { vm.globals.vars[it.name] = it }
            vm.globals.vars["touch"] = touch
            vm.globals.vars["screen"] = ScreenProps
            vm.globals.vars["PI"] = Math.PI
            vm.globals.vars["time"] = 0.0
            vm.builtins["beep"] = { _ -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90); null }
            vm.builtins["vibrate"] = { x -> vibrate(ctx, (x.getOrNull(0) as? Double)?.toLong() ?: 40L); null }

            vm.load(source)
            if (vm.has("start")) vm.call("start", emptyList())

            var last = withFrameNanos { it }
            var time = 0.0
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9).coerceAtMost(0.05)
                last = now
                time += dt
                vm.globals.vars["time"] = time
                if (vm.has("update")) vm.call("update", listOf(dt))
                for (o in objs) { o.x += o.vx * dt; o.y += o.vy * dt }
                touch.tapped = false
                frame++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrixError) {
            error = e.message
        } catch (e: Exception) {
            error = "erro interno: ${e.message}"
        } finally {
            tone?.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060D))) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { s ->
                    xf.sc = minOf(s.width / WW, s.height / WH)
                    xf.ox = (s.width - WW * xf.sc) / 2f
                    xf.oy = (s.height - WH * xf.sc) / 2f
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: continue
                            touch.down = ch.pressed
                            touch.x = ((ch.position.x - xf.ox) / xf.sc).toDouble()
                            touch.y = ((ch.position.y - xf.oy) / xf.sc).toDouble()
                            if (ch.pressed && !ch.previousPressed) touch.tapped = true
                        }
                    }
                }
        ) {
            val f = frame // força redesenho a cada frame
            drawRect(Color(0xFF0D1020), Offset(xf.ox, xf.oy), Size(WW * xf.sc, WH * xf.sc))
            clipRect(xf.ox, xf.oy, xf.ox + WW * xf.sc, xf.oy + WH * xf.sc) {
                drawObjects(objs, xf.sc, xf.ox, xf.oy)
            }
        }

        FilledTonalButton(
            onClick = onExit,
            modifier = Modifier.statusBarsPadding().padding(10.dp)
        ) { Text("■ Parar") }

        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(10.dp)) {
            logs.forEach { Text(it, color = Color(0xFF86EFAC), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }

        error?.let { msg ->
            Card(
                Modifier.align(Alignment.Center).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surf)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Erro no script", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(msg, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Button(onClick = onExit) { Text("Voltar ao editor") }
                }
            }
        }
    }
}
