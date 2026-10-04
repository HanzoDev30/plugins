'use strict';
/*
 * CSS Peek + Bootstrap + Emmet + HTML language server (stdio).
 *
 * A file gets exactly one language server, so everything for .html lives behind this proxy:
 *   - textDocument/definition (+ declaration): answered here. Class / id under the cursor
 *     -> where the project's stylesheets define it (css-peek-core.js).
 *   - hover: the rules that style the class / id, then the Bootstrap docs, then the html server's.
 *   - completion: html + emmet + Bootstrap class names (only in Bootstrap projects).
 * It is a proxy in front of two stock servers:
 *   - vscode-html-language-server  (primary: everything goes to it)
 *   - @olrtg/emmet-language-server (optional: gets the same documents and is asked for
 *                                   completions too; its items are merged into the answer)
 * Completion/hover answers are additionally enriched with Bootstrap classes taken from the
 * project's own node_modules/bootstrap/dist/css/bootstrap.css (or the private copy installed
 * under /opt/bootstrap-lsp). All logging goes to stderr.
 */
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const argv = process.argv.slice(2);
const opt = (name) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : null; };
let config = {};
try { config = JSON.parse(fs.readFileSync(opt('--config') || '/opt/csspeek-lsp/config.json', 'utf8')); } catch (e) { /* flags only */ }
// The IDE already installs these binaries system-wide; reuse them instead of a second copy.
const BIN_DIRS = (process.env.BOOTSTRAP_LSP_BIN_DIRS || '/usr/local/bin:/usr/bin').split(':');
function systemBin(name) {
  for (const d of BIN_DIRS) {
    const f = path.join(d, name);
    try { if (fs.statSync(f).isFile()) return f; } catch (e) { /* next */ }
  }
  return null;
}
const HTML_SERVER = opt('--html-server') || config.htmlServer || systemBin('vscode-html-language-server');
const EMMET_SERVER = opt('--emmet-server') || config.emmetServer || systemBin('emmet-language-server');
const EMMET_WAIT_MS = 2500;
const FALLBACK_CSS = [opt('--css'), '/opt/csspeek-lsp/node_modules/bootstrap/dist/css/bootstrap.css', '/opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.css'].filter(Boolean);
const MAX_EMPTY_PREFIX_ITEMS = 500;
const DOC_LIMIT = 400;

function log(msg) { process.stderr.write('[css-peek] ' + msg + '\n'); }

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
function positionAt(text, offset) {
  const starts = lineStarts(text);
  let lo = 0, hi = starts.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (starts[mid] <= offset) lo = mid; else hi = mid - 1;
  }
  return { line: lo, character: offset - starts[lo] };
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

// ---------------------------------------------------------------- bootstrap css index
let roots = [process.cwd()];
let index = null;

function uriToPath(uri) {
  if (!uri || !uri.startsWith('file://')) return null;
  try { return decodeURIComponent(uri.slice('file://'.length)); } catch (e) { return null; }
}

function findCss() {
  const candidates = roots.map((r) => path.join(r, 'node_modules/bootstrap/dist/css/bootstrap.css'));
  candidates.push(...FALLBACK_CSS);
  return candidates.find((c) => { try { return fs.statSync(c).isFile(); } catch (e) { return false; } }) || null;
}

function buildIndex() {
  const file = findCss();
  const map = new Map();
  if (!file) { log('bootstrap.css not found in ' + roots.join(', ') + ' or ' + FALLBACK_CSS.join(', ')); return map; }
  let css = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const rule = /([^{}]+)\{([^{}]*)\}/g;
  let m;
  while ((m = rule.exec(css))) {
    const selector = m[1].trim();
    if (!selector || selector[0] === '@') continue;
    const decl = m[2].trim().replace(/\s*\n\s*/g, '\n  ');
    for (const part of selector.split(',')) {
      const sel = part.trim();
      const classRe = /\.(-?[_a-zA-Z][\w-]*)/g;
      let c;
      while ((c = classRe.exec(sel))) {
        const name = c[1];
        const text = sel + ' {\n  ' + decl + '\n}';
        const entry = map.get(name) || { rules: [], simple: false };
        if (entry.rules.length < 2 && !entry.rules.includes(text)) entry.rules.push(text);
        if (sel === '.' + name) entry.simple = true;
        map.set(name, entry);
      }
    }
  }
  log('indexed ' + map.size + ' classes from ' + file);
  return map;
}
function getIndex() { if (!index) index = buildIndex(); return index; }

