// README diagram of how profiles work: node tools/readme/diagram.mjs
// Three miniature Homes drawn like the onboarding one (`mini*` tokens in `ui/Tokens.kt`) on the screenshots'
// background, with the profile pill: same favorites, only the dock changes, following the Wi-Fi.
// No side annotation column and large text: GitHub scales the SVG down to the phone's width (~370 px, about 55 %),
// so every label stays at 12 px or more there. The legend sits under the Homes.
// Two SVGs, light and dark (only the annotations change), picked by <picture>.
// Output: .github/assets/switching-{light,dark}.svg.
import { writeFileSync } from 'fs';

const root = new URL('../../', import.meta.url).pathname;
const THEMES = {
  light: { text: '#1E2026', soft: '#6A6E79', mark: '#9094A0', chip: '#E6E9FA', chipText: '#4B55A8' },
  dark: { text: '#E9EAEE', soft: '#9C9FA9', mark: '#6F727C', chip: '#272C48', chipText: '#A9B3F2' },
};
const FONT = `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif`;
// Dock frame drawn on the dark miniature, so the same color in both themes; it matches the dark theme's chip text.
const FRAME = '#A9B3F2';
const STILL = 0.6;
const WHITE = a => `rgba(255,255,255,${a})`;
// Favorite dots: the screenshots' colors, identical on the three Homes.
const DOTS = ['#F5A85A', '#8EC9F5', '#9E9E9E', '#B4B8F5', '#7CC8F0'];
const BARS = [56, 44, 36, 64, 48];
// Dock: pictograms suggesting each profile's apps, different from one profile to another.
const PROFILES = [
  { name: 'Work', ssid: 'Office Wi-Fi', dock: ['mail', 'calendar', 'chat'] },
  { name: 'Home', ssid: 'Home Wi-Fi', dock: ['music', 'play', 'book'] },
  { name: 'Out', ssid: 'No Wi-Fi', dock: ['pin', 'camera', 'card'] },
];
// Miniature Home, plus the profile pill; above it, the Wi-Fi network pill; below, the legend.
const PW = 200, PH = 346, LEFT = 8, GAP = 24, TOP = 64, W = 2 * LEFT + 3 * PW + 2 * GAP;
const ROW_H = 27, ROW_GAP = 1, INSET = 10, ROWS = 4;
const LIST_Y = 70, DOCK_Y = LIST_Y + (ROWS + 1) * (ROW_H + ROW_GAP) + 16 + 20, PILL_Y = DOCK_Y + 34;
const LEGEND_Y = TOP + PH + 44, H = LEGEND_Y + 40;

