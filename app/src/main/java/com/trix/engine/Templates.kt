package com.trix.engine

import android.graphics.Color as AColor

class Tpl(val objs: List<GameObject>, val scripts: Map<String, String>, val sceneScript: String, val settings: ProjectSettings) {
    val objects get() = objs
}

object Templates {
    private fun c(hex: String) = AColor.parseColor(hex)

    fun build(mode: String, template: Int): Tpl {
        if (mode == "3d") return build3d(template)
        val g = "scripts/main.tr"
        return when (template) {
            1 -> Tpl(coinsScene(), mapOf(g to COINS_SCRIPT), g, ProjectSettings())
            2 -> Tpl(jumpScene(), mapOf(g to JUMP_SCRIPT), g, ProjectSettings(gravity = 1500.0))
            3 -> Tpl(marioScene(), mapOf(g to MARIO_SCRIPT), g, ProjectSettings(gravity = 1700.0, gamepad = true, bg = AColor.parseColor("#5C94FC"), showGrid = false))
            4 -> {
                val objs = sonicScene()
                objs.first { it.name == "sonic" }.script = "scripts/sonic.tr"
                Tpl(objs, mapOf("scripts/sonic.tr" to SONIC_SCRIPT.replace("sonic", "self")), "", ProjectSettings(gravity = 787.5, gamepad = true, bg = AColor.parseColor("#2563EB"), showGrid = false))
            }
            5 -> Tpl(minecraftScene(), mapOf(g to MINECRAFT_SCRIPT), g, ProjectSettings(gravity = 1600.0, gamepad = true, bg = AColor.parseColor("#87CEEB"), showGrid = false))
            else -> Tpl(blankScene(), mapOf(g to BLANK_SCRIPT), g, ProjectSettings())
        }
    }

    // ------------------------------------------------------------ 3D
    private fun mesh(name: String, shape: String, x: Double, y: Double, z: Double, w: Double, h: Double, d: Double, color: String) =
        GameObject(name, "mesh").apply { this.shape = shape; this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.d = d; this.color = c(color) }

    private fun build3d(template: Int): Tpl {
        val g = "scripts/main.tr"
        val s3 = ProjectSettings(
            mode = "3d", gravity = 22.0, gamepad = true, bg = AColor.parseColor("#7DD3FC"), showGrid = true,
            camX = 0.0, camY = 6.0, camZ = -10.0, camYaw = 0.0, camPitch = -25.0, camFov = 60.0
        )
        if (template == 1) {
            val objs = listOf(
                mesh("chao", "plane", 0.0, 0.0, 0.0, 40.0, 1.0, 40.0, "#4ADE80").apply { body = "static" },
                mesh("jogador", "box", 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, "#F59E0B").apply { body = "dynamic"; gravity = true },
                mesh("bloco1", "box", 4.0, 0.75, 4.0, 3.0, 1.5, 3.0, "#94A3B8").apply { body = "static" },
                mesh("bloco2", "box", -5.0, 1.5, 7.0, 3.0, 3.0, 3.0, "#64748B").apply { body = "static" },
                mesh("piramide", "pyramid", 8.0, 1.5, -3.0, 3.0, 3.0, 3.0, "#F43F5E"),
                mesh("coluna", "cylinder", -7.0, 2.0, -3.0, 1.5, 4.0, 1.5, "#A855F7"),
                mesh("moeda1", "sphere", 4.0, 2.2, 4.0, 0.7, 0.7, 0.7, "#FACC15"),
                mesh("moeda2", "sphere", -5.0, 3.7, 7.0, 0.7, 0.7, 0.7, "#FACC15"),
                mesh("moeda3", "sphere", 8.0, 1.0, 2.0, 0.7, 0.7, 0.7, "#FACC15"),
                mesh("moeda4", "sphere", -2.0, 1.0, 9.0, 0.7, 0.7, 0.7, "#FACC15"),
                hud("Moedas: 0")
            )
            return Tpl(objs, mapOf(g to SCRIPT_3D), g, s3)
        }
        return Tpl(
            listOf(
                mesh("chao", "plane", 0.0, 0.0, 0.0, 20.0, 1.0, 20.0, "#4ADE80").apply { body = "static" },
                mesh("cubo", "box", 0.0, 1.0, 0.0, 2.0, 2.0, 2.0, "#A855F7")
            ),
            mapOf(g to BLANK_3D), g, s3.copy(gamepad = false)
        )
    }

    private fun part(name: String, shape: String, x: Double, y: Double, w: Double, h: Double, color: String) =
        GameObject(name, "part").apply { this.shape = shape; this.x = x; this.y = y; this.w = w; this.h = h; this.color = c(color) }