function docFor(name) {
  const entry = getIndex().get(name);
  if (!entry) return null;
  return '```css\n' + entry.rules.join('\n\n') + '\n```';
}

// ---------------------------------------------------------------- css peek wiring
const core = require(path.join(__dirname, 'css-peek-core.js'));

/** URIs from the client may live at another path than this process can read (proot): map both ways. */
let uriRoot = null;   // root as the client names it
let localRoot = null; // same directory as this process sees it
function toLocal(p) { return uriRoot && localRoot && uriRoot !== localRoot && (p === uriRoot || p.startsWith(uriRoot + '/')) ? localRoot + p.slice(uriRoot.length) : p; }
function toUri(p) { return core.pathToUri(uriRoot && localRoot && uriRoot !== localRoot && (p === localRoot || p.startsWith(localRoot + '/')) ? uriRoot + p.slice(localRoot.length) : p); }

let peek = null;
function setupPeek(rootPaths) {
  const list = rootPaths.filter(Boolean);
  uriRoot = list[0] || null;
  const isDir = (d) => { try { return fs.statSync(d).isDirectory(); } catch (e) { return false; } };
  localRoot = uriRoot && isDir(uriRoot) ? uriRoot : process.cwd();
  if (uriRoot && localRoot !== uriRoot) log('project root ' + uriRoot + ' is not readable here, using ' + localRoot);
  const localRoots = Array.from(new Set((list.length ? list : [process.cwd()]).map(toLocal).filter(isDir)));
  if (!localRoots.length) localRoots.push(process.cwd());
  peek = new core.CssPeek({ roots: localRoots, toLocal, toUri, log });
}
const getPeek = () => peek || (setupPeek([]), peek);

/** Bootstrap completion / hover only where the project (or the document) uses Bootstrap. */
const bootstrapDirCache = new Map();
function dirUsesBootstrap(dir) {
  for (let i = 0; dir && i < 8; i++) {
    if (bootstrapDirCache.has(dir)) return bootstrapDirCache.get(dir);
    let hit = false;
    try { hit = fs.statSync(path.join(dir, 'node_modules/bootstrap')).isDirectory(); } catch (e) { /* no */ }
    if (!hit) { try { hit = fs.readFileSync(path.join(dir, 'package.json'), 'utf8').includes('"bootstrap"'); } catch (e) { /* no */ } }
    if (hit) { bootstrapDirCache.set(dir, true); return true; }
    const up = path.dirname(dir);
    if (up === dir) break;
    dir = up;
  }
  return false;
}
function bootstrapEnabled(uri, text) {
  if (/bootstrap/i.test(text)) return true;
  const p = core.uriToPath(uri);
  return !!p && dirUsesBootstrap(path.dirname(toLocal(p)));
}

// ---------------------------------------------------------------- class attribute context
const CLASS_ATTRS = /^(class|classname)$/i;

/** If offset is inside a class="..." value returns {valueStart, prefixStart, wordEnd}. */
function classContext(text, offset) {
  let i = offset - 1;
  while (i >= 0) {
    const ch = text[i];
    if (ch === '"' || ch === "'") break;
    if (ch === '<' || ch === '>') return null;
    i--;
  }
  if (i < 0) return null;
  const quote = i;
  let j = quote - 1;
  while (j >= 0 && /\s/.test(text[j])) j--;
  if (text[j] !== '=') return null;
  j--;
  while (j >= 0 && /\s/.test(text[j])) j--;
  let end = j + 1;
  while (j >= 0 && /[\w:\-.@\[\]()]/.test(text[j])) j--;
  const name = text.slice(j + 1, end);
  if (!CLASS_ATTRS.test(name)) return null;
  let prefixStart = offset;
  while (prefixStart > quote + 1 && /[\w\-]/.test(text[prefixStart - 1])) prefixStart--;
  let wordEnd = offset;
  while (wordEnd < text.length && /[\w\-]/.test(text[wordEnd])) wordEnd++;
  return { valueStart: quote + 1, prefixStart, wordEnd };
}

