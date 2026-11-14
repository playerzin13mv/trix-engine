#!/data/data/com.termux/files/usr/bin/bash
# Uso: bash export.sh "Nome do Projeto"
# Compila o jogo como APK independente usando o GitHub Actions.
set -e
NAME="$1"
if [ -z "$NAME" ]; then echo 'Uso: bash export.sh "Nome do Projeto"'; exit 1; fi
SRC="$HOME/storage/shared/TrixEngine/Projects/$NAME"
[ -d "$SRC" ] || { echo "Projeto não encontrado: $SRC"; exit 1; }
SLUG=$(printf '%s' "$NAME" | tr 'A-Z' 'a-z' | tr -cd 'a-z0-9')
[ -n "$SLUG" ] || SLUG="game"
cd "$(dirname "$0")"
rm -rf "games/$SLUG"
mkdir -p "games/$SLUG"
cp -r "$SRC"/. "games/$SLUG"/
rm -f "games/$SLUG/save.json"
printf '%s' "$NAME" > "games/$SLUG/.name"
git add -A
git commit -m "Exportar $NAME" || true
git push
echo "Iniciando build no GitHub..."
gh workflow run export-game.yml -f game="$SLUG"
sleep 10
RUN=$(gh run list --workflow=export-game.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN" --exit-status
gh release download "game-$SLUG" -p "$SLUG.apk" -D "$HOME/storage/downloads" --clobber
echo "Pronto! APK salvo em Downloads: $SLUG.apk"
