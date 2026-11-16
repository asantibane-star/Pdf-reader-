#!/usr/bin/env node
/*
 * Prueba el visor web (app/src/main/assets/viewer) en Chromium real con el mismo régimen que el WebView:
 * CSP como cabecera, `/doc/<nonce>` sirve el archivo y todo lo demás responde 403.
 *
 * Uso: node scripts/check-viewer.js [--shots <carpeta>]     (requiere `playwright` con Chromium)
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const fixtures = path.join(root, 'app/src/test/resources/fixtures');
const shots = process.argv.includes('--shots') ? process.argv[process.argv.indexOf('--shots') + 1] : null;
const CSP = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src blob: data:; font-src blob: data:; connect-src 'self'; base-uri 'none'; form-action 'none'";
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
const NONCE = 'a1b2c3d4e5f60718293a4b5c6d7e8f90';
let current = null;

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const deny = () => { res.writeHead(403, { 'content-security-policy': CSP }); res.end(); };
  if (url.pathname === `/doc/${NONCE}` && current) {
    res.writeHead(200, { 'content-type': 'application/octet-stream', 'content-security-policy': CSP });
    return res.end(fs.readFileSync(current));
  }
  if (url.pathname.startsWith('/viewer/')) {
    const file = path.join(assets, url.pathname);
    if (file.startsWith(path.join(assets, 'viewer')) && fs.existsSync(file) && fs.statSync(file).isFile()) {
      res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream', 'content-security-policy': CSP });
      return res.end(fs.readFileSync(file));
    }
  }
  deny();
});

const cases = [
  { file: 'simple.xlsx', kind: 'xlsx', expectText: 'Ingresos', expectSheets: 3 },
  { file: 'legacy.xls', kind: 'xls', expectText: 'Ingresos', expectSheets: 3 },
  { file: 'sample.csv', kind: 'csv', expectText: 'Valparaíso', expectSheets: 1 },
  { file: 'big.xlsx', kind: 'xlsx', expectText: 'Fila 1999', expectSheets: 1, truncated: true },
  { file: 'simple.docx', kind: 'docx', expectText: 'Informe de prueba' },
  { file: 'images.docx', kind: 'docx', expectText: 'Texto bajo la imagen', expectImage: true },
  { file: 'simple.pptx', kind: 'pptx', expectText: 'Presentación simple' },
  { file: 'theme-gradients.pptx', kind: 'pptx', expectText: 'Degradado' },
  { file: 'charts-tables.pptx', kind: 'pptx', expectText: 'Gráfico y tabla' },
  { file: 'fonts-calibri.pptx', kind: 'pptx', expectText: 'Calibri' },
  { file: 'external-link.pptx', kind: 'pptx', expectText: 'Enlace externo' },
  { file: 'macro.pptm', kind: 'pptx', expectText: 'Presentación simple' },
  { file: 'truncated.pdf', kind: 'docx', expectError: true },
];

async function run(browser, port, c) {
  current = path.join(fixtures, c.file);
  const ctx = await browser.newContext({ viewport: { width: 412, height: 800 }, serviceWorkers: 'block' });
  const page = await ctx.newPage();
  const external = [];
  const dialogs = [];
  page.on('request', (r) => { if (!r.url().startsWith(`http://127.0.0.1:${port}/`) && !/^(blob|data):/.test(r.url())) external.push(r.url()); });
  page.on('dialog', (d) => { dialogs.push(d.message()); d.dismiss(); });
  await page.addInitScript(() => { window.__msgs = []; window.lectorBridge = { postMessage: (m) => window.__msgs.push(m) }; });
  const q = new URLSearchParams({ kind: c.kind, nonce: NONCE, ti: '72', bi: '96', notice: 'Se muestran solo las primeras 2000 filas y 100 columnas.' });
  const t0 = Date.now();
  await page.goto(`http://127.0.0.1:${port}/viewer/index.html?${q}`);
  await page.waitForFunction(() => window.__msgs.some((m) => m === 'rendered' || m.startsWith('error:')), null, { timeout: 40000 }).catch(() => {});
  const msgs = await page.evaluate(() => window.__msgs);
  const ms = Date.now() - t0;
  await page.waitForTimeout(400);
  const body = await page.evaluate(() => document.body.innerText);
  const problems = [];
  const rendered = msgs.includes('rendered');
  const errorMsg = msgs.find((m) => m.startsWith('error:'));
  if (c.expectError) { if (!errorMsg) problems.push('debía fallar con error:*'); }
  else {
    if (!rendered) problems.push(`no emitió rendered (${msgs.join(',')})`);
    if (c.expectText && !body.includes(c.expectText)) problems.push(`falta el texto "${c.expectText}"`);
    if (c.expectSheets && !msgs.includes(`sheets:${c.expectSheets}`)) problems.push(`debía emitir sheets:${c.expectSheets}`);
    if (c.truncated && !body.includes('primeras 2000 filas')) problems.push('falta el aviso de recorte');
    if (c.expectImage && (await page.locator('img').count()) === 0) problems.push('falta la imagen');
  }
  if (external.length) problems.push('peticiones externas: ' + external.join(', '));
  if (dialogs.length) problems.push('se ejecutó script del documento: ' + dialogs.join(','));
  if (msgs.some((m) => m.length > 64)) problems.push('mensaje demasiado largo');
  if (shots) { fs.mkdirSync(shots, { recursive: true }); await page.screenshot({ path: path.join(shots, `${c.file}.png`) }); }
  await ctx.close();
  console.log(`${problems.length ? 'FALLA' : 'ok   '} ${c.file.padEnd(22)} ${String(ms).padStart(5)} ms  [${msgs.join(', ')}]${problems.length ? '\n       ' + problems.join('\n       ') : ''}`);
  return problems.length === 0;
}

async function networkChecks(browser, port) {
  current = path.join(fixtures, 'simple.xlsx');
  const ctx = await browser.newContext({ serviceWorkers: 'block' });
  const page = await ctx.newPage();
  await page.goto(`http://127.0.0.1:${port}/viewer/index.html?kind=xlsx&nonce=${NONCE}`);
  const results = await page.evaluate(async (nonce) => {
    const out = {};
    for (const [name, url] of [['example.com', 'https://example.com/'], ['nonce incorrecto', '/doc/deadbeefdeadbeefdeadbeefdeadbeef'], ['fuera de /viewer', '/etc/passwd'], ['traversal', '/viewer/../viewer.js']]) {
      try { const r = await fetch(url, { cache: 'no-store' }); out[name] = r.status; } catch (e) { out[name] = 'bloqueada'; }
    }
    return out;
  }, NONCE);
  await ctx.close();
  let ok = true;
  for (const [name, r] of Object.entries(results)) {
    const blocked = r === 'bloqueada' || r === 403 || r === 404;
    ok = ok && blocked;
    console.log(`${blocked ? 'ok   ' : 'FALLA'} petición a ${name}: ${r}`);
  }
  return ok;
}

(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const browser = await chromium.launch();
  let ok = true;
  for (const c of cases) ok = (await run(browser, port, c)) && ok;
  ok = (await networkChecks(browser, port)) && ok;
  await browser.close();
  server.close();
  console.log(ok ? '\nVisor web: todo correcto' : '\nVisor web: HAY FALLOS');
  process.exit(ok ? 0 : 1);
})();
