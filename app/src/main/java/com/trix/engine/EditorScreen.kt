package com.trix.engine

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.layout.onSizeChanged
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
    val objects = remember { mutableStateListOf<GameObject>().apply { addAll(Projects.loadScene(dir)) } }
    var script by remember { mutableStateOf(TextFieldValue(Projects.readScript(dir))) }
    var rev by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<GameObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(true) }

    fun persist() { Projects.saveScene(dir, objects); Projects.writeScript(dir, script.text); saved = true }
    fun edited() { rev++ }

    LaunchedEffect(rev, script.text) {
        saved = false
        delay(900)
        persist()
    }

    fun unique(base: String): String { var i = 1; while (objects.any { it.name == "$base$i" }) i++; return "$base$i" }

    fun add(type: String) {
        val o = GameObject(unique(type), type)
        when (type) {
            "circle" -> { o.w = 80.0; o.h = 80.0; o.color = android.graphics.Color.parseColor("#A855F7") }
            "text" -> { o.h = 28.0; o.text = "Olá"; o.color = android.graphics.Color.WHITE }
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

    BackHandler(enabled = !playing) { persist(); onClose() }

    if (playing) {
        PlayScreen(objects.toList(), script.text) { playing = false }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { persist(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f)) {
                Text(dir.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Text(if (saved) "salvo ✓" else "salvando…", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Button(onClick = { persist(); playing = true }) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Testar")
            }
            Spacer(Modifier.width(8.dp))
        }
        TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Cyan) {
            listOf("Cena", "Código", "Ajuda").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> SceneTab(objects, rev, sel, snap, { snap = it }, { sel = it }, ::edited, ::add, ::duplicate, ::delete)
                1 -> CodeTab(script) { script = it }
                else -> HelpTab()
            }
        }
    }
}

// ------------------------------------------------------------------ CENA

private val SWATCHES = listOf("#22D3EE", "#A855F7", "#F43F5E", "#FACC15", "#4ADE80", "#FB923C", "#FFFFFF", "#64748B")

@Composable
private fun SceneTab(
    objects: SnapshotStateList<GameObject>, rev: Int, sel: GameObject?, snap: Boolean,
    onSnap: (Boolean) -> Unit, onSel: (GameObject?) -> Unit, onEdit: () -> Unit,
    onAdd: (String) -> Unit, onDup: () -> Unit, onDel: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { SceneCanvas(objects, rev, sel, snap, onSel, onEdit) }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 290.dp).background(Surf)
                .verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onAdd("rect") }, label = { Text("+ Retângulo") })
                AssistChip(onClick = { onAdd("circle") }, label = { Text("+ Círculo") })
                AssistChip(onClick = { onAdd("text") }, label = { Text("+ Texto") })
                AssistChip(onClick = onDup, label = { Text("Duplicar") }, enabled = sel != null)
                AssistChip(onClick = onDel, label = { Text("Apagar") }, enabled = sel != null)
                FilterChip(selected = snap, onClick = { onSnap(!snap) }, label = { Text("Grade 10") })
            }
            Text("Hierarquia", fontSize = 11.sp, color = Color(0xFF94A3B8))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                objects.forEach { o ->
                    FilterChip(selected = o === sel, onClick = { onSel(o) }, label = { Text(o.name) })
                }
                if (objects.isEmpty()) Text("(cena vazia — adicione um objeto)", fontSize = 12.sp)
            }
            if (sel != null) key(sel) { Inspector(sel, rev, onEdit) }
        }
    }
}