function completionItems(text, offset) {
  const ctx = classContext(text, offset);
  if (!ctx) return [];
  const prefix = text.slice(ctx.prefixStart, offset);
  const range = { start: positionAt(text, ctx.prefixStart), end: positionAt(text, ctx.wordEnd) };
  const names = [];
  for (const name of getIndex().keys()) if (name.startsWith(prefix)) names.push(name);
  names.sort();
  const limited = prefix.length === 0 ? names.slice(0, MAX_EMPTY_PREFIX_ITEMS) : names;
  return limited.map((name) => {
    let doc = docFor(name) || '';
    if (doc.length > DOC_LIMIT) doc = doc.slice(0, DOC_LIMIT) + '\n…\n```';
    return {
      label: name,
      kind: 12, // Value
      detail: 'Bootstrap',
      sortText: '0_' + name,
      filterText: name,
      documentation: { kind: 'markdown', value: doc },
      textEdit: { range, newText: name },
    };
  });
}

function hoverFor(text, offset) {
  const ctx = classContext(text, offset);
  if (!ctx) return null;
  let s = offset, e = offset;
  while (s > ctx.valueStart && /[\w\-]/.test(text[s - 1])) s--;
  while (e < text.length && /[\w\-]/.test(text[e])) e++;
  const name = text.slice(s, e);
  const doc = name ? docFor(name) : null;
  if (!doc) return null;
  return { contents: { kind: 'markdown', value: doc }, range: { start: positionAt(text, s), end: positionAt(text, e) } };
}

function toMarkdown(contents) {
  if (!contents) return '';
  if (Array.isArray(contents)) return contents.map(toMarkdown).filter(Boolean).join('\n\n');
  if (typeof contents === 'string') return contents;
  if (contents.language) return '```' + contents.language + '\n' + contents.value + '\n```';
  return contents.value || '';
}

// ---------------------------------------------------------------- proxy
if (!HTML_SERVER) { log('missing --html-server <path> (or htmlServer in config.json)'); process.exit(1); }

const pending = new Map();   // client request id -> {method, params, html, emmet, timer}
const emmetSent = new Map(); // 'emmet:<id>' -> original client request id
const emmetReqIds = new Map(); // 'emmet:<id>' -> original id of a request the emmet server sent to the client
let initId = null;

function spawnChild(name, entry, critical) {
  const viaNode = /\.(?:c|m)?js$/i.test(entry);
  const env = Object.assign({}, process.env, {
    PATH: path.dirname(process.execPath) + path.delimiter + (process.env.PATH || '/usr/local/bin:/usr/bin:/bin'),
  });
  const stdio = ['pipe', 'pipe', 'inherit'];
  log('starting ' + name + ' server: ' + entry);
  const proc = viaNode ? spawn(process.execPath, [entry, '--stdio'], { stdio, env }) : spawn(entry, ['--stdio'], { stdio, env });
  const child = {
    name,
    alive: true,
    send(m) { if (this.alive) { try { proc.stdin.write(encode(m)); } catch (e) { /* gone */ } } },
  };
  proc.stdin.on('error', () => {});
  proc.on('error', (e) => { log(name + ' server cannot start: ' + e.message); gone(child, critical, 1); });
  proc.on('exit', (code) => { log(name + ' server exited with ' + code); gone(child, critical, code); });
  proc.stdout.on('data', makeReader((msg) => onChildMessage(child, msg)));
  child.proc = proc;
  return child;
}
function gone(child, critical, code) {
  if (!child.alive) return;
  child.alive = false;
  if (critical) process.exit(code === null || code === undefined ? 1 : code);
  for (const [id, p] of Array.from(pending)) { if (p.method === 'textDocument/completion' && p.emmet === undefined) { p.emmet = null; tryFinish(id, p); } }
}

