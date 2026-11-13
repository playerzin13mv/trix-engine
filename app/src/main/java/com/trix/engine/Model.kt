package com.trix.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Environment
import com.trix.engine.lang.Props
import com.trix.engine.lang.TrixError
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GameObject(var name: String, var type: String = "rect") : Props {
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
    var color: Int = AColor.parseColor("#22D3EE")
    var text = "Texto"
    var visible = true

    fun bw(): Double = if (type == "text") Math.max(text.length * h * 0.55, h) else w
    fun bh(): Double = h
    fun colorHex(): String = String.format("#%06X", color and 0xFFFFFF)

    private fun d(v: Any?, n: String): Double =
        (v as? Double) ?: throw TrixError("'$n' precisa ser um número")

    override fun get(n: String): Any? = when (n) {
        "x" -> x; "y" -> y; "w" -> w; "h" -> h; "vx" -> vx; "vy" -> vy
        "rotation" -> rotation; "alpha" -> alpha; "gravity" -> gravity; "image" -> image
        "text" -> text; "visible" -> visible; "name" -> name; "type" -> type
        "color" -> colorHex()
        else -> throw TrixError("objeto '$name' não tem '$n'")
    }

    override fun set(n: String, v: Any?) {
        when (n) {
            "x" -> x = d(v, n); "y" -> y = d(v, n)
            "w" -> w = d(v, n); "h" -> h = d(v, n)
            "vx" -> vx = d(v, n); "vy" -> vy = d(v, n)
            "rotation" -> rotation = d(v, n)
            "alpha" -> alpha = d(v, n).coerceIn(0.0, 1.0)
            "gravity" -> gravity = v == true
            "image" -> image = com.trix.engine.lang.fmt(v).let { if (it == "null") "" else it }
            "text" -> text = com.trix.engine.lang.fmt(v)
            "visible" -> visible = v != false && v != null
            "color" -> color = when (v) {
                is String -> try { AColor.parseColor(v) } catch (e: IllegalArgumentException) {
                    throw TrixError("cor inválida '$v' (use \"#RRGGBB\")")
                }
                is Double -> 0xFF000000.toInt() or v.toInt()
                else -> throw TrixError("cor deve ser texto \"#RRGGBB\"")
            }
            else -> throw TrixError("objeto '$name' não tem '$n'")
        }
    }

    override fun toString() = name

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("type", type)
        .put("x", x).put("y", y).put("w", w).put("h", h)
        .put("vx", vx).put("vy", vy)
        .put("rotation", rotation).put("alpha", alpha).put("gravity", gravity)
        .put("image", image)
        .put("color", colorHex()).put("text", text).put("visible", visible)

    fun copy(): GameObject = fromJson(toJson())

    companion object {
        fun fromJson(o: JSONObject) = GameObject(o.getString("name"), o.optString("type", "rect")).apply {
            x = o.optDouble("x", 400.0); y = o.optDouble("y", 225.0)
            w = o.optDouble("w", 100.0); h = o.optDouble("h", 100.0)
            vx = o.optDouble("vx", 0.0); vy = o.optDouble("vy", 0.0)
            rotation = o.optDouble("rotation", 0.0); alpha = o.optDouble("alpha", 1.0)
            gravity = o.optBoolean("gravity", false); image = o.optString("image", "")
            color = try { AColor.parseColor(o.optString("color", "#22D3EE")) } catch (e: Exception) { color }
            text = o.optString("text", "Texto"); visible = o.optBoolean("visible", true)
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
    val grid: Int = 10
) {
    val landscape: Boolean get() = worldW >= worldH

    fun toJson(): JSONObject = JSONObject()
        .put("author", author).put("description", description).put("version", version)
        .put("worldW", worldW).put("worldH", worldH)
        .put("bg", String.format("#%06X", bg and 0xFFFFFF))
        .put("gravity", gravity).put("showFps", showFps)
        .put("showGrid", showGrid).put("grid", grid)

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
                grid = o.optInt("grid", 10)
            )
        }
    }
}

object Projects {
    const val SCENE = "scene.json"
    const val SCRIPT = "main.trix"
    const val SETTINGS = "project.json"

