'use strict';
/*
 * CSS Peek core: no LSP, no dependencies. Pure functions + one class, testable on their own.
 *   parseCss      CSS / SCSS / Less -> every class and id selector with its exact source range
 *   scanHtml      <link rel=stylesheet> and <style> blocks of an HTML document
 *   attrContext   which attribute value (class / id) a cursor offset is in
 *   CssPeek       finds where a class / id used in HTML is defined
 */
const fs = require('fs');
const path = require('path');

const isWs = (c) => c === ' ' || c === '\t' || c === '\n' || c === '\r' || c === '\f';
const isIdentChar = (c) => /[\w-]/.test(c) || c.charCodeAt(0) >= 0x80;

// ------------------------------------------------------------------ lines
function makeLines(text) {
  const starts = [0];
  for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) === 10) starts.push(i + 1);
  return {
    starts,
    at(offset) {
      let lo = 0, hi = starts.length - 1;
      while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (starts[mid] <= offset) lo = mid; else hi = mid - 1; }
      return { line: lo, character: offset - starts[lo] };
    },
  };
}

// ------------------------------------------------------------------ css: masking
/** Blanks comments, string contents and unquoted url() bodies (same length, newlines kept). */
function maskCss(text, lang) {
  const n = text.length;
  const out = [];
  let last = 0;
  const blank = (a, b) => {
    if (b <= a) return;
    out.push(text.slice(last, a), text.slice(a, b).replace(/[^\r\n]/g, ' '));
    last = b;
  };
  const lineComments = lang === 'scss' || lang === 'less';
  let i = 0;
  while (i < n) {
    const c = text.charCodeAt(i);
    if (c === 47) { // /
      const d = text.charCodeAt(i + 1);
      if (d === 42) { let e = text.indexOf('*/', i + 2); e = e < 0 ? n : e + 2; blank(i, e); i = e; continue; }
      if (d === 47 && lineComments) { let e = text.indexOf('\n', i); if (e < 0) e = n; blank(i, e); i = e; continue; }
    } else if (c === 34 || c === 39) { // " '
      let j = i + 1;
      while (j < n) { const x = text.charCodeAt(j); if (x === 92) { j += 2; continue; } if (x === c || x === 10) break; j++; }
      blank(i + 1, Math.min(j, n));
      i = j + 1;
      continue;
    } else if ((c === 117 || c === 85) && text.substr(i, 4).toLowerCase() === 'url(' && !(i > 0 && isIdentChar(text[i - 1]))) {
      let j = i + 4;
      while (j < n && isWs(text[j])) j++;
      if (text[j] === '"' || text[j] === "'") { i = j; continue; } // quoted: the string branch handles it
      let e = text.indexOf(')', j); if (e < 0) e = n;
      blank(j, e);
      i = e;
      continue;
    }
    i++;
  }
  out.push(text.slice(last));
  return out.join('');
}

// ------------------------------------------------------------------ css: selectors
const TRANSPARENT_PSEUDO = new Set(['not', 'is', 'where', 'has', 'matches', 'any', '-webkit-any', '-moz-any', 'global', 'local', 'host', 'host-context']);

function skipBalanced(s, j, open, close) {
  let depth = 0;
  for (; j < s.length; j++) {
    if (s[j] === '\\') { j++; continue; }
    if (s[j] === open) depth++;
    else if (s[j] === close) { depth--; if (depth <= 0) return j + 1; }
  }
  return s.length;
}

/** Reads identifier characters at j (escapes decoded, interpolation flagged). Returns {name,end,dynamic}. */
function readIdentChars(s, j, interp) {
  let name = '';
  let dynamic = false;
  while (j < s.length) {
    const ch = s[j];
    if (ch === '\\') {
      const hex = /^[0-9a-fA-F]{1,6}/.exec(s.slice(j + 1, j + 7));
      if (hex) {
        name += String.fromCodePoint(parseInt(hex[0], 16) || 0xfffd);
        j += 1 + hex[0].length;
        if (isWs(s[j])) j++;
      } else if (j + 1 < s.length) { name += s[j + 1]; j += 2; } else { j++; }
    } else if (/[\w-]/.test(ch) || ch.charCodeAt(0) >= 0x80) { name += ch; j++; }
    else if (interp && (ch === '#' || ch === '@') && s[j + 1] === '{') { dynamic = true; j = skipBalanced(s, j + 1, '{', '}'); }
    else break;
  }
  return { name, end: j, dynamic };
}

