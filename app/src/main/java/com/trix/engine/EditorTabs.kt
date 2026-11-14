package com.trix.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trix.engine.lang.Parser
import com.trix.engine.lang.TrixError
import com.trix.engine.lang.lex
import java.io.File

// ------------------------------------------------------------------ PROJETO

private val BG_SWATCHES = listOf("#0D1020", "#000000", "#1E293B", "#FFFFFF", "#14532D", "#7C2D12", "#312E81", "#87CEEB", "#5C94FC", "#2563EB")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(dir: File, s: ProjectSettings, onExport: () -> Unit, onChange: (ProjectSettings) -> Unit) {
    @Composable fun Title(t: String) = Text(t, fontWeight = FontWeight.Bold, color = Cyan, fontSize = 15.sp)
    val grey = Color(0xFF94A3B8)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Title("Tela do jogo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = s.landscape, onClick = { if (!s.landscape) onChange(s.copy(worldW = s.worldH, worldH = s.worldW)) }, label = { Text("Horizontal") })
            FilterChip(selected = !s.landscape, onClick = { if (s.landscape) onChange(s.copy(worldW = s.worldH, worldH = s.worldW)) }, label = { Text("Vertical") })
        }
        Text("Resoluções prontas", fontSize = 11.sp, color = grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("800×450" to (800 to 450), "1280×720" to (1280 to 720), "800×600" to (800 to 600),
                "450×800" to (450 to 800), "720×1280" to (720 to 1280), "600×600" to (600 to 600)
            ).forEach { (label, wh) ->
                AssistChip(onClick = { onChange(s.copy(worldW = wh.first, worldH = wh.second)) }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            NumField("Largura da tela", s.worldW.toDouble(), { if (it >= 1) onChange(s.copy(worldW = it.toInt().coerceAtMost(4000))) }, Modifier.weight(1f))
            NumField("Altura da tela", s.worldH.toDouble(), { if (it >= 1) onChange(s.copy(worldH = it.toInt().coerceAtMost(4000))) }, Modifier.weight(1f))
        }
        Text("O mundo pode ser maior que a tela: use a câmera (follow / camera.x).", fontSize = 11.sp, color = grey)
        Text("Cor de fundo", fontSize = 11.sp, color = grey)
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

        Title("Controles")
        SwitchRow("Botões na tela (setas + A/B)", s.gamepad) { onChange(s.copy(gamepad = it)) }
        Text("No código: input.left  input.right  input.a  input.b  input.a_pressed  input.b_pressed", fontSize = 11.sp, color = grey)

        Title("Física")
        NumField("Gravidade (px/s²)", s.gravity, { onChange(s.copy(gravity = it)) }, Modifier.widthIn(max = 260.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { onChange(s.copy(gravity = 0.0)) }, label = { Text("Nenhuma") })
            AssistChip(onClick = { onChange(s.copy(gravity = 300.0)) }, label = { Text("Lua 300") })
            AssistChip(onClick = { onChange(s.copy(gravity = 787.5)) }, label = { Text("Sonic 787") })
            AssistChip(onClick = { onChange(s.copy(gravity = 1700.0)) }, label = { Text("Mario 1700") })
            AssistChip(onClick = { onChange(s.copy(gravity = 2500.0)) }, label = { Text("Pesado 2500") })
        }
        Text("Corpo \"Dinâmico\" colide com corpos \"Estáticos\", rampas e Tilemaps.", fontSize = 11.sp, color = grey)

        Title("Editor e teste")
        SwitchRow("Mostrar grade", s.showGrid) { onChange(s.copy(showGrid = it)) }
        SwitchRow("Mostrar FPS ao testar", s.showFps) { onChange(s.copy(showFps = it)) }
        SwitchRow("Mostrar colisões ao testar", s.showCollisions) { onChange(s.copy(showCollisions = it)) }
        Text("Tamanho da grade / snap", fontSize = 11.sp, color = grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 16, 20, 32, 50).forEach { g ->
                FilterChip(selected = s.grid == g, onClick = { onChange(s.copy(grid = g)) }, label = { Text("$g") })
            }
        }

        Title("Informações")
        OutlinedTextField(value = s.author, onValueChange = { onChange(s.copy(author = it)) }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        OutlinedTextField(value = s.version, onValueChange = { onChange(s.copy(version = it)) }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        OutlinedTextField(value = s.description, onValueChange = { onChange(s.copy(description = it)) }, label = { Text("Descrição") }, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())

        Title("Exportar")
        Button(onClick = onExport) { Text("Exportar APK…") }
        Text("Pasta: ${dir.path}", fontSize = 11.sp, color = grey, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ CÓDIGO

private val SNIPPETS = listOf(
    "func" to "func |()\n    \nend",
    "if" to "if |\n    \nend",
    "elif" to "elif |\n    ",
    "else" to "else\n    ",
    "while" to "while |\n    \nend",
    "for" to "for i in 1..|\n    \nend",
    "for in" to "for x in |\n    \nend",
    "var" to "var |= 0",
    "( )" to "(|)",
    "[ ]" to "[|]",
    "\" \"" to "\"|\"",
    "=" to " = ",
    "+=" to " += ",
    "." to ".",
    "dt" to "dt",
    "↹" to "    "
)

private val KW_RE = Regex("\\b(var|func|end|if|elif|else|while|for|in|return|break|and|or|not|true|false|null)\\b")
private val FN_RE = Regex("\\b(print|sin|cos|tan|asin|acos|atan2|abs|sqrt|pow|floor|ceil|round|min|max|clamp|lerp|approach|sign|rad|deg|random|str|len|push|pop|remove|contains|overlaps|dist|dist_xy|destroy|spawn|find|clicked|after|every|save|load|restart|tone|beep|vibrate|follow|unfollow|play|tile_get|tile_set|tile_name|tile_solid)\\b")
private val OBJ_RE = Regex("\\b(touch|input|camera|screen|time)\\b")
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
        paint(OBJ_RE, Color(0xFFFDE68A))
        paint(STR_RE, Color(0xFF86EFAC))
        paint(COM_RE, Color(0xFF64748B))
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun CodeTab(script: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
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
var lista = [1, 2, 3]
lista[0] = 10        len(lista)

func start()          # roda 1 vez ao iniciar
end
func update(dt)       # roda todo frame
end

if a > 1 and b < 2 ... elif ... else ... end
while x < 10 ... end
for i in 1..5 ... end      (break sai)
for item in lista ... end

OBJETOS (o nome vira variável)
  x y w h vx vy rotation alpha
  text visible gravity fixed flipx
  color = "#FF8800"   image = "arquivo"
  shape = "box|circle|wedge|capsule|triangle"
FÍSICA (body = "none|static|dynamic")
  stick  stepup  bounce  trigger
  só leitura: grounded ground_angle wall ceiling
ANIMAÇÃO (Sprite Animado)
  play(obj,"correr")   obj.anim = "pular"
  obj.playing  obj.animspeed  obj.frame

ENTRADA, TELA E CÂMERA
  touch.x touch.y touch.down touch.tapped
  input.left right up down a b a_pressed b_pressed
  screen.w screen.h    time
  camera.x camera.y camera.zoom
  follow(obj, suavidade)   unfollow()

TILEMAP
  tile_get(mapa,x,y)  tile_set(mapa,x,y,id)
  tile_name(id)  tile_solid(id)

JOGO
  spawn(obj) clone   destroy(obj)   find("nome")
  clicked(obj)  overlaps(a,b)  dist(a,b)
  after(2,"fn")  every(1,"fn")
  save("k",v)  load("k",padrão)   restart()

SOM / VIBRAÇÃO
  tone(freq, ms)   beep()   vibrate(ms)

MATEMÁTICA E LISTAS
  sin cos tan asin acos atan2 abs sqrt pow
  floor ceil round min max clamp lerp
  approach(v,alvo,passo) sign rad deg random(a,b)
  dist_xy(x1,y1,x2,y2)
  push pop remove contains len str print

FÍSICA ESTILO SONIC
  Marque o corpo como Dinâmico, ligue "Colar no
  chão" (stick) e use rampas (Parte > Rampa).
  Leia sonic.ground_angle (graus, positivo em "/")
  e aplique vx = gsp * cos(rad(ang)) * 60.
  Veja o modelo "Estilo Sonic".

DICAS
- Origem (x,y) = CENTRO (Tilemap = canto).
- Editor: arraste um objeto para mover, use as
  alças brancas para redimensionar, arraste o fundo
  para mover a vista, 2 dedos para zoom.
- Objeto invisível serve de molde p/ spawn().
- Menu Projeto > Exportar APK.
"""

@Composable
fun HelpTab() {
    Text(
        HELP, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    )
}
