// README diagram of how profiles work: node tools/readme/diagram.mjs
// Three miniature Homes drawn like the onboarding one (`mini*` tokens in `ui/Tokens.kt`) on the screenshots'
// background, with the profile pill: same favorites, only the dock changes, following the Wi-Fi.
// Two SVGs, light and dark (only the annotations change), picked by <picture>.
// Output: .github/assets/switching-{light,dark}.svg.
import { writeFileSync } from 'fs';

const root = new URL('../../', import.meta.url).pathname;
const THEMES = {
  light: { text: '#1E2026', soft: '#6A6E79', mark: '#9094A0', chip: '#E6E9FA', chipText: '#4B55A8' },
  dark: { text: '#E9EAEE', soft: '#9C9FA9', mark: '#6F727C', chip: '#272C48', chipText: '#A9B3F2' },
};
const FONT = `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif`;
const WHITE = a => `rgba(255,255,255,${a})`;
// Favorite dots: the screenshots' colors, identical on the three Homes.
const DOTS = ['#F5A85A', '#8EC9F5', '#9E9E9E', '#B4B8F5', '#7CC8F0'];
const BARS = [56, 44, 36, 64, 48];
// Dock: simple shapes, different from one profile to another.
const PROFILES = [
  { name: 'Work', ssid: 'Office Wi-Fi', dock: ['square', 'triangle', 'ring'] },
  { name: 'Home', ssid: 'Home Wi-Fi', dock: ['heart', 'plus', 'diamond'] },
  { name: 'Out', ssid: 'No Wi-Fi', dock: ['star', 'bars', 'dot'] },
];
// Miniature Home in dp (200 × 360), plus the profile pill; above it, the Wi-Fi network pill.
const PW = 200, PH = 400, LEFT = 190, GAP = 40, TOP = 70, W = LEFT + 3 * PW + 2 * GAP + 16, H = TOP + PH + 16;
const ROW_H = 27, ROW_GAP = 1, INSET = 10, ROWS = 5;
const LIST_Y = 128, DOCK_Y = LIST_Y + (ROWS + 1) * (ROW_H + ROW_GAP) + 16 + 12, PILL_Y = DOCK_Y + 30;

function glyph(kind, cx, cy) {
  const f = WHITE(0.85);
  switch (kind) {
    case 'square': return `<rect x="${cx - 5}" y="${cy - 5}" width="10" height="10" rx="2" fill="${f}"/>`;
    case 'triangle': return `<path d="M${cx} ${cy - 6} L${cx + 6} ${cy + 5} L${cx - 6} ${cy + 5} Z" fill="${f}"/>`;
    case 'ring': return `<circle cx="${cx}" cy="${cy}" r="5" fill="none" stroke="${f}" stroke-width="2.2"/>`;
    case 'heart': return `<path d="M${cx} ${cy + 5} C${cx - 9} ${cy - 1} ${cx - 4} ${cy - 8} ${cx} ${cy - 3} C${cx + 4} ${cy - 8} ${cx + 9} ${cy - 1} ${cx} ${cy + 5} Z" fill="${f}"/>`;
    case 'plus': return `<path d="M${cx - 6} ${cy} H${cx + 6} M${cx} ${cy - 6} V${cy + 6}" stroke="${f}" stroke-width="2.6" stroke-linecap="round"/>`;
    case 'diamond': return `<path d="M${cx} ${cy - 6} L${cx + 6} ${cy} L${cx} ${cy + 6} L${cx - 6} ${cy} Z" fill="${f}"/>`;
    case 'star': return `<path d="M${cx} ${cy - 6.5} L${cx + 1.9} ${cy - 2} L${cx + 6.2} ${cy - 2} L${cx + 2.8} ${cy + 1} L${cx + 4} ${cy + 5.8} L${cx} ${cy + 3} L${cx - 4} ${cy + 5.8} L${cx - 2.8} ${cy + 1} L${cx - 6.2} ${cy - 2} L${cx - 1.9} ${cy - 2} Z" fill="${f}"/>`;
    case 'bars': return `<path d="M${cx - 5} ${cy + 5} V${cy + 1} M${cx} ${cy + 5} V${cy - 2} M${cx + 5} ${cy + 5} V${cy - 5}" stroke="${f}" stroke-width="2.4" stroke-linecap="round"/>`;
    default: return `<circle cx="${cx}" cy="${cy}" r="3.5" fill="${f}"/>`;
  }
}

// 16 px Wi-Fi icon centered on (cx, cy); struck through without a network.
function wifi(cx, cy, c, on) {
  const arcs = `<g fill="none" stroke="${c}" stroke-width="1.8" stroke-linecap="round" opacity="${on ? 1 : 0.6}">`
    + `<path d="M${cx - 7.5} ${cy - 2.5} a10.5 10.5 0 0 1 15 0"/><path d="M${cx - 4.5} ${cy + 0.8} a6.4 6.4 0 0 1 9 0"/></g>`;
  return arcs + (on ? `<circle cx="${cx}" cy="${cy + 4.6}" r="1.6" fill="${c}"/>` : `<path d="M${cx - 7} ${cy - 6} L${cx + 7} ${cy + 6}" stroke="${c}" stroke-width="1.8" stroke-linecap="round"/>`);
}

