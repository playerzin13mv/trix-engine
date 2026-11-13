#!/data/data/com.termux/files/usr/bin/bash
# Roda dentro da pasta do projeto no Termux
set -e
pkg install -y git gh >/dev/null 2>&1 || true
[ -d .git ] || git init -b main
git add -A
git commit -m "Trix Engine" || true
gh repo create trix-engine --public --source=. --remote=origin --push || git push -u origin main
echo "Pronto! Veja o build em: Actions > Build APK, e o APK em Releases > latest"
