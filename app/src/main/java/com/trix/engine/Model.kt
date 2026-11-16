package com.trix.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Environment
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError
import com.trix.engine.lang.fmt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Anim(var name: String, var from: Int, var to: Int, var fps: Double, var loop: Boolean) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("from", from).put("to", to).put("fps", fps).put("loop", loop)
    companion object {
        fun fromJson(o: JSONObject) = Anim(o.optString("name", "anim"), o.optInt("from", 0), o.optInt("to", 0), o.optDouble("fps", 8.0), o.optBoolean("loop", true))
    }
}

/**
 * Tipos: part (forma), text, sprite (imagem), anim (sprite animado), collision (forma de colisão), tilemap (blocos).
 * Formas de "part": box, circle, wedge (rampa), capsule, triangle.
 */
class GameObject(var name: String, var type: String = "part") : Props {
    var x = 400.0
    var y = 225.0
    var w = 100.0
    var h = 100.0
    var vx = 0.0
    var vy = 0.0
    var rotation = 0.0
    var alpha = 1.0
    var gravity = false
    var image = ""
    var shape = "box"
    var flip = false
    var flipX = false
    var fixed = false
    var color: Int = AColor.parseColor("#22D3EE")
    var text = "Texto"
    var visible = true
    var script = ""            // caminho do script anexado (ex.: scripts/jogador.tr)

    // 3D (type = "mesh": shape box | sphere | plane | cylinder | pyramid)
    var z = 0.0
    var d = 1.0
    var rx = 0.0
    var ry = 0.0
    var vz = 0.0

    // física
    var body = "none"          // none | static | dynamic
    var trigger = false
    var stick = false
    var stepUp = 12.0
    var bounce = 0.0
    var grounded = false
    var groundAngle = 0.0
    var wall = 0
    var ceiling = false

    // sprite animado
    var animMode = "sheet"     // sheet | files
    var sheet = ""
    var fw = 32.0
    var fh = 32.0
    val frameFiles = ArrayList<String>()
    val anims = ArrayList<Anim>()
    var anim = ""
    var playing = true
    var animSpeed = 1.0
    var frameT = 0.0

    // tilemap
    var cols = 20
    var rows = 12
    var tileSize = 32.0
    var tiles = IntArray(20 * 12)

    fun bw(): Double = when (type) {
        "text" -> Math.max(text.length * h * 0.55, h)
        "tilemap" -> cols * tileSize
        else -> w
    }
    fun bh(): Double = if (type == "tilemap") rows * tileSize else h
    fun left(): Double = if (type == "tilemap") x else x - bw() / 2
    fun top(): Double = if (type == "tilemap") y else y - bh() / 2
    fun colorHex(): String = String.format("#%06X", color and 0xFFFFFF)

    fun currentAnim(): Anim? = anims.firstOrNull { it.name == anim } ?: anims.firstOrNull()
    fun frameIndex(): Int {
        val a = currentAnim() ?: return 0
        val n = a.to - a.from + 1
        if (n <= 0) return a.from
        val i = frameT.toInt()
        return a.from + (if (a.loop) i % n else Math.min(i, n - 1))
    }
    fun updateAnim(dt: Double) {
        if (type != "anim" || !playing) return
        val a = currentAnim() ?: return
        val n = a.to - a.from + 1
        if (n <= 1) return
        frameT += dt * a.fps * animSpeed
        if (a.loop) { if (frameT >= n) frameT %= n } else if (frameT > n - 0.001) frameT = n - 0.001
    }

    fun tileAt(c: Int, r: Int): Int = if (c in 0 until cols && r in 0 until rows) tiles[r * cols + c] else 0
    fun setTile(c: Int, r: Int, id: Int) { if (c in 0 until cols && r in 0 until rows) tiles[r * cols + c] = id }
    fun resizeTiles(nc: Int, nr: Int) {
        val n = IntArray(nc * nr)
        for (r in 0 until Math.min(rows, nr)) for (c in 0 until Math.min(cols, nc)) n[r * nc + c] = tiles[r * cols + c]
        tiles = n; cols = nc; rows = nr
    }

