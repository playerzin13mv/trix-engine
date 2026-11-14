package com.trix.engine

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

@Composable
private fun MenuBtn(label: String, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(label, fontSize = 13.sp, color = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (t, a) -> DropdownMenuItem(text = { Text(t, fontSize = 14.sp) }, onClick = { open = false; a() }) }
        }
    }
}

private fun yn(b: Boolean) = if (b) "sim" else "não"

@Composable
fun EditorScreen(dir: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember(dir) { EditorState(dir) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var leftDock by remember { mutableStateOf(true) }
    var rightDock by remember { mutableStateOf(true) }
    var onImported by remember { mutableStateOf<((List<String>) -> Unit)?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val cb = onImported
        onImported = null
        if (uris.isNotEmpty()) {
            val names = importImages(ctx, dir, uris)
            if (names.isNotEmpty()) { cb?.invoke(names); st.edited() }
        }
    }
    val pick: ((List<String>) -> Unit) -> Unit = { cb -> onImported = cb; picker.launch("image/*") }

    LaunchedEffect(st.rev, st.script.text, st.settings) {
        st.saved = false
        delay(900)
        st.persist()
    }
    LaunchedEffect(st.rev) {
        delay(450)
        st.commitHistory()
    }

    BackHandler(enabled = !playing) { st.persist(); onClose() }

    if (playing) {
        PlayScreen(dir, st.objects.toList(), st.settings, st.script.text) { playing = false }
        return
    }

    fun play() { st.persist(); playing = true }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // ---- barra superior (estilo Godot)
        Row(Modifier.fillMaxWidth().height(44.dp).background(Surf).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { st.persist(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            MenuBtn("Cena", listOf(
                "Novo objeto…" to { tab = 0; showAdd = true },
                "Duplicar" to { st.duplicate() },
                "Apagar" to { st.delete() },
                "Trazer para frente" to { st.move(1) },
                "Enviar para trás" to { st.move(-1) },
                "Salvar" to { st.persist() }
            ))
            MenuBtn("Projeto", listOf(
                "Configurações do projeto" to { tab = 2 },
                "Exportar APK…" to { st.persist(); showExport = true },
                "Fechar projeto" to { st.persist(); onClose() }
            ))
            MenuBtn("Debug", listOf(
                "Testar" to { play() },
                "Mostrar colisões: ${yn(st.settings.showCollisions)}" to { st.settings = st.settings.copy(showCollisions = !st.settings.showCollisions) },
                "Mostrar FPS: ${yn(st.settings.showFps)}" to { st.settings = st.settings.copy(showFps = !st.settings.showFps) }
            ))
            MenuBtn("Editor", listOf(
                "Grade: ${yn(st.settings.showGrid)}" to { st.settings = st.settings.copy(showGrid = !st.settings.showGrid) },
                "Snap: ${yn(st.snap)}" to { st.snap = !st.snap },
                "Painel da cena: ${yn(leftDock)}" to { leftDock = !leftDock },
                "Inspetor: ${yn(rightDock)}" to { rightDock = !rightDock }
            ))
            MenuBtn("Ajuda", listOf("Referência da linguagem" to { tab = 3 }))
            Spacer(Modifier.weight(1f))
            listOf("2D", "Script", "Projeto", "Ajuda").forEachIndexed { i, t ->
                TextButton(
                    onClick = { tab = i },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (tab == i) Cyan else Color(0xFF94A3B8))
                ) { Text(t, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
            }
            Text(if (st.saved) "✓" else "…", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            TextButton(onClick = { st.undo() }, enabled = st.history.size > 1, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("↶", fontSize = 18.sp) }
            TextButton(onClick = { st.redoIt() }, enabled = st.redo.isNotEmpty(), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("↷", fontSize = 18.sp) }
            Button(onClick = { play() }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(2.dp)); Text("Testar", fontSize = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        HorizontalDivider(color = Surf2)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Row(Modifier.fillMaxSize()) {
                    if (leftDock) {
                        Column(Modifier.width(190.dp).fillMaxHeight().background(Surf)) {
                            SceneTree(st, { showAdd = true }, Modifier.weight(1f).fillMaxWidth())
                            HorizontalDivider(color = Dark, thickness = 2.dp)
                            FilesPanel(
                                st, { tab = 1 }, { tab = 2 },
                                { pick { } },
                                Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                    SceneViewport(st, Modifier.weight(1f).fillMaxHeight())
                    if (rightDock) {
                        Column(Modifier.width(270.dp).fillMaxHeight().background(Surf).verticalScroll(rememberScrollState())) {
                            Inspector(st, pick)
                        }
                    }
                }
                1 -> CodeTab(st.script) { st.script = it }
                2 -> SettingsTab(dir, st.settings, { st.persist(); showExport = true }) { st.settings = it }
                else -> HelpTab()
            }
        }
    }

    if (showAdd) {
        AddObjectDialog(onDismiss = { showAdd = false }) { kind -> st.add(kind); showAdd = false }
    }
    if (showExport) ExportDialog(dir) { showExport = false }
}
