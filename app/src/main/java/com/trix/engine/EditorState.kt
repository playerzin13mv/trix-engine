package com.trix.engine

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

/** Um tipo de objeto que aparece na janela "Criar novo objeto". */
class NodeKind(val icon: String, val title: String, val desc: String, val make: () -> GameObject)

private fun part(shape: String, base: String, w: Double, h: Double, color: String) = GameObject(base, "part").apply {
    this.shape = shape; this.w = w; this.h = h; this.color = android.graphics.Color.parseColor(color)
}

val NODE_KINDS: List<NodeKind> = listOf(
    NodeKind("▭", "Parte — Caixa", "Forma retangular. A forma pode ser trocada no Inspetor.") { part("box", "caixa", 100.0, 100.0, "#22D3EE") },
    NodeKind("●", "Parte — Círculo", "Forma circular.") { part("circle", "bola", 80.0, 80.0, "#A855F7") },
    NodeKind("◢", "Parte — Rampa (Wedge)", "Rampa para física de inclinação (estilo Sonic).") { part("wedge", "rampa", 200.0, 100.0, "#15803D").apply { body = "static" } },
    NodeKind("▬", "Parte — Cápsula", "Retângulo com pontas arredondadas.") { part("capsule", "capsula", 120.0, 50.0, "#F43F5E") },
    NodeKind("▲", "Parte — Triângulo", "Triângulo isósceles.") { part("triangle", "triangulo", 90.0, 90.0, "#FACC15") },
    NodeKind("▰", "Plataforma sólida", "Caixa estática: corpos dinâmicos ficam em cima dela.") { part("box", "plataforma", 240.0, 32.0, "#64748B").apply { body = "static" } },
    NodeKind("🏃", "Personagem (corpo dinâmico)", "Caixa com gravidade e colisão com o cenário.") { part("box", "jogador", 32.0, 48.0, "#F59E0B").apply { body = "dynamic"; gravity = true } },
    NodeKind("T", "Texto", "Texto na cena. Use \"Fixo na tela\" para HUD.") { GameObject("texto", "text").apply { h = 28.0; text = "Olá"; color = android.graphics.Color.WHITE } },
    NodeKind("🖼", "Sprite (imagem)", "Mostra uma imagem da pasta assets.") { GameObject("sprite", "sprite").apply { w = 100.0; h = 100.0 } },
    NodeKind("🎞", "Sprite Animado", "Animação com spritesheet ou com imagens soltas.") { GameObject("animado", "anim").apply { w = 64.0; h = 64.0 } },
    NodeKind("⬚", "Forma de Colisão (retângulo)", "Área sólida invisível no jogo.") { GameObject("colisao", "collision").apply { w = 200.0; h = 40.0; body = "static" } },
    NodeKind("◿", "Forma de Colisão (rampa)", "Rampa sólida invisível no jogo.") { GameObject("colisao_rampa", "collision").apply { shape = "wedge"; w = 200.0; h = 100.0; body = "static" } },
    NodeKind("◌", "Gatilho (área)", "Área que não bloqueia: use overlaps(a, b) no código.") { GameObject("gatilho", "collision").apply { w = 120.0; h = 120.0; body = "static"; trigger = true } },
    NodeKind("▦", "Tilemap (blocos)", "Grade de blocos para fases e mundos. Pinte direto na cena.") { GameObject("mapa", "tilemap").apply { x = 0.0; y = 0.0; cols = 25; rows = 14; tileSize = 32.0; tiles = IntArray(25 * 14) } }
)

fun iconOf(o: GameObject): String = when (o.type) {
    "text" -> "T"; "sprite" -> "🖼"; "anim" -> "🎞"; "collision" -> if (o.trigger) "◌" else "⬚"; "tilemap" -> "▦"
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

class EditorState(val dir: File) {
    val objects: SnapshotStateList<GameObject> = mutableStateListOf()
    var settings by mutableStateOf(Projects.loadSettings(dir))
    var script by mutableStateOf(TextFieldValue(Projects.readScript(dir)))
    var rev by mutableIntStateOf(0)
    var sel by mutableStateOf<GameObject?>(null)
    var snap by mutableStateOf(true)
    var paint by mutableStateOf(false)
    var brush by mutableIntStateOf(1)
    var saved by mutableStateOf(true)
    val history: SnapshotStateList<String> = mutableStateListOf()
    val redo: SnapshotStateList<String> = mutableStateListOf()

    init {
        objects.addAll(Projects.loadScene(dir))
        history.add(snapshotOf(objects))
        Images.dir = Projects.assetsDir(dir)
    }

    fun edited() { rev++ }

    fun persist() {
        Projects.saveScene(dir, objects)
        Projects.saveSettings(dir, settings)
        Projects.writeScript(dir, script.text)
        saved = true
    }

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
            restoreFrom(history.last(), objects); sel = null; rev++
        }
    }

    fun redoIt() {
        if (redo.isNotEmpty()) {
            val j = redo.removeAt(redo.size - 1)
            history.add(j); restoreFrom(j, objects); sel = null; rev++
        }
    }

    fun unique(base: String): String {
        val b = base.trimEnd { it.isDigit() }.ifEmpty { "obj" }
        var i = 1
        while (objects.any { it.name == "$b$i" }) i++
        return "$b$i"
    }

    fun add(kind: NodeKind) {
        val o = kind.make()
        o.name = unique(o.name)
        if (o.type != "tilemap") { o.x = settings.worldW / 2.0; o.y = settings.worldH / 2.0 }
        objects.add(o); sel = o; edited()
    }

    fun duplicate() {
        val o = sel ?: return
        val c = o.copy()
        c.name = unique(o.name)
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
}
