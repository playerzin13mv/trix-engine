package com.trix.engine

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.lang.Interpreter
import com.trix.engine.lang.TrixError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import java.io.File

private fun vibrate(ctx: Context, ms: Long) {
    try {
        ctx.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) { }
}

private class GameTimer(var at: Double, val every: Double, val fn: String)

@Composable
fun PlayScreen(dir: File, scene: List<GameObject>, settings: ProjectSettings, source: String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val objs = remember { ArrayList<GameObject>() }
    val logs = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }
    val xf = remember { ViewXf() }
    val touch = remember { TouchProps() }
    val ww = settings.worldW.toFloat()
    val wh = settings.worldH.toFloat()

    BackHandler { onExit() }

    // A orientação do jogo vem das configurações do projeto
    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        activity?.requestedOrientation =
            if (settings.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    }

    LaunchedEffect(Unit) {
        try {
            while (isActive) {
                var restart = false
                var time = 0.0
                var spawnId = 0
                objs.clear()
                objs.addAll(scene.map { it.copy() })
                val timers = ArrayList<GameTimer>()
                val data = Projects.loadData(dir)
                val vm = Interpreter { l -> logs.add(l); while (logs.size > 6) logs.removeAt(0) }

                objs.forEach { vm.globals.vars[it.name] = it }
                vm.globals.vars["touch"] = touch
                vm.globals.vars["screen"] = ScreenProps(ww.toDouble(), wh.toDouble())
                vm.globals.vars["PI"] = Math.PI
                vm.globals.vars["time"] = 0.0

                val b = vm.builtins
                b["tone"] = { x ->
                    Audio.tone((x.getOrNull(0) as? Double) ?: 660.0, ((x.getOrNull(1) as? Double) ?: 100.0).toInt()); null
                }
                b["beep"] = { _ -> Audio.tone(660.0, 90); null }
                b["vibrate"] = { x -> vibrate(ctx, (x.getOrNull(0) as? Double)?.toLong() ?: 40L); null }
                b["restart"] = { _ -> restart = true; null }
                b["spawn"] = { x ->
                    val src = x.getOrNull(0) as? GameObject ?: throw TrixError("spawn(obj) precisa de um objeto")
                    val c = src.copy()
                    spawnId++
                    c.name = src.name + "_" + spawnId
                    c.visible = true
                    objs.add(c)
                    c
                }
                b["destroy"] = { x -> (x.getOrNull(0) as? GameObject)?.let { objs.remove(it) }; null }
                b["find"] = { x -> val n = x.getOrNull(0); objs.firstOrNull { it.name == n } }
                b["clicked"] = { x ->
                    val o = x.getOrNull(0) as? GameObject ?: throw TrixError("clicked(obj) precisa de um objeto")
                    touch.tapped && Math.abs(touch.x - o.x) <= o.bw() / 2 && Math.abs(touch.y - o.y) <= o.bh() / 2
                }
                b["after"] = { x ->
                    val fn = x.getOrNull(1) as? String ?: throw TrixError("after(segundos, \"funcao\")")
                    timers.add(GameTimer(time + ((x.getOrNull(0) as? Double) ?: 1.0), 0.0, fn)); null
                }
                b["every"] = { x ->
                    val fn = x.getOrNull(1) as? String ?: throw TrixError("every(segundos, \"funcao\")")
                    val s = ((x.getOrNull(0) as? Double) ?: 1.0).coerceAtLeast(0.01)
                    timers.add(GameTimer(time + s, s, fn)); null
                }
                b["save"] = { x ->
                    val k = x.getOrNull(0) as? String ?: throw TrixError("save(chave, valor): chave deve ser texto")
                    when (val v = x.getOrNull(1)) {
                        is Double -> data.put(k, v)
                        is String -> data.put(k, v)
                        is Boolean -> data.put(k, v)
                        else -> data.remove(k)
                    }
                    Projects.saveData(dir, data)
                    null
                }
                b["load"] = { x ->
                    val k = x.getOrNull(0) as? String ?: throw TrixError("load(chave, padrão): chave deve ser texto")
                    if (data.has(k)) {
                        val v = data.get(k)
                        if (v is Number) v.toDouble() else v
                    } else x.getOrNull(1)
                }

                vm.load(source)
                if (vm.has("start")) vm.call("start", emptyList())

                var last = withFrameNanos { it }
                var fpsT = 0.0
                var fpsN = 0
                while (isActive && !restart) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9).coerceAtMost(0.05)
                    last = now
                    time += dt
                    vm.globals.vars["time"] = time

                    if (vm.has("update")) vm.call("update", listOf(dt))

                    if (timers.isNotEmpty()) {
                        val due = timers.filter { it.at <= time }
                        for (t in due) {
                            if (vm.has(t.fn)) vm.call(t.fn, emptyList())
                            if (t.every > 0) t.at += t.every else timers.remove(t)
                        }
                    }

                    for (o in objs) {
                        if (o.gravity) o.vy += settings.gravity * dt
                        o.x += o.vx * dt
                        o.y += o.vy * dt
                    }
                    touch.tapped = false
                    frame++

                    fpsT += dt; fpsN++
                    if (fpsT >= 0.5) { fps = (fpsN / fpsT).toInt(); fpsT = 0.0; fpsN = 0 }
                }
                if (!restart) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrixError) {
            error = e.message
        } catch (e: Exception) {
            error = "erro interno: ${e.message}"
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060D))) {
        Canvas(
            Modifier.fillMaxSize()
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
            xf.cw = size.width; xf.ch = size.height
            xf.fit(ww, wh, 1f)
            drawRect(Color(settings.bg), Offset(xf.ox, xf.oy), Size(ww * xf.sc, wh * xf.sc))
            clipRect(xf.ox, xf.oy, xf.ox + ww * xf.sc, xf.oy + wh * xf.sc) {
                drawObjects(objs, xf.sc, xf.ox, xf.oy)
            }
        }

        FilledTonalButton(
            onClick = onExit,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp)
        ) { Text("■ Parar") }

        if (settings.showFps) {
            Text(
                "$fps FPS", color = Color(0xFFFACC15), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)
            )
        }

        Column(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)) {
            logs.forEach { Text(it, color = Color(0xFF86EFAC), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }

        error?.let { msg ->
            Card(
                Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 460.dp),
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
