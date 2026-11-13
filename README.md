# Trix Engine 🎮

Engine de jogos 2D **nativa para Android** (Kotlin + Jetpack Compose, sem navegador/WebView),
com linguagem própria: **TrixScript** (`.trix`).

- Lista de projetos + criação/exclusão
- Pede permissão de armazenamento e cria `TrixEngine/Projects/`
- Editor visual: arrastar objetos, grade, hierarquia, inspetor (posição, tamanho, velocidade, cor, texto)
- Editor de código com destaque de sintaxe, barra de atalhos e verificação de erros em tempo real
- Botão **Testar** roda o jogo dentro do app (60 fps), com toque, som (beep) e vibração
- Autosave

## Compilar (pelo Termux + GitHub)
1. `bash push.sh`  (cria o repositório e envia)
2. No GitHub: aba **Actions** → "Build APK" (~4 min)
3. Baixe `TrixEngine.apk` em **Releases → latest** e instale.

## TrixScript
Veja a aba **Ajuda** dentro do app. Exemplo:

```
var score = 0
func update(dt)
    if touch.down
        player.x = clamp(touch.x, 45, 355)
    end
    if overlaps(player, coin)
        score += 1
        hud.text = "Pontos: " + score
    end
end
```
