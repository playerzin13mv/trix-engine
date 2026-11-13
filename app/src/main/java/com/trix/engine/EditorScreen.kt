package com.trix.engine

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.lang.Parser
import com.trix.engine.lang.TrixError
import com.trix.engine.lang.lex
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

@Composable
fun EditorScreen(dir: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val objects = remember { mutableStateListOf<GameObject>().apply { addAll(Projects.loadScene(dir)) } }
    var settings by remember { mutableStateOf(Projects.loadSettings(dir)) }
    var script by remember { mutableStateOf(TextFieldValue(Projects.readScript(dir))) }
    var rev by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<GameObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(true) }
    var pickFor by remember { mutableStateOf<GameObject?>(null) }
    val history = remember { mutableStateListOf(snapshotOf(objects)) }
    val redo = remember { mutableStateListOf<String>() }
    remember { Images.dir = Projects.assetsDir(dir) }

    fun persist() {
        Projects.saveScene(dir, objects)
        Projects.saveSettings(dir, settings)
        Projects.writeScript(dir, script.text)
        saved = true
    }
    fun edited() { rev++ }

    LaunchedEffect(rev, script.text, settings) {
        saved = false
        delay(900)
        persist()
    }

    // histórico para desfazer/refazer
    LaunchedEffect(rev) {
        delay(450)
        val j = snapshotOf(objects)
        if (history.last() != j) {
            history.add(j)
            if (history.size > 60) history.removeAt(0)
            redo.clear()
        }
    }
    fun undo() {
        if (history.size > 1) {
            redo.add(history.removeAt(history.size - 1))
            restoreFrom(history.last(), objects); sel = null; rev++
        }
    }
    fun redoIt() {
        if (redo.isNotEmpty()) {
            val j = redo.removeAt(redo.size - 1)
            history.add(j); restoreFrom(j, objects); sel = null; rev++
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val o = pickFor
        if (uri != null && o != null) {
            try {
                val type = ctx.contentResolver.getType(uri)
                val ext = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "png"
                val name = "img_${System.currentTimeMillis()}.$ext"
                val outDir = Projects.assetsDir(dir); outDir.mkdirs()
                ctx.contentResolver.openInputStream(uri)?.use { inp ->
                    File(outDir, name).outputStream().use { out -> inp.copyTo(out) }
                }
                o.image = name; edited()
            } catch (e: Exception) { }
        }
    }

    fun unique(base: String): String { var i = 1; while (objects.any { it.name == "$base$i" }) i++; return "$base$i" }

    fun add(type: String) {
        val o = GameObject(unique(type), type)
        o.x = settings.worldW / 2.0; o.y = settings.worldH / 2.0
        when (type) {
            "circle" -> { o.w = 80.0; o.h = 80.0; o.color = android.graphics.Color.parseColor("#A855F7") }
            "text" -> { o.h = 28.0; o.text = "Olá"; o.color = android.graphics.Color.WHITE }
            "sprite" -> { o.w = 100.0; o.h = 100.0 }
        }
        objects.add(o); sel = o; edited()
    }
    fun duplicate() {
        val o = sel ?: return
        val c = o.copy()
        c.name = unique(o.name.trimEnd { it.isDigit() }.ifEmpty { "obj" })
        c.x += 20; c.y += 20
        objects.add(c); sel = c; edited()
    }
    fun delete() {
        val o = sel ?: return
        objects.remove(o); sel = null; edited()
    }
    fun move(delta: Int) {
        val o = sel ?: return
        val i = objects.indexOf(o)
        val j = i + delta
        if (i >= 0 && j in objects.indices) { objects.removeAt(i); objects.add(j, o); edited() }
    }

    BackHandler(enabled = !playing) { persist(); onClose() }

    if (playing) {
        PlayScreen(dir, objects.toList(), settings, script.text) { playing = false }
        return
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { persist(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text(dir.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.widthIn(max = 150.dp))
            Text(if (saved) " ✓" else " …", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            listOf("Cena", "Código", "Projeto", "Ajuda").forEachIndexed { i, t ->
                TextButton(
                    onClick = { tab = i },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (tab == i) Cyan else Color(0xFF94A3B8))
                ) { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { undo() }, enabled = history.size > 1) { Text("↶", fontSize = 20.sp) }
            TextButton(onClick = { redoIt() }, enabled = redo.isNotEmpty()) { Text("↷", fontSize = 20.sp) }
            Button(onClick = { persist(); playing = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Testar")
            }
            Spacer(Modifier.width(6.dp))
        }
        HorizontalDivider(color = Surf2)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> SceneTab(
                    objects, rev, sel, snap, settings, { snap = it }, { sel = it }, ::edited,
                    ::add, ::duplicate, ::delete, ::move,
                    { sel?.let { pickFor = it; picker.launch("image/*") } }
                )
                1 -> CodeTab(script) { script = it }
                2 -> SettingsTab(dir, settings) { settings = it }
                else -> HelpTab()
            }
        }
    }
}

// ------------------------------------------------------------------ CENA

private val SWATCHES = listOf("#22D3EE", "#A855F7", "#F43F5E", "#FACC15", "#4ADE80", "#FB923C", "#FFFFFF", "#64748B")
private val BG_SWATCHES = listOf("#0D1020", "#000000", "#1E293B", "#FFFFFF", "#14532D", "#7C2D12", "#312E81", "#87CEEB")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneTab(
    objects: SnapshotStateList<GameObject>, rev: Int, sel: GameObject?, snap: Boolean, settings: ProjectSettings,
    onSnap: (Boolean) -> Unit, onSel: (GameObject?) -> Unit, onEdit: () -> Unit,
    onAdd: (String) -> Unit, onDup: () -> Unit, onDel: () -> Unit, onMove: (Int) -> Unit,
    onPickImage: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { SceneCanvas(objects, rev, sel, snap, settings, onSel, onEdit) }
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Surf)
                .verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onAdd("rect") }, label = { Text("+ Retângulo") })
                AssistChip(onClick = { onAdd("circle") }, label = { Text("+ Círculo") })
                AssistChip(onClick = { onAdd("text") }, label = { Text("+ Texto") })
                AssistChip(onClick = { onAdd("sprite") }, label = { Text("+ Sprite") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onDup, label = { Text("Duplicar") }, enabled = sel != null)
                AssistChip(onClick = onDel, label = { Text("Apagar") }, enabled = sel != null)
                AssistChip(onClick = { onMove(1) }, label = { Text("↑ Frente") }, enabled = sel != null)
                AssistChip(onClick = { onMove(-1) }, label = { Text("↓ Trás") }, enabled = sel != null)
                FilterChip(selected = snap, onClick = { onSnap(!snap) }, label = { Text("Snap ${settings.grid}") })
            }
            Text("Hierarquia (ordem de desenho)", fontSize = 11.sp, color = Color(0xFF94A3B8))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                objects.forEach { o ->
                    FilterChip(selected = o === sel, onClick = { onSel(o) }, label = { Text(o.name) })
                }
                if (objects.isEmpty()) Text("(cena vazia — adicione um objeto)", fontSize = 12.sp)
            }
            if (sel != null) key(sel) { Inspector(sel, rev, onEdit, onPickImage) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Inspector(o: GameObject, rev: Int, onEdit: () -> Unit, onPickImage: () -> Unit) {
    HorizontalDivider(color = Surf2)
    Text("Inspetor — ${o.type}", fontSize = 11.sp, color = Color(0xFF94A3B8))
    var name by remember { mutableStateOf(o.name) }
    OutlinedTextField(
        value = name,
        onValueChange = { raw ->
            var n = raw.filter { it.isLetterOrDigit() || it == '_' }
            if (n.firstOrNull()?.isDigit() == true) n = "_$n"
            name = n
            if (n.isNotEmpty()) { o.name = n; onEdit() }
        },
        label = { Text("Nome (usado no código)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumField("X", o.x, { o.x = it; onEdit() }, Modifier.weight(1f))
        NumField("Y", o.y, { o.y = it; onEdit() }, Modifier.weight(1f))
    }
    if (o.type == "text") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Tamanho", o.h, { o.h = it; onEdit() }, Modifier.weight(1f))
            NumField("Rotação", o.rotation, { o.rotation = it; onEdit() }, Modifier.weight(1f))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Largura", o.w, { o.w = it; onEdit() }, Modifier.weight(1f))
            NumField("Altura", o.h, { o.h = it; onEdit() }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Rotação", o.rotation, { o.rotation = it; onEdit() }, Modifier.weight(1f))
            NumField("Alpha 0-1", o.alpha, { o.alpha = it.coerceIn(0.0, 1.0); onEdit() }, Modifier.weight(1f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumField("Vel X", o.vx, { o.vx = it; onEdit() }, Modifier.weight(1f))
        NumField("Vel Y", o.vy, { o.vy = it; onEdit() }, Modifier.weight(1f))
    }
    if (o.type == "text") {
        var t by remember { mutableStateOf(o.text) }
        OutlinedTextField(
            value = t, onValueChange = { t = it; o.text = it; onEdit() },
            label = { Text("Texto") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickImage) { Text(if (o.image.isEmpty()) "Escolher imagem…" else "Trocar imagem") }
            if (o.image.isNotEmpty()) TextButton(onClick = { o.image = ""; onEdit() }) { Text("Remover") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SWATCHES.forEach { hex ->
            val c = android.graphics.Color.parseColor(hex)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (o.color == c) 3.dp else 1.dp, if (o.color == c) Color.White else Color(0x55FFFFFF), CircleShape)
                    .clickable { o.color = c; onEdit() }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = o.visible, onCheckedChange = { o.visible = it; onEdit() })
        Spacer(Modifier.width(10.dp)); Text("Visível no início")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = o.gravity, onCheckedChange = { o.gravity = it; onEdit() })
        Spacer(Modifier.width(10.dp)); Text("Afetado pela gravidade")
    }
}

@Composable
private fun NumField(label: String, v: Double, onVal: (Double) -> Unit, modifier: Modifier) {
    fun f(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(java.util.Locale.US, d)
    var t by remember { mutableStateOf(f(v)) }
    LaunchedEffect(v) { if (t.toDoubleOrNull() != v) t = f(v) }
    OutlinedTextField(
        value = t,
        onValueChange = { t = it; it.toDoubleOrNull()?.let(onVal) },
        label = { Text(label, fontSize = 11.sp) },
        textStyle = TextStyle(fontSize = 14.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

private fun hitTest(list: List<GameObject>, xf: ViewXf, p: Offset): GameObject? {
    val wx = (p.x - xf.ox) / xf.sc
    val wy = (p.y - xf.oy) / xf.sc
    for (i in list.indices.reversed()) {
        val o = list[i]
        if (abs(wx - o.x) <= o.bw() / 2 + 8 && abs(wy - o.y) <= o.bh() / 2 + 8) return o
    }
    return null
}

@Composable
private fun SceneCanvas(
    objects: SnapshotStateList<GameObject>, rev: Int, sel: GameObject?, snap: Boolean, settings: ProjectSettings,
    onSel: (GameObject?) -> Unit, onEdit: () -> Unit
) {
    val xf = remember { ViewXf() }
    val selCb by rememberUpdatedState(onSel)
    val editCb by rememberUpdatedState(onEdit)
    val snapNow by rememberUpdatedState(snap)
    val setNow by rememberUpdatedState(settings)

    Canvas(
        Modifier.fillMaxSize().background(Dark)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    xf.fit(setNow.worldW.toFloat(), setNow.worldH.toFloat(), 0.94f)
                    selCb(hitTest(objects, xf, off))
                }
            }
            .pointerInput(Unit) {
                var target: GameObject? = null
                var rx = 0.0
                var ry = 0.0
                detectDragGestures(
                    onDragStart = { off ->
                        xf.fit(setNow.worldW.toFloat(), setNow.worldH.toFloat(), 0.94f)
                        val h = hitTest(objects, xf, off)
                        target = h
                        if (h != null) { rx = h.x; ry = h.y; selCb(h) }
                    },
                    onDragEnd = { target = null },
                    onDragCancel = { target = null },
                    onDrag = { change, d ->
                        change.consume()
                        val t = target
                        if (t != null) {
                            val g = setNow.grid.toDouble().coerceAtLeast(1.0)
                            rx += d.x / xf.sc
                            ry += d.y / xf.sc
                            t.x = if (snapNow) Math.round(rx / g) * g else rx
                            t.y = if (snapNow) Math.round(ry / g) * g else ry
                            editCb()
                        }
                    }
                )
            }
    ) {
        val r = rev
        val ww = settings.worldW.toFloat()
        val wh = settings.worldH.toFloat()
        xf.cw = size.width; xf.ch = size.height
        xf.fit(ww, wh, 0.94f)
        val sc = xf.sc; val ox = xf.ox; val oy = xf.oy

        drawRect(Color(settings.bg), Offset(ox, oy), Size(ww * sc, wh * sc))
        if (settings.showGrid) {
            val gs = settings.grid.coerceAtLeast(1)
            if (gs * sc >= 5f) {
                var gx = 0
                while (gx <= settings.worldW) { drawLine(Color(0x0DFFFFFF), Offset(ox + gx * sc, oy), Offset(ox + gx * sc, oy + wh * sc)); gx += gs }
                var gy = 0
                while (gy <= settings.worldH) { drawLine(Color(0x0DFFFFFF), Offset(ox, oy + gy * sc), Offset(ox + ww * sc, oy + gy * sc)); gy += gs }
            }
            var mx = 0
            while (mx <= settings.worldW) { drawLine(Color(0x1CFFFFFF), Offset(ox + mx * sc, oy), Offset(ox + mx * sc, oy + wh * sc)); mx += 50 }
            var my = 0
            while (my <= settings.worldH) { drawLine(Color(0x1CFFFFFF), Offset(ox, oy + my * sc), Offset(ox + ww * sc, oy + my * sc)); my += 50 }
        }
        clipRect(ox, oy, ox + ww * sc, oy + wh * sc) { drawObjects(objects, sc, ox, oy) }
        drawRect(Color(0x66FFFFFF), Offset(ox, oy), Size(ww * sc, wh * sc), style = Stroke(2f))
        sel?.let { o ->
            val bw = o.bw().toFloat() * sc
            val bh = o.bh().toFloat() * sc
            drawRect(Cyan, Offset(ox + o.x.toFloat() * sc - bw / 2, oy + o.y.toFloat() * sc - bh / 2), Size(bw, bh), style = Stroke(3f))
        }
    }
}

// ------------------------------------------------------------------ PROJETO

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab(dir: File, s: ProjectSettings, onChange: (ProjectSettings) -> Unit) {
    @Composable fun Title(t: String) = Text(t, fontWeight = FontWeight.Bold, color = Cyan, fontSize = 15.sp)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Title("Tela do jogo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = s.landscape, onClick = { if (!s.landscape) onChange(s.copy(worldW = s.worldH, worldH = s.worldW)) }, label = { Text("Horizontal") })
            FilterChip(selected = !s.landscape, onClick = { if (s.landscape) onChange(s.copy(worldW = s.worldH, worldH = s.worldW)) }, label = { Text("Vertical") })
        }
        Text("Resoluções prontas", fontSize = 11.sp, color = Color(0xFF94A3B8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("800×450" to (800 to 450), "1280×720" to (1280 to 720), "800×600" to (800 to 600),
                "450×800" to (450 to 800), "720×1280" to (720 to 1280), "600×600" to (600 to 600)
            ).forEach { (label, wh) ->
                AssistChip(onClick = { onChange(s.copy(worldW = wh.first, worldH = wh.second)) }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            NumField("Largura", s.worldW.toDouble(), { if (it >= 1) onChange(s.copy(worldW = it.toInt().coerceAtMost(4000))) }, Modifier.weight(1f))
            NumField("Altura", s.worldH.toDouble(), { if (it >= 1) onChange(s.copy(worldH = it.toInt().coerceAtMost(4000))) }, Modifier.weight(1f))
        }
        Text("Cor de fundo", fontSize = 11.sp, color = Color(0xFF94A3B8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BG_SWATCHES.forEach { hex ->
                val c = android.graphics.Color.parseColor(hex)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (s.bg == c) 3.dp else 1.dp, if (s.bg == c) Cyan else Color(0x55FFFFFF), CircleShape)
                        .clickable { onChange(s.copy(bg = c)) }
                )
            }
        }

        Title("Física")
        NumField("Gravidade (px/s²)", s.gravity, { onChange(s.copy(gravity = it)) }, Modifier.widthIn(max = 260.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { onChange(s.copy(gravity = 0.0)) }, label = { Text("Nenhuma") })
            AssistChip(onClick = { onChange(s.copy(gravity = 300.0)) }, label = { Text("Lua 300") })
            AssistChip(onClick = { onChange(s.copy(gravity = 1200.0)) }, label = { Text("Normal 1200") })
            AssistChip(onClick = { onChange(s.copy(gravity = 2500.0)) }, label = { Text("Pesado 2500") })
        }
        Text("Marque \"Afetado pela gravidade\" nos objetos que devem cair.", fontSize = 11.sp, color = Color(0xFF94A3B8))

        Title("Editor e teste")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.showGrid, onCheckedChange = { onChange(s.copy(showGrid = it)) })
            Spacer(Modifier.width(10.dp)); Text("Mostrar grade")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.showFps, onCheckedChange = { onChange(s.copy(showFps = it)) })
            Spacer(Modifier.width(10.dp)); Text("Mostrar FPS ao testar")
        }
        Text("Tamanho da grade / snap", fontSize = 11.sp, color = Color(0xFF94A3B8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 20, 50).forEach { g ->
                FilterChip(selected = s.grid == g, onClick = { onChange(s.copy(grid = g)) }, label = { Text("$g") })
            }
        }

        Title("Informações")
        OutlinedTextField(value = s.author, onValueChange = { onChange(s.copy(author = it)) }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        OutlinedTextField(value = s.version, onValueChange = { onChange(s.copy(version = it)) }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        OutlinedTextField(value = s.description, onValueChange = { onChange(s.copy(description = it)) }, label = { Text("Descrição") }, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        Text("Pasta: ${dir.path}", fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ CÓDIGO

private val SNIPPETS = listOf(
    "func" to "func |()\n    \nend",
    "if" to "if |\n    \nend",
    "else" to "else\n    ",
    "while" to "while |\n    \nend",
    "for" to "for i in 1..|\n    \nend",
    "var" to "var |= 0",
    "( )" to "(|)",
    "\" \"" to "\"|\"",
    "=" to " = ",
    "+=" to " += ",
    "." to ".",
    "dt" to "dt",
    "↹" to "    "
)

private val KW_RE = Regex("\\b(var|func|end|if|elif|else|while|for|in|return|break|and|or|not|true|false|null)\\b")
private val FN_RE = Regex("\\b(print|sin|cos|atan2|abs|sqrt|pow|floor|ceil|round|min|max|clamp|lerp|random|str|overlaps|dist|destroy|spawn|find|clicked|after|every|save|load|restart|tone|beep|vibrate)\\b")
private val NUM_RE = Regex("\\b\\d+(\\.\\d+)?\\b")
private val STR_RE = Regex("\"[^\"\\n]*\"")
private val COM_RE = Regex("(?m)(?:^|\\s)#.*")

private class TrixHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(s)
        fun paint(re: Regex, c: Color) { for (m in re.findAll(s)) b.addStyle(SpanStyle(color = c), m.range.first, m.range.last + 1) }
        paint(NUM_RE, Color(0xFFFB923C))
        paint(KW_RE, Color(0xFFC084FC))
        paint(FN_RE, Color(0xFF22D3EE))
        paint(STR_RE, Color(0xFF86EFAC))
        paint(COM_RE, Color(0xFF64748B))
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun CodeTab(script: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    val err = remember(script.text) {
        try { Parser(lex(script.text)).program(); null } catch (e: TrixError) { e.message }
    }
    fun insert(snip: String) {
        val caret = snip.indexOf('|')
        val clean = snip.replace("|", "")
        val s = script.selection
        val t = script.text.replaceRange(s.min, s.max, clean).toString()
        onChange(TextFieldValue(t, TextRange(s.min + (if (caret >= 0) caret else clean.length))))
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Surf).horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SNIPPETS.forEach { (label, s) -> AssistChip(onClick = { insert(s) }, label = { Text(label, fontFamily = FontFamily.Monospace) }) }
        }
        BasicTextField(
            value = script, onValueChange = onChange,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White),
            cursorBrush = SolidColor(Cyan),
            visualTransformation = remember { TrixHighlight() },
            modifier = Modifier.weight(1f).fillMaxWidth().background(Dark).padding(10.dp)
        )
        Text(
            err ?: "✓ sem erros de sintaxe",
            color = if (err == null) Color(0xFF86EFAC) else Color(0xFFF87171),
            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().background(Surf).padding(6.dp)
        )
    }
}

// ------------------------------------------------------------------ AJUDA

private const val HELP = """TRIXSCRIPT — referência rápida

# comentário
var vida = 3
vida += 1

func start()          # roda 1 vez ao iniciar
end

func update(dt)       # roda todo frame
    nave.x += 100 * dt
end

if a > 1 and b < 2 ... elif ... else ... end
while x < 10 ... end
for i in 1..5 ... end      (break sai do laço)

OBJETOS (o nome vira variável)
  x y w h vx vy rotation alpha
  text  visible  gravity
  color = "#FF8800"
  image = "img_123.png"   (arquivo da pasta assets)

ENTRADA E TELA
  touch.x  touch.y  touch.down  touch.tapped
  screen.w  screen.h  (tamanho do mundo)
  time  (segundos desde o início)

JOGO
  spawn(obj)      clona um objeto e devolve o clone
  destroy(obj)    remove do jogo
  find("nome")    acha um objeto pelo nome
  clicked(obj)    toque em cima do objeto (botões)
  overlaps(a,b)   colisão    dist(a,b)  distância
  after(2,"fn")   chama fn daqui a 2 segundos
  every(1,"fn")   chama fn a cada 1 segundo
  save("k",v)  load("k",padrão)   dados salvos
  restart()       reinicia o jogo

SOM E VIBRAÇÃO
  tone(freq, ms)   beep()   vibrate(ms)

MATEMÁTICA
  sin cos atan2 abs sqrt pow floor ceil round
  min max clamp lerp random(a,b) str print

DICAS
- Origem (x,y) é o CENTRO do objeto.
- Orientação, tamanho da tela, gravidade e
  cor de fundo ficam na aba Projeto.
- Objeto com "Visível no início" desligado
  serve de molde para spawn().
- Imagens: selecione um objeto > Escolher imagem.
- Arquivos em TrixEngine/Projects/<nome>/
"""

@Composable
private fun HelpTab() {
    Text(
        HELP, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    )
}
