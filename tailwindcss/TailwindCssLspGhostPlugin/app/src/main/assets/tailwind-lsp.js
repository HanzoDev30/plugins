'use strict';
/*
 * Tailwind CSS language server proxy (stdio).
 *
 * The stock @tailwindcss/language-server is wrapped by this proxy for two reasons:
 *   1. It asks the client for `workspace/configuration` on startup and for nearly every
 *      request. Editors that do not implement that request make it stall forever, so hover
 *      and completion never show up. This proxy answers those requests itself using the
 *      Tailwind configuration below.
 *   2. It runs next to vscode-html-language-server (and optionally emmet-language-server),
 *      merging completion/hover answers so general HTML support is kept while the Tailwind
 *      server adds the class names.
 *
 * All logging goes to stderr. Unlike the Bootstrap proxy this one does not need its own
 * class index: the Tailwind server already knows every utility and returns the generated
 * CSS on hover.
 */
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const LOG_FILE = process.env.TAILWIND_LSP_LOG || '/tmp/tailwind-lsp.log';
function log(msg) {
  const line = '[tailwind-lsp] ' + new Date().toISOString() + ' ' + msg + '\n';
  try { process.stderr.write(line); } catch (e) { /* ignore */ }
  try { fs.appendFileSync(LOG_FILE, line); } catch (e) { /* ignore */ }
}

const argv = process.argv.slice(2);
const opt = (name) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : null; };
let config = {};
try { config = JSON.parse(fs.readFileSync(opt('--config') || '/opt/tailwindcss-lsp/config.json', 'utf8')); } catch (e) { /* flags only */ }

function systemBin(name) {
  const dirs = (process.env.TAILWIND_LSP_BIN_DIRS || '/usr/local/bin:/usr/bin').split(':');
  for (const d of dirs) {
    const f = path.join(d, name);
    try { if (fs.statSync(f).isFile()) return f; } catch (e) { /* next */ }
  }
  return null;
}

const TAILWIND_SERVER = opt('--tailwind-server') || config.tailwindServer || systemBin('tailwindcss-language-server');
const HTML_SERVER = opt('--html-server') || config.htmlServer || systemBin('vscode-html-language-server');
const EMMET_SERVER = opt('--emmet-server') || config.emmetServer || systemBin('emmet-language-server');

if (!TAILWIND_SERVER) { log('missing tailwindcss-language-server'); process.exit(1); }

const TAILWIND_CONFIG = {
  classAttributes: ['class', 'className', 'ngClass', 'class:list'],
  classFunctions: ['clsx', 'cva', 'cn', 'twMerge', 'tv'],
  includeLanguages: { html: 'html', blade: 'html' },
  validate: true,
  lint: {
    cssConflict: 'warning',
    invalidApply: 'error',
    invalidScreen: 'error',
    invalidVariant: 'error',
    invalidConfigPath: 'error',
    invalidTailwindDirective: 'error',
    recommendedVariantOrder: 'warning',
  },
};

const USER_LANGUAGES = {
  html: 'html', css: 'css', javascript: 'javascript', javascriptreact: 'javascriptreact',
  typescript: 'typescript', typescriptreact: 'typescriptreact', vue: 'vue', svelte: 'svelte',
  astro: 'astro', php: 'html', blade: 'html', erb: 'html',
};

// ---------------------------------------------------------------- framing
function encode(message) {
  const body = Buffer.from(JSON.stringify(message), 'utf8');
  return Buffer.concat([Buffer.from('Content-Length: ' + body.length + '\r\n\r\n', 'ascii'), body]);
}

function makeReader(onMessage) {
  let buf = Buffer.alloc(0);
  return (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    for (;;) {
      const split = buf.indexOf('\r\n\r\n');
      if (split < 0) return;
      const m = /content-length:\s*(\d+)/i.exec(buf.subarray(0, split).toString('ascii'));
      if (!m) { buf = buf.subarray(split + 4); continue; }
      const start = split + 4;
      const len = Number(m[1]);
      if (buf.length < start + len) return;
      let msg = null;
      try { msg = JSON.parse(buf.subarray(start, start + len).toString('utf8')); } catch (e) { log('bad json: ' + e.message); }
      buf = buf.subarray(start + len);
      if (msg) onMessage(msg);
    }
  };
}

// ---------------------------------------------------------------- documents
const docs = new Map();

function lineStarts(text) {
  const starts = [0];
  for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) === 10) starts.push(i + 1);
  return starts;
}
function offsetAt(text, pos) {
  const starts = lineStarts(text);
  if (pos.line >= starts.length) return text.length;
  return Math.min(starts[pos.line] + pos.character, text.length);
}
function applyChanges(text, changes) {
  for (const c of changes || []) {
    if (!c.range) { text = c.text; continue; }
    const s = offsetAt(text, c.range.start);
    const e = offsetAt(text, c.range.end);
    text = text.slice(0, s) + c.text + text.slice(e);
  }
  return text;
}

// ---------------------------------------------------------------- servers
const servers = new Map();