    fun root() = File(Environment.getExternalStorageDirectory(), "TrixEngine/Projects")
    fun assetsDir(dir: File) = File(dir, "assets")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun list(): List<File> =
        root().listFiles { f -> f.isDirectory && File(f, SCENE).exists() }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun create(name: String, template: Int): File? {
        val clean = name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "")
        if (clean.isEmpty()) return null
        val dir = File(root(), clean)
        if (dir.exists()) return null
        if (!dir.mkdirs()) return null
        val (objs, script, settings) = when (template) {
            1 -> Triple(coinsScene(), COINS_SCRIPT, ProjectSettings())
            2 -> Triple(jumpScene(), JUMP_SCRIPT, ProjectSettings(gravity = 1500.0))
            else -> Triple(blankScene(), BLANK_SCRIPT, ProjectSettings())
        }
        saveScene(dir, objs)
        saveSettings(dir, settings)
        writeScript(dir, script)
        assetsDir(dir).mkdirs()
        return dir
    }

    fun duplicate(dir: File): File? {
        var i = 1
        var target = File(dir.parentFile, dir.name + " cópia")
        while (target.exists()) { i++; target = File(dir.parentFile, dir.name + " cópia " + i) }
        return if (dir.copyRecursively(target)) target else null
    }

    fun loadScene(dir: File): List<GameObject> = try {
        val arr = JSONObject(File(dir, SCENE).readText()).getJSONArray("objects")
        (0 until arr.length()).map { GameObject.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveScene(dir: File, objs: List<GameObject>) {
        val arr = JSONArray(); objs.forEach { arr.put(it.toJson()) }
        File(dir, SCENE).writeText(JSONObject().put("objects", arr).toString(2))
    }

    fun loadSettings(dir: File): ProjectSettings = try {
        ProjectSettings.fromJson(JSONObject(File(dir, SETTINGS).readText()))
    } catch (e: Exception) { ProjectSettings() }

    fun saveSettings(dir: File, s: ProjectSettings) { File(dir, SETTINGS).writeText(s.toJson().toString(2)) }

    fun readScript(dir: File): String = File(dir, SCRIPT).let { if (it.exists()) it.readText() else "" }
    fun writeScript(dir: File, s: String) { File(dir, SCRIPT).writeText(s) }

    fun loadData(dir: File): JSONObject = try { JSONObject(File(dir, "save.json").readText()) } catch (e: Exception) { JSONObject() }
    fun saveData(dir: File, o: JSONObject) { File(dir, "save.json").writeText(o.toString()) }

    private fun blankScene() = listOf(
        GameObject("box", "rect").apply { x = 400.0; y = 225.0; color = AColor.parseColor("#A855F7") }
    )

    private fun coinsScene() = listOf(
        GameObject("player", "rect").apply { x = 400.0; y = 410.0; w = 110.0; h = 28.0 },
        GameObject("coin", "circle").apply { x = 400.0; y = 100.0; w = 36.0; h = 36.0; color = AColor.parseColor("#FACC15") },
        GameObject("hud", "text").apply { x = 400.0; y = 36.0; h = 28.0; text = "Pontos: 0"; color = AColor.WHITE }
    )

    private fun jumpScene() = listOf(
        GameObject("ground", "rect").apply { x = 400.0; y = 430.0; w = 800.0; h = 40.0; color = AColor.parseColor("#475569") },
        GameObject("player", "rect").apply { x = 120.0; y = 385.0; w = 50.0; h = 50.0; gravity = true },
        GameObject("obstacle", "rect").apply { x = 720.0; y = 385.0; w = 36.0; h = 50.0; vx = -320.0; color = AColor.parseColor("#F43F5E") },
        GameObject("hud", "text").apply { x = 400.0; y = 36.0; h = 28.0; text = "Pontos: 0"; color = AColor.WHITE }
    )

    private val BLANK_SCRIPT = """
# Script principal (TrixScript)
# 'box' é um objeto da cena

func start()
    print("Olá, Trix!")
end

func update(dt)
    box.x += 120 * dt
    box.rotation += 90 * dt
    if box.x > screen.w
        box.x = 0
    end
end
""".trimStart()

    private val COINS_SCRIPT = """
# Coletar moedas — exemplo da Trix Engine
# Toque e arraste para mover a nave

var score = 0
var best = load("best", 0)

func reset_coin()
    coin.x = random(40, screen.w - 40)
    coin.y = -30
    coin.vy = random(180, 320)
end

func start()
    hud.text = "Pontos: 0   Recorde: " + best
    reset_coin()
end

func update(dt)
    if touch.down
        player.x = clamp(touch.x, 55, screen.w - 55)
    end

    if overlaps(player, coin)
        score += 1
        if score > best
            best = score
            save("best", best)
        end
        hud.text = "Pontos: " + score + "   Recorde: " + best
        tone(880, 80)
        vibrate(30)
        reset_coin()
    end

    if coin.y > screen.h + 30
        reset_coin()
    end
end
""".trimStart()

    private val JUMP_SCRIPT = """
# Pulo com gravidade — toque na tela para pular
# (a gravidade do projeto está em Projeto > Física)

var score = 0

func update(dt)
    var floor_y = ground.y - ground.h / 2 - player.h / 2
    if player.y >= floor_y
        player.y = floor_y
        player.vy = 0
        if touch.tapped
            player.vy = -560
            tone(520, 60)
        end
    end

    if obstacle.x < -40
        obstacle.x = screen.w + 40
        score += 1
        hud.text = "Pontos: " + score
    end

    if overlaps(player, obstacle)
        tone(180, 220)
        vibrate(80)
        restart()
    end
end
""".trimStart()
}