    private fun num(v: Any?, n: String): Double = (v as? Double) ?: throw TrixError("'$n' precisa ser um número")
    private fun str(v: Any?): String = fmt(v).let { if (it == "null") "" else it }

    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "w" -> w; "h" -> h; "vx" -> vx; "vy" -> vy
        "rotation" -> rotation; "alpha" -> alpha; "gravity" -> gravity; "image" -> image
        "text" -> text; "visible" -> visible; "name" -> name; "type" -> type
        "shape" -> shape; "flip" -> flip; "flipx" -> flipX; "fixed" -> fixed
        "body" -> body; "trigger" -> trigger; "stick" -> stick; "stepup" -> stepUp; "bounce" -> bounce
        "grounded" -> grounded; "ground_angle" -> groundAngle; "wall" -> wall.toDouble(); "ceiling" -> ceiling
        "anim" -> anim; "playing" -> playing; "animspeed" -> animSpeed; "frame" -> frameIndex().toDouble()
        "sheet" -> sheet; "script" -> script
        "z" -> z; "d" -> d; "rx" -> rx; "ry" -> ry; "vz" -> vz
        "cols" -> cols.toDouble(); "rows" -> rows.toDouble(); "tilesize" -> tileSize
        "color" -> colorHex()
        else -> throw TrixError("objeto '$name' não tem '$n'")
    }

    override fun set(n: String, v: Any?) {
        when (n) {
            "x" -> x = num(v, n); "y" -> y = num(v, n)
            "w" -> w = num(v, n); "h" -> h = num(v, n)
            "vx" -> vx = num(v, n); "vy" -> vy = num(v, n)
            "rotation" -> rotation = num(v, n)
            "alpha" -> alpha = num(v, n).coerceIn(0.0, 1.0)
            "gravity" -> gravity = v == true
            "image" -> image = str(v)
            "sheet" -> sheet = str(v)
            "z" -> z = num(v, n); "d" -> d = num(v, n); "rx" -> rx = num(v, n); "ry" -> ry = num(v, n); "vz" -> vz = num(v, n)
            "text" -> text = fmt(v)
            "visible" -> visible = v != false && v != null
            "shape" -> shape = str(v)
            "flip" -> flip = v == true
            "flipx" -> flipX = v == true
            "fixed" -> fixed = v == true
            "body" -> body = str(v)
            "trigger" -> trigger = v == true
            "stick" -> stick = v == true
            "stepup" -> stepUp = num(v, n)
            "bounce" -> bounce = num(v, n)
            "anim" -> { val s = str(v); if (s != anim) { anim = s; frameT = 0.0 } }
            "playing" -> playing = v == true
            "animspeed" -> animSpeed = num(v, n)
            "color" -> color = when (v) {
                is String -> try { AColor.parseColor(v) } catch (e: IllegalArgumentException) {
                    throw TrixError("cor inválida '$v' (use \"#RRGGBB\")")
                }
                is Double -> 0xFF000000.toInt() or v.toInt()
                else -> throw TrixError("cor deve ser texto \"#RRGGBB\"")
            }
            "grounded", "ground_angle", "wall", "ceiling", "frame", "type", "name", "cols", "rows", "tilesize" ->
                throw TrixError("'$n' é somente leitura")
            else -> throw TrixError("objeto '$name' não tem '$n'")
        }
    }

    override fun toString() = name

    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("name", name).put("type", type)
            .put("x", x).put("y", y).put("w", w).put("h", h)
            .put("vx", vx).put("vy", vy)
            .put("rotation", rotation).put("alpha", alpha).put("gravity", gravity)
            .put("image", image).put("shape", shape).put("flip", flip).put("flipX", flipX).put("fixed", fixed)
            .put("color", colorHex()).put("text", text).put("visible", visible)
            .put("body", body).put("trigger", trigger).put("stick", stick)
            .put("stepUp", stepUp).put("bounce", bounce).put("script", script)
        if (type == "mesh") o.put("z", z).put("d", d).put("rx", rx).put("ry", ry).put("vz", vz)
        if (type == "anim") {
            o.put("animMode", animMode).put("sheet", sheet).put("fw", fw).put("fh", fh)
                .put("anim", anim).put("playing", playing).put("animSpeed", animSpeed)
            val ff = JSONArray(); frameFiles.forEach { ff.put(it) }; o.put("frameFiles", ff)
            val aa = JSONArray(); anims.forEach { aa.put(it.toJson()) }; o.put("anims", aa)
        }
        if (type == "tilemap") {
            o.put("cols", cols).put("rows", rows).put("tileSize", tileSize)
            val t = JSONArray(); tiles.forEach { t.put(it) }; o.put("tiles", t)
        }
        return o
    }

    fun copy(): GameObject = fromJson(toJson())

    companion object {
        fun fromJson(o: JSONObject): GameObject {
            var type = o.optString("type", "part")
            var shape = o.optString("shape", "box")
            if (type == "rect") { type = "part"; shape = "box" }       // projetos antigos
            if (type == "circle") { type = "part"; shape = "circle" }
            return GameObject(o.getString("name"), type).apply {
                this.shape = shape
                x = o.optDouble("x", 400.0); y = o.optDouble("y", 225.0)
                w = o.optDouble("w", 100.0); h = o.optDouble("h", 100.0)
                vx = o.optDouble("vx", 0.0); vy = o.optDouble("vy", 0.0)
                rotation = o.optDouble("rotation", 0.0); alpha = o.optDouble("alpha", 1.0)
                gravity = o.optBoolean("gravity", false); image = o.optString("image", "")
                flip = o.optBoolean("flip", false); flipX = o.optBoolean("flipX", false); fixed = o.optBoolean("fixed", false)
                color = try { AColor.parseColor(o.optString("color", "#22D3EE")) } catch (e: Exception) { color }
                text = o.optString("text", "Texto"); visible = o.optBoolean("visible", true)
                script = o.optString("script", "")
                z = o.optDouble("z", 0.0); d = o.optDouble("d", 1.0); rx = o.optDouble("rx", 0.0); ry = o.optDouble("ry", 0.0); vz = o.optDouble("vz", 0.0)
                body = o.optString("body", "none"); trigger = o.optBoolean("trigger", false)
                stick = o.optBoolean("stick", false); stepUp = o.optDouble("stepUp", 12.0); bounce = o.optDouble("bounce", 0.0)
                if (type == "anim") {
                    animMode = o.optString("animMode", "sheet"); sheet = o.optString("sheet", "")
                    fw = o.optDouble("fw", 32.0); fh = o.optDouble("fh", 32.0)
                    anim = o.optString("anim", ""); playing = o.optBoolean("playing", true); animSpeed = o.optDouble("animSpeed", 1.0)
                    o.optJSONArray("frameFiles")?.let { a -> for (i in 0 until a.length()) frameFiles.add(a.getString(i)) }
                    o.optJSONArray("anims")?.let { a -> for (i in 0 until a.length()) anims.add(Anim.fromJson(a.getJSONObject(i))) }
                }
                if (type == "tilemap") {
                    cols = o.optInt("cols", 20); rows = o.optInt("rows", 12); tileSize = o.optDouble("tileSize", 32.0)
                    tiles = IntArray(cols * rows)
                    o.optJSONArray("tiles")?.let { a -> for (i in 0 until Math.min(a.length(), tiles.size)) tiles[i] = a.optInt(i, 0) }
                }
            }
        }
    }
}

