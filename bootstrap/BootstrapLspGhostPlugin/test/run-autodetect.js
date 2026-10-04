const { spawn } = require('child_process'); const path = require('path'); const assert = require('assert');
const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };
const srv = spawn(process.execPath, [path.join(__dirname, '../app/src/main/assets/bootstrap-lsp.js'), '--html-server', path.join(__dirname, 'mock-html-server.js'), '--config', '/nonexistent.json', '--stdio'],
  { stdio: ['pipe', 'pipe', 'pipe'], env: Object.assign({}, process.env, { BOOTSTRAP_LSP_BIN_DIRS: path.join(__dirname, 'fakebin'), PATH: '/usr/bin:/bin' }) });
let err = ''; srv.stderr.on('data', (d) => err += d);
let buf = Buffer.alloc(0); const w = new Map(); let id = 0;
srv.stdout.on('data', (c) => { buf = Buffer.concat([buf, c]); for (;;) { const s = buf.indexOf('\r\n\r\n'); if (s < 0) return; const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]); if (buf.length < s + 4 + len) return; const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString()); buf = buf.subarray(s + 4 + len); if (m.method) continue; if (w.has(m.id)) { w.get(m.id)(m); w.delete(m.id); } } });
const req = (method, params) => new Promise((res) => { const i = ++id; w.set(i, res); srv.stdin.write(frame({ jsonrpc: '2.0', id: i, method, params })); });
(async () => {
  await req('initialize', { rootUri: 'file://' + path.join(__dirname, 'proj'), capabilities: {} });
  const uri = 'file:///x/a.html';
  srv.stdin.write(frame({ jsonrpc: '2.0', method: 'textDocument/didOpen', params: { textDocument: { uri, languageId: 'html', version: 1, text: 'ul>li*3' } } }));
  const r = await req('textDocument/completion', { textDocument: { uri }, position: { line: 0, character: 7 } });
  const labels = r.result.items.map((i) => i.label);
  assert.deepStrictEqual(labels, ['html-own-item', 'ul>li*3'], err);
  assert(/starting emmet server: .*fakebin\/emmet-language-server/.test(err), 'emmet must be auto-detected: ' + err);
  console.log('ok  emmet auto-detected from system bin dir and run directly via its shebang');
  srv.kill(); process.exit(0);
})().catch((e) => { console.error('FAIL', e.message); process.exit(1); });
setTimeout(() => { console.error('FAIL timeout\n' + err); process.exit(1); }, 8000);