/** After "." or "#": a valid identifier must start with a letter, _, -, non-ASCII, escape or interpolation. */
function readIdent(s, j, interp) {
  const ch = s[j];
  if (ch === undefined) return null;
  const startsOk = /[A-Za-z_-]/.test(ch) || ch.charCodeAt(0) >= 0x80 || ch === '\\' || (interp && (ch === '#' || ch === '@') && s[j + 1] === '{');
  if (!startsOk) return null;
  return readIdentChars(s, j, interp);
}

function splitTop(header, base) {
  const parts = [];
  let depth = 0, from = 0;
  for (let i = 0; i < header.length; i++) {
    const c = header[i];
    if (c === '\\') { i++; continue; }
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']' || c === '}') depth = Math.max(0, depth - 1);
    else if (c === ',' && depth === 0) { parts.push({ text: header.slice(from, i), offset: base + from }); from = i + 1; }
  }
  parts.push({ text: header.slice(from), offset: base + from });
  return parts;
}

/** One complex selector (no top-level commas). parentTails = classes/ids the enclosing rule ends with ("&" targets). */
function scanSelector(sel, base, parentTails, interp) {
  const tokens = [];
  const n = sel.length;
  let selEnd = n;
  while (selEnd > 0 && isWs(sel[selEnd - 1])) selEnd--;
  let endsWithAmp = false;
  let j = 0;
  while (j < n) {
    const ch = sel[j];
    if (ch === '\\') { j += 2; continue; }
    if (ch === '[') { j = skipBalanced(sel, j, '[', ']'); continue; }
    if (ch === ':') {
      let k = j + 1;
      if (sel[k] === ':') k++;
      let name = '';
      while (k < n && /[\w-]/.test(sel[k])) name += sel[k++];
      j = k;
      if (sel[j] === '(') {
        if (TRANSPARENT_PSEUDO.has(name.toLowerCase())) j++; // look inside :not(.a) / :is(.a, .b)
        else j = skipBalanced(sel, j, '(', ')');
      }
      continue;
    }
    if (ch === '(') { j = skipBalanced(sel, j, '(', ')'); continue; } // less mixin args, guards
    if (ch === ')') { j++; continue; }
    if ((ch === '#' || ch === '@') && interp && sel[j + 1] === '{') { j = skipBalanced(sel, j + 1, '{', '}'); continue; }
    if (ch === '.' || ch === '#') {
      const id = readIdent(sel, j + 1, interp);
      if (id) {
        if (!id.dynamic && id.name) tokens.push({ kind: ch === '.' ? 'class' : 'id', name: id.name, start: base + j, end: base + id.end });
        j = id.end;
        continue;
      }
      j++;
      continue;
    }
    if (ch === '&') {
      const sfx = readIdentChars(sel, j + 1, interp);
      if (sfx.name || sfx.dynamic) {
        if (!sfx.dynamic) {
          for (const pt of parentTails) tokens.push({ kind: pt.kind, name: pt.name + sfx.name, start: base + j, end: base + sfx.end });
        }
        j = sfx.end;
        continue;
      }
      if (j + 1 === selEnd) endsWithAmp = true;
      j++;
      continue;
    }
    j++;
  }
  let tails;
  if (endsWithAmp) tails = parentTails.slice();
  else tails = tokens.filter((t) => t.end - base === selEnd).map((t) => ({ kind: t.kind, name: t.name }));
  return { tokens, tails };
}