fun snapshotOf(list: List<GameObject>): String {
    val a = JSONArray(); list.forEach { a.put(it.toJson()) }; return a.toString()
}

fun restoreFrom(json: String, target: MutableList<GameObject>) {
    val a = JSONArray(json)
    target.clear()
    for (i in 0 until a.length()) target.add(GameObject.fromJson(a.getJSONObject(i)))
}

data class ProjectSettings(
    val author: String = "",
    val description: String = "",
    val version: String = "1.0",
    val worldW: Int = 800,
    val worldH: Int = 450,
    val bg: Int = AColor.parseColor("#0D1020"),
    val gravity: Double = 0.0,
    val showFps: Boolean = false,
    val showGrid: Boolean = true,
    val grid: Int = 10,
    val gamepad: Boolean = false,
    val showCollisions: Boolean = false,
    val mode: String = "2d",
    val mainScene: String = "scenes/main.scene",
    val camX: Double = 0.0, val camY: Double = 5.0, val camZ: Double = -10.0,
    val camYaw: Double = 0.0, val camPitch: Double = -20.0, val camFov: Double = 60.0
) {
    val is3d: Boolean get() = mode == "3d"
    val landscape: Boolean get() = worldW >= worldH

    fun toJson(): JSONObject = JSONObject()
        .put("author", author).put("description", description).put("version", version)
        .put("worldW", worldW).put("worldH", worldH)
        .put("bg", String.format("#%06X", bg and 0xFFFFFF))
        .put("gravity", gravity).put("showFps", showFps)
        .put("showGrid", showGrid).put("grid", grid)
        .put("gamepad", gamepad).put("showCollisions", showCollisions)
        .put("mode", mode).put("mainScene", mainScene)
        .put("camX", camX).put("camY", camY).put("camZ", camZ).put("camYaw", camYaw).put("camPitch", camPitch).put("camFov", camFov)

    companion object {
        fun fromJson(o: JSONObject): ProjectSettings {
            val d = ProjectSettings()
            return ProjectSettings(
                author = o.optString("author", ""),
                description = o.optString("description", ""),
                version = o.optString("version", "1.0"),
                worldW = o.optInt("worldW", d.worldW),
                worldH = o.optInt("worldH", d.worldH),
                bg = try { AColor.parseColor(o.optString("bg", "#0D1020")) } catch (e: Exception) { d.bg },
                gravity = o.optDouble("gravity", 0.0),
                showFps = o.optBoolean("showFps", false),
                showGrid = o.optBoolean("showGrid", true),
                grid = o.optInt("grid", 10),
                gamepad = o.optBoolean("gamepad", false),
                showCollisions = o.optBoolean("showCollisions", false),
                mode = o.optString("mode", "2d"),
                mainScene = o.optString("mainScene", "scenes/main.scene"),
                camX = o.optDouble("camX", 0.0), camY = o.optDouble("camY", 5.0), camZ = o.optDouble("camZ", -10.0),
                camYaw = o.optDouble("camYaw", 0.0), camPitch = o.optDouble("camPitch", -20.0), camFov = o.optDouble("camFov", 60.0)
            )
        }
    }
}

