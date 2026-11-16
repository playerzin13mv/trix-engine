package com.trix.engine

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

/** Um tipo de objeto que aparece na janela "Criar novo objeto". mode: "2d", "3d" ou "both". */
class NodeKind(val icon: String, val title: String, val desc: String, val mode: String, val make: () -> GameObject)

private fun part(shape: String, base: String, w: Double, h: Double, color: String) = GameObject(base, "part").apply {
    this.shape = shape; this.w = w; this.h = h; this.color = android.graphics.Color.parseColor(color)
}

private fun mesh(shape: String, base: String, w: Double, h: Double, d: Double, color: String) = GameObject(base, "mesh").apply {
    this.shape = shape; this.w = w; this.h = h; this.d = d; this.color = android.graphics.Color.parseColor(color)
}

val NODE_KINDS: List<NodeKind> = listOf(
    // ---- 2D
    NodeKind("▭", "Parte — Caixa", "Forma retangular. A forma pode ser trocada no Inspetor.", "2d") { part("box", "caixa", 100.0, 100.0, "#22D3EE") },
    NodeKind("●", "Parte — Círculo", "Forma circular.", "2d") { part("circle", "bola", 80.0, 80.0, "#A855F7") },
    NodeKind("◢", "Parte — Rampa (Wedge)", "Rampa para física de inclinação (estilo Sonic).", "2d") { part("wedge", "rampa", 200.0, 100.0, "#15803D").apply { body = "static" } },
    NodeKind("▬", "Parte — Cápsula", "Retângulo com pontas arredondadas.", "2d") { part("capsule", "capsula", 120.0, 50.0, "#F43F5E") },
    NodeKind("▲", "Parte — Triângulo", "Triângulo isósceles.", "2d") { part("triangle", "triangulo", 90.0, 90.0, "#FACC15") },
    NodeKind("▰", "Plataforma sólida", "Caixa estática: corpos dinâmicos ficam em cima dela.", "2d") { part("box", "plataforma", 240.0, 32.0, "#64748B").apply { body = "static" } },
    NodeKind("🏃", "Personagem (corpo dinâmico)", "Caixa com gravidade e colisão com o cenário.", "2d") { part("box", "jogador", 32.0, 48.0, "#F59E0B").apply { body = "dynamic"; gravity = true } },
    NodeKind("🖼", "Sprite (imagem)", "Mostra uma imagem da pasta assets.", "2d") { GameObject("sprite", "sprite").apply { w = 100.0; h = 100.0 } },
    NodeKind("🎞", "Sprite Animado", "Animação com spritesheet, imagens soltas ou GIF (corta os quadros sozinho).", "2d") { GameObject("animado", "anim").apply { w = 64.0; h = 64.0 } },
    NodeKind("⬚", "Forma de Colisão (retângulo)", "Área sólida invisível no jogo.", "2d") { GameObject("colisao", "collision").apply { w = 200.0; h = 40.0; body = "static" } },
    NodeKind("◿", "Forma de Colisão (rampa)", "Rampa sólida invisível no jogo.", "2d") { GameObject("colisao_rampa", "collision").apply { shape = "wedge"; w = 200.0; h = 100.0; body = "static" } },
    NodeKind("◌", "Gatilho (área)", "Área que não bloqueia: use overlaps(a, b) no código.", "2d") { GameObject("gatilho", "collision").apply { w = 120.0; h = 120.0; body = "static"; trigger = true } },
    NodeKind("▦", "Tilemap (blocos)", "Grade de blocos para fases e mundos. Pinte direto na cena.", "2d") { GameObject("mapa", "tilemap").apply { x = 0.0; y = 0.0; cols = 25; rows = 14; tileSize = 32.0; tiles = IntArray(25 * 14) } },
    // ---- 3D
    NodeKind("🧊", "Cubo 3D", "Malha em forma de caixa. A forma pode ser trocada no Inspetor.", "3d") { mesh("box", "cubo", 1.0, 1.0, 1.0, "#A855F7") },
    NodeKind("⚪", "Esfera 3D", "Malha esférica.", "3d") { mesh("sphere", "esfera", 1.0, 1.0, 1.0, "#FACC15") },
    NodeKind("▱", "Plano (chão)", "Superfície plana. Marque como Estático para andar nela.", "3d") { mesh("plane", "chao", 20.0, 1.0, 20.0, "#4ADE80").apply { body = "static" } },
    NodeKind("🛢", "Cilindro 3D", "Malha cilíndrica.", "3d") { mesh("cylinder", "cilindro", 1.0, 2.0, 1.0, "#3B82F6") },
    NodeKind("🔺", "Pirâmide 3D", "Malha em forma de pirâmide.", "3d") { mesh("pyramid", "piramide", 2.0, 2.0, 2.0, "#F43F5E") },
    NodeKind("🟫", "Bloco sólido 3D", "Cubo estático: corpos dinâmicos ficam em cima.", "3d") { mesh("box", "bloco", 3.0, 1.5, 3.0, "#94A3B8").apply { body = "static" } },
    NodeKind("🧍", "Personagem 3D (corpo dinâmico)", "Cubo com gravidade e colisão.", "3d") { mesh("box", "jogador", 1.0, 1.0, 1.0, "#F59E0B").apply { body = "dynamic"; gravity = true } },
    // ---- ambos
    NodeKind("T", "Texto", "Texto na cena. Use \"Fixo na tela\" para HUD.", "both") { GameObject("texto", "text").apply { h = 28.0; text = "Olá"; color = android.graphics.Color.WHITE } }
)

