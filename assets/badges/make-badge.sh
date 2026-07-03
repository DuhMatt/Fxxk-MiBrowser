#!/bin/bash
# Usage: make-badge.sh <label> <value> <output.svg> [left_width] [right_width]
# Generates an SVG badge: dark left panel, light right panel, sharp corners, 1px border

set -euo pipefail

if [ $# -lt 3 ]; then
  echo "Usage: $0 <label> <value> <output.svg> [left_width] [right_width]"
  exit 1
fi

LABEL="$1"
VALUE="$2"
OUTPUT="$3"

# Font config
FONT_FAMILY="Arial, 'Noto Sans CJK SC', 'PingFang SC', 'Microsoft YaHei', sans-serif"
FONT_SIZE_LABEL=12
FONT_SIZE_VALUE=13

# Colors
BORDER="#555555"
BG_LEFT="#1c1c1c"
TEXT_LEFT="#cccccc"
BG_RIGHT="#f0f0f0"
TEXT_RIGHT="#333333"

# Badge height
H=28

# Widths: use provided or auto-estimate
if [ -n "${4:-}" ]; then
  LEFT_W=$4
else
  # Estimate: CJK chars ~14px, latin ~7px, plus 24px padding
  cjk=$(echo -n "$LABEL" | sed 's/[a-zA-Z0-9\/\.\ \-]//g' | wc -c | tr -d ' ')
  latin=$(echo -n "$LABEL" | sed 's/[^a-zA-Z0-9\/\.\ \-]//g' | wc -c | tr -d ' ')
  [ -z "$cjk" ] && cjk=0
  [ -z "$latin" ] && latin=0
  label_px=$(( cjk * 14 + latin * 7 + 24 ))
  LEFT_W=$label_px
fi

if [ -n "${5:-}" ]; then
  RIGHT_W=$5
else
  vchars=$(echo -n "$VALUE" | wc -c | tr -d ' ')
  RIGHT_W=$(( vchars * 8 + 24 ))
fi

TOTAL_W=$(( LEFT_W + 2 + RIGHT_W ))

# Center positions
left_center=$(( LEFT_W / 2 ))
right_center=$(( LEFT_W + 2 + RIGHT_W / 2 ))
y_text=$(( H / 2 + 1 ))

cat > "$OUTPUT" << SVGEOF
<svg xmlns="http://www.w3.org/2000/svg" width="$TOTAL_W" height="$H">
  <rect width="$TOTAL_W" height="$H" fill="$BORDER" rx="0"/>
  <rect x="1" y="1" width="$LEFT_W" height="$((H-2))" fill="$BG_LEFT" rx="0"/>
  <line x1="$((LEFT_W+1))" y1="1" x2="$((LEFT_W+1))" y2="$((H-1))" stroke="$BORDER" stroke-width="1"/>
  <rect x="$((LEFT_W+2))" y="1" width="$RIGHT_W" height="$((H-2))" fill="$BG_RIGHT" rx="0"/>
  <text x="$left_center" y="$y_text" fill="$TEXT_LEFT" font-family="$FONT_FAMILY" font-size="${FONT_SIZE_LABEL}px" text-anchor="middle" dominant-baseline="central">$LABEL</text>
  <text x="$right_center" y="$y_text" fill="$TEXT_RIGHT" font-family="$FONT_FAMILY" font-size="${FONT_SIZE_VALUE}px" font-weight="700" text-anchor="middle" dominant-baseline="central">$VALUE</text>
</svg>
SVGEOF

echo "Generated: $OUTPUT (${TOTAL_W}x${H})"
