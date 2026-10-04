// emmet missing / crashing / hanging must never take html + bootstrap down
const { spawn } = require('child_process'); const path = require('path'); const assert = require('assert');
const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };
function run(name, emmetArg, env) {
  return new Promise((resolve, reject) => {
    const args = [path.join(__dirname, '../app/src/main/assets/bootstrap-lsp.js'), '--html-server', path.join(__dirname, 'mock-html-server.js')];
    if (emmetArg) args.push('--emmet-server', emmetArg);
    args.push('--stdio');
    const srv = spawn(process.execPath, args, { stdio: ['pipe', 'pipe', 'ignore'], env: Object.assign({}, process.env, env) });
    let buf = Buffer.alloc(0); const w = new Map(); let id = 0;
    srv.stdout.on('data', (c) => { buf = Buffer.concat([buf, c]); for (;;) { const s = buf.indexOf('\r\n\r\n'); if (s < 0) return; const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]); if (buf.length < s + 4 + len) return; const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString()); buf = buf.subarray(s + 4 + len); if (m.method) continue; if (w.has(m.id)) { w.get(m.id)(m); w.delete(m.id); } } });
    const req = (method, params) => new Promise((res) => { const i = ++id; w.set(i, res); srv.stdin.write(frame({ jsonrpc: '2.0', id: i, method, params })); });
    const uri = 'file:///x/a.html'; const t0 = Date.now();
    (async () => {
      await req('initialize', { rootUri: 'file://' + path.join(__dirname, 'proj'), capabilities: {} });
      srv.stdin.write(frame({ jsonrpc: '2.0', method: 'textDocument/didOpen', params: { textDocument: { uri, languageId: 'html', version: 1, text: '<div class="btn-p">' } } }));
      let r = await req('textDocument/completion', { textDocument: { uri }, position: { line: 0, character: 17 } });
      const labels = r.result.items.map((i) => i.label);
      assert(labels.includes('html-own-item') && labels.includes('btn-primary'), name + ': ' + labels);
      // still alive and answering after the first completion
      const uri2 = 'file:///x/b.html';
      srv.stdin.write(frame({ jsonrpc: '2.0', method: 'textDocument/didOpen', params: { textDocument: { uri: uri2, languageId: 'html', version: 1, text: '<div class="btn">' } } }));
      r = await req('textDocument/hover', { textDocument: { uri: uri2 }, position: { line: 0, character: 14 } });
      assert(r.result && r.result.contents.value.includes('.btn {'), name + ': hover ' + JSON.stringify(r));
      console.log('ok  ' + name + ' (' + (Date.now() - t0) + ' ms)');
      srv.kill(); resolve();
    })().catch(reject);
    setTimeout(() => reject(new Error(name + ': timeout')), 8000);
  });
}
(async () => {
  await run('emmet not configured', null, {});
  await run('emmet path does not exist', '/nonexistent/emmet.js', {});
  await run('emmet never answers (waits ~2.5s, then moves on)', path.join(__dirname, 'mock-emmet-server.js'), { EMMET_SLOW: '1' });
  console.log('ALL RESILIENCE TESTS PASSED'); process.exit(0);
})().catch((e) => { console.error('FAIL', e.message); process.exit(1); });