fun iconOf(o: GameObject): String = when (o.type) {
    "text" -> "T"; "sprite" -> "🖼"; "anim" -> "🎞"; "collision" -> if (o.trigger) "◌" else "⬚"; "tilemap" -> "▦"
    "mesh" -> when (o.shape) { "sphere" -> "⚪"; "plane" -> "▱"; "cylinder" -> "🛢"; "pyramid" -> "🔺"; else -> "🧊" }
    else -> when (o.shape) { "circle" -> "●"; "wedge" -> "◢"; "capsule" -> "▬"; "triangle" -> "▲"; else -> "▭" }
}

/** Copia imagens escolhidas para a pasta assets do projeto e devolve os nomes. */
fun importImages(ctx: Context, dir: File, uris: List<Uri>): List<String> {
    val out = ArrayList<String>()
    val outDir = Projects.assetsDir(dir); outDir.mkdirs()
    for ((i, uri) in uris.withIndex()) {
        try {
            val type = ctx.contentResolver.getType(uri)
            val ext = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "png"
            val name = "img_${System.currentTimeMillis()}_$i.$ext"
            ctx.contentResolver.openInputStream(uri)?.use { inp ->
                File(outDir, name).outputStream().use { o -> inp.copyTo(o) }
            }
            out.add(name)
        } catch (e: Exception) { }
    }
    return out
}

const val SCRIPT_TEMPLATE = """# Script anexado a um objeto.
# 'self' é o objeto. Os outros objetos são acessados pelo nome.
# game.algo = 1  guarda valores compartilhados entre scripts.

func start()
end

func update(dt)
end
"""

class EditorState(val dir: File) {
    val objects: SnapshotStateList<GameObject> = mutableStateListOf()
    var scenePath by mutableStateOf<String?>(null)          // null = cena nova ainda não salva
    var sceneScript by mutableStateOf("")                   // script da cena (global), opcional
    private var _settings by mutableStateOf(ProjectSettings())
    var settings: ProjectSettings
        get() = _settings
        set(v) { _settings = v; dirty = true }
    var dirty by mutableStateOf(false)
    var rev by mutableIntStateOf(0)
    var sel by mutableStateOf<GameObject?>(null)
    var snap by mutableStateOf(true)
    var paint by mutableStateOf(false)
    var brush by mutableIntStateOf(1)
    val history: SnapshotStateList<String> = mutableStateListOf()
    val redo: SnapshotStateList<String> = mutableStateListOf()

    // scripts abertos (caminho relativo -> texto)
    val scriptTexts = mutableStateMapOf<String, TextFieldValue>()
    val dirtyScripts: SnapshotStateList<String> = mutableStateListOf()
    var currentScript by mutableStateOf<String?>(null)
    var attachTarget by mutableStateOf<GameObject?>(null)     // abre "Anexar script" para este objeto
    var newScriptTarget by mutableStateOf<GameObject?>(null)  // abre "Novo script" para este objeto
    var attachToScene by mutableStateOf(false)                // "Script da cena…"
    var wantTab by mutableIntStateOf(-1)                      // pedido para trocar de aba

    init {
        Projects.migrate(dir)
        _settings = Projects.loadSettings(dir)
        val (rel, data) = Projects.loadMainScene(dir, _settings)
        scenePath = rel
        sceneScript = data.script
        objects.addAll(data.objects)
        history.add(snapshotOf(objects))
        Images.dir = Projects.assetsDir(dir)
    }

    val sceneName: String get() = scenePath?.substringAfterLast('/') ?: "sem_titulo.scene"
    fun hasUnsaved(): Boolean = dirty || dirtyScripts.isNotEmpty() || scenePath == null

    fun edited() { dirty = true; rev++ }

    // ---------------------------------------------------------------- scripts
    fun scriptValue(path: String): TextFieldValue =
        scriptTexts[path] ?: TextFieldValue(Projects.readText(dir, path)).also { scriptTexts[path] = it }