function phone(p, x, t) {
  const cx = x + PW / 2;
  let s = `<rect x="${x}" y="${TOP}" width="${PW}" height="${PH}" rx="28" fill="url(#wall)" stroke="${WHITE(0.7)}" stroke-width="1.5"/>`;
  s += `<text x="${x + 24}" y="${TOP + 92}" class="clock">16:00</text>`;
  for (let i = 0; i <= ROWS; i++) {
    const y = TOP + LIST_Y + i * (ROW_H + ROW_GAP), w = PW - 2 * INSET;
    const top = i === 0 ? 12 : 3, bottom = i === ROWS ? 12 : 3;
    s += `<path d="M${x + INSET + top} ${y} H${x + INSET + w - top} Q${x + INSET + w} ${y} ${x + INSET + w} ${y + top} V${y + ROW_H - bottom} Q${x + INSET + w} ${y + ROW_H} ${x + INSET + w - bottom} ${y + ROW_H} H${x + INSET + bottom} Q${x + INSET} ${y + ROW_H} ${x + INSET} ${y + ROW_H - bottom} V${y + top} Q${x + INSET} ${y} ${x + INSET + top} ${y} Z" fill="${WHITE(0.07)}"/>`;
    const dy = y + ROW_H / 2;
    if (i < ROWS) s += `<circle cx="${x + INSET + 15}" cy="${dy}" r="3" fill="${DOTS[i]}"/><rect x="${x + INSET + 26}" y="${dy - 2.5}" width="${BARS[i]}" height="5" rx="2.5" fill="${WHITE(0.38)}"/>`;
    else s += `<circle cx="${x + INSET + 15}" cy="${dy}" r="3.5" fill="none" stroke="${WHITE(0.7)}" stroke-width="1"/><rect x="${x + INSET + 26}" y="${dy - 2.5}" width="44" height="5" rx="2.5" fill="${WHITE(0.38)}"/>`;
  }
  p.dock.forEach((g, i) => {
    const dx = cx + (i - 1) * 34;
    s += `<circle cx="${dx}" cy="${TOP + DOCK_Y}" r="12" fill="${WHITE(0.16)}"/>` + glyph(g, dx, TOP + DOCK_Y);
  });
  const pw = 54;
  s += `<rect x="${cx - pw / 2}" y="${TOP + PILL_Y}" width="${pw}" height="22" rx="11" fill="none" stroke="${WHITE(0.45)}" stroke-width="1"/>`
    + `<text x="${cx}" y="${TOP + PILL_Y + 15}" class="pill">${p.name}</text>`;
  const on = p.ssid !== 'No Wi-Fi', chipW = 132;
  s += `<rect x="${cx - chipW / 2}" y="14" width="${chipW}" height="32" rx="16" fill="${t.chip}"/>`
    + wifi(cx - chipW / 2 + 22, 30, t.chipText, on) + `<text x="${cx + 11}" y="35" class="chip">${p.ssid}</text>`;
  return s;
}

function svg(t) {
  const listTop = TOP + LIST_Y, listBottom = listTop + ROWS * (ROW_H + ROW_GAP) - ROW_GAP;
  const dockTop = TOP + DOCK_Y - 14, dockBottom = TOP + PILL_Y + 22;
  const note = (y1, y2, a, b) => {
    const y = (y1 + y2) / 2;
    return `<path d="M${LEFT - 16} ${y1} h-6 V${y2} h6" fill="none" stroke="${t.mark}" stroke-width="1.4"/>`
      + `<text x="${LEFT - 32}" y="${y - 3}" class="t">${a}</text><text x="${LEFT - 32}" y="${y + 15}" class="s">${b}</text>`;
  };
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-label="Same favorites in every profile; only the dock changes with the Wi-Fi network">
<defs><linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#34305A"/><stop offset=".38" stop-color="#4B3529"/><stop offset=".62" stop-color="#262722"/><stop offset="1" stop-color="#0B0B0C"/></linearGradient></defs>
<style>text { font-family: ${FONT}; } .t { font-size: 15px; font-weight: 600; fill: ${t.text}; text-anchor: end; } .s { font-size: 13px; fill: ${t.soft}; text-anchor: end; } .pill { font-size: 11.5px; fill: #fff; text-anchor: middle; } .chip { font-size: 13px; font-weight: 600; fill: ${t.chipText}; text-anchor: middle; } .clock { font-family: Georgia, 'Times New Roman', serif; font-size: 34px; fill: #fff; }</style>
${note(listTop, listBottom, 'Favorites', 'always the same')}
${note(dockTop, dockBottom, 'Dock', 'follows the Wi-Fi SSID')}
${PROFILES.map((p, i) => phone(p, LEFT + i * (PW + GAP), t)).join('\n')}
</svg>
`;
}
for (const [name, t] of Object.entries(THEMES)) writeFileSync(`${root}.github/assets/switching-${name}.svg`, svg(t));
console.log('ok');