function spawnChild(name, entry, critical) {
  if (!entry) return null;
  const env = Object.assign({}, process.env, {
    PATH: path.dirname(process.execPath) + path.delimiter + (process.env.PATH || '/usr/local/bin:/usr/bin:/bin'),
  });
  log('starting ' + name + ' server: ' + entry);
  const proc = spawn(entry, ['--stdio'], { stdio: ['pipe', 'pipe', 'inherit'], env });
  const s = { name, proc, alive: true, send(m) { if (this.alive) { try { proc.stdin.write(encode(m)); } catch (e) { /* gone */ } } } };
  proc.stdin.on('error', () => {});
  proc.on('error', (e) => { log(name + ' cannot start: ' + e.message); onServerGone(s, critical, 1); });
  proc.on('exit', (code) => { log(name + ' exited with ' + code); onServerGone(s, critical, code); });
  proc.stdout.on('data', makeReader((msg) => onServerMessage(s, msg)));
  servers.set(name, s);
  return s;
}

function onServerGone(s, critical, code) {
  if (!s.alive) return;
  s.alive = false;
  servers.delete(s.name);
  if (critical) { try { process.exit(code === null || code === undefined ? 1 : code); } catch (e) { /* */ } }
}

const tailwind = spawnChild('tailwind', TAILWIND_SERVER, true);
const html = HTML_SERVER ? spawnChild('html', HTML_SERVER, false) : null;
const emmet = EMMET_SERVER ? spawnChild('emmet', EMMET_SERVER, false) : null;
if (!html) log('html server not configured, running tailwind only');
if (!emmet) log('emmet server not configured');
log('proxy started pid=' + process.pid + ' tailwind=' + TAILWIND_SERVER + ' html=' + (HTML_SERVER || '-') + ' emmet=' + (EMMET_SERVER || '-') + ' log=' + LOG_FILE);

function primary() { return tailwind || [...servers.values()][0]; }
function broadcast(msg) { for (const s of servers.values()) s.send(msg); }
const toClient = (m) => process.stdout.write(encode(m));

// ---------------------------------------------------------------- configuration requests
function handleConfiguration(params) {
  const items = (params && params.items) || [];
  return items.map((it) => {
    const section = it && it.section;
    if (section === 'tailwindCSS') return TAILWIND_CONFIG;
    if (section && section.indexOf('tailwindCSS.') === 0) return undefined;
    return {};
  });
}

// ---------------------------------------------------------------- client -> servers
const pendingReqs = new Map();  // client id -> {method, params, results, done, timer}
const clientResponds = new Map(); // proxy request id -> {server, id}
const initResults = new Map();  // server name -> initialize response
let initClientId = null;
let initForwarded = false;

process.stdin.on('data', makeReader((msg) => {
  try { track(msg); } catch (e) { log('client message handling failed: ' + e.stack); }
  routeClient(msg);
}));
process.stdin.on('end', () => { for (const s of servers.values()) s.proc.stdin.end(); });
process.on('SIGTERM', () => { for (const s of servers.values()) s.proc.kill(); });

function track(msg) {
  switch (msg.method) {
    case 'textDocument/didOpen':
      log('client didOpen ' + (msg.params.textDocument && msg.params.textDocument.uri));
      docs.set(msg.params.textDocument.uri, msg.params.textDocument.text);
      break;
    case 'textDocument/didChange': {
      const uri = msg.params.textDocument.uri;
      if (docs.has(uri)) docs.set(uri, applyChanges(docs.get(uri), msg.params.contentChanges));
      break;
    }
    case 'textDocument/didClose':
      docs.delete(msg.params.textDocument.uri);
      break;
    default:
  }
}

function routeClient(msg) {
  if (msg.method === undefined) {
    if (typeof msg.id === 'string' && clientResponds.has(msg.id)) {
      const r = clientResponds.get(msg.id);
      clientResponds.delete(msg.id);
      if (r.server && r.server.alive) r.server.send(Object.assign({}, msg, { id: r.id }));
    }
    return;
  }
  if (msg.id === undefined) { broadcast(msg); return; }

  switch (msg.method) {
    case 'initialize': {
      log('client initialize');
      initClientId = msg.id;
      const caps = (msg.params && msg.params.capabilities) || {};
      const params = Object.assign({}, msg.params, {
        processId: process.pid,
        initializationOptions: Object.assign({}, (msg.params && msg.params.initializationOptions) || {}, {
          userLanguages: USER_LANGUAGES,
          tailwindCSS: TAILWIND_CONFIG,
        }),
        capabilities: Object.assign({}, caps, {
          workspace: Object.assign({ configuration: true }, caps.workspace || {}),
        }),
      });
      broadcast(Object.assign({}, msg, { params }));
      break;
    }
    case 'textDocument/completion':
      log('client completion #' + msg.id);
      pendingReqs.set(msg.id, { method: msg.method, params: msg.params, results: {}, done: new Set(), timer: null });
      broadcast(msg);
      armTimer(msg.id);
      break;
    case 'textDocument/hover':
      log('client hover #' + msg.id);
      pendingReqs.set(msg.id, { method: msg.method, params: msg.params, results: {}, done: new Set(), timer: null });
      broadcast(msg);
      armTimer(msg.id);
      break;
    case 'shutdown':
      broadcast(msg);
      break;
    default:
      pickServer(msg.method).send(msg);
  }
}

