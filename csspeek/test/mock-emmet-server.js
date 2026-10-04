const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };
let buf = Buffer.alloc(0);
process.stdin.on('data', (c) => {
  buf = Buffer.concat([buf, c]);
  for (;;) {
    const s = buf.indexOf('\r\n\r\n'); if (s < 0) return;
    const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]);
    if (buf.length < s + 4 + len) return;
    const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString()); buf = buf.subarray(s + 4 + len);
    if (m.method === 'initialize') process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: { capabilities: { completionProvider: {} } } }));
    else if (m.method === 'initialized') process.stdout.write(frame({ jsonrpc: '2.0', id: 1, method: 'workspace/configuration', params: { items: [{ section: 'emmet' }] } }));
    else if (m.method === undefined && m.id === 1) process.stdout.write(frame({ jsonrpc: '2.0', method: 'custom/ack', params: { from: 'emmet', id: m.id, result: m.result } }));
    else if (m.method === 'textDocument/completion') {
      if (process.env.EMMET_SLOW) return; // never answers: the proxy must not hang
      process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: { isIncomplete: false, items: [{ label: 'ul>li*3', kind: 15, insertTextFormat: 2, textEdit: { range: { start: m.params.position, end: m.params.position }, newText: '<ul>\n\t<li>${1}</li>\n</ul>' } }] } }));
    }
  }
});
