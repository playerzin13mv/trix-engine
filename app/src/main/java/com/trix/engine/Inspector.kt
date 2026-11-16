package com.trix.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SWATCHES = listOf("#22D3EE", "#A855F7", "#F43F5E", "#FACC15", "#4ADE80", "#FB923C", "#FFFFFF", "#64748B", "#3B82F6", "#16A34A")

@Composable
private fun Section(title: String, startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(Surf2).clickable { open = !open }.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(if (open) "▾ " else "▸ ", fontSize = 12.sp, color = Cyan)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (open) Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private fun typeLabel(o: GameObject) = when (o.type) {
    "part" -> "Parte"; "text" -> "Texto"; "sprite" -> "Sprite"; "anim" -> "Sprite Animado"
    "collision" -> if (o.trigger) "Gatilho" else "Forma de Colisão"; "tilemap" -> "Tilemap"; "mesh" -> "Malha 3D"; else -> o.type
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Inspector(st: EditorState, pick: ((List<String>) -> Unit) -> Unit) {
    DockHeader("Inspetor")
    val o = st.sel
    if (o == null) {
        Text(
            "Nenhum objeto selecionado.\nToque em um objeto na cena ou na lista.",
            fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(12.dp)
        )
        return
    }
    key(o) { InspectorBody(st, o, pick) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectorBody(st: EditorState, o: GameObject, pick: ((List<String>) -> Unit) -> Unit) {
    val rev = st.rev
    val edit = { st.edited() }
    val isShape = o.type == "part" || o.type == "collision"

    if (o.type == "mesh") {
        MeshBody(st, o)
        return
    }

    Section("Objeto") {
        var name by remember { mutableStateOf(o.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { raw ->
                var n = raw.filter { it.isLetterOrDigit() || it == '_' }
                if (n.firstOrNull()?.isDigit() == true) n = "_$n"
                name = n
                if (n.isNotEmpty()) { o.name = n; edit() }
            },
            label = { Text("Nome (usado no código)", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Tipo: ${typeLabel(o)}", fontSize = 11.sp, color = Color(0xFF94A3B8))
        if (isShape) {
            Text("Forma", fontSize = 11.sp, color = Color(0xFF94A3B8))
            val shapes = if (o.type == "collision") listOf("box" to "Caixa", "wedge" to "Rampa")
            else listOf("box" to "Caixa", "circle" to "Círculo", "wedge" to "Rampa", "capsule" to "Cápsula", "triangle" to "Triângulo")
            ChipRow(shapes, o.shape) { o.shape = it; edit() }
            if (o.shape == "wedge") SwitchRow("Rampa invertida (alta à esquerda)", o.flip) { o.flip = it; edit() }
        }
    }

    Section("Transformação") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField(if (o.type == "tilemap") "X (canto)" else "X", o.x, { o.x = it; edit() }, Modifier.weight(1f))
            NumField(if (o.type == "tilemap") "Y (canto)" else "Y", o.y, { o.y = it; edit() }, Modifier.weight(1f))
        }
        if (o.type == "text") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("Tamanho", o.h, { o.h = it; edit() }, Modifier.weight(1f))
                NumField("Rotação", o.rotation, { o.rotation = it; edit() }, Modifier.weight(1f))
            }
        } else if (o.type != "tilemap") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("Largura", o.w, { o.w = it.coerceAtLeast(1.0); edit() }, Modifier.weight(1f))
                NumField("Altura", o.h, { o.h = it.coerceAtLeast(1.0); edit() }, Modifier.weight(1f))
            }
            NumField("Rotação", o.rotation, { o.rotation = it; edit() }, Modifier.fillMaxWidth())
        }
    }

    if (o.type != "collision" && o.type != "tilemap") {
        Section("Aparência") {
            if (o.type == "part" || o.type == "text") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SWATCHES.forEach { hex ->
                        val c = android.graphics.Color.parseColor(hex)
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                                .border(if (o.color == c) 3.dp else 1.dp, if (o.color == c) Color.White else Color(0x55FFFFFF), CircleShape)
                                .clickable { o.color = c; edit() }
                        )
                    }
                }
            }
            NumField("Transparência (0–1)", o.alpha, { o.alpha = it.coerceIn(0.0, 1.0); edit() }, Modifier.fillMaxWidth())
            if (o.type == "part" || o.type == "sprite") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pick { n -> o.image = n.first() } }) {
                        Text(if (o.image.isEmpty()) "Escolher imagem…" else "Trocar imagem", fontSize = 12.sp)
                    }
                    if (o.image.isNotEmpty()) TextButton(onClick = { o.image = ""; edit() }) { Text("Remover") }
                }
            }
            if (o.type != "text") SwitchRow("Espelhar horizontalmente", o.flipX) { o.flipX = it; edit() }
            SwitchRow("Fixo na tela (HUD)", o.fixed) { o.fixed = it; edit() }
        }
    }

    if (o.type == "text") {
        Section("Texto") {
            var t by remember { mutableStateOf(o.text) }
            OutlinedTextField(
                value = t, onValueChange = { t = it; o.text = it; edit() },
                label = { Text("Texto", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (o.type != "text" && o.type != "tilemap") {
        Section("Física", startOpen = o.body != "none") {
            Text("Corpo", fontSize = 11.sp, color = Color(0xFF94A3B8))
            ChipRow(listOf("none" to "Nenhum", "static" to "Estático", "dynamic" to "Dinâmico"), o.body) { o.body = it; edit() }
            if (o.body == "static") SwitchRow("Gatilho (não bloqueia)", o.trigger) { o.trigger = it; edit() }
            if (o.body == "dynamic") {
                SwitchRow("Afetado pela gravidade", o.gravity) { o.gravity = it; edit() }
                SwitchRow("Colar no chão (segue rampas)", o.stick) { o.stick = it; edit() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("Degrau (px)", o.stepUp, { o.stepUp = it.coerceAtLeast(0.0); edit() }, Modifier.weight(1f))
                    NumField("Quique 0–1", o.bounce, { o.bounce = it.coerceIn(0.0, 1.0); edit() }, Modifier.weight(1f))
                }
            } else if (o.body == "none") {
                SwitchRow("Afetado pela gravidade", o.gravity) { o.gravity = it; edit() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("Vel X", o.vx, { o.vx = it; edit() }, Modifier.weight(1f))
                NumField("Vel Y", o.vy, { o.vy = it; edit() }, Modifier.weight(1f))
            }
        }
    }

    if (o.type == "anim") AnimSection(st, o, pick)
    if (o.type == "tilemap") TilemapSection(st, o)

    ScriptSection(st, o)

    Section("Visibilidade") {
        if (o.type != "collision") SwitchRow("Visível no início", o.visible) { o.visible = it; edit() }
        else Text("Formas de colisão são invisíveis no jogo.", fontSize = 11.sp, color = Color(0xFF94A3B8))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimSection(st: EditorState, o: GameObject, pick: ((List<String>) -> Unit) -> Unit) {
    val edit = { st.edited() }
    Section("Animação") {
        Text("Origem dos quadros", fontSize = 11.sp, color = Color(0xFF94A3B8))
        ChipRow(listOf("sheet" to "Spritesheet", "files" to "Imagens soltas"), o.animMode) { o.animMode = it; edit() }
        if (o.animMode == "sheet") {
            OutlinedButton(onClick = { pick { n -> st.applyAnimImages(o, n) } }) {
                Text(if (o.sheet.isEmpty()) "Escolher spritesheet ou GIF…" else "Trocar spritesheet / GIF", fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("Quadro L", o.fw, { o.fw = it.coerceAtLeast(1.0); edit() }, Modifier.weight(1f))
                NumField("Quadro A", o.fh, { o.fh = it.coerceAtLeast(1.0); edit() }, Modifier.weight(1f))
            }
            val sz = Images.size(o.sheet)
            if (sz != null) {
                val c = Math.max(1, sz.width / o.fw.toInt().coerceAtLeast(1))
                val r = Math.max(1, sz.height / o.fh.toInt().coerceAtLeast(1))
                Text("Imagem ${sz.width}×${sz.height}  →  $c×$r = ${c * r} quadros (0 a ${c * r - 1})", fontSize = 11.sp, color = Color(0xFF94A3B8))
            } else if (o.sheet.isNotEmpty()) {
                Text("Não consegui abrir ${o.sheet}", fontSize = 11.sp, color = Color(0xFFF87171))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pick { n -> st.applyAnimImages(o, n) } }) { Text("＋ Adicionar imagens ou GIF", fontSize = 12.sp) }
                if (o.frameFiles.isNotEmpty()) TextButton(onClick = { o.frameFiles.clear(); edit() }) { Text("Limpar") }
            }
            Text("${o.frameFiles.size} quadros (índices 0 a ${Math.max(0, o.frameFiles.size - 1)})", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        Text("Animações (De/Até = índices dos quadros)", fontSize = 11.sp, color = Color(0xFF94A3B8))
        o.anims.toList().forEachIndexed { i, a ->
            key(a) {
                Surface(color = Surf, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var nm by remember { mutableStateOf(a.name) }
                        OutlinedTextField(
                            value = nm,
                            onValueChange = { raw ->
                                val n = raw.filter { it.isLetterOrDigit() || it == '_' }
                                nm = n
                                if (n.isNotEmpty()) { if (o.anim == a.name) o.anim = n; a.name = n; edit() }
                            },
                            label = { Text("Nome", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NumField("De", a.from.toDouble(), { a.from = it.toInt().coerceAtLeast(0); edit() }, Modifier.weight(1f))
                            NumField("Até", a.to.toDouble(), { a.to = it.toInt().coerceAtLeast(0); edit() }, Modifier.weight(1f))
                            NumField("FPS", a.fps, { a.fps = it.coerceAtLeast(0.1); edit() }, Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SwitchRow("Repetir", a.loop) { a.loop = it; edit() } }
                            TextButton(onClick = { o.anims.remove(a); edit() }) { Text("Apagar", color = Color(0xFFF43F5E)) }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            val n = o.anims.size + 1
            o.anims.add(Anim("anim$n", 0, 0, 8.0, true))
            if (o.anim.isEmpty()) o.anim = "anim$n"
            edit()
        }) { Text("＋ Nova animação", fontSize = 12.sp) }

        if (o.anims.isNotEmpty()) {
            Text("Animação inicial", fontSize = 11.sp, color = Color(0xFF94A3B8))
            ChipRow(o.anims.map { it.name to it.name }, o.currentAnim()?.name ?: "") { o.anim = it; o.frameT = 0.0; edit() }
        }
        SwitchRow("Tocando ao iniciar", o.playing) { o.playing = it; edit() }
        NumField("Velocidade (x)", o.animSpeed, { o.animSpeed = it.coerceAtLeast(0.0); edit() }, Modifier.fillMaxWidth())
        Text("No código: play(obj, \"nome\")  •  obj.flipx = true", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TilemapSection(st: EditorState, o: GameObject) {
    val edit = { st.edited() }
    Section("Tilemap") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Colunas", o.cols.toDouble(), { o.resizeTiles(it.toInt().coerceIn(1, 400), o.rows); edit() }, Modifier.weight(1f))
            NumField("Linhas", o.rows.toDouble(), { o.resizeTiles(o.cols, it.toInt().coerceIn(1, 400)); edit() }, Modifier.weight(1f))
        }
        NumField("Tamanho do bloco (px)", o.tileSize, { o.tileSize = it.coerceAtLeast(4.0); edit() }, Modifier.fillMaxWidth())
        Text("Blocos (0 = apagar)", fontSize = 11.sp, color = Color(0xFF94A3B8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in 0 until TileSet.count) {
                val col = if (id == 0) Color(0xFF334155) else Color(TileSet.colors[id])
                Box(
                    Modifier.size(34.dp).background(col)
                        .border(if (st.brush == id) 3.dp else 1.dp, if (st.brush == id) Color.White else Color(0x66FFFFFF))
                        .clickable { st.brush = id; st.paint = true },
                    contentAlignment = Alignment.Center
                ) { if (id == 0) Text("✕", fontSize = 12.sp) }
            }
        }
        Text("Selecionado: ${TileSet.name(st.brush)}", fontSize = 11.sp)
        SwitchRow("Pincel ativo (pintar na cena)", st.paint) { st.paint = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { java.util.Arrays.fill(o.tiles, st.brush); edit() }) { Text("Preencher", fontSize = 12.sp) }
            OutlinedButton(onClick = { java.util.Arrays.fill(o.tiles, 0); edit() }) { Text("Limpar", fontSize = 12.sp) }
        }
        Text("Blocos sólidos: Grama, Terra, Pedra, Madeira, Folhas, Tijolo, Bloco ?, Areia, Ouro.", fontSize = 10.sp, color = Color(0xFF94A3B8))
        Text("No código: tile_get(mapa,x,y)  tile_set(mapa,x,y,id)", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptSection(st: EditorState, o: GameObject) {
    Section("Script", startOpen = o.script.isNotEmpty()) {
        if (o.script.isEmpty()) Text("Nenhum script anexado a este objeto.", fontSize = 11.sp, color = Color(0xFF94A3B8))
        else Text("📜 ${o.script}", fontSize = 12.sp, color = Cyan)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { st.attachTarget = o }) { Text("Anexar…", fontSize = 12.sp) }
            OutlinedButton(onClick = { st.newScriptTarget = o }) { Text("Novo…", fontSize = 12.sp) }
            if (o.script.isNotEmpty()) {
                OutlinedButton(onClick = { st.currentScript = o.script; st.wantTab = 1 }) { Text("Abrir", fontSize = 12.sp) }
                TextButton(onClick = { o.script = ""; st.edited() }) { Text("Remover") }
            }
        }
        Text("Dentro do script, 'self' é este objeto.", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeshBody(st: EditorState, o: GameObject) {
    val edit = { st.edited() }
    Section("Objeto") {
        var name by remember { mutableStateOf(o.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { raw ->
                var n = raw.filter { it.isLetterOrDigit() || it == '_' }
                if (n.firstOrNull()?.isDigit() == true) n = "_$n"
                name = n
                if (n.isNotEmpty()) { o.name = n; edit() }
            },
            label = { Text("Nome (usado no código)", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Tipo: Malha 3D", fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text("Forma", fontSize = 11.sp, color = Color(0xFF94A3B8))
        ChipRow(listOf("box" to "Cubo", "sphere" to "Esfera", "plane" to "Plano", "cylinder" to "Cilindro", "pyramid" to "Pirâmide"), o.shape) { o.shape = it; edit() }
    }
    Section("Transformação") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("X", o.x, { o.x = it; edit() }, Modifier.weight(1f))
            NumField("Y", o.y, { o.y = it; edit() }, Modifier.weight(1f))
            NumField("Z", o.z, { o.z = it; edit() }, Modifier.weight(1f))
        }
        Text("Tamanho", fontSize = 11.sp, color = Color(0xFF94A3B8))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("Larg.", o.w, { o.w = it.coerceAtLeast(0.01); edit() }, Modifier.weight(1f))
            NumField("Alt.", o.h, { o.h = it.coerceAtLeast(0.01); edit() }, Modifier.weight(1f))
            NumField("Prof.", o.d, { o.d = it.coerceAtLeast(0.01); edit() }, Modifier.weight(1f))
        }
        Text("Rotação (graus)", fontSize = 11.sp, color = Color(0xFF94A3B8))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("RX", o.rx, { o.rx = it; edit() }, Modifier.weight(1f))
            NumField("RY", o.ry, { o.ry = it; edit() }, Modifier.weight(1f))
            NumField("RZ", o.rotation, { o.rotation = it; edit() }, Modifier.weight(1f))
        }
    }
    Section("Aparência") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { hex ->
                val c = android.graphics.Color.parseColor(hex)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (o.color == c) 3.dp else 1.dp, if (o.color == c) Color.White else Color(0x55FFFFFF), CircleShape)
                        .clickable { o.color = c; edit() }
                )
            }
        }
    }
    Section("Física", startOpen = o.body != "none") {
        Text("Corpo", fontSize = 11.sp, color = Color(0xFF94A3B8))
        ChipRow(listOf("none" to "Nenhum", "static" to "Estático", "dynamic" to "Dinâmico"), o.body) { o.body = it; edit() }
        if (o.body == "static") SwitchRow("Gatilho (não bloqueia)", o.trigger) { o.trigger = it; edit() }
        SwitchRow("Afetado pela gravidade", o.gravity) { o.gravity = it; edit() }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("Vel X", o.vx, { o.vx = it; edit() }, Modifier.weight(1f))
            NumField("Vel Y", o.vy, { o.vy = it; edit() }, Modifier.weight(1f))
            NumField("Vel Z", o.vz, { o.vz = it; edit() }, Modifier.weight(1f))
        }
        Text("Corpo dinâmico colide com blocos estáticos (caixas alinhadas aos eixos).", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
    ScriptSection(st, o)
    Section("Visibilidade") { SwitchRow("Visível no início", o.visible) { o.visible = it; edit() } }
}