    private fun solid(name: String, shape: String, x: Double, y: Double, w: Double, h: Double, color: String) =
        part(name, shape, x, y, w, h, color).apply { body = "static" }

    private fun hud(text: String) =
        GameObject("hud", "text").apply { x = 400.0; y = 30.0; h = 26.0; this.text = text; color = AColor.WHITE; fixed = true }

    // ------------------------------------------------------------ básicos
    fun blankScene() = listOf(part("box", "box", 400.0, 225.0, 100.0, 100.0, "#A855F7"))

    fun coinsScene() = listOf(
        part("player", "box", 400.0, 410.0, 110.0, 28.0, "#22D3EE"),
        part("coin", "circle", 400.0, 100.0, 36.0, 36.0, "#FACC15"),
        hud("Pontos: 0").apply { fixed = false; y = 36.0 }
    )

    fun jumpScene() = listOf(
        part("ground", "box", 400.0, 430.0, 800.0, 40.0, "#475569"),
        part("player", "box", 120.0, 385.0, 50.0, 50.0, "#22D3EE").apply { gravity = true },
        part("obstacle", "box", 720.0, 385.0, 36.0, 50.0, "#F43F5E").apply { vx = -320.0 },
        hud("Pontos: 0").apply { fixed = false; y = 36.0 }
    )

    // ------------------------------------------------------------ Mario
    fun marioScene(): List<GameObject> {
        val cols = 60
        val rows = 14
        val map = GameObject("map", "tilemap").apply { x = 0.0; y = 0.0; this.cols = cols; this.rows = rows; tileSize = 32.0; tiles = IntArray(cols * rows) }
        for (col in 0 until cols) {
            if (col in 22..24 || col in 40..41) continue
            map.setTile(col, 12, 1); map.setTile(col, 13, 2)
        }
        for (col in 8..12) map.setTile(col, 8, 6)
        map.setTile(10, 8, 7)
        for (col in 28..31) map.setTile(col, 9, 6)
        map.setTile(29, 9, 7)
        for (r in 10..11) { map.setTile(16, r, 3); map.setTile(17, r, 3) }
        for (i in 0..3) for (r in (11 - i)..11) map.setTile(50 + i, r, 3)
        return listOf(
            map,
            part("player", "box", 64.0, 340.0, 26.0, 30.0, "#EF4444").apply { body = "dynamic"; gravity = true },
            part("enemy1", "box", 600.0, 360.0, 28.0, 26.0, "#92400E").apply { body = "dynamic"; gravity = true; vx = -50.0 },
            part("enemy2", "box", 1400.0, 360.0, 28.0, 26.0, "#92400E").apply { body = "dynamic"; gravity = true; vx = -50.0 },
            part("coin1", "circle", 300.0, 340.0, 16.0, 16.0, "#FACC15"),
            part("coin2", "circle", 330.0, 340.0, 16.0, 16.0, "#FACC15"),
            part("coin3", "circle", 900.0, 260.0, 16.0, 16.0, "#FACC15"),
            part("coin4", "circle", 930.0, 260.0, 16.0, 16.0, "#FACC15"),
            part("coin5", "circle", 1250.0, 340.0, 16.0, 16.0, "#FACC15"),
            hud("Moedas: 0")
        )
    }

    // ------------------------------------------------------------ Sonic
    fun sonicScene() = listOf(
        solid("ground", "box", 1600.0, 425.0, 3200.0, 50.0, "#16A34A"),
        solid("ramp1", "wedge", 700.0, 350.0, 200.0, 100.0, "#15803D"),
        solid("hill", "box", 950.0, 350.0, 300.0, 100.0, "#16A34A"),
        solid("ramp2", "wedge", 1200.0, 350.0, 200.0, 100.0, "#15803D").apply { flip = true },
        solid("ramp3", "wedge", 1700.0, 325.0, 150.0, 150.0, "#15803D"),
        solid("plateau", "box", 1975.0, 325.0, 400.0, 150.0, "#16A34A"),
        solid("ramp4", "wedge", 2250.0, 325.0, 150.0, 150.0, "#15803D").apply { flip = true },
        solid("wall_end", "box", 3175.0, 300.0, 50.0, 300.0, "#475569"),
        part("sonic", "circle", 100.0, 360.0, 36.0, 36.0, "#3B82F6").apply { body = "dynamic"; gravity = true; stepUp = 14.0 },
        part("ring", "circle", -200.0, -200.0, 14.0, 14.0, "#FACC15").apply { visible = false },
        hud("Anéis: 0")
    )