class SceneData(val script: String, val objects: List<GameObject>)

object Projects {
    const val SETTINGS = "project.json"

    fun root() = File(Environment.getExternalStorageDirectory(), "TrixEngine/Projects")
    fun assetsDir(dir: File) = File(dir, "assets")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun isProject(f: File) = f.isDirectory && (File(f, SETTINGS).exists() || File(f, "scene.json").exists() || listRel(f, ".scene").isNotEmpty())

    fun list(): List<File> =
        root().listFiles { f -> isProject(f) }?.sortedByDescending { it.lastModified() } ?: emptyList()

    val TEMPLATE_NAMES_2D = listOf("Vazio", "Coletar moedas", "Pulo com gravidade", "Estilo Mario", "Estilo Sonic", "Estilo Minecraft")
    val TEMPLATE_NAMES_3D = listOf("3D vazio", "3D simples (andar e coletar)")

    fun safeRel(rel: String): Boolean = rel.isNotEmpty() && !rel.startsWith("/") && !rel.split("/").any { it == ".." }

    /** Lista arquivos do projeto (caminhos relativos com "/") que terminam com [ext]. */
    fun listRel(dir: File, ext: String): List<String> {
        val out = ArrayList<String>()
        fun walk(f: File, prefix: String, depth: Int) {
            if (depth > 5) return
            val kids = f.listFiles() ?: return
            for (k in kids.sortedBy { it.name }) {
                val rel = if (prefix.isEmpty()) k.name else prefix + "/" + k.name
                if (k.isDirectory) { if (!k.name.startsWith(".")) walk(k, rel, depth + 1) }
                else if (k.name.endsWith(ext)) out.add(rel)
            }
        }
        walk(dir, "", 0)
        return out
    }

