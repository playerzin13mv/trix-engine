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

const val WW = 400f   // largura do mundo do jogo
const val WH = 700f   // altura do mundo do jogo

class GameObject(var name: String, var type: String = "rect") : Props {
    var x = 200.0
    var y = 350.0
    var w = 100.0
    var h = 100.0
    var vx = 0.0
    var vy = 0.0
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
        "text" -> text; "visible" -> visible; "name" -> name; "color" -> colorHex()
        else -> throw TrixError("objeto '$name' não tem '$n'")
    }

    override fun set(n: String, v: Any?) {
        when (n) {
            "x" -> x = d(v, n); "y" -> y = d(v, n)
            "w" -> w = d(v, n); "h" -> h = d(v, n)
            "vx" -> vx = d(v, n); "vy" -> vy = d(v, n)
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
        .put("color", colorHex()).put("text", text).put("visible", visible)

    fun copy(): GameObject = fromJson(toJson())

    companion object {
        fun fromJson(o: JSONObject) = GameObject(o.getString("name"), o.optString("type", "rect")).apply {
            x = o.optDouble("x", 200.0); y = o.optDouble("y", 350.0)
            w = o.optDouble("w", 100.0); h = o.optDouble("h", 100.0)
            vx = o.optDouble("vx", 0.0); vy = o.optDouble("vy", 0.0)
            color = try { AColor.parseColor(o.optString("color", "#22D3EE")) } catch (e: Exception) { color }
            text = o.optString("text", "Texto"); visible = o.optBoolean("visible", true)
        }
    }
}

object Projects {
    const val SCENE = "scene.json"
    const val SCRIPT = "main.trix"

    fun root() = File(Environment.getExternalStorageDirectory(), "TrixEngine/Projects")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun list(): List<File> =
        root().listFiles { f -> f.isDirectory && File(f, SCENE).exists() }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun create(name: String, sample: Boolean): File? {
        val clean = name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "")
        if (clean.isEmpty()) return null
        val dir = File(root(), clean)
        if (dir.exists()) return null
        if (!dir.mkdirs()) return null
        val objs = if (sample) listOf(
            GameObject("player", "rect").apply { x = 200.0; y = 620.0; w = 90.0; h = 30.0 },
            GameObject("coin", "circle").apply { x = 200.0; y = 120.0; w = 36.0; h = 36.0; color = AColor.parseColor("#FACC15") },
            GameObject("hud", "text").apply { x = 200.0; y = 60.0; h = 30.0; text = "Pontos: 0"; color = AColor.WHITE }
        ) else listOf(
            GameObject("box", "rect").apply { x = 200.0; y = 350.0; color = AColor.parseColor("#A855F7") }
        )
        saveScene(dir, objs)
        writeScript(dir, if (sample) SAMPLE_SCRIPT else BLANK_SCRIPT)
        return dir
    }

    fun loadScene(dir: File): List<GameObject> = try {
        val arr = JSONObject(File(dir, SCENE).readText()).getJSONArray("objects")
        (0 until arr.length()).map { GameObject.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveScene(dir: File, objs: List<GameObject>) {
        val arr = JSONArray(); objs.forEach { arr.put(it.toJson()) }
        File(dir, SCENE).writeText(JSONObject().put("world_w", WW.toDouble()).put("world_h", WH.toDouble()).put("objects", arr).toString(2))
    }

    fun readScript(dir: File): String = File(dir, SCRIPT).let { if (it.exists()) it.readText() else "" }
    fun writeScript(dir: File, s: String) { File(dir, SCRIPT).writeText(s) }

    private val BLANK_SCRIPT = """
# Script principal (TrixScript)
# 'box' é um objeto da cena

func start()
    print("Olá, Trix!")
end

func update(dt)
    box.x += 60 * dt
    if box.x > 400
        box.x = 0
    end
end
""".trimStart()

    private val SAMPLE_SCRIPT = """
# Coletar moedas — exemplo da Trix Engine
# Toque e arraste para mover a nave

var score = 0

func reset_coin()
    coin.x = random(30, 370)
    coin.y = -30
    coin.vy = random(150, 300)
end

func start()
    hud.text = "Pontos: 0"
    reset_coin()
end

func update(dt)
    if touch.down
        player.x = clamp(touch.x, 45, 355)
    end

    if overlaps(player, coin)
        score += 1
        hud.text = "Pontos: " + score
        beep()
        vibrate(30)
        reset_coin()
    end

    if coin.y > 740
        reset_coin()
    end
end
""".trimStart()
}
