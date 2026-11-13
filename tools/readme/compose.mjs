// README screenshots image: node tools/readme/compose.mjs
// The five store screenshots on two rows of equal width (2 larger ones, then 3), rounded corners and gray captions, on a transparent
// background (legible in light and dark themes), rendered by headless Chrome. Two rows keep the screens and captions
// legible once GitHub scales the image down to a phone's width. Output: .github/assets/screenshots.png.
import { writeFileSync, mkdtempSync } from 'fs';
import { execFileSync } from 'child_process';
import { tmpdir } from 'os';
import { join } from 'path';

const root = new URL('../../', import.meta.url).pathname;
const shots = `${root}fastlane/metadata/android/en-US/images/phoneScreenshots/`;
const out = `${root}.github/assets/screenshots.png`;
// Chrome or Chromium binary: set CHROME, otherwise the default macOS location.
const CHROME = process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SCREENS = [
  ['1_home.png', 'Home'],
  ['2_catalog.png', 'All apps'],
  ['3_weather_agenda.png', 'Weather and agenda'],
  ['4_dock_slot.png', 'Dock slot'],
  ['5_settings.png', 'Settings'],
];
const ROWS = [SCREENS.slice(0, 2), SCREENS.slice(2)];
// Captions are sized for a ~370 px wide phone rendering, where the image is scaled to about 40 %.
const W = 270, GAP = 28, PAD = 24, LABEL = 56, ROW_GAP = 28;
// Every row spans the width of the widest one: fewer screens means larger screens.
const inner = Math.max(...ROWS.map(r => r.length)) * (W + GAP) - GAP;
const size = r => { const w = (inner - (r.length - 1) * GAP) / r.length; return [w, Math.round(w * 2412 / 1080)]; };
const width = PAD * 2 + inner;
const height = PAD * 2 + ROWS.reduce((h, r) => h + size(r)[1] + LABEL, 0) + (ROWS.length - 1) * ROW_GAP;

const page = `<!doctype html><meta charset="utf-8"><style>
html, body { margin: 0; background: transparent; }
.grid { display: flex; flex-direction: column; gap: ${ROW_GAP}px; padding: ${PAD}px; }
.row { display: flex; justify-content: center; gap: ${GAP}px; }
figure { margin: 0; }
img { display: block; border-radius: 26px; box-shadow: 0 0 0 1px rgba(128,128,128,.35), 0 6px 18px rgba(0,0,0,.18); }
figcaption { margin-top: 18px; text-align: center; white-space: nowrap; font: 500 24px/1.2 -apple-system, "Helvetica Neue", Arial, sans-serif; color: #8a8a8f; }
</style><div class="grid">${ROWS.map(r => `<div class="row">${r.map(([f, l]) =>
  `<figure style="width: ${size(r)[0]}px"><img style="width: ${size(r)[0]}px; height: ${size(r)[1]}px" src="file://${shots}${f}"><figcaption>${l}</figcaption></figure>`).join('')}</div>`).join('')}</div>`;
const dir = mkdtempSync(join(tmpdir(), 'cobble-readme-'));
writeFileSync(join(dir, 'page.html'), page);
execFileSync(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=2',
  '--default-background-color=00000000', `--window-size=${width},${height}`, '--allow-file-access-from-files',
  `--screenshot=${out}`, `file://${join(dir, 'page.html')}`], { stdio: 'ignore' });
console.log('ok', out, `${width * 2}x${height * 2}`);