// Tailwind owns the class-centric features; everything else (symbols, definition,
// rename, folding, formatting, ...) is HTML territory and must not be sent to a server
// that does not advertise it, otherwise the editor sees only errors.
const TAILWIND_METHODS = new Set([
  'textDocument/diagnostic',
  'workspace/diagnostic',
  'textDocument/codeAction',
  'codeAction/resolve',
  'textDocument/codeLens',
  'codeLens/resolve',
  'textDocument/documentLink',
  'documentLink/resolve',
  'textDocument/documentColor',
  'textDocument/colorPresentation',
]);

function pickServer(method) {
  if (TAILWIND_METHODS.has(method) && tailwind && tailwind.alive) return tailwind;
  if (html && html.alive) return html;
  return primary();
}

function armTimer(id) {
  const p = pendingReqs.get(id);
  if (!p || p.timer) return;
  p.timer = setTimeout(() => finalize(id, p), 8000);
}

// ---------------------------------------------------------------- servers -> client
function onServerMessage(s, msg) {
  if (msg.method !== undefined) {
    if (msg.id === undefined) { toClient(msg); return; }
    if (msg.method === 'workspace/configuration') {
      s.send({ jsonrpc: '2.0', id: msg.id, result: handleConfiguration(msg.params) });
      return;
    }
    if (msg.method === 'client/registerCapability' || msg.method === 'client/unregisterCapability') {
      s.send({ jsonrpc: '2.0', id: msg.id, result: null });
      return;
    }
    const pid = 'srv:' + s.name + ':' + msg.id;
    clientResponds.set(pid, { server: s, id: msg.id });
    toClient(Object.assign({}, msg, { id: pid }));
    return;
  }

  if (initClientId !== null && msg.id === initClientId) {
    initResults.set(s.name, msg);
    if (s.name === 'tailwind') maybeForwardInit();
    return;
  }

  const p = pendingReqs.get(msg.id);
  if (p) { p.results[s.name] = msg; p.done.add(s.name); tryFinish(msg.id, p); return; }
  toClient(msg);
}

function maybeForwardInit() {
  if (initForwarded) return;
  const t = initResults.get('tailwind');
  if (!t) return;
  const finish = () => {
    if (initForwarded) return;
    initForwarded = true;
    const h = initResults.get('html');
    toClient(h ? mergeCapabilities(t, h) : t);
  };
  if (initResults.has('html') || !html) finish();
  else setTimeout(finish, 800);
}

function mergeCapabilities(target, extra) {
  const out = JSON.parse(JSON.stringify(target));
  const tc = (out.result && out.result.capabilities) || (out.result && (out.result.capabilities = {}));
  const ec = (extra.result && extra.result.capabilities) || {};
  for (const key of Object.keys(ec)) if (tc[key] === undefined) tc[key] = ec[key];
  return out;
}

function tryFinish(id, p) {
  if (p.done.has('tailwind')) { finalize(id, p); return; }
  if (!servers.has('tailwind') && p.done.size) finalize(id, p);
}

function finalize(id, p) {
  if (!pendingReqs.delete(id)) return;
  if (p.timer) clearTimeout(p.timer);
  const t = p.results.tailwind || null;
  log('finalize ' + p.method + ' #' + id + ' tailwind=' + (t && !t.error ? 'ok' : 'none') + ' html=' + (p.results.html ? 'ok' : 'none'));

  if (p.method === 'textDocument/completion') {
    const items = [];
    for (const n of ['tailwind', 'html', 'emmet']) {
      const r = p.results[n];
      if (r && !r.error) items.push(...itemsOf(r).items);
    }
    if (!items.length) { toClient(t || { jsonrpc: '2.0', id, result: null }); return; }
    toClient({ jsonrpc: '2.0', id, result: { isIncomplete: false, items } });
    return;
  }

  const parts = [];
  let range = null;
  for (const n of ['tailwind', 'html']) {
    const r = p.results[n];
    if (!r || r.error || !r.result) continue;
    const text = toMarkdown(r.result.contents);
    if (text) parts.push(text);
    if (!range && r.result.range) range = r.result.range;
  }
  if (!parts.length) { toClient(t || { jsonrpc: '2.0', id, result: null }); return; }
  toClient({ jsonrpc: '2.0', id, result: { contents: { kind: 'markdown', value: parts.join('\n\n---\n\n') }, range } });
}

function itemsOf(msg) {
  const r = msg && !msg.error ? msg.result : null;
  if (Array.isArray(r)) return { items: r };
  if (r && typeof r === 'object') return { items: r.items || [] };
  return { items: [] };
}

function toMarkdown(contents) {
  if (!contents) return '';
  if (Array.isArray(contents)) return contents.map(toMarkdown).filter(Boolean).join('\n\n');
  if (typeof contents === 'string') return contents;
  if (contents.language) return '```' + contents.language + '\n' + contents.value + '\n```';
  return contents.value || '';
}
