package com.trix.engine

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.Renderer3D.draw3D
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

/** Uma instância de script: cada script anexado a um objeto (ou à cena) roda no seu próprio interpretador. */
private class Inst(val vm: Interpreter, val self: GameObject?, val label: String)
private class GameTimer(var at: Double, val every: Double, val fn: String, val vm: Interpreter)

@Composable
private fun PadButton(label: String, size: Dp, onState: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (down) Color(0x99FFFFFF) else Color(0x44FFFFFF))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    down = true; onState(true)
                    do {
                        val ev = awaitPointerEvent()
                    } while (ev.changes.any { it.pressed })
                    down = false; onState(false)
                }
            },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
fun PlayScreen(dir: File, startScene: SceneData, settings: ProjectSettings, readScript: (String) -> String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val objs = remember { ArrayList<GameObject>() }
    val logs = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }
    val xf = remember { ViewXf() }
    val touch = remember { TouchProps() }
    val input = remember { InputProps() }
    val game = remember { GameVars() }
    val ww = settings.worldW.toFloat()
    val wh = settings.worldH.toFloat()
    val is3d = settings.is3d
    val cam = remember { CameraProps(ww / 2.0, wh / 2.0) }
    val cam3 = remember { Camera3D(settings.camX, settings.camY, settings.camZ, settings.camYaw, settings.camPitch, settings.camFov) }

    BackHandler { onExit() }

    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        activity?.requestedOrientation =
            if (settings.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    }

    LaunchedEffect(Unit) {
        var sceneData = startScene
        var pendingScene: String? = null
        try {
            while (isActive) {
                var restart = false
                var time = 0.0
                var spawnId = 0
                val ps = pendingScene
                if (ps != null) {
                    if (!File(dir, ps).exists()) throw TrixError("cena não encontrada: $ps")
                    sceneData = Projects.loadScene(dir, ps)
                    pendingScene = null
                }
                objs.clear()
                objs.addAll(sceneData.objects.map { it.copy() })
                cam.x = ww / 2.0; cam.y = wh / 2.0; cam.zoom = 1.0; cam.target = null
                cam3.x = settings.camX; cam3.y = settings.camY; cam3.z = settings.camZ
                cam3.yaw = settings.camYaw; cam3.pitch = settings.camPitch; cam3.fov = settings.camFov; cam3.target = null
                val timers = ArrayList<GameTimer>()
                val insts = ArrayList<Inst>()
                val data = Projects.loadData(dir)

                fun <T> guard(label: String, f: () -> T): T =
                    try { f() } catch (e: TrixError) { throw TrixError("[$label] ${e.message}") }

                fun install(vm: Interpreter) {
                    val b = vm.builtins
                    b["tone"] = { x ->
                        Audio.tone((x.getOrNull(0) as? Double) ?: 660.0, ((x.getOrNull(1) as? Double) ?: 100.0).toInt()); null
                    }
                    b["beep"] = { _ -> Audio.tone(660.0, 90); null }
                    b["vibrate"] = { x -> vibrate(ctx, (x.getOrNull(0) as? Double)?.toLong() ?: 40L); null }
                    b["restart"] = { _ -> restart = true; null }
                    b["goto_scene"] = { x ->
                        pendingScene = x.getOrNull(0) as? String ?: throw TrixError("goto_scene(\"scenes/fase2.scene\")")
                        restart = true; null
                    }
                    b["find"] = { x -> val n = x.getOrNull(0); objs.firstOrNull { it.name == n } }
                    b["clicked"] = { x ->
                        val o = x.getOrNull(0) as? GameObject ?: throw TrixError("clicked(obj) precisa de um objeto")
                        touch.tapped && Math.abs(touch.x - o.x) <= o.bw() / 2 && Math.abs(touch.y - o.y) <= o.bh() / 2
                    }
                    b["after"] = { x ->
                        val fn = x.getOrNull(1) as? String ?: throw TrixError("after(segundos, \"funcao\")")
                        timers.add(GameTimer(time + ((x.getOrNull(0) as? Double) ?: 1.0), 0.0, fn, vm)); null
                    }
                    b["every"] = { x ->
                        val fn = x.getOrNull(1) as? String ?: throw TrixError("every(segundos, \"funcao\")")
                        val s = ((x.getOrNull(0) as? Double) ?: 1.0).coerceAtLeast(0.01)
                        timers.add(GameTimer(time + s, s, fn, vm)); null
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
                    b["follow"] = { x ->
                        cam.target = x.getOrNull(0) as? GameObject
                        cam.smooth = (x.getOrNull(1) as? Double) ?: 0.0
                        null
                    }
                    b["unfollow"] = { _ -> cam.target = null; cam3.target = null; null }
                    b["follow3d"] = { x ->
                        cam3.target = x.getOrNull(0) as? GameObject
                        cam3.dist = (x.getOrNull(1) as? Double) ?: 8.0
                        cam3.height = (x.getOrNull(2) as? Double) ?: 4.0
                        cam3.smooth = (x.getOrNull(3) as? Double) ?: 0.0
                        null
                    }
                    b["play"] = { x ->
                        val o = x.getOrNull(0) as? GameObject ?: throw TrixError("play(obj, \"animacao\")")
                        val n = x.getOrNull(1) as? String ?: throw TrixError("play(obj, \"animacao\")")
                        if (o.anim != n) { o.anim = n; o.frameT = 0.0 }
                        o.playing = true
                        null
                    }
                    b["tile_get"] = { x ->
                        val m = x.getOrNull(0) as? GameObject ?: throw TrixError("tile_get(mapa, x, y)")
                        val tx = (x.getOrNull(1) as? Double) ?: 0.0
                        val ty = (x.getOrNull(2) as? Double) ?: 0.0
                        m.tileAt(Math.floor((tx - m.x) / m.tileSize).toInt(), Math.floor((ty - m.y) / m.tileSize).toInt()).toDouble()
                    }
                    b["tile_set"] = { x ->
                        val m = x.getOrNull(0) as? GameObject ?: throw TrixError("tile_set(mapa, x, y, id)")
                        val tx = (x.getOrNull(1) as? Double) ?: 0.0
                        val ty = (x.getOrNull(2) as? Double) ?: 0.0
                        val id = ((x.getOrNull(3) as? Double) ?: 0.0).toInt().coerceIn(0, TileSet.count - 1)
                        m.setTile(Math.floor((tx - m.x) / m.tileSize).toInt(), Math.floor((ty - m.y) / m.tileSize).toInt(), id)
                        null
                    }
                    b["tile_name"] = { x -> TileSet.name(((x.getOrNull(0) as? Double) ?: 0.0).toInt()) }
                    b["tile_solid"] = { x -> TileSet.solid(((x.getOrNull(0) as? Double) ?: 0.0).toInt()) }
                    b["overlaps3d"] = { x ->
                        val p = x.getOrNull(0) as? GameObject; val q = x.getOrNull(1) as? GameObject
                        if (p == null || q == null) throw TrixError("overlaps3d(a, b) precisa de 2 objetos")
                        Math.abs(p.x - q.x) * 2 < p.w + q.w && Math.abs(p.y - q.y) * 2 < p.h + q.h && Math.abs(p.z - q.z) * 2 < p.d + q.d
                    }
                    b["dist3d"] = { x ->
                        val p = x.getOrNull(0) as? GameObject; val q = x.getOrNull(1) as? GameObject
                        if (p == null || q == null) throw TrixError("dist3d(a, b) precisa de 2 objetos")
                        Math.sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y) + (p.z - q.z) * (p.z - q.z))
                    }
                }

                fun startInst(i: Inst) { if (i.vm.has("start")) guard(i.label) { i.vm.call("start", emptyList()) } }

                var buildFn: ((String, GameObject?, String) -> Inst)? = null

                fun installAll(vm: Interpreter) {
                    install(vm)
                    vm.builtins["spawn"] = { x ->
                        val src = x.getOrNull(0) as? GameObject ?: throw TrixError("spawn(obj) precisa de um objeto")
                        val c = src.copy()
                        spawnId++
                        c.name = src.name + "_" + spawnId
                        c.visible = true
                        objs.add(c)
                        if (c.script.isNotEmpty()) buildFn?.let { bf -> startInst(bf(c.script, c, c.script)) }
                        c
                    }
                    vm.builtins["destroy"] = { x ->
                        (x.getOrNull(0) as? GameObject)?.let { o -> objs.remove(o); insts.removeAll { it.self === o } }
                        null
                    }
                    vm.builtins["call_on"] = { x ->
                        val o = x.getOrNull(0) as? GameObject ?: throw TrixError("call_on(obj, \"funcao\", args...)")
                        val fn = x.getOrNull(1) as? String ?: throw TrixError("call_on(obj, \"funcao\", args...)")
                        val target = insts.firstOrNull { it.self === o }
                        if (target != null && target.vm.has(fn)) guard(target.label) { target.vm.call(fn, x.drop(2)) } else null
                    }
                }

                // cria uma instância: script da cena (self = null) ou de um objeto (self = objeto)
                fun build(label: String, self: GameObject?, path: String): Inst {
                    val vm = Interpreter { l -> logs.add(l); while (logs.size > 6) logs.removeAt(0) }
                    objs.forEach { vm.globals.vars[it.name] = it }
                    val g = vm.globals.vars
                    g["touch"] = touch; g["input"] = input; g["game"] = game
                    g["camera"] = if (is3d) cam3 else cam
                    g["screen"] = ScreenProps(ww.toDouble(), wh.toDouble())
                    g["PI"] = Math.PI; g["time"] = 0.0; g["self"] = self
                    installAll(vm)
                    val inst = Inst(vm, self, label)
                    insts.add(inst)
                    guard(label) { vm.load(readScript(path)) }
                    return inst
                }
                buildFn = ::build

                if (sceneData.script.isNotEmpty()) build(sceneData.script, null, sceneData.script)
                for (o in objs.toList()) if (o.script.isNotEmpty()) build(o.script, o, o.script)
                for (i in insts.toList()) startInst(i)

                var last = withFrameNanos { it }
                var fpsT = 0.0
                var fpsN = 0
                while (isActive && !restart) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9).coerceAtMost(0.05)
                    last = now
                    time += dt
                    for (i in insts) i.vm.globals.vars["time"] = time
                    input.beginFrame()

                    for (i in insts.toList()) {
                        if (restart) break
                        if (i.vm.has("update")) guard(i.label) { i.vm.call("update", listOf(dt)) }
                    }

                    if (timers.isNotEmpty() && !restart) {
                        val due = timers.filter { it.at <= time }
                        for (t in due) {
                            if (t.vm.has(t.fn)) t.vm.call(t.fn, emptyList())
                            if (t.every > 0) t.at += t.every else timers.remove(t)
                        }
                    }

                    if (is3d) { Physics3D.step(objs, dt, settings.gravity); cam3.update(dt) }
                    else { Physics.step(objs, dt, settings.gravity); cam.update(dt) }
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
            val base = minOf(size.width / ww, size.height / wh)
            val fx = (size.width - ww * base) / 2f
            val fy = (size.height - wh * base) / 2f
            if (is3d) {
                drawRect(Color(settings.bg), Offset.Zero, size)
                xf.sc = base; xf.ox = fx; xf.oy = fy
                draw3D(objs, cam3.x, cam3.y, cam3.z, cam3.yaw, cam3.pitch, cam3.fov, false, null)
                drawObjects(objs, base, fx, fy, 1, false)
            } else {
                xf.sc = base * cam.zoom.toFloat()
                xf.ox = size.width / 2f - cam.x.toFloat() * xf.sc
                xf.oy = size.height / 2f - cam.y.toFloat() * xf.sc
                drawRect(Color(settings.bg), Offset(fx, fy), Size(ww * base, wh * base))
                clipRect(fx, fy, fx + ww * base, fy + wh * base) {
                    drawObjects(objs, xf.sc, xf.ox, xf.oy, 0, settings.showCollisions)
                    drawObjects(objs, base, fx, fy, 1, false)
                }
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

        if (settings.gamepad) {
            Row(
                Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom
            ) {
                PadButton("◀", 64.dp) { input.left = it }
                if (is3d) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PadButton("▲", 64.dp) { input.up = it }
                        PadButton("▼", 64.dp) { input.down = it }
                    }
                }
                PadButton("▶", 64.dp) { input.right = it }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom
            ) {
                PadButton("B", 54.dp) { input.b = it }
                PadButton("A", 72.dp) { input.a = it }
            }
        }

        Column(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 52.dp, start = 10.dp)) {
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
