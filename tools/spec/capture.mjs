// Captures every public source listed in docs/spec-index.json into
// docs/sources/<source-id>.md (gitignored: spec content is not committed).
// Tables become tab-separated rows, headings become markdown headings, hidden
// (collapsed) content is included. Prints a sha256 per capture.
//
// Usage: npm ci && npx playwright install chromium && npm run capture [-- <source-id>...]
import { chromium } from 'playwright';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const index = JSON.parse(readFileSync(`${repo}/docs/spec-index.json`, 'utf8'));
const outDir = `${repo}/docs/sources`;
const only = process.argv.slice(2);

const known = new Set(index.sources.map(s => s.id));
const unknown = only.filter(id => !known.has(id));
if (unknown.length) throw new Error(`unknown source id(s): ${unknown.join(', ')}`);

const targets = index.sources.filter(s => s.access === 'public' && (only.length === 0 || only.includes(s.id)));
if (targets.length === 0) throw new Error('no public sources selected');

// Runs in the page. gen_index.py hashes the output (everything after the header line).
function dump() {
  const lines = [];
  const clean = t => t.replace(/\s+/g, ' ').trim();
  const walk = n => {
    if (n.nodeType === 3) { const t = clean(n.textContent); if (t) lines.push(t); return; }
    // FORM is skipped: contact forms carry a per-load captcha that would change the hash.
    if (n.nodeType !== 1 || ['SCRIPT', 'STYLE', 'NOSCRIPT', 'FORM'].includes(n.tagName)) return;
    if (n.tagName === 'TABLE') {
      for (const tr of n.querySelectorAll('tr')) lines.push([...tr.children].map(c => clean(c.textContent)).join('\t'));
      lines.push('');
      return;
    }
    if (/^H[1-6]$/.test(n.tagName)) { lines.push('', '#'.repeat(+n.tagName[1]) + ' ' + clean(n.textContent)); return; }
    if (n.tagName === 'PRE') { lines.push('```', n.textContent, '```'); return; }
    for (const c of n.childNodes) walk(c);
  };
  walk(document.body);
  return lines.join('\n');
}

mkdirSync(outDir, { recursive: true });
// Full Chromium, not the headless shell: the BRI portal's bot protection rejects the shell.
const browser = await chromium.launch({ channel: 'chromium' });
const failures = [];
try {
  const context = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
  const page = await context.newPage();
  // The first request to a portal gets a bot-protection challenge; visiting the
  // origin first obtains the session cookie that later page loads need.
  for (const origin of new Set(targets.map(s => new URL(s.url).origin))) {
    await page.goto(origin, { waitUntil: 'networkidle', timeout: 90_000 });
    await page.waitForTimeout(3000);
  }
  for (const s of targets) {
    const res = await page.goto(s.url, { waitUntil: 'networkidle', timeout: 90_000 });
    await page.waitForTimeout(3000);
    const body = await page.evaluate(dump);
    const status = res ? res.status() : 'no response';
    if (status !== 200 || /do not have permission/i.test(body)) {
      failures.push(`${s.id}: HTTP ${status} ${s.url}`);
      continue;
    }
    const header = `<!-- source: ${s.id} ${s.url} captured ${new Date().toISOString()} -->\n`;
    writeFileSync(`${outDir}/${s.id}.md`, header + body);
    console.log(`${s.id}\t${createHash('sha256').update(body).digest('hex')}\t${body.length} chars`);
    await page.waitForTimeout(3000); // be gentle with the portals' bot protection
  }
} finally {
  await browser.close();
}
if (failures.length) {
  console.error('capture failed (existing files left untouched):\n' + failures.join('\n'));
  process.exit(1);
}