@Composable
private fun Inspector(o: GameObject, rev: Int, onEdit: () -> Unit) {
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
        NumField(if (o.type == "text") "Tam." else "Larg.", if (o.type == "text") o.h else o.w,
            { if (o.type == "text") o.h = it else o.w = it; onEdit() }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (o.type != "text") NumField("Alt.", o.h, { o.h = it; onEdit() }, Modifier.weight(1f))
        NumField("Vel X", o.vx, { o.vx = it; onEdit() }, Modifier.weight(1f))
        NumField("Vel Y", o.vy, { o.vy = it; onEdit() }, Modifier.weight(1f))
        if (o.type == "text") Spacer(Modifier.weight(0.0001f))
    }
    if (o.type == "text") {
        var t by remember { mutableStateOf(o.text) }
        OutlinedTextField(
            value = t, onValueChange = { t = it; o.text = it; onEdit() },
            label = { Text("Texto") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.width(10.dp))
        Text("Visível no início")
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
        if (!o.visible && false) continue
        if (abs(wx - o.x) <= o.bw() / 2 + 8 && abs(wy - o.y) <= o.bh() / 2 + 8) return o
    }
    return null
}

@Composable
private fun SceneCanvas(
    objects: SnapshotStateList<GameObject>, rev: Int, sel: GameObject?, snap: Boolean,
    onSel: (GameObject?) -> Unit, onEdit: () -> Unit
) {
    val xf = remember { ViewXf() }
    val selCb by rememberUpdatedState(onSel)
    val editCb by rememberUpdatedState(onEdit)
    val snapNow by rememberUpdatedState(snap)

    Canvas(
        Modifier.fillMaxSize().background(Dark)
            .onSizeChanged { s ->
                xf.sc = minOf(s.width / WW, s.height / WH) * 0.94f
                xf.ox = (s.width - WW * xf.sc) / 2f
                xf.oy = (s.height - WH * xf.sc) / 2f
            }
            .pointerInput(Unit) { detectTapGestures { off -> selCb(hitTest(objects, xf, off)) } }
            .pointerInput(Unit) {
                var target: GameObject? = null
                var rx = 0.0
                var ry = 0.0
                detectDragGestures(
                    onDragStart = { off ->
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
                            rx += d.x / xf.sc
                            ry += d.y / xf.sc
                            t.x = if (snapNow) Math.round(rx / 10.0) * 10.0 else rx
                            t.y = if (snapNow) Math.round(ry / 10.0) * 10.0 else ry
                            editCb()
                        }
                    }
                )
            }
    ) {
        val r = rev
        val sc = xf.sc; val ox = xf.ox; val oy = xf.oy
        drawRect(Color(0xFF12152A), Offset(ox, oy), Size(WW * sc, WH * sc))
        var g = 0f
        while (g <= WW) { drawLine(Color(0x14FFFFFF), Offset(ox + g * sc, oy), Offset(ox + g * sc, oy + WH * sc)); g += 50f }
        g = 0f
        while (g <= WH) { drawLine(Color(0x14FFFFFF), Offset(ox, oy + g * sc), Offset(ox + WW * sc, oy + g * sc)); g += 50f }
        clipRect(ox, oy, ox + WW * sc, oy + WH * sc) { drawObjects(objects, sc, ox, oy) }
        drawRect(Color(0x66FFFFFF), Offset(ox, oy), Size(WW * sc, WH * sc), style = Stroke(2f))
        sel?.let { o ->
            val bw = o.bw().toFloat() * sc; val bh = o.bh().toFloat() * sc
            drawRect(Cyan, Offset(ox + o.x.toFloat() * sc - bw / 2, oy + o.y.toFloat() * sc - bh / 2), Size(bw, bh), style = Stroke(3f))
        }
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
private val FN_RE = Regex("\\b(print|sin|cos|atan2|abs|sqrt|pow|floor|ceil|round|min|max|clamp|lerp|random|str|overlaps|dist|destroy|beep|vibrate)\\b")
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
            Modifier.fillMaxWidth().background(Surf).horizontalScroll(rememberScrollState()).padding(6.dp),
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
            modifier = Modifier.fillMaxWidth().background(Surf).padding(8.dp)
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

CONDIÇÃO / LAÇOS
if a > 1 and b < 2
    ...
elif a == 0
    ...
else
    ...
end

while x < 10 ... end
for i in 1..5 ... end   (break sai)

OBJETOS DA CENA
O nome do objeto vira variável:
  player.x  player.y  player.w  player.h
  player.vx player.vy (velocidade)
  player.text   player.visible
  player.color = "#FF8800"

ENTRADA E TELA
  touch.x  touch.y  touch.down  touch.tapped
  screen.w (400)  screen.h (700)
  time  (segundos desde o início)

FUNÇÕES
  print(x)  str(x)
  sin cos atan2 abs sqrt pow
  floor ceil round min max clamp
  lerp(a,b,t)  random(a,b)
  overlaps(a,b)  dist(a,b)
  destroy(obj)  beep()  vibrate(ms)

DICAS
- Origem (x,y) é o CENTRO do objeto.
- O mundo mede 400 x 700.
- Objetos com vx/vy se movem sozinhos.
- Os arquivos ficam em
  TrixEngine/Projects/<nome>/
  (main.trix e scene.json)
"""

@Composable
private fun HelpTab() {
    Text(
        HELP, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    )
}
