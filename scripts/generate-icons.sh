#!/usr/bin/env bash
# Regenerates the packaging icons from branding/orca-eye-icon.svg.
#
#   composeApp/icon.icns  macOS app bundle / DMG
#   composeApp/icon.ico   Windows MSI + exe
#   composeApp/src/commonMain/composeResources/drawable/icon.png
#                         Linux packages + the runtime window icon
#
# Requires: rsvg-convert (brew install librsvg), magick (brew install imagemagick),
# and iconutil (macOS only, for the .icns).
set -euo pipefail

cd "$(dirname "$0")/.."

SVG="branding/orca-eye-icon.svg"
OUT="composeApp"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for tool in rsvg-convert magick iconutil; do
  command -v "$tool" >/dev/null || { echo "error: $tool not found" >&2; exit 1; }
done

rsvg-convert -w 1024 -h 1024 "$SVG" -o "$WORK/master.png"

# macOS .icns
ICONSET="$WORK/icon.iconset"
mkdir -p "$ICONSET"
for size in 16 32 128 256 512; do
  magick "$WORK/master.png" -resize "${size}x${size}" "$ICONSET/icon_${size}x${size}.png"
  magick "$WORK/master.png" -resize "$((size * 2))x$((size * 2))" "$ICONSET/icon_${size}x${size}@2x.png"
done
iconutil -c icns "$ICONSET" -o "$OUT/icon.icns"

# Windows .ico
magick "$WORK/master.png" -define icon:auto-resize=256,128,64,48,32,16 "$OUT/icon.ico"

# Linux packages + runtime window icon
PNG="$OUT/src/commonMain/composeResources/drawable/icon.png"
mkdir -p "$(dirname "$PNG")"
magick "$WORK/master.png" -resize 512x512 "$PNG"

echo "Wrote $OUT/icon.icns, $OUT/icon.ico, $PNG"
