// stands in for vscode-html-language-server: answers a few requests, logs what it saw to stderr
const frame = (m) => { const b = Buffer.from(JSON.stringify(m)); return Buffer.concat([Buffer.from('Content-Length: ' + b.length + '\r\n\r\n'), b]); };
let buf = Buffer.alloc(0);
process.stdin.on('data', (c) => {
  buf = Buffer.concat([buf, c]);
  for (;;) {
    const s = buf.indexOf('\r\n\r\n'); if (s < 0) return;
    const len = Number(/content-length:\s*(\d+)/i.exec(buf.subarray(0, s).toString())[1]);
    if (buf.length < s + 4 + len) return;
    const m = JSON.parse(buf.subarray(s + 4, s + 4 + len).toString()); buf = buf.subarray(s + 4 + len);
    process.stderr.write('[mock] got ' + (m.method || 'response') + '\n');
    if (m.method === 'initialize') process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: { capabilities: { completionProvider: {}, hoverProvider: true } } }));
    else if (m.method === 'textDocument/completion') process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: { isIncomplete: false, items: [{ label: 'html-own-item' }] } }));
    else if (m.method === 'textDocument/hover') process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: m.params.position.line === 1 ? { contents: { kind: 'markdown', value: 'html hover' } } : null }));
    else if (m.method === 'custom/echo') process.stdout.write(frame({ jsonrpc: '2.0', id: m.id, result: { echoed: m.params } }));
    else if (m.method === 'exit') process.exit(0);
  }
});