// ------------------------------------------------------------------ css: parser
/** @returns {{entries, rules, imports, byName}} entry = {kind,name,start,end,rule}; rule = {start,end} (offsets). */
function parseCss(text, lang) {
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
  const m = maskCss(text, lang);
  const n = m.length;
  const interp = lang === 'scss' || lang === 'less';
  const entries = [];
  const rules = [];
  const imports = [];
  const stack = [];
  let tails = [];
  let start = 0;
  let paren = 0;

  const addImports = (s, e) => {
    let hs = s;
    while (hs < e && isWs(m[hs])) hs++;
    if (m.charCodeAt(hs) !== 64 || !/^@(import|use|forward)\b/i.test(m.slice(hs, hs + 9))) return;
    const raw = text.slice(hs, e);
    const re = /["']([^"']+)["']|url\(\s*([^)\s"']+)\s*\)/gi;
    let mm;
    while ((mm = re.exec(raw))) {
      const spec = mm[1] || mm[2];
      if (spec && !/^(?:[a-z][\w+.-]*:)?\/\//i.test(spec) && !/^[a-z]+:/i.test(spec)) imports.push(spec);
    }
  };

  const enter = (s, e) => {
    let hs = s, he = e;
    while (hs < he && isWs(m[hs])) hs++;
    while (he > hs && isWs(m[he - 1])) he--;
    const frame = { tails, rule: -1 };
    let next = tails;
    if (hs < he) {
      const header = m.slice(hs, he);
      if (header.charCodeAt(0) === 64) {
        // at-rule (@media, @include, @mixin ...): transparent container
      } else if (interp && /^[-\w]+\s*:$/.test(header)) {
        // scss nested property block "font: {"
      } else {
        const ruleIdx = rules.length;
        rules.push({ start: hs, end: n });
        frame.rule = ruleIdx;
        next = [];
        for (const part of splitTop(header, hs)) {
          const r = scanSelector(part.text, part.offset, tails, interp);
          for (const t of r.tokens) entries.push({ kind: t.kind, name: t.name, start: t.start, end: t.end, rule: ruleIdx });
          for (const t of r.tails) next.push(t);
        }
      }
    }
    stack.push(frame);
    tails = next;
  };

  for (let i = 0; i < n; i++) {
    const c = m.charCodeAt(i);
    if (c === 123) { // {
      if (interp && i > 0 && (m[i - 1] === '#' || (lang === 'less' && m[i - 1] === '@'))) { i = skipBalanced(m, i, '{', '}') - 1; continue; }
      enter(start, i);
      start = i + 1;
      paren = 0;
    } else if (c === 125) { // }
      const f = stack.pop();
      if (f) { if (f.rule >= 0) rules[f.rule].end = i + 1; tails = f.tails; }
      start = i + 1;
      paren = 0;
    } else if (c === 40) paren++;
    else if (c === 41) paren = Math.max(0, paren - 1);
    else if (c === 59 && paren === 0) { addImports(start, i); start = i + 1; }
  }
  addImports(start, n);

  const byName = new Map();
  for (const e of entries) {
    const key = e.kind + ':' + e.name;
    const list = byName.get(key);
    if (list) list.push(e); else byName.set(key, [e]);
  }
  return { entries, rules, imports, byName };
}

// ------------------------------------------------------------------ html
const blankOut = (s) => s.replace(/[^\r\n]/g, ' ');

function attrsOf(s) {
  const out = {};
  const re = /([^\s=\/"'<>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
  let m;
  while ((m = re.exec(s))) out[m[1].toLowerCase()] = m[2] !== undefined ? m[2] : m[3] !== undefined ? m[3] : m[4] !== undefined ? m[4] : '';
  return out;
}

/** @returns {{links:[{href,start}], styles:[{start,end,lang}]}} in document order. */
function scanHtml(text) {
  const m = text
    .replace(/<!--[\s\S]*?(?:-->|$)/g, blankOut)
    .replace(/(<script\b[^>]*>)([\s\S]*?)(<\/script\s*>|$)/gi, (all, a, b, c) => a + blankOut(b) + c);
  const links = [];
  const styles = [];
  let re = /<link\b([^>]*)>/gi;
  let mm;
  while ((mm = re.exec(m))) {
    const a = attrsOf(mm[1]);
    const rel = (a.rel || '').toLowerCase().split(/\s+/);
    const isSheet = rel.includes('stylesheet') || (rel.includes('preload') && (a.as || '').toLowerCase() === 'style');
    if (isSheet && a.href) links.push({ href: a.href, start: mm.index });
  }
  re = /<style\b([^>]*)>/gi;
  while ((mm = re.exec(m))) {
    const contentStart = re.lastIndex;
    const close = /<\/style\s*>/gi;
    close.lastIndex = contentStart;
    const cm = close.exec(m);
    const end = cm ? cm.index : m.length;
    const a = attrsOf(mm[1]);
    const hint = ((a.lang || '') + ' ' + (a.type || '')).toLowerCase();
    const lang = /scss/.test(hint) ? 'scss' : /less/.test(hint) ? 'less' : 'css';
    styles.push({ start: contentStart, end, lang });
    re.lastIndex = end;
  }
  return { links, styles };
}

/** Cursor offset -> the attribute value it is in: {name, valueStart, valueEnd} (null when not in one). */
function attrContext(text, offset) {
  let i = offset - 1;
  while (i >= 0) {
    const ch = text[i];
    if (ch === '"' || ch === "'") break;
    if (ch === '<' || ch === '>') return null;
    i--;
  }
  if (i < 0) return null;
  const q = text[i];
  let j = i - 1;
  while (j >= 0 && isWs(text[j])) j--;
  if (text[j] !== '=') return null;
  j--;
  while (j >= 0 && isWs(text[j])) j--;
  const end = j + 1;
  while (j >= 0 && /[\w:\-.@\[\]()]/.test(text[j])) j--;
  const name = text.slice(j + 1, end).toLowerCase();
  let valueEnd = text.indexOf(q, i + 1);
  if (valueEnd < 0) valueEnd = text.length;
  if (offset > valueEnd) return null;
  return { name, valueStart: i + 1, valueEnd };
}

/** The whitespace-separated token under the cursor inside an attribute value. */
function tokenAt(text, ctx, offset) {
  let s = offset, e = offset;
  while (s > ctx.valueStart && !isWs(text[s - 1])) s--;
  while (e < ctx.valueEnd && !isWs(text[e])) e++;
  if (s === e) return null;
  return { name: text.slice(s, e), start: s, end: e };
}

/** class / className -> 'class', id -> 'id', anything else -> null */
function kindOfAttr(name) {
  if (name === 'class' || name === 'classname') return 'class';
  if (name === 'id') return 'id';
  return null;
}

// ------------------------------------------------------------------ paths
function uriToPath(uri) {
  if (!uri || !uri.startsWith('file://')) return null;
  try { return decodeURIComponent(uri.slice('file://'.length)); } catch (e) { return null; }
}
function pathToUri(p) { return 'file://' + p.split('/').map(encodeURIComponent).join('/'); }

const langOf = (file) => { const e = path.extname(file).toLowerCase(); return e === '.scss' ? 'scss' : e === '.less' ? 'less' : 'css'; };
const isFile = (f) => { try { return fs.statSync(f).isFile(); } catch (e) { return false; } };
const isDir = (f) => { try { return fs.statSync(f).isDirectory(); } catch (e) { return false; } };

const SKIP_DIRS = new Set(['node_modules', '.git', '.svn', '.hg', 'dist', 'build', 'out', 'coverage', '.cache', '.idea', '.vscode', '__pycache__', '.next', '.nuxt', 'vendor', 'bower_components']);

// ------------------------------------------------------------------ CssPeek
class CssPeek {
  /**
   * @param {object} o
   * @param {string[]} o.roots     project roots, local (readable) paths
   * @param {(p:string)=>string} o.toLocal   path in the URI namespace -> path this process can read
   * @param {(p:string)=>string} o.toUri     local path -> URI string in the client's namespace
   */
  constructor(o) {
    this.roots = o.roots || [];
    this.toLocal = o.toLocal || ((p) => p);
    this.toUri = o.toUri || pathToUri;
    this.log = o.log || (() => {});
    this.maxWorkspaceFiles = o.maxWorkspaceFiles || 2000;
    this.cache = new Map();
    this.walkCache = new Map();
    this.warned = new Set();
  }

  _warn(key, msg) { if (!this.warned.has(key)) { this.warned.add(key); this.log(msg); } }

  /** Cached, mtime-validated read+parse of a stylesheet on disk. */
  file(f) {
    let st;
    try { st = fs.statSync(f); } catch (e) { this._warn('stat:' + f, 'css-peek: cannot read ' + f + ' (' + e.code + ')'); return null; }
    if (!st.isFile()) return null;
    const hit = this.cache.get(f);
    if (hit && hit.mtime === st.mtimeMs && hit.size === st.size) return hit;
    if (st.size > 8 * 1024 * 1024) { this._warn('big:' + f, 'css-peek: skipping huge stylesheet ' + f); return null; }
    let text;
    try { text = fs.readFileSync(f, 'utf8'); } catch (e) { return null; }
    if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
    const rec = { file: f, text, lines: makeLines(text), parsed: parseCss(text, langOf(f)), mtime: st.mtimeMs, size: st.size };
    this.cache.set(f, rec);
    return rec;
  }

  _resolveHref(href, docPath) {
    let h = href.split('#')[0].split('?')[0].trim();
    if (!h || /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(h)) return null; // http(s):, data:, //cdn
    try { h = decodeURIComponent(h); } catch (e) { /* keep */ }
    const docDir = path.dirname(docPath);
    const tries = [];
    if (h.startsWith('/')) {
      for (const r of this.roots) tries.push(path.join(r, h));
      for (let d = docDir, k = 0; d && k < 8; d = path.dirname(d), k++) { tries.push(path.join(d, h)); if (path.dirname(d) === d) break; }
    } else {
      tries.push(path.resolve(docDir, h));
    }
    return tries.find(isFile) || null;
  }

  _resolveImport(spec, fromFile) {
    const dirs = [path.dirname(fromFile)];
    let s = spec;
    if (s.startsWith('~')) { s = s.slice(1); for (const r of this.roots) dirs.push(path.join(r, 'node_modules')); }
    else if (!s.startsWith('.') && !path.isAbsolute(s)) { for (const r of this.roots) dirs.push(path.join(r, 'node_modules'), r); }
    const exts = ['', '.scss', '.css', '.less'];
    for (const dir of dirs) {
      const p = path.isAbsolute(s) ? s : path.join(dir, s);
      const base = path.basename(p), pd = path.dirname(p);
      const cands = [];
      for (const e of exts) cands.push(p + e, path.join(pd, '_' + base + e));
      for (const e of exts.slice(1)) cands.push(path.join(p, 'index' + e), path.join(p, '_index' + e));
      const hit = cands.find(isFile);
      if (hit) return hit;
    }
    return null;
  }

  /** A linked stylesheet plus everything it @imports, depth first, each file once. */
  _closure(file, seen, out, depth) {
    if (seen.has(file)) return;
    seen.add(file);
    out.push(file);
    if (depth >= 4) return;
    const rec = this.file(file);
    if (!rec) return;
    for (const spec of rec.parsed.imports) {
      const hit = this._resolveImport(spec, file);
      if (hit) this._closure(hit, seen, out, depth + 1);
    }
  }

  _workspaceFiles() {
    const key = this.roots.join('|');
    const hit = this.walkCache.get(key);
    if (hit && Date.now() - hit.at < 20000) return hit.files;
    const files = [];
    let dirsLeft = 6000;
    for (const root of this.roots) {
      const todo = [root];
      while (todo.length && files.length < this.maxWorkspaceFiles && dirsLeft-- > 0) {
        const dir = todo.pop();
        let items;
        try { items = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
        for (const it of items) {
          if (it.isDirectory()) { if (!SKIP_DIRS.has(it.name) && !it.name.startsWith('.')) todo.push(path.join(dir, it.name)); }
          else if (/\.(?:css|scss|less)$/i.test(it.name) && !/\.min\.css$/i.test(it.name)) files.push(path.join(dir, it.name));
        }
      }
    }
    files.sort();
    this.walkCache.set(key, { at: Date.now(), files });
    return files;
  }

  /** Every definition of a class / id as seen from this HTML document, in cascade order. */
  lookup(docUri, docText, kind, name) {
    const docPath = this.toLocal(uriToPath(docUri) || '');
    const scan = scanHtml(docText);
    const key = kind + ':' + name;
    const out = [];
    const seenFiles = new Set();
    const seenInline = [];

    const sources = [];
    for (const l of scan.links) sources.push({ at: l.start, link: l });
    for (const s of scan.styles) sources.push({ at: s.start, style: s });
    sources.sort((a, b) => a.at - b.at);

    for (const src of sources) {
      if (src.link) {
        const f = this._resolveHref(src.link.href, docPath);
        if (!f) {
          // remote (http:, //cdn, data:) stylesheets cannot be opened: skip them silently
          if (!/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(src.link.href.trim())) this._warn('href:' + src.link.href, 'css-peek: cannot resolve stylesheet ' + src.link.href + ' from ' + docPath);
          continue;
        }
        const files = [];
        this._closure(f, seenFiles, files, 0);
        for (const file of files) {
          const rec = this.file(file);
          if (!rec) continue;
          for (const e of rec.parsed.byName.get(key) || []) out.push({ uri: this.toUri(file), rec, entry: e, base: 0, via: 'link' });
        }
      } else {
        const st = src.style;
        const parsed = parseCss(docText.slice(st.start, st.end), st.lang);
        const rec = { file: docPath, text: docText, lines: makeLines(docText), parsed };
        for (const e of parsed.byName.get(key) || []) out.push({ uri: docUri, rec, entry: e, base: st.start, via: 'inline' });
        seenInline.push(st);
      }
    }

    if (!out.length) {
      // a big workspace must not freeze the proxy: parse for at most ~1.5 s per request, the cache keeps
      // the progress so the next request continues where this one stopped
      const t0 = Date.now();
      for (const file of this._workspaceFiles()) {
        if (Date.now() - t0 > 1500) { this.log('css-peek: workspace scan paused after 1.5 s, will continue on the next request'); break; }
        if (seenFiles.has(file)) continue;
        const rec = this.file(file);
        if (!rec) continue;
        for (const e of rec.parsed.byName.get(key) || []) out.push({ uri: this.toUri(file), rec, entry: e, base: 0, via: 'workspace' });
      }
    }
    return out;
  }

  _target(docText, ctxOffset) {
    const ctx = attrContext(docText, ctxOffset);
    if (!ctx) return null;
    const kind = kindOfAttr(ctx.name);
    if (!kind) return null;
    const tok = tokenAt(docText, ctx, ctxOffset);
    if (!tok) return null;
    return { kind, name: tok.name, tok };
  }

  /** @returns {Array<{uri,range}>|null} LSP Locations */
  definition(docUri, docText, offset) {
    const t = this._target(docText, offset);
    if (!t) return null;
    const hits = this.lookup(docUri, docText, t.kind, t.name);
    if (!hits.length) return null;
    const seen = new Set();
    const locs = [];
    for (const h of hits) {
      const lines = h.rec.lines;
      const range = { start: lines.at(h.base + h.entry.start), end: lines.at(h.base + h.entry.end) };
      const k = h.uri + ':' + range.start.line + ':' + range.start.character;
      if (seen.has(k)) continue;
      seen.add(k);
      locs.push({ uri: h.uri, range });
    }
    return locs;
  }

  /** @returns {{markdown:string, range}|null} the rules that style the class / id under the cursor */
  hover(docUri, docText, offset, maxRules = 3) {
    const t = this._target(docText, offset);
    if (!t) return null;
    const hits = this.lookup(docUri, docText, t.kind, t.name);
    if (!hits.length) return null;
    const lines = makeLines(docText);
    const seenRule = new Set();
    const blocks = [];
    for (const h of hits) {
      const rule = h.rec.parsed.rules[h.entry.rule];
      const rk = h.uri + ':' + rule.start;
      if (seenRule.has(rk)) continue;
      seenRule.add(rk);
      if (blocks.length >= maxRules) break;
      let body = h.rec.text.slice(h.base + rule.start, h.base + rule.end);
      const bl = body.split('\n');
      if (bl.length > 30) body = bl.slice(0, 30).join('\n') + '\n  /* … */';
      if (body.length > 2000) body = body.slice(0, 2000) + ' /* … */';
      const where = (h.via === 'inline' ? '<style>' : path.basename(uriToPath(h.uri) || h.uri)) + ':' + (h.rec.lines.at(h.base + h.entry.start).line + 1);
      blocks.push('**' + where + '**\n```css\n' + body + '\n```');
    }
    const more = hits.length > blocks.length ? '\n\n_+' + (hits.length - blocks.length) + ' more_' : '';
    return { markdown: blocks.join('\n\n') + more, range: { start: lines.at(t.tok.start), end: lines.at(t.tok.end) } };
  }
}

module.exports = { makeLines, maskCss, parseCss, scanHtml, attrContext, tokenAt, kindOfAttr, uriToPath, pathToUri, CssPeek };
