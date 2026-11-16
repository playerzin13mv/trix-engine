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

private fun assignImage(st: EditorState, o: GameObject, name: String) {
    when (o.type) {
        "anim" -> st.applyAnimImages(o, listOf(name))
        "part", "sprite" -> o.image = name
    }
}

private class FEntry(val rel: String, val name: String, val isDir: Boolean, val depth: Int)

private fun tree(dir: File): List<FEntry> {
    val out = ArrayList<FEntry>()
    fun walk(f: File, prefix: String, depth: Int) {
        if (depth > 4) return
        val kids = f.listFiles()?.filter { !it.name.startsWith(".") && it.name != "save.json" } ?: return
        val sorted = kids.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        for (k in sorted) {
            val rel = if (prefix.isEmpty()) k.name else prefix + "/" + k.name
            out.add(FEntry(rel, k.name, k.isDirectory, depth))
            if (k.isDirectory) walk(k, rel, depth + 1)
        }
    }
    walk(dir, "", 0)
    return out
}

@Composable
fun FilesPanel(
    st: EditorState, onOpenScript: (String) -> Unit, onOpenScene: (String) -> Unit, onOpenSettings: () -> Unit,
    onImport: () -> Unit, modifier: Modifier = Modifier
) {
    val rev = st.rev
    val entries = tree(st.dir)
    Column(modifier) {
        DockHeader("Arquivos  res://") {
            Text("＋ imagem", fontSize = 11.sp, color = Cyan, modifier = Modifier.clickable { onImport() }.padding(horizontal = 6.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (e in entries) {
                val pad = "   ".repeat(e.depth)
                val icon = when {
                    e.isDir -> "📁"
                    e.name.endsWith(".scene") -> "🎬"
                    e.name.endsWith(".tr") -> "📜"
                    e.name.endsWith(".json") -> "⚙"
                    else -> "🖼"
                }
                val current = e.rel == st.scenePath
                Text(
                    "$pad$icon ${e.name}", fontSize = 12.sp, maxLines = 1,
                    color = if (current) Cyan else Color.White,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !e.isDir) {
                        when {
                            e.name.endsWith(".scene") -> onOpenScene(e.rel)
                            e.name.endsWith(".tr") -> onOpenScript(e.rel)
                            e.name == "project.json" -> onOpenSettings()
                            e.rel.startsWith("assets/") -> { val o = st.sel; if (o != null) { assignImage(st, o, e.name); st.edited() } }
                        }
                    }.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            if (st.scenePath == null) {
                Text("🎬 sem_titulo.scene  (não salva)", fontSize = 12.sp, color = Color(0xFFFACC15), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            Text(
                "Toque numa imagem em assets/ para aplicar ao objeto selecionado.",
                fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(10.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- janela "Criar novo objeto"

@Composable
fun AddObjectDialog(mode: String, onDismiss: () -> Unit, onPick: (NodeKind) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = NODE_KINDS.filter { (it.mode == "both" || it.mode == mode) && (q.isBlank() || it.title.contains(q, true) || it.desc.contains(q, true)) }
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


// ---------------------------------------------------------------- salvar em uma pasta do projeto (res://)

@Composable
fun SaveAsDialog(dir: File, title: String, defaultName: String, ext: String, startFolder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var folder by remember { mutableStateOf(startFolder) }
    var name by remember { mutableStateOf(defaultName) }
    var newFolder by remember { mutableStateOf("") }
    val here = if (folder.isEmpty()) dir else File(dir, folder)
    val subs = here.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name } ?: emptyList()
    val clean = name.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    val rel = (if (folder.isEmpty()) "" else "$folder/") + clean + ext
    val exists = clean.isNotEmpty() && File(dir, rel).exists()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(14.dp), color = Surf, modifier = Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.92f)) {
            Column(Modifier.padding(14.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Pasta: res://" + folder, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Cyan, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(Modifier.weight(1f).background(Dark)) {
                    if (folder.isNotEmpty()) item {
                        Text("⬆  ..", modifier = Modifier.fillMaxWidth().clickable { folder = folder.substringBeforeLast('/', "") }.padding(10.dp))
                    }
                    items(subs) { f ->
                        Text("📁  ${f.name}", modifier = Modifier.fillMaxWidth().clickable { folder = if (folder.isEmpty()) f.name else "$folder/${f.name}" }.padding(10.dp))
                    }
                    if (subs.isEmpty()) item { Text("(sem subpastas)", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(10.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(value = newFolder, onValueChange = { newFolder = it }, label = { Text("Nova pasta") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        val n = newFolder.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                        if (n.isNotEmpty()) { File(here, n).mkdirs(); folder = if (folder.isEmpty()) n else "$folder/$n"; newFolder = "" }
                    }) { Text("Criar") }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nome do arquivo ($ext)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                if (exists) Text("Já existe: será substituído.", fontSize = 11.sp, color = Color(0xFFFACC15))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = { onSave(rel) }, enabled = clean.isNotEmpty()) { Text("Salvar") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- alterações não salvas

@Composable
fun UnsavedDialog(st: EditorState, saveLabel: String, discardLabel: String, onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Alterações não salvas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estes itens ainda não foram salvos:")
                if (st.scenePath == null) Text("• Cena nova (sem_titulo.scene) — você escolhe a pasta", fontSize = 13.sp)
                else if (st.dirty) Text("• Cena ${st.sceneName} / configurações do projeto", fontSize = 13.sp)
                st.dirtyScripts.forEach { Text("• Script $it", fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(saveLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Cancelar") }
                TextButton(onClick = onDiscard) { Text(discardLabel, color = Color(0xFFF87171)) }
            }
        }
    )
}

// ---------------------------------------------------------------- escolher um arquivo do projeto

@Composable
fun PickFileDialog(title: String, files: List<String>, newLabel: String?, onNew: () -> Unit, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(14.dp), color = Surf, modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.85f)) {
            Column(Modifier.padding(14.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(files) { f ->
                        Text(f, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 10.dp, horizontal = 6.dp))
                        HorizontalDivider(color = Surf2)
                    }
                    if (files.isEmpty()) item { Text("Nenhum arquivo ainda.", color = Color(0xFF94A3B8), modifier = Modifier.padding(10.dp)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (newLabel != null) TextButton(onClick = onNew) { Text(newLabel) }
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}