// Generic pictograms, no app brand, centered on (cx, cy) in a ~16 px box: what each profile's dock would hold.
function glyph(kind, cx, cy) {
  const c = WHITE(0.9), line = `fill="none" stroke="${c}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"`;
  const g = body => `<g transform="translate(${cx} ${cy})">${body}</g>`;
  switch (kind) {
    case 'mail': return g(`<rect x="-7" y="-5" width="14" height="10" rx="1.5" ${line}/><path d="M-6.5 -4 L0 1 L6.5 -4" ${line}/>`);
    case 'calendar': return g(`<rect x="-6.5" y="-5" width="13" height="11.5" rx="1.5" ${line}/><path d="M-6.5 -1.5 H6.5 M-3 -7.5 V-4 M3 -7.5 V-4" ${line}/>`);
    case 'chat': return g(`<path d="M-7 -5.5 H7 V3.5 H-1 L-5 7 V3.5 H-7 Z" ${line}/>`);
    case 'music': return g(`<circle cx="-2.5" cy="4.5" r="2.6" fill="${c}"/><path d="M0 4.5 V-6.5 L5.5 -4.5" ${line}/>`);
    case 'play': return g(`<path d="M-3.5 -6 L6 0 L-3.5 6 Z" fill="${c}"/>`);
    case 'book': return g(`<path d="M0 -4 C-3 -6.5 -6 -6 -7.5 -5 V5.5 C-6 4.5 -3 4 0 6 C3 4 6 4.5 7.5 5.5 V-5 C6 -6 3 -6.5 0 -4 Z M0 -4 V6" ${line}/>`);
    case 'pin': return g(`<path d="M0 7.5 C-3.5 3.5 -5.5 0.5 -5.5 -1.5 A5.5 5.5 0 0 1 5.5 -1.5 C5.5 0.5 3.5 3.5 0 7.5 Z" ${line}/><circle cx="0" cy="-1.5" r="1.8" fill="${c}"/>`);
    case 'camera': return g(`<rect x="-7" y="-3.5" width="14" height="10" rx="2" ${line}/><path d="M-3 -3.5 L-1.5 -6 H1.5 L3 -3.5" ${line}/><circle cx="0" cy="1.5" r="2.6" ${line}/>`);
    default: return g(`<rect x="-7.5" y="-5" width="15" height="10" rx="1.5" ${line}/><path d="M-7.5 -1.5 H7.5" fill="none" stroke="${c}" stroke-width="2.4"/>`);
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
  // Clock and favorites slightly faded: they stay the same, so the eye goes to the framed dock.
  s += `<g opacity="${STILL}"><text x="${x + 22}" y="${TOP + 50}" class="clock">16:00</text>`;
  for (let i = 0; i <= ROWS; i++) {
    const y = TOP + LIST_Y + i * (ROW_H + ROW_GAP), w = PW - 2 * INSET;
    const top = i === 0 ? 12 : 3, bottom = i === ROWS ? 12 : 3;
    s += `<path d="M${x + INSET + top} ${y} H${x + INSET + w - top} Q${x + INSET + w} ${y} ${x + INSET + w} ${y + top} V${y + ROW_H - bottom} Q${x + INSET + w} ${y + ROW_H} ${x + INSET + w - bottom} ${y + ROW_H} H${x + INSET + bottom} Q${x + INSET} ${y + ROW_H} ${x + INSET} ${y + ROW_H - bottom} V${y + top} Q${x + INSET} ${y} ${x + INSET + top} ${y} Z" fill="${WHITE(0.07)}"/>`;
    const dy = y + ROW_H / 2;
    if (i < ROWS) s += `<circle cx="${x + INSET + 15}" cy="${dy}" r="3" fill="${DOTS[i]}"/><rect x="${x + INSET + 26}" y="${dy - 2.5}" width="${BARS[i]}" height="5" rx="2.5" fill="${WHITE(0.38)}"/>`;
    else s += `<circle cx="${x + INSET + 15}" cy="${dy}" r="3.5" fill="none" stroke="${WHITE(0.7)}" stroke-width="1"/><rect x="${x + INSET + 26}" y="${dy - 2.5}" width="44" height="5" rx="2.5" fill="${WHITE(0.38)}"/>`;
  }
  s += '</g>';
  p.dock.forEach((g, i) => {
    const dx = cx + (i - 1) * 40;
    s += `<circle cx="${dx}" cy="${TOP + DOCK_Y}" r="15" fill="${WHITE(0.16)}"/>` + glyph(g, dx, TOP + DOCK_Y);
  });
  // Dashed frame around the dock and its pill: the only part that changes from one profile to another.
  const fy = TOP + DOCK_Y - 25, fh = PILL_Y + 30 + 10 - (DOCK_Y - 25);
  s += `<rect x="${x + INSET}" y="${fy}" width="${PW - 2 * INSET}" height="${fh}" rx="14" fill="none" stroke="${FRAME}" stroke-width="1.6" stroke-dasharray="5 4"/>`;
  const pw = 76;
  s += `<rect x="${cx - pw / 2}" y="${TOP + PILL_Y}" width="${pw}" height="30" rx="15" fill="none" stroke="${WHITE(0.45)}" stroke-width="1"/>`
    + `<text x="${cx}" y="${TOP + PILL_Y + 21}" class="pill">${p.name}</text>`;
  const on = p.ssid !== 'No Wi-Fi', chipW = 184;
  s += `<rect x="${cx - chipW / 2}" y="6" width="${chipW}" height="40" rx="20" fill="${t.chip}"/>`
    + wifi(cx - chipW / 2 + 24, 26, t.chipText, on) + `<text x="${cx + 12}" y="33" class="chip">${p.ssid}</text>`;
  return s;
}

function svg(t) {
  const legend = `<text x="${W / 2}" y="${LEGEND_Y}" class="t"><tspan font-weight="600">Favorites</tspan> always the same · <tspan font-weight="600" fill="${t.chipText}">Dock</tspan> follows the Wi-Fi</text>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-label="Same favorites in every profile; only the dock changes with the Wi-Fi network">
<defs><linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#34305A"/><stop offset=".38" stop-color="#4B3529"/><stop offset=".62" stop-color="#262722"/><stop offset="1" stop-color="#0B0B0C"/></linearGradient></defs>
<style>text { font-family: ${FONT}; } .t { font-size: 22px; fill: ${t.text}; text-anchor: middle; } .pill { font-size: 17px; fill: #fff; text-anchor: middle; } .chip { font-size: 20px; font-weight: 600; fill: ${t.chipText}; text-anchor: middle; } .clock { font-family: Georgia, 'Times New Roman', serif; font-size: 30px; fill: #fff; }</style>
${PROFILES.map((p, i) => phone(p, LEFT + i * (PW + GAP), t)).join('\n')}
${legend}
</svg>
`;
}
for (const [name, t] of Object.entries(THEMES)) writeFileSync(`${root}.github/assets/switching-${name}.svg`, svg(t));
console.log('ok');