    fun readText(dir: File, rel: String): String = if (!safeRel(rel)) "" else File(dir, rel).let { if (it.exists()) it.readText() else "" }
    fun writeText(dir: File, rel: String, text: String) {
        if (!safeRel(rel)) return
        val f = File(dir, rel); f.parentFile?.mkdirs(); f.writeText(text)
    }

    fun loadScene(dir: File, rel: String): SceneData = try {
        val o = JSONObject(File(dir, rel).readText())
        val arr = o.getJSONArray("objects")
        SceneData(o.optString("script", ""), (0 until arr.length()).map { GameObject.fromJson(arr.getJSONObject(it)) })
    } catch (e: Exception) { SceneData("", emptyList()) }

    fun saveScene(dir: File, rel: String, data: SceneData) {
        val arr = JSONArray(); data.objects.forEach { arr.put(it.toJson()) }
        writeText(dir, rel, JSONObject().put("script", data.script).put("objects", arr).toString(2))
    }

    fun loadSettings(dir: File): ProjectSettings = try {
        ProjectSettings.fromJson(JSONObject(File(dir, SETTINGS).readText()))
    } catch (e: Exception) { ProjectSettings() }

    fun saveSettings(dir: File, s: ProjectSettings) { File(dir, SETTINGS).writeText(s.toJson().toString(2)) }

    /** Projetos antigos (scene.json + main.trix/main.tr) -> scenes/main.scene + scripts/main.tr */
    fun migrate(dir: File) {
        val old = File(dir, "scene.json")
        if (old.exists() && listRel(dir, ".scene").isEmpty()) {
            try {
                val arr = JSONObject(old.readText()).getJSONArray("objects")
                var scriptRel = ""
                val oldScript = listOf("main.tr", "main.trix").map { File(dir, it) }.firstOrNull { it.exists() }
                if (oldScript != null) { writeText(dir, "scripts/main.tr", oldScript.readText()); scriptRel = "scripts/main.tr"; oldScript.delete() }
                writeText(dir, "scenes/main.scene", JSONObject().put("script", scriptRel).put("objects", arr).toString(2))
                old.delete()
                if (!File(dir, SETTINGS).exists()) saveSettings(dir, ProjectSettings())
            } catch (e: Exception) { }
        }
    }

    fun loadMainScene(dir: File, settings: ProjectSettings): Pair<String, SceneData> {
        var rel = settings.mainScene
        if (!File(dir, rel).exists()) rel = listRel(dir, ".scene").firstOrNull() ?: rel
        return rel to loadScene(dir, rel)
    }

    fun loadData(dir: File): JSONObject = try { JSONObject(File(dir, "save.json").readText()) } catch (e: Exception) { JSONObject() }
    fun saveData(dir: File, o: JSONObject) { File(dir, "save.json").writeText(o.toString()) }

    fun create(name: String, mode: String, template: Int): File? {
        val clean = name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "")
        if (clean.isEmpty()) return null
        val dir = File(root(), clean)
        if (dir.exists()) return null
        if (!dir.mkdirs()) return null
        val t = Templates.build(mode, template)
        for ((rel, text) in t.scripts) writeText(dir, rel, text)
        saveScene(dir, "scenes/main.scene", SceneData(t.sceneScript, t.objects))
        saveSettings(dir, t.settings)
        assetsDir(dir).mkdirs()
        return dir
    }

    fun duplicate(dir: File): File? {
        var i = 1
        var target = File(dir.parentFile, dir.name + " cópia")
        while (target.exists()) { i++; target = File(dir.parentFile, dir.name + " cópia " + i) }
        return if (dir.copyRecursively(target)) target else null
    }
}
