const { spawn } = require('child_process');
const fs = require('fs'), os = require('os'), path = require('path'), assert = require('assert');
const SERVER = path.join(__dirname, '../app/src/main/assets/css-peek-lsp.js');
const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };

// ------------------------------------------------------------ fixture project (space + unicode in the path on purpose)
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'peek-'));
const root = path.join(tmp, 'my project');
const w = (rel, text) => { const f = path.join(root, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, text); return f; };
const BS_CSS = '.btn{display:inline-block}\n.btn-primary{color:#fff}\n.d-flex{display:flex}\n';
w('node_modules/bootstrap/dist/css/bootstrap.css', BS_CSS);
w('css/main.css', '@import "reset.css";\n.hero {\n  color: red;\n}\n#main { margin: 0 }\n.shared { a: 1 }\n');
w('css/reset.css', '/* reset */\n.reset-me { margin: 0 }\n');
w('css/abs.css', '.from-abs { x: y }\n');
w('sass/components/card.scss', '.card {\n  &-title { font-weight: bold }\n  &__body { padding: 1px }\n}\n');
w('plain/style.css', '.p { a: b }\n');
const INDEX_LF = [
  '<!DOCTYPE html><html><head>',
  '<link rel="stylesheet" href="css/main.css">',
  '<link rel="stylesheet" href="/css/abs.css">',
  '<link rel="stylesheet" href="https://cdn.example.com/x.css">',
  '<link rel="stylesheet" href="missing.css">',
  '<style>.inline { a: b }\n.shared { a: 2 }</style>',
  '</head><body>',
  '<p>متن فارسی سلام دنیا 😀 <b class="hero reset-me from-abs">x</b></p>',
  '<h1 id="main" class="shared inline btn btn-primary">t</h1>',
  '<div class="card-title card__body nope">y</div>',
  '</body></html>',
].join('\n');
const indexHtml = w('index.html', INDEX_LF);
const plainHtml = w('plain/page.html', '<link rel="stylesheet" href="style.css"><div class="p">x</div>');

// ------------------------------------------------------------ tiny lsp client
function start({ rootUri, cwd, extraArgs = [], env = {} }) {
  const srv = spawn(process.execPath, [SERVER, '--html-server', path.join(__dirname, 'mock-html-server.js'), '--emmet-server', path.join(__dirname, 'mock-emmet-server.js'), '--config', '/nonexistent.json', ...extraArgs, '--stdio'],
    { cwd: cwd || root, stdio: ['pipe', 'pipe', 'pipe'], env: Object.assign({}, process.env, env) });
  let err = ''; srv.stderr.on('data', (d) => (err += d));
  let buf = Buffer.alloc(0); const waiters = new Map(); let id = 0;
  srv.stdout.on('data', (c) => { buf = Buffer.concat([buf, c]); for (;;) { const s = buf.indexOf('\r\n\r\n'); if (s < 0) return; const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]); if (buf.length < s + 4 + len) return; const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString('utf8')); buf = buf.subarray(s + 4 + len); if (m.method) { if (m.id !== undefined) srv.stdin.write(frame({ jsonrpc: '2.0', id: m.id, result: [null] })); continue; } if (waiters.has(m.id)) { waiters.get(m.id)(m); waiters.delete(m.id); } } });
  const c = {
    err: () => err,
    req: (method, params) => new Promise((res, rej) => { const i = ++id; const t = setTimeout(() => rej(new Error('timeout ' + method)), 6000); waiters.set(i, (m) => { clearTimeout(t); res(m); }); srv.stdin.write(frame({ jsonrpc: '2.0', id: i, method, params })); }),
    note: (method, params) => srv.stdin.write(frame({ jsonrpc: '2.0', method, params })),
    open(uri, text) { c.note('textDocument/didOpen', { textDocument: { uri, languageId: 'html', version: 1, text } }); },
    def: (uri, line, character) => c.req('textDocument/definition', { textDocument: { uri }, position: { line, character } }),
    hover: (uri, line, character) => c.req('textDocument/hover', { textDocument: { uri }, position: { line, character } }),
    comp: (uri, line, character) => c.req('textDocument/completion', { textDocument: { uri }, position: { line, character } }),
    stop() { srv.kill(); },
    async init() { const r = await c.req('initialize', { rootUri, capabilities: {} }); c.note('initialized', {}); return r; },
  };
  return c;
}
const U = (p) => 'file://' + p.split('/').map(encodeURIComponent).join('/');
const posOf = (text, needle, nth = 0, delta = 1) => { let i = -1; for (let k = 0; k <= nth; k++) i = text.indexOf(needle, i + 1); assert(i >= 0, 'needle ' + needle); const before = text.slice(0, i + delta).split('\n'); return { line: before.length - 1, character: before[before.length - 1].length }; };
const loc = (l) => ({ file: decodeURIComponent(l.uri.slice(7)).replace(root, '<root>'), line: l.range.start.line, ch: l.range.start.character, endCh: l.range.end.character });