    fun setScript(path: String, v: TextFieldValue) {
        val old = scriptTexts[path]
        scriptTexts[path] = v
        if (old == null || old.text != v.text) { if (path !in dirtyScripts) dirtyScripts.add(path) }
    }

    /** Texto do script para o jogo: o que está em memória (mesmo sem salvar) ou o do disco. */
    fun scriptSource(path: String): String = scriptTexts[path]?.text ?: Projects.readText(dir, path)

    fun createScript(path: String, text: String = SCRIPT_TEMPLATE) {
        Projects.writeText(dir, path, text)
        scriptTexts[path] = TextFieldValue(text)
        dirtyScripts.remove(path)
        currentScript = path
        rev++
    }

    fun deleteScript(path: String) {
        File(dir, path).delete()
        scriptTexts.remove(path); dirtyScripts.remove(path)
        objects.forEach { if (it.script == path) it.script = "" }
        if (sceneScript == path) sceneScript = ""
        if (currentScript == path) currentScript = null
        edited()
    }

    // ---------------------------------------------------------------- salvar / abrir
    fun saveScripts() {
        for (p in dirtyScripts.toList()) scriptTexts[p]?.let { Projects.writeText(dir, p, it.text) }
        dirtyScripts.clear()
    }

    fun saveAll() {
        val rel = scenePath ?: return
        Projects.saveScene(dir, rel, SceneData(sceneScript, objects.toList()))
        Projects.saveSettings(dir, settings)
        saveScripts()
        dirty = false
    }

    fun saveSceneAs(rel: String) {
        scenePath = rel
        if (settings.mainScene.isBlank() || !File(dir, settings.mainScene).exists()) _settings = settings.copy(mainScene = rel)
        saveAll()
    }

    fun setMainScene() { val r = scenePath ?: return; _settings = settings.copy(mainScene = r); dirty = true }

    fun newScene() {
        objects.clear(); sceneScript = ""; scenePath = null; sel = null
        history.clear(); redo.clear(); history.add(snapshotOf(objects))
        dirty = true; rev++
    }

    fun openScene(rel: String) {
        val data = Projects.loadScene(dir, rel)
        objects.clear(); objects.addAll(data.objects)
        sceneScript = data.script; scenePath = rel; sel = null
        history.clear(); redo.clear(); history.add(snapshotOf(objects))
        dirty = false; rev++
    }

    // ---------------------------------------------------------------- histórico
    fun commitHistory() {
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
            restoreFrom(history.last(), objects); sel = null; dirty = true; rev++
        }
    }

    fun redoIt() {
        if (redo.isNotEmpty()) {
            val j = redo.removeAt(redo.size - 1)
            history.add(j); restoreFrom(j, objects); sel = null; dirty = true; rev++
        }
    }

    // ---------------------------------------------------------------- objetos
    fun unique(base: String): String {
        val b = base.trimEnd { it.isDigit() }.ifEmpty { "obj" }
        var i = 1
        while (objects.any { it.name == "$b$i" }) i++
        return "$b$i"
    }

    fun add(kind: NodeKind) {
        val o = kind.make()
        o.name = unique(o.name)
        if (o.type == "mesh") { o.x = 0.0; o.y = o.h / 2; o.z = 0.0; if (o.shape == "plane") o.y = 0.0 }
        else if (o.type != "tilemap") { o.x = settings.worldW / 2.0; o.y = settings.worldH / 2.0 }
        if (o.type == "text") o.fixed = settings.is3d
        objects.add(o); sel = o; edited()
    }

    fun duplicate() {
        val o = sel ?: return
        val c = o.copy()
        c.name = unique(o.name)
        if (o.type == "mesh") { c.x += 1.0; c.z += 1.0 } else { c.x += 20; c.y += 20 }
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

    // ---------------------------------------------------------------- GIF e animação
    /** Aplica imagens escolhidas a um Sprite Animado. GIFs são cortados em quadros automaticamente. */
    fun applyAnimImages(o: GameObject, names: List<String>) {
        for (name in names) {
            if (GifSlicer.isGif(name)) {
                val r = GifSlicer.slice(Projects.assetsDir(dir), name, o.animMode == "files") ?: continue
                if (o.animMode == "files") { o.frameFiles.clear(); o.frameFiles.addAll(r.files) } else { o.sheet = r.sheet ?: ""; o.fw = r.w.toDouble(); o.fh = r.h.toDouble() }
                o.anims.clear()
                o.anims.add(Anim("gif", 0, r.frames - 1, r.fps, true))
                o.anim = "gif"; o.frameT = 0.0
                val sc = if (r.w > 200) 200.0 / r.w else 1.0
                o.w = r.w * sc; o.h = r.h * sc
            } else if (o.animMode == "files") o.frameFiles.add(name) else o.sheet = name
        }
    }
}
