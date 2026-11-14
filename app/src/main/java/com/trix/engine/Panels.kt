package com.trix.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

// ---------------------------------------------------------------- widgets compartilhados

@Composable
fun NumField(label: String, v: Double, onVal: (Double) -> Unit, modifier: Modifier = Modifier) {
    fun f(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(java.util.Locale.US, d)
    var t by remember { mutableStateOf(f(v)) }
    LaunchedEffect(v) { if (t.toDoubleOrNull() != v) t = f(v) }
    OutlinedTextField(
        value = t,
        onValueChange = { t = it; it.toDoubleOrNull()?.let(onVal) },
        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
        textStyle = TextStyle(fontSize = 14.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.height(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSel: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) ->
            FilterChip(selected = selected == id, onClick = { onSel(id) }, label = { Text(label, fontSize = 12.sp) })
        }
    }
}

@Composable
fun DockHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Surf2).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------- árvore da cena

@Composable
fun SceneTree(st: EditorState, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val rev = st.rev
    Column(modifier) {
        DockHeader("Cena") {
            Text("＋", fontSize = 18.sp, color = Cyan, modifier = Modifier.clickable { onAdd() }.padding(horizontal = 6.dp))
        }
        if (st.objects.isEmpty()) {
            Text("Cena vazia.\nToque em ＋ para criar um objeto.", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(10.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(st.objects.toList()) { o ->
                val selected = o === st.sel
                Row(
                    Modifier.fillMaxWidth().background(if (selected) Color(0xFF33407A) else Color.Transparent)
                        .clickable { st.sel = o }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(iconOf(o), fontSize = 14.sp, modifier = Modifier.width(24.dp))
                    Text(o.name, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (o.body == "dynamic") Text("⚙", fontSize = 11.sp, color = Cyan)
                    if (!o.visible && o.type != "collision") Text(" ⊘", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- arquivos

private fun assignImage(o: GameObject, name: String) {
    when (o.type) {
        "anim" -> if (o.animMode == "files") o.frameFiles.add(name) else o.sheet = name
        "part", "sprite" -> o.image = name
    }
}

@Composable
fun FilesPanel(st: EditorState, onOpenScript: () -> Unit, onOpenSettings: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val rev = st.rev
    val assets = Projects.assetsDir(st.dir).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    Column(modifier) {
        DockHeader("Arquivos") {
            Text("＋ imagem", fontSize = 11.sp, color = Cyan, modifier = Modifier.clickable { onImport() }.padding(horizontal = 6.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            @Composable fun Item(label: String, onClick: (() -> Unit)?) {
                Text(
                    label, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Item("📁 res://", null)
            Item("    📄 main.trix", onOpenScript)
            Item("    ⚙ project.json", onOpenSettings)
            Item("    🎬 scene.json", null)
            Item("    📁 assets/", null)
            assets.forEach { f ->
                Item("        🖼 ${f.name}") {
                    val o = st.sel
                    if (o != null) { assignImage(o, f.name); st.edited() }
                }
            }
            if (assets.isNotEmpty()) {
                Text(
                    "Toque numa imagem para aplicar ao objeto selecionado.",
                    fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- janela "Criar novo objeto"

@Composable
fun AddObjectDialog(onDismiss: () -> Unit, onPick: (NodeKind) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = NODE_KINDS.filter { q.isBlank() || it.title.contains(q, true) || it.desc.contains(q, true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(14.dp), color = Surf,
            modifier = Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Criar novo objeto", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Pesquisar objetos…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(list) { k ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(k) }.padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(k.icon, fontSize = 22.sp, modifier = Modifier.width(40.dp))
                            Column {
                                Text(k.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(k.desc, fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                        HorizontalDivider(color = Surf2)
                    }
                    if (list.isEmpty()) item { Text("Nada encontrado para \"$q\".", color = Color(0xFF94A3B8), modifier = Modifier.padding(12.dp)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- exportar APK

fun gameSlug(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.ifEmpty { "game" }

@Composable
fun ExportDialog(dir: File, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    val cmd = "cd ~/trix-engine && bash export.sh \"${dir.name}\""
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(14.dp), color = Surf, modifier = Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.9f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exportar APK", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Jogo: ${dir.name}", color = Cyan)
                Text("Pacote: com.trix.game.g${gameSlug(dir.name)}", fontSize = 12.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                Text(
                    "O APK é compilado pelo GitHub (leva uns 4 minutos) e fica em Downloads. " +
                            "Seu projeto já foi salvo. Rode este comando no Termux:",
                    fontSize = 13.sp
                )
                Surface(color = Dark, shape = RoundedCornerShape(8.dp)) {
                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF86EFAC), modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clip.setText(AnnotatedString(cmd)) }) { Text("Copiar comando") }
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
                Text(
                    "O que o comando faz: copia o projeto para o repositório trix-engine, envia ao GitHub, " +
                            "roda o workflow \"Export game\" e baixa o APK pronto. " +
                            "O jogo exportado abre direto, sem o editor e sem pedir permissão de armazenamento.",
                    fontSize = 11.sp, color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