    // ------------------------------------------------------------ Minecraft
    fun minecraftScene() = listOf(
        GameObject("world", "tilemap").apply { x = 0.0; y = 0.0; cols = 96; rows = 40; tileSize = 24.0; tiles = IntArray(96 * 40) },
        part("player", "box", 1152.0, 100.0, 20.0, 44.0, "#F59E0B").apply { body = "dynamic"; gravity = true },
        hud("Bloco")
    )

    // ============================================================ scripts
    val BLANK_SCRIPT = """
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

    val COINS_SCRIPT = """
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

    val JUMP_SCRIPT = """
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

    val MARIO_SCRIPT = """
# Estilo Mario — setas e botão A na tela
# O mapa é um Tilemap; o jogador é um corpo "dinâmico".

var score = 0
var coyote = 0
var speed = 190
var jump_v = -620
var enemies = [enemy1, enemy2]
var coins = [coin1, coin2, coin3, coin4, coin5]

func start()
    follow(player, 6)
    hud.text = "Moedas: 0"
end

func add_score()
    score += 1
    hud.text = "Moedas: " + score
    tone(988, 70)
end

func update(dt)
    # movimento com aceleração
    var dir = 0
    if input.left
        dir = -1
    end
    if input.right
        dir = 1
    end
    player.vx = approach(player.vx, dir * speed, 1400 * dt)
    if dir != 0
        player.flipx = dir < 0
    end

    # pulo com "coyote time" e altura variável
    if player.grounded
        coyote = 0.1
    else
        coyote -= dt
    end
    if input.a_pressed and coyote > 0
        player.vy = jump_v
        coyote = 0
        tone(520, 60)
    end
    if not input.a and player.vy < -200
        player.vy = -200
    end

    # moedas
    for c in coins
        if c.visible and overlaps(player, c)
            c.visible = false
            add_score()
        end
    end

    # inimigos: viram quando batem na parede; pisar derrota
    for e in enemies
        if e.visible
            if e.wall > 0
                e.vx = -50
            elif e.wall < 0
                e.vx = 50
            end
            if overlaps(player, e)
                if player.vy > 0 and player.y < e.y - 6
                    e.visible = false
                    e.body = "none"
                    e.gravity = false
                    e.vx = 0
                    e.vy = 0
                    player.vy = -380
                    add_score()
                else
                    restart()
                end
            end
        end
    end

    # bater a cabeça num bloco "?"
    if player.ceiling
        var bx = player.x
        var by = player.y - player.h / 2 - 3
        if tile_get(map, bx, by) == 7
            tile_set(map, bx, by, 6)
            add_score()
        end
    end

    if player.y > 560
        restart()
    end
end
""".trimStart()

    val SONIC_SCRIPT = """
# Estilo Sonic — baseado no "Sonic Physics Guide"
# Constantes em px/frame (60 fps). O motor usa px/s, por isso x60.
# O corpo usa stick = true no chão: ele segue rampas sozinho.

var acc = 0.046875
var dec = 0.5
var frc = 0.046875
var top = 6
var slp = 0.125
var jmp = 6.5
var air = 0.09375
var gsp = 0
var score = 0
var rings = []

func start()
    follow(sonic, 7)
    for i in 0..29
        var r = spawn(ring)
        r.x = 300 + i * 80
        r.y = 360 - sin(i * 0.5) * 30
        push(rings, r)
    end
    hud.text = "Anéis: 0"
end

func update(dt)
    var f = dt * 60
    var dir = 0
    if input.left
        dir = -1
    end
    if input.right
        dir = 1
    end

    if sonic.grounded
        sonic.stick = true
        var ang = rad(sonic.ground_angle)
        if sonic.wall != 0
            gsp = 0
        end

        # aceleração, frenagem e atrito
        if dir != 0
            if dir * gsp < 0
                gsp += dir * dec * f
            elif abs(gsp) < top
                gsp += dir * acc * f
                if abs(gsp) > top
                    gsp = dir * top
                end
            end
        else
            gsp -= min(abs(gsp), frc * f) * sign(gsp)
        end

        # fator de inclinação (sobe devagar, desce rápido)
        gsp -= slp * sin(ang) * f
        sonic.vx = gsp * cos(ang) * 60

        # pulo na direção da normal do chão
        if input.a_pressed
            sonic.stick = false
            sonic.vx -= jmp * sin(ang) * 60
            sonic.vy = -jmp * cos(ang) * 60
            tone(660, 80)
        end
        sonic.rotation += gsp * 8 * f
    else
        sonic.stick = false

        # controle no ar
        if dir != 0
            sonic.vx += dir * air * 3600 * dt
            sonic.vx = clamp(sonic.vx, -top * 60, top * 60)
        end

        # altura do pulo variável
        if not input.a and sonic.vy < -240
            sonic.vy = -240
        end

