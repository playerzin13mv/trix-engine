package com.trix.engine

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private class SaveReq(val title: String, val default: String, val ext: String, val folder: String, val onSave: (String) -> Unit)
private class GuardReq(val saveLabel: String, val discardLabel: String, val action: () -> Unit)

@Composable
fun EditorScreen(dir: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember(dir) { EditorState(dir) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showOpenScene by remember { mutableStateOf(false) }
    var leftDock by remember { mutableStateOf(true) }
    var rightDock by remember { mutableStateOf(true) }
    var onImported by remember { mutableStateOf<((List<String>) -> Unit)?>(null) }
    var saveReq by remember { mutableStateOf<SaveReq?>(null) }
    var guardReq by remember { mutableStateOf<GuardReq?>(null) }
    var newSceneScript by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val cb = onImported
        onImported = null
        if (uris.isNotEmpty()) {
            val names = importImages(ctx, dir, uris)
            if (names.isNotEmpty()) { cb?.invoke(names); st.edited() }
        }
    }
    val pick: ((List<String>) -> Unit) -> Unit = { cb -> onImported = cb; picker.launch("image/*") }

    LaunchedEffect(st.rev) {
        delay(450)
        st.commitHistory()
    }
    LaunchedEffect(st.wantTab) {
        if (st.wantTab >= 0) { tab = st.wantTab; st.wantTab = -1 }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(2200); toast = null }
    }

    /** Garante que a cena tem um caminho (pergunta a pasta se for nova) e salva tudo. */
    fun saveThen(then: () -> Unit) {
        if (st.scenePath == null) {
            saveReq = SaveReq("Salvar cena", "cena1", ".scene", "scenes") { rel -> st.saveSceneAs(rel); then() }
        } else { st.saveAll(); then() }
    }

    fun guarded(saveLabel: String, discardLabel: String, action: () -> Unit) {
        if (!st.hasUnsaved()) action() else guardReq = GuardReq(saveLabel, discardLabel, action)
    }

    fun play() = guarded("Salvar e testar", "Testar sem salvar") { playing = true }
    fun close() = guarded("Salvar e sair", "Sair sem salvar") { onClose() }

    BackHandler(enabled = !playing) { close() }

    if (playing) {
        PlayScreen(dir, SceneData(st.sceneScript, st.objects.toList()), st.settings, { st.scriptSource(it) }) { playing = false }
        return
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            // ---- barra superior (estilo Godot)
            Row(Modifier.fillMaxWidth().height(44.dp).background(Surf).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                MenuBtn("Cena", listOf(
                    "Nova cena" to { guarded("Salvar e criar", "Criar sem salvar") { st.newScene() } },
                    "Abrir cena…" to { showOpenScene = true },
                    "Salvar cena" to { saveThen { toast = "Cena salva" } },
                    "Salvar cena como…" to { saveReq = SaveReq("Salvar cena como", st.sceneName.removeSuffix(".scene"), ".scene", st.scenePath?.substringBeforeLast('/', "") ?: "scenes") { rel -> st.saveSceneAs(rel); toast = "Salvo em res://$rel" } },
                    "Definir como cena principal" to { if (st.scenePath == null) toast = "Salve a cena primeiro" else { st.setMainScene(); toast = "Cena principal definida" } },
                    "Script da cena…" to { st.attachToScene = true },
                    "Novo objeto…" to { tab = 0; showAdd = true },
                    "Duplicar objeto" to { st.duplicate() },
                    "Apagar objeto" to { st.delete() },
                    "Trazer para frente" to { st.move(1) },
                    "Enviar para trás" to { st.move(-1) }
                ))
                MenuBtn("Projeto", listOf(
                    "Configurações do projeto" to { tab = 2 },
                    "Exportar APK…" to { saveThen { showExport = true } },
                    "Exportar .zip" to { saveThen { val f = ImportExport.exportZip(dir); toast = if (f != null) "ZIP salvo em ${f.path}" else "Falha ao criar o ZIP" } },
                    "Fechar projeto" to { close() }
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
                Text(
                    st.sceneName + if (st.hasUnsaved()) " ●" else "",
                    fontSize = 12.sp, color = if (st.hasUnsaved()) Color(0xFFFACC15) else Color(0xFF94A3B8), maxLines = 1,
                    modifier = Modifier.widthIn(max = 150.dp).padding(horizontal = 4.dp)
                )
                listOf(if (st.settings.is3d) "3D" else "2D", "Script", "Projeto", "Ajuda").forEachIndexed { i, t ->
                    TextButton(
                        onClick = { tab = i },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = if (tab == i) Cyan else Color(0xFF94A3B8))
                    ) { Text(t, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
                }
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
                                    st,
                                    { path -> st.currentScript = path; tab = 1 },
                                    { rel -> guarded("Salvar e abrir", "Abrir sem salvar") { st.openScene(rel) } },
                                    { tab = 2 },
                                    { pick { } },
                                    Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                        if (st.settings.is3d) SceneViewport3D(st, Modifier.weight(1f).fillMaxHeight())
                        else SceneViewport(st, Modifier.weight(1f).fillMaxHeight())
                        if (rightDock) {
                            Column(Modifier.width(270.dp).fillMaxHeight().background(Surf).verticalScroll(rememberScrollState())) {
                                Inspector(st, pick)
                            }
                        }
                    }
                    1 -> ScriptTab(st) { newSceneScript = false; st.newScriptTarget = GameObject("novo", "part").also { it.name = "script" } }
                    2 -> SettingsTab(dir, st.settings, { saveThen { showExport = true } }) { st.settings = it }
                    else -> HelpTab()
                }
            }
        }

        toast?.let { t ->
            Surface(
                color = Color(0xEE0F172A), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            ) { Text(t, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp) }
        }
    }

    // ---------------------------------------------------------------- diálogos
    if (showAdd) {
        AddObjectDialog(if (st.settings.is3d) "3d" else "2d", onDismiss = { showAdd = false }) { kind -> st.add(kind); showAdd = false }
    }
    if (showExport) ExportDialog(dir) { showExport = false }

    if (showOpenScene) {
        PickFileDialog(
            "Abrir cena", Projects.listRel(dir, ".scene"), null, {},
            onDismiss = { showOpenScene = false }
        ) { rel -> showOpenScene = false; guarded("Salvar e abrir", "Abrir sem salvar") { st.openScene(rel) } }
    }

    saveReq?.let { r ->
        SaveAsDialog(dir, r.title, r.default, r.ext, r.folder, onDismiss = { saveReq = null }) { rel ->
            saveReq = null
            r.onSave(rel)
        }
    }

    guardReq?.let { g ->
        UnsavedDialog(
            st, g.saveLabel, g.discardLabel,
            onSave = { guardReq = null; saveThen { g.action() } },
            onDiscard = { guardReq = null; g.action() },
            onCancel = { guardReq = null }
        )
    }

    // anexar script a um objeto ou à cena
    val target = st.attachTarget
    if (target != null || st.attachToScene) {
        PickFileDialog(
            if (target != null) "Anexar script a ${target.name}" else "Script da cena",
            Projects.listRel(dir, ".tr"), "Novo script…",
            onNew = {
                newSceneScript = target == null
                st.newScriptTarget = target ?: GameObject("cena", "part")
                st.attachTarget = null; st.attachToScene = false
            },
            onDismiss = { st.attachTarget = null; st.attachToScene = false }
        ) { rel ->
            if (target != null) target.script = rel else st.sceneScript = rel
            st.attachTarget = null; st.attachToScene = false; st.edited()
        }
    }

    val nt = st.newScriptTarget
    if (nt != null) {
        SaveAsDialog(dir, "Novo script", if (nt.name == "script") "script1" else nt.name, ".tr", "scripts", onDismiss = { st.newScriptTarget = null; newSceneScript = false }) { rel ->
            st.createScript(rel)
            if (tab == 1 && nt.name == "script") { /* criado pela aba Script: só abre */ }
            else if (newSceneScript) st.sceneScript = rel
            else nt.script = rel
            st.newScriptTarget = null; newSceneScript = false
            st.edited()
            tab = 1
        }
    }
}