const html = spawnChild('html', HTML_SERVER, true);
const emmet = EMMET_SERVER ? spawnChild('emmet', EMMET_SERVER, false) : null;
if (!emmet) log('emmet server not configured, running html + bootstrap only');

const toClient = (m) => process.stdout.write(encode(m));
function emmetSend(msg) {
  if (!emmet || !emmet.alive) return;
  const pid = 'emmet:' + msg.id;
  emmetSent.set(pid, msg.id);
  emmet.send(Object.assign({}, msg, { id: pid }));
}

// -------- client -> servers
process.stdin.on('data', makeReader((msg) => {
  try { track(msg); } catch (e) { log('client message handling failed: ' + e.stack); }
  route(msg);
}));
process.stdin.on('end', () => { html.proc.stdin.end(); if (emmet) emmet.proc.stdin.end(); });
process.on('SIGTERM', () => { html.proc.kill(); if (emmet) emmet.proc.kill(); });

function track(msg) {
  switch (msg.method) {
    case 'initialize': {
      const p = msg.params || {};
      const found = [];
      for (const f of p.workspaceFolders || []) found.push(uriToPath(f.uri));
      found.push(uriToPath(p.rootUri), p.rootPath);
      const list = found.filter(Boolean);
      if (list.length) roots = list;
      index = null;
      setupPeek(list);
      if (msg.id !== undefined) initId = msg.id;
      break;
    }
    case 'textDocument/didOpen':
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

function route(msg) {
  if (msg.method === undefined) { // the client answering a request that a server sent earlier
    if (typeof msg.id === 'string' && emmetReqIds.has(msg.id)) {
      if (emmet) emmet.send(Object.assign({}, msg, { id: emmetReqIds.get(msg.id) }));
      emmetReqIds.delete(msg.id);
    } else {
      html.send(msg);
    }
    return;
  }
  if (msg.id === undefined) { // notification: every server sees every document
    html.send(msg);
    if (emmet) emmet.send(msg);
    return;
  }
  switch (msg.method) {
    case 'initialize':
    case 'shutdown':
      html.send(msg);
      emmetSend(msg);
      break;
    case 'textDocument/completion':
      pending.set(msg.id, { method: msg.method, params: msg.params, html: undefined, emmet: emmet && emmet.alive ? undefined : null, timer: null });
      html.send(msg);
      emmetSend(msg);
      break;
    case 'textDocument/definition':
    case 'textDocument/declaration':
      answerDefinition(msg);
      break;
    case 'textDocument/hover':
      pending.set(msg.id, { method: msg.method, params: msg.params, html: undefined, emmet: null, timer: null });
      html.send(msg);
      break;
    default:
      html.send(msg);
  }
}

function answerDefinition(msg) {
  let result = null;
  try {
    const uri = msg.params.textDocument.uri;
    const text = docs.get(uri);
    if (text !== undefined) result = getPeek().definition(uri, text, offsetAt(text, msg.params.position));
  } catch (e) { log('definition failed: ' + e.stack); }
  toClient({ jsonrpc: '2.0', id: msg.id, result });
}

// -------- servers -> client
function onChildMessage(child, msg) {
  if (msg.method !== undefined) { // a request or notification from the server
    if (child.name === 'emmet' && msg.id !== undefined) {
      const pid = 'emmet:' + msg.id;
      emmetReqIds.set(pid, msg.id);
      msg = Object.assign({}, msg, { id: pid });
    }
    toClient(msg);
    return;
  }
  if (child.name === 'html') {
    if (msg.id !== undefined && msg.id === initId) {
      try { addTriggerCharacters(msg); } catch (e) { log('trigger patch failed: ' + e.stack); }
      toClient(msg);
      return;
    }
    const p = pending.get(msg.id);
    if (p) { p.html = msg; tryFinish(msg.id, p); return; }
    toClient(msg);
    return;
  }
  // a response from the emmet server: only completion answers matter
  if (typeof msg.id === 'string' && emmetSent.has(msg.id)) {
    const realId = emmetSent.get(msg.id);
    emmetSent.delete(msg.id);
    const p = pending.get(realId);
    if (p && p.method === 'textDocument/completion') { p.emmet = msg; tryFinish(realId, p); }
  }
}

function tryFinish(id, p) {
  if (p.html === undefined) return;
  if (p.emmet === undefined) { // html is in, give emmet a moment
    if (!p.timer) p.timer = setTimeout(() => { if (p.emmet === undefined) { p.emmet = null; tryFinish(id, p); } }, EMMET_WAIT_MS);
    return;
  }
  if (!pending.delete(id)) return;
  if (p.timer) clearTimeout(p.timer);
  let out = p.html;
  try {
    if (p.emmet) out = mergeCompletion(out, p.emmet);
    out = enrich(p, out);
  } catch (e) { log('enrich failed: ' + e.stack); }
  toClient(out);
}

function itemsOf(msg) {
  const r = msg && !msg.error ? msg.result : null;
  if (Array.isArray(r)) return { items: r, incomplete: false };
  if (r && typeof r === 'object') return { items: r.items || [], incomplete: !!r.isIncomplete };
  return { items: [], incomplete: false };
}
function mergeCompletion(htmlMsg, emmetMsg) {
  const e = itemsOf(emmetMsg);
  if (!e.items.length) return htmlMsg;
  const h = itemsOf(htmlMsg);
  return { jsonrpc: '2.0', id: htmlMsg.id, result: { isIncomplete: h.incomplete || e.incomplete, items: h.items.concat(e.items) } };
}

/** Editors close the popup on "-" (not an identifier char); make it re-trigger completion. */
function addTriggerCharacters(msg) {
  const caps = msg.result && msg.result.capabilities;
  if (!caps) return;
  // the stock html server has no definition support: without this the client never asks
  caps.definitionProvider = true;
  caps.declarationProvider = true;
  const cp = caps.completionProvider || (caps.completionProvider = {});
  const chars = cp.triggerCharacters || (cp.triggerCharacters = []);
  if (!chars.includes('-')) chars.push('-');
}

function enrich(req, msg) {
  if (msg.error) {
    // the html server failed (or has nothing for this spot): still offer Bootstrap classes
    if (req.method !== 'textDocument/completion') return msg;
    const text0 = docs.get(req.params.textDocument.uri);
    if (text0 === undefined) return msg;
    const extra0 = bootstrapEnabled(req.params.textDocument.uri, text0) ? completionItems(text0, offsetAt(text0, req.params.position)) : [];
    if (!extra0.length) return msg;
    return { jsonrpc: '2.0', id: msg.id, result: { isIncomplete: false, items: extra0 } };
  }
  const text = docs.get(req.params.textDocument.uri);
  if (text === undefined) return msg;
  const offset = offsetAt(text, req.params.position);
  const useBootstrap = bootstrapEnabled(req.params.textDocument.uri, text);
  if (req.method === 'textDocument/completion') {
    const extra = useBootstrap ? completionItems(text, offset) : [];
    if (!extra.length) return msg;
    const r = msg.result;
    if (Array.isArray(r)) msg.result = r.concat(extra);
    else if (r && typeof r === 'object') r.items = (r.items || []).concat(extra);
    else msg.result = { isIncomplete: false, items: extra };
    return msg;
  }
  // hover = html server's answer, then the project's rules, then the Bootstrap docs
  const sections = [];
  const base = msg.result ? toMarkdown(msg.result.contents) : '';
  if (base) sections.push(base);
  let range = msg.result && msg.result.range;
  try {
    const mine = getPeek().hover(req.params.textDocument.uri, text, offset);
    if (mine) { sections.push(mine.markdown); range = range || mine.range; }
  } catch (e) { log('hover failed: ' + e.stack); }
  const bs = useBootstrap ? hoverFor(text, offset) : null;
  if (bs) { sections.push(bs.contents.value); range = range || bs.range; }
  if (!sections.length) return msg;
  msg.result = { contents: { kind: 'markdown', value: sections.join('\n\n---\n\n') }, range };
  return msg;
}
