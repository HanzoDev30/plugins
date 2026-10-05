'use strict';
/*
 * Transparent stdio proxy for the Docker language servers.
 *
 * The compose language service identifies Compose documents by languageId "dockercompose", but the
 * editor names a file's language after its extension ("yml"). This proxy forwards every byte untouched
 * and only rewrites textDocument.languageId in textDocument/didOpen.
 *
 *   node docker-shim.js --server <entry> --language-id dockercompose --stdio
 *
 * Everything that is not --server / --language-id is passed on to the server.
 */
const { spawn } = require('child_process');
const path = require('path');

const argv = process.argv.slice(2);
function take(flag) {
  const i = argv.indexOf(flag);
  if (i < 0) return null;
  const v = argv[i + 1];
  argv.splice(i, 2);
  return v;
}
const SERVER = take('--server');
const LANGUAGE_ID = take('--language-id');
const log = (m) => process.stderr.write('[docker-shim] ' + m + '\n');
if (!SERVER) { log('missing --server <path>'); process.exit(1); }

const viaNode = /\.(?:c|m)?js$/i.test(SERVER);
const env = Object.assign({}, process.env, {
  PATH: path.dirname(process.execPath) + path.delimiter + (process.env.PATH || '/usr/local/bin:/usr/bin:/bin'),
});
const stdio = ['pipe', 'pipe', 'inherit'];
const child = viaNode ? spawn(process.execPath, [SERVER, ...argv], { stdio, env }) : spawn(SERVER, argv, { stdio, env });
child.on('error', (e) => { log('cannot start ' + SERVER + ': ' + e.message); process.exit(1); });
child.on('exit', (code) => process.exit(code === null ? 1 : code));
child.stdin.on('error', () => {});
child.stdout.pipe(process.stdout); // server -> client: untouched

let buf = Buffer.alloc(0);
process.stdin.on('data', (chunk) => {
  buf = Buffer.concat([buf, chunk]);
  for (;;) {
    const split = buf.indexOf('\r\n\r\n');
    if (split < 0) return;
    const header = buf.subarray(0, split).toString('ascii');
    const m = /content-length:\s*(\d+)/i.exec(header);
    if (!m) { child.stdin.write(buf.subarray(0, split + 4)); buf = buf.subarray(split + 4); continue; }
    const start = split + 4;
    const len = Number(m[1]);
    if (buf.length < start + len) return;
    const body = buf.subarray(start, start + len);
    buf = buf.subarray(start + len);
    let out = body;
    if (LANGUAGE_ID && body.includes('didOpen')) { // cheap pre-check, then parse
      try {
        const msg = JSON.parse(body.toString('utf8'));
        if (msg.method === 'textDocument/didOpen' && msg.params && msg.params.textDocument) {
          msg.params.textDocument.languageId = LANGUAGE_ID;
          out = Buffer.from(JSON.stringify(msg), 'utf8');
        }
      } catch (e) { /* forward as is */ }
    }
    child.stdin.write(Buffer.concat([Buffer.from('Content-Length: ' + out.length + '\r\n\r\n', 'ascii'), out]));
  }
});
process.stdin.on('end', () => child.stdin.end());
process.on('SIGTERM', () => child.kill());
