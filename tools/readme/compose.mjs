// README screenshots image: node tools/readme/compose.mjs
// The five store screenshots in a row, rounded corners and gray captions, on a transparent background (legible in
// light and dark themes), rendered by headless Chrome. Output: .github/assets/screenshots.png.
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
const W = 270, H = Math.round(W * 2412 / 1080), GAP = 28, PAD = 24, LABEL = 44;
const width = PAD * 2 + SCREENS.length * W + (SCREENS.length - 1) * GAP, height = PAD * 2 + H + LABEL;

const page = `<!doctype html><meta charset="utf-8"><style>
html, body { margin: 0; background: transparent; }
.row { display: flex; gap: ${GAP}px; padding: ${PAD}px; }
figure { margin: 0; width: ${W}px; }
img { display: block; width: ${W}px; height: ${H}px; border-radius: 26px; box-shadow: 0 0 0 1px rgba(128,128,128,.35), 0 6px 18px rgba(0,0,0,.18); }
figcaption { margin-top: 16px; text-align: center; font: 500 17px/1.2 -apple-system, "Helvetica Neue", Arial, sans-serif; color: #8a8a8f; }
</style><div class="row">${SCREENS.map(([f, l]) => `<figure><img src="file://${shots}${f}"><figcaption>${l}</figcaption></figure>`).join('')}</div>`;
const dir = mkdtempSync(join(tmpdir(), 'cobble-readme-'));
writeFileSync(join(dir, 'page.html'), page);
execFileSync(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=2',
  '--default-background-color=00000000', `--window-size=${width},${height}`, '--allow-file-access-from-files',
  `--screenshot=${out}`, `file://${join(dir, 'page.html')}`], { stdio: 'ignore' });
console.log('ok', out, `${width * 2}x${height * 2}`);
