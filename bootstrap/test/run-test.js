const { spawn } = require('child_process');
const path = require('path');
const assert = require('assert');
const srv = spawn(process.execPath, [path.join(__dirname, '../app/src/main/assets/bootstrap-lsp.js'), '--html-server', path.join(__dirname, 'mock-html-server.js'), '--stdio'], { stdio: ['pipe', 'pipe', 'inherit'] });
const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };
const waiters = new Map(); let buf = Buffer.alloc(0);
srv.stdout.on('data', (c) => { buf = Buffer.concat([buf, c]); for (;;) { const s = buf.indexOf('\r\n\r\n'); if (s < 0) return; const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]); if (buf.length < s + 4 + len) return; const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString()); buf = buf.subarray(s + 4 + len); if (waiters.has(m.id)) { waiters.get(m.id)(m); waiters.delete(m.id); } } });
let id = 0;
const req = (method, params) => new Promise((res) => { const i = ++id; waiters.set(i, res); srv.stdin.write(frame({ jsonrpc: '2.0', id: i, method, params })); });
const note = (method, params) => srv.stdin.write(frame({ jsonrpc: '2.0', method, params }));
const uri = 'file:///x/index.html';
const comp = (line, character) => req('textDocument/completion', { textDocument: { uri }, position: { line, character } });
(async () => {
  await req('initialize', { rootUri: 'file://' + path.join(__dirname, 'proj'), capabilities: {} });
  note('textDocument/didOpen', { textDocument: { uri, languageId: 'html', version: 1, text: '<div class="btn-p">\n  <p class="d-sm-none btn" id="a">x</p>\n</div>\n' } });

  let r = await comp(0, 17); // right after "btn-p"
  let labels = r.result.items.map((i) => i.label);
  console.log('completion btn-p ->', labels);
  assert.deepStrictEqual(labels, ['html-own-item', 'btn-primary']);
  const edit = r.result.items[1].textEdit;
  assert.deepStrictEqual(edit.range, { start: { line: 0, character: 12 }, end: { line: 0, character: 17 } });
  assert(r.result.items[1].documentation.value.includes('--bs-btn-bg'));

  r = await comp(0, 4); // inside tag name, not a class value
  assert.deepStrictEqual(r.result.items.map((i) => i.label), ['html-own-item']);
  r = await comp(1, 22); // start of 2nd word, prefix "" -> all classes
  labels = r.result.items.map((i) => i.label);
  console.log('completion empty prefix ->', labels.length, 'items');
  assert(labels.includes('col-sm-1') && labels.includes('d-sm-none') && labels.includes('btn-group-lg') && labels.includes('text-bg-danger'));
  assert(!labels.some((l) => l.includes('\\')), 'escaped selectors must not leak');
  r = await comp(1, 15); // prefix "d-s"
  labels = r.result.items.map((i) => i.label);
  assert.deepStrictEqual(labels, ['html-own-item', 'd-sm-none']);

  // incremental edit: change "btn-p" -> "col-s", then ask again
  note('textDocument/didChange', { textDocument: { uri, version: 2 }, contentChanges: [{ range: { start: { line: 0, character: 12 }, end: { line: 0, character: 17 } }, text: 'col-s' }] });
  r = await comp(0, 17);
  labels = r.result.items.map((i) => i.label);
  console.log('after edit ->', labels);
  assert.deepStrictEqual(labels, ['html-own-item', 'col-sm-1']);

  // hover on class word inside attribute (line 1) merges with the html server's hover
  let h = await req('textDocument/hover', { textDocument: { uri }, position: { line: 1, character: 15 } });
  console.log('hover d-sm-none ->', JSON.stringify(h.result.contents.value));
  assert(h.result.contents.value.startsWith('html hover') && h.result.contents.value.includes('.d-sm-none'));
  h = await req('textDocument/hover', { textDocument: { uri }, position: { line: 0, character: 1 } });
  assert.strictEqual(h.result, null); // not in class attr, html server said null
  // unknown request passes through untouched
  const e = await req('custom/echo', { a: 1 });
  assert.deepStrictEqual(e.result, { echoed: { a: 1 } });
  note('exit'); console.log('ALL PROXY TESTS PASSED'); setTimeout(() => process.exit(0), 100);
})().catch((e) => { console.error('FAIL', e); process.exit(1); });