let passed = 0, failed = 0;
async function t(name, fn) { try { await fn(); passed++; console.log('ok   ' + name); } catch (e) { failed++; console.log('FAIL ' + name + '\n     ' + String(e.stack || e).split('\n').slice(0, 6).join('\n     ')); } }

(async () => {
  const uri = U(indexHtml);
  const c = start({ rootUri: U(root) });
  const init = await c.init();
  c.open(uri, INDEX_LF);

  await t('initialize advertises definitionProvider (the stock html server does not)', () => {
    assert.strictEqual(init.result.capabilities.definitionProvider, true);
    assert.deepStrictEqual(init.result.capabilities.completionProvider.triggerCharacters.slice(-1), ['-']);
  });
  await t('class in a linked stylesheet -> exact selector range', async () => {
    const r = await c.def(uri, ...Object.values(posOf(INDEX_LF, 'hero', 0)).map((v, i) => v));
    assert(Array.isArray(r.result) && r.result.length === 1, JSON.stringify(r));
    assert.deepStrictEqual(loc(r.result[0]), { file: '<root>/css/main.css', line: 1, ch: 0, endCh: 5 });
    assert(r.result[0].uri.includes('my%20project'), 'uri must be percent-encoded: ' + r.result[0].uri);
  });
  await t('class reached through @import (reset.css)', async () => {
    const p = posOf(INDEX_LF, 'reset-me');
    const r = await c.def(uri, p.line, p.character);
    assert.deepStrictEqual(r.result.map(loc), [{ file: '<root>/css/reset.css', line: 1, ch: 0, endCh: 9 }]);
  });
  await t('root-relative href (/css/abs.css) resolves against the project root', async () => {
    const p = posOf(INDEX_LF, 'from-abs');
    const r = await c.def(uri, p.line, p.character);
    assert.deepStrictEqual(r.result.map(loc), [{ file: '<root>/css/abs.css', line: 0, ch: 0, endCh: 9 }]);
  });
  await t('cursor after persian text + emoji on the same line: UTF-16 columns still right', async () => {
    const p = posOf(INDEX_LF, 'hero');
    const line = INDEX_LF.split('\n')[p.line];
    assert(line.indexOf('hero') > 20 && /[\u0600-\u06ff]/.test(line) && line.includes('😀'));
    const r = await c.def(uri, p.line, p.character + 2); // middle of the word
    assert.strictEqual(r.result[0].range.start.line, 1);
  });
  await t('id', async () => {
    const p = posOf(INDEX_LF, 'id="main"', 0, 5);
    const r = await c.def(uri, p.line, p.character);
    assert.deepStrictEqual(r.result.map(loc), [{ file: '<root>/css/main.css', line: 4, ch: 0, endCh: 5 }]);
  });
  await t('same class in a stylesheet AND an inline <style>: both, cascade order', async () => {
    const p = posOf(INDEX_LF, 'shared', 0);
    const r = await c.def(uri, p.line, p.character);
    // the first "shared" in the file is inside <style>, make the cursor sit on the class attribute instead
    const q = posOf(INDEX_LF, 'class="shared', 0, 7);
    const r2 = await c.def(uri, q.line, q.character);
    assert.strictEqual(r2.result.length, 2, JSON.stringify(r2.result));
    assert.strictEqual(loc(r2.result[0]).file, '<root>/css/main.css');
    assert.strictEqual(r2.result[1].uri, uri, 'inline style lives in the html document');
    assert.deepStrictEqual(r2.result[1].range, { start: { line: 6, character: 0 }, end: { line: 6, character: 7 } });
    assert.strictEqual(INDEX_LF.split('\n')[6].slice(0, 7), '.shared');
    void r;
  });
  await t('inline <style> class', async () => {
    const q = posOf(INDEX_LF, 'inline btn', 0, 2);
    const r = await c.def(uri, q.line, q.character);
    assert.deepStrictEqual(r.result.map((l) => [l.uri === uri, l.range.start.line, l.range.start.character]), [[true, 5, 7]]);
  });
  await t('only found in the workspace (scss, not linked): nested &-title and &__body', async () => {
    const a = posOf(INDEX_LF, 'card-title'), b = posOf(INDEX_LF, 'card__body');
    const ra = await c.def(uri, a.line, a.character), rb = await c.def(uri, b.line, b.character);
    assert.deepStrictEqual(ra.result.map(loc), [{ file: '<root>/sass/components/card.scss', line: 1, ch: 2, endCh: 9 }]);
    assert.deepStrictEqual(rb.result.map(loc), [{ file: '<root>/sass/components/card.scss', line: 2, ch: 2, endCh: 9 }]);
  });
  await t('unknown class -> result null (never an error)', async () => {
    const p = posOf(INDEX_LF, 'nope');
    const r = await c.def(uri, p.line, p.character);
    assert(!r.error && r.result === null, JSON.stringify(r));
  });
  await t('cursor on tag name / text -> null', async () => {
    const r1 = await c.def(uri, 7, 2), r2 = await c.def(uri, 7, 12);
    assert(r1.result === null && r2.result === null);
  });
  await t('declaration request behaves like definition', async () => {
    const p = posOf(INDEX_LF, 'hero');
    const r = await c.req('textDocument/declaration', { textDocument: { uri }, position: p });
    assert.strictEqual(r.result.length, 1);
  });
  await t('missing stylesheet + remote cdn link do not break anything', () => {
    assert(c.err().includes('cannot resolve stylesheet missing.css'), c.err());
    assert(!c.err().includes('cdn.example.com'), 'remote href must be ignored silently');
  });

  await t('edits are tracked: add a class via didChange, then peek it', async () => {
    c.note('textDocument/didChange', { textDocument: { uri, version: 2 }, contentChanges: [{ range: { start: { line: 9, character: 0 }, end: { line: 9, character: 0 } }, text: '<i class="reset-me"></i>\n' }] });
    const r = await c.def(uri, 9, 12);
    assert.deepStrictEqual(r.result.map(loc), [{ file: '<root>/css/reset.css', line: 1, ch: 0, endCh: 9 }]);
    c.note('textDocument/didChange', { textDocument: { uri, version: 3 }, contentChanges: [{ range: { start: { line: 9, character: 0 }, end: { line: 10, character: 0 } }, text: '' }] });
  });
  await t('stylesheet edited on disk -> new position without restarting', async () => {
    const f = path.join(root, 'css/reset.css');
    fs.writeFileSync(f, '/* reset */\n/* more */\n\n.reset-me { margin: 0; padding: 0 }\n');
    const p = posOf(INDEX_LF, 'reset-me');
    const r = await c.def(uri, p.line, p.character);
    assert.deepStrictEqual(r.result.map(loc), [{ file: '<root>/css/reset.css', line: 3, ch: 0, endCh: 9 }]);
  });

  await t('hover: html server answer, then project rule(s), then bootstrap docs', async () => {
    // mock html server answers a hover only on line 1; use a doc where the class sits on that line
    const hu = U(path.join(root, 'hover.html'));
    const txt = '<link rel="stylesheet" href="css/main.css">\n<p class="hero btn">x</p>';
    c.open(hu, txt);
    const p = posOf(txt, 'hero');
    const r = await c.hover(hu, p.line, p.character + 1);
    const v = r.result.contents.value;
    assert(v.startsWith('html hover'), v);
    assert(v.indexOf('main.css:2') > v.indexOf('html hover'), v);
    assert(v.includes('.hero {\n  color: red;\n}'), v);
    const q = posOf(txt, 'btn');
    const r2 = await c.hover(hu, q.line, q.character + 1);
    assert(r2.result.contents.value.includes('.btn {'), r2.result.contents.value); // bootstrap docs (project uses it)
    assert(!r2.result.contents.value.includes('main.css'), 'btn is not defined in main.css');
  });
  await t('hover on something unrelated passes the html answer through untouched', async () => {
    const hu = U(path.join(root, 'hover.html'));
    const r = await c.hover(hu, 1, 1);
    assert.strictEqual(r.result.contents.value, 'html hover');
  });
  await t('completion still merges html + emmet + bootstrap in a bootstrap project', async () => {
    const p = posOf(INDEX_LF, 'btn-primary', 0, 6);
    const r = await c.comp(uri, p.line, p.character);
    const labels = r.result.items.map((i) => i.label);
    assert(labels.includes('html-own-item') && labels.includes('ul>li*3') && labels.includes('btn-primary'), labels.join());
  });
  await t('CRLF document: inline <style> and class positions are still right', async () => {
    const crlf = INDEX_LF.split('\n').join('\r\n');
    const cu = U(path.join(root, 'crlf.html'));
    c.open(cu, crlf);
    const q = posOf(crlf.split('\r\n').join('\n'), 'inline btn', 0, 2); // positions are line/char, \r never counts
    const r = await c.def(cu, q.line, q.character);
    assert.deepStrictEqual(r.result.map((l) => [l.uri === cu, l.range.start.line, l.range.start.character, l.range.end.character]), [[true, 5, 7, 14]]);
    const h = posOf(crlf.split('\r\n').join('\n'), 'hero');
    const r2 = await c.def(cu, h.line, h.character);
    assert.strictEqual(loc(r2.result[0]).file, '<root>/css/main.css');
  });
  c.stop();

  // ---- bootstrap gating: a bootstrap.css exists system-wide (--css), but only bootstrap projects may see it
  const bsFile = path.join(tmp, 'sys-bootstrap.css'); fs.writeFileSync(bsFile, BS_CSS);
  const g = start({ rootUri: U(path.join(root, 'plain')), cwd: path.join(root, 'plain'), extraArgs: ['--css', bsFile] });
  await g.init();
  const pu = U(plainHtml);
  const ptxt = fs.readFileSync(plainHtml, 'utf8');
  g.open(pu, ptxt);
  await t('plain (non-bootstrap) project: no bootstrap completions, but css peek works', async () => {
    const p = posOf(ptxt, 'class="p', 0, 8);
    const r = await g.comp(pu, p.line, p.character);
    assert(!r.result.items.some((i) => i.label === 'btn-primary'), 'bootstrap leaked into a non-bootstrap project');
    const d = await g.def(pu, p.line, p.character - 1);
    assert.deepStrictEqual(d.result.map(loc), [{ file: '<root>/plain/style.css', line: 0, ch: 0, endCh: 2 }]);
  });
  await t('a document mentioning bootstrap (CDN link) turns bootstrap completions on', async () => {
    const bu = U(path.join(root, 'plain/cdn.html'));
    const btxt = '<link href="https://cdn/bootstrap.min.css" rel="stylesheet"><div class="btn-pr">';
    g.open(bu, btxt);
    const o = btxt.indexOf('btn-pr') + 6;
    const r = await g.comp(bu, 0, o);
    assert(r.result.items.some((i) => i.label === 'btn-primary'), r.result.items.map((i) => i.label).join());
  });
  g.stop();

  // ---- the client names the project differently from what this process can read (proot): map both ways
  const FAKE = '/storage/emulated/0/Projects/my project';
  const m = start({ rootUri: U(FAKE), cwd: root });
  await m.init();
  const mu = U(FAKE + '/index.html');
  m.open(mu, INDEX_LF);
  await t('client path != local path (proot): reads via cwd, answers in the client\'s namespace', async () => {
    const p = posOf(INDEX_LF, 'hero');
    const r = await m.def(mu, p.line, p.character);
    assert.strictEqual(r.result.length, 1, JSON.stringify(r) + m.err());
    assert.strictEqual(r.result[0].uri, U(FAKE + '/css/main.css'));
    const s = posOf(INDEX_LF, 'card-title');
    const r2 = await m.def(mu, s.line, s.character);
    assert.strictEqual(r2.result[0].uri, U(FAKE + '/sass/components/card.scss'));
  });
  m.stop();

  console.log(`\n${passed} passed, ${failed} failed`);
  fs.rmSync(tmp, { recursive: true, force: true });
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