        # resistência do ar
        if sonic.vy < 0 and sonic.vy > -240
            if abs(sonic.vx) >= 7.5
                sonic.vx *= pow(0.96875, f)
            end
        end
        gsp = sonic.vx / 60
        sonic.rotation += 600 * dt
    end

    # anéis
    for r in rings
        if overlaps(sonic, r)
            remove(rings, r)
            destroy(r)
            score += 1
            hud.text = "Anéis: " + score
            tone(1200, 50)
        end
    end

    if sonic.y > 700
        restart()
    end
end
""".trimStart()

    val BLANK_3D = """
# Script 3D (TrixScript)
# camera.x/y/z, camera.yaw, camera.pitch controlam a câmera
# objetos 3D: x y z  w h d  rx ry rotation  vx vy vz

func update(dt)
    cubo.ry += 60 * dt
end
""".trimStart()

    val SCRIPT_3D = """
# 3D simples — setas movem o cubo, A pula
# Setas: ◀ ▶ lado, ▲ ▼ frente/trás.   A: pular

var score = 0
var coins = [moeda1, moeda2, moeda3, moeda4]

func start()
    follow3d(jogador, 9, 5, 4)
    hud.text = "Moedas: 0"
end

func update(dt)
    var mx = 0
    var mz = 0
    if input.left
        mx = -1
    end
    if input.right
        mx = 1
    end
    if input.up
        mz = 1
    end
    if input.down
        mz = -1
    end
    jogador.vx = approach(jogador.vx, mx * 6, 45 * dt)
    jogador.vz = approach(jogador.vz, mz * 6, 45 * dt)
    if mx != 0 or mz != 0
        jogador.ry = deg(atan2(mx, mz))
    end

    if input.a_pressed and jogador.grounded
        jogador.vy = 9
        tone(520, 60)
    end

    piramide.ry += 40 * dt
    for c in coins
        c.ry += 180 * dt
        if c.visible and overlaps3d(jogador, c)
            c.visible = false
            score += 1
            hud.text = "Moedas: " + score
            tone(988, 70)
        end
    end

    if jogador.y < -10
        restart()
    end
end
""".trimStart()

    val MINECRAFT_SCRIPT = """
# Estilo Minecraft (2D) — toque no mundo para quebrar ou colocar blocos
# Setas e A: andar e pular.  B: troca o bloco selecionado.

var blocks = [2, 3, 4, 5, 6, 8]
var inv = [0, 20, 20, 20, 20, 20, 20, 20, 20, 0, 0]
var idx = 0

func ground_row(c)
    return floor(14 + sin(c * 0.25) * 3 + sin(c * 0.09) * 5)
end

func start()
    # gera o terreno
    for c in 0..95
        var h = ground_row(c)
        for r in h..39
            var id = 2
            if r == h
                id = 1
            elif r > h + 6
                id = 3
            end
            tile_set(world, c * 24 + 1, r * 24 + 1, id)
        end
    end

    # árvores
    for t in 0..9
        var c = 6 + t * 9
        var h = ground_row(c)
        for k in 1..4
            tile_set(world, c * 24 + 1, (h - k) * 24 + 1, 4)
        end
        for lc in -1..1
            for lr in 5..6
                tile_set(world, (c + lc) * 24 + 1, (h - lr) * 24 + 1, 5)
            end
        end
    end

    follow(player, 8)
end

func update(dt)
    var dir = 0
    if input.left
        dir = -1
    end
    if input.right
        dir = 1
    end
    player.vx = approach(player.vx, dir * 180, 1500 * dt)

    if input.a_pressed and player.grounded
        player.vy = -520
    end

    if input.b_pressed
        idx += 1
        if idx >= len(blocks)
            idx = 0
        end
    end

    var b = blocks[idx]
    hud.text = "Bloco: " + tile_name(b) + " x" + inv[b]

    # quebrar / colocar blocos (alcance de 140 px)
    if touch.tapped
        var tx = touch.x
        var ty = touch.y
        if dist_xy(player.x, player.y, tx, ty) < 140
            var id = tile_get(world, tx, ty)
            if id > 0
                tile_set(world, tx, ty, 0)
                inv[id] += 1
                tone(300, 40)
            elif inv[b] > 0
                var cx = floor(tx / 24) * 24 + 12
                var cy = floor(ty / 24) * 24 + 12
                var inside = abs(cx - player.x) * 2 < 24 + player.w and abs(cy - player.y) * 2 < 24 + player.h
                if not inside
                    tile_set(world, tx, ty, b)
                    inv[b] -= 1
                    tone(500, 40)
                end
            end
        end
    end

    if player.y > 1200
        player.y = 0
        player.vy = 0
    end
end
""".trimStart()
}
