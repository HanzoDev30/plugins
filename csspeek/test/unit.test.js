const assert = require('assert');
const { parseCss, scanHtml, attrContext, tokenAt, maskCss } = require('../app/src/main/assets/css-peek-core.js');

let passed = 0, failed = 0;
function t(name, fn) { try { fn(); passed++; console.log('ok   ' + name); } catch (e) { failed++; console.log('FAIL ' + name + '\n     ' + String(e.message).split('\n').join('\n     ')); } }

/** [kind:name@sliceOfSource, ...] so a wrong range shows up immediately */
const found = (text, lang = 'css') => parseCss(text, lang).entries.map((e) => `${e.kind}:${e.name}@${text.slice(e.start, e.end)}`);
const names = (text, lang) => found(text, lang).map((s) => s.split('@')[0]);

t('basic class / id / compound / descendant', () => {
  assert.deepStrictEqual(found('.a{} #b{} .c.d > .e ~ #f + .g{}'), ['class:a@.a', 'id:b@#b', 'class:c@.c', 'class:d@.d', 'class:e@.e', 'id:f@#f', 'class:g@.g']);
});
t('selector lists + :not/:is/:where/:has see inside, other pseudos do not', () => {
  assert.deepStrictEqual(names('.a:not(.b), .c:is(.d, .e):hover::after, li:nth-child(2n+1) .f, .g:lang(en){}'), ['class:a', 'class:b', 'class:c', 'class:d', 'class:e', 'class:f', 'class:g']);
});
t('attribute selectors and strings never leak classes', () => {
  assert.deepStrictEqual(names('a[href$=".pdf"], [class*=".foo"], input[data-x=\'.bar #baz\'] .real{}'), ['class:real']);
});
t('comments (also containing braces) are ignored, positions stay exact', () => {
  const src = '/* .no { } */\n.yes /* .also-no */ {\n  color: red; /* } */\n}\n.after{}';
  assert.deepStrictEqual(found(src), ['class:yes@.yes', 'class:after@.after']);
});
t('braces / dots / semicolons inside strings and url()', () => {
  const src = '.a{background:url(data:image/svg+xml;utf8,<svg xmlns="x"> .no { } </svg>);content:"}.nope{";}\n.b{background:url( "a.png?x=.y;z" )}\n.c{}';
  assert.deepStrictEqual(names(src), ['class:a', 'class:b', 'class:c']);
});
t('@media / @supports / @layer nesting and keyframes percentages', () => {
  const src = '@media (min-width:576px) and (max-width:2px){.col-sm-1{width:8%}.d-sm-none{display:none}}\n@keyframes spin{from{a:b}50.5%{a:b}to{a:b}}\n@layer base{.x{}}\n@font-face{font-family:x}';
  assert.deepStrictEqual(names(src), ['class:col-sm-1', 'class:d-sm-none', 'class:x']);
});
t('css escapes: tailwind style, hex escapes, unicode', () => {
  const src = '.md\\:flex{} .\\31 2{} .w-1\\/2{} .sm\\:hover\\:bg-red:hover{} .فارسی{} .a\\.b{}';
  const r = parseCss(src, 'css').entries.map((e) => e.name);
  assert.deepStrictEqual(r, ['md:flex', '12', 'w-1/2', 'sm:hover:bg-red', 'فارسی', 'a.b']);
  assert.strictEqual(src.slice(parseCss(src).entries[1].start, parseCss(src).entries[1].end), '.\\31 2');
});
t('numbers in selectors are not classes (.5 in keyframes / decimals)', () => {
  assert.deepStrictEqual(names('@keyframes k{ .5%{a:b} 10.5%{a:b} } .ok{}'), ['class:ok']);
});
t('minified css on one line', () => {
  const src = '.a{x:y}.b,.c>.d{x:y}@media(min-width:1px){.e{x:y}}#f{x:y}';
  assert.deepStrictEqual(found(src), ['class:a@.a', 'class:b@.b', 'class:c@.c', 'class:d@.d', 'class:e@.e', 'id:f@#f']);
});
t('CRLF line endings and BOM', () => {
  assert.deepStrictEqual(names('\uFEFF.a{\r\n  x:y;\r\n}\r\n.b{}\r\n'), ['class:a', 'class:b']);
});
t('rule ranges cover the whole block', () => {
  const src = '.a { x: y }\n.b { .c { z: 1 } }';
  const p = parseCss(src, 'scss');
  const rule = (name) => { const e = p.entries.find((x) => x.name === name); const r = p.rules[e.rule]; return src.slice(r.start, r.end); };
  assert.strictEqual(rule('a'), '.a { x: y }');
  assert.strictEqual(rule('b'), '.b { .c { z: 1 } }');
  assert.strictEqual(rule('c'), '.c { z: 1 }');
});

// ---- scss
t('scss: nesting, &-suffix, &__elem, &--mod, &.state, deep BEM', () => {
  const src = '.card {\n  &-title { a:b }\n  &__body { &-x { a:b } &--wide { a:b } }\n  &.active { a:b }\n  .inner { a:b }\n  &:hover { a:b }\n}';
  assert.deepStrictEqual(found(src, 'scss'), [
    'class:card@.card', 'class:card-title@&-title', 'class:card__body@&__body', 'class:card__body-x@&-x',
    'class:card__body--wide@&--wide', 'class:active@.active', 'class:inner@.inner']);
});
t('scss: & with a selector list parent produces every combination', () => {
  assert.deepStrictEqual(names('.a, .b { &-x { c:d } }', 'scss'), ['class:a', 'class:b', 'class:a-x', 'class:b-x']);
});
t('scss: @media / @include blocks are transparent for &', () => {
  const src = '.btn { @media (min-width: 1px) { &-lg { a:b } } @include foo(1) { &-sm { a:b } } }';
  assert.deepStrictEqual(names(src, 'scss'), ['class:btn', 'class:btn-lg', 'class:btn-sm']);
});
t('scss: interpolation does not break parsing or invent classes', () => {
  const src = '@each $n in a, b { .col-#{$n} { width: calc(100% - #{$gap}); } }\n.ok { &-#{$x} { a:b } &-real { a:b } }\n$map: (a: 1, b: 2);\n.last{}';
  assert.deepStrictEqual(names(src, 'scss'), ['class:ok', 'class:ok-real', 'class:last']);
});
t('scss: // line comments with braces, but url(http://) is not a comment', () => {
  const src = '// .no { }\n.a { background: url(http://x.com/a.png); } // } .no\n.b{}';
  assert.deepStrictEqual(names(src, 'scss'), ['class:a', 'class:b']);
});
t('scss: nested property blocks and @mixin', () => {
  const src = '@mixin m($x) { .in-mixin { a:b } }\n.a { font: { family: x; size: 1px; } .b { a:b } }';
  assert.deepStrictEqual(names(src, 'scss'), ['class:in-mixin', 'class:a', 'class:b']);
});
t('scss: placeholder selectors and @extend are ignored', () => {
  assert.deepStrictEqual(names('%ph { a:b } .a { @extend %ph; @extend .b; }', 'scss'), ['class:a']);
});
t('css native nesting with &', () => {
  assert.deepStrictEqual(names('.card { &.x { a:b } & .y { a:b } }', 'css'), ['class:card', 'class:x', 'class:y']);
});
t('css: // is NOT a comment in plain css (would swallow a rule)', () => {
  assert.deepStrictEqual(names('.a{background:url(//cdn/x.png)}\n.b{}', 'css'), ['class:a', 'class:b']);
});

// ---- less
t('less: mixins definitions, calls, guards, ; inside parens', () => {
  const src = '.mixin(@a; @b) { color: @a }\n.box { .mixin(1; 2); .inner { a:b } }\n.g when (iscolor(@c)) { a:b }\n.after{}';
  assert.deepStrictEqual(names(src, 'less'), ['class:mixin', 'class:box', 'class:inner', 'class:g', 'class:after']);
});
t('less: @{var} interpolation skipped', () => {
  assert.deepStrictEqual(names('.@{p}-x { a:b } .real { &-y { a:b } }', 'less'), ['class:real', 'class:real-y']);
});

// ---- imports
t('imports: @import / @use / @forward, url(), lists, remote ignored', () => {
  const src = '@import "a.css";\n@import url(b.css) screen;\n@import "c", \'d\';\n@import url("https://x/y.css");\n@use "sass:math";\n@use "./e" as e;\n@forward "f";\n.x{}';
  assert.deepStrictEqual(parseCss(src, 'scss').imports, ['a.css', 'b.css', 'c', 'd', './e', 'f']);
});

// ---- html
t('html: links (any case/quotes/rel list) and styles, comments and scripts ignored', () => {
  const html = `<!-- <link rel="stylesheet" href="no.css"> -->
<LINK REL=stylesheet HREF=unq.css>
<link rel='stylesheet preload' href='q.css?v=2'>
<link rel="icon" href="x.ico"><link rel="preload" as="style" href="pre.css">
<script>var s='<link rel="stylesheet" href="js.css">'; var t='<style>.js{}</style>';</script>
<style>.a{}</style><style lang="scss">.b{ &-c{} }</style>`;
  const r = scanHtml(html);
  assert.deepStrictEqual(r.links.map((l) => l.href), ['unq.css', 'q.css?v=2', 'pre.css']);
  assert.deepStrictEqual(r.styles.map((s) => [html.slice(s.start, s.end), s.lang]), [['.a{}', 'css'], ['.b{ &-c{} }', 'scss']]);
});
t('html: unterminated <style> runs to the end', () => {
  const html = '<style>.a{}'; const r = scanHtml(html);
  assert.strictEqual(html.slice(r.styles[0].start, r.styles[0].end), '.a{}');
});

// ---- attribute context
const at = (html, marker = '|') => { const o = html.indexOf(marker); const text = html.replace(marker, ''); const c = attrContext(text, o); return c && { name: c.name, tok: (tokenAt(text, c, o) || {}).name }; };
t('attr: class tokens at start / middle / end / between', () => {
  assert.deepStrictEqual(at('<div class="|btn btn-primary">'), { name: 'class', tok: 'btn' });
  assert.deepStrictEqual(at('<div class="btn btn-pr|imary x">'), { name: 'class', tok: 'btn-primary' });
  assert.deepStrictEqual(at('<div class="btn btn-primary|">'), { name: 'class', tok: 'btn-primary' });
  assert.deepStrictEqual(at('<div class="btn btn-primary x|">'), { name: 'class', tok: 'x' });
});
t('attr: id, className (jsx), single quotes, spaces around =, multiline value', () => {
  assert.deepStrictEqual(at("<p id = 'ma|in'>"), { name: 'id', tok: 'main' });
  assert.deepStrictEqual(at('<p className="a b|">'), { name: 'classname', tok: 'b' });
  assert.deepStrictEqual(at('<p class="a\n   b|\n   c">'), { name: 'class', tok: 'b' });
});
t('attr: not inside a value -> null (tag name, between attrs, text, after closing quote)', () => {
  assert.strictEqual(at('<di|v class="a">'), null);
  assert.strictEqual(at('<div class="a" |id="b">'), null);
  assert.strictEqual(at('<div class="a">te|xt</div>'), null);
  assert.strictEqual(at('<div class="a"|>'), null);
});
t('attr: other attributes give a name that is not class/id (caller filters)', () => {
  assert.deepStrictEqual(at('<a href="/x|">'), { name: 'href', tok: '/x' });
});

// ---- scale
t('performance: 6 MB of generated css parses in < 2.5 s and finds the last rule', () => {
  let s = ''; let i = 0;
  while (s.length < 6 * 1024 * 1024) { s += `.c${i}:hover > .d${i}, #i${i}{color:red;background:url(x${i}.png)} @media (min-width:${i}px){.m${i}{margin:0}}\n`; i++; }
  const t0 = Date.now(); const p = parseCss(s, 'css'); const ms = Date.now() - t0;
  assert(p.byName.has('class:m' + (i - 1)), 'last rule missing');
  assert(ms < 2500, 'took ' + ms + ' ms');
  console.log('     (' + (s.length / 1048576).toFixed(1) + ' MB, ' + p.entries.length + ' entries, ' + ms + ' ms)');
});
t('robustness: garbage / unbalanced input never throws or loops', () => {
  for (const g of ['}}}}', '{{{{', '.a{', '.a{ .b{', '/* unterminated', '"unterminated', 'url(', '@import', '.a,,,{}', '#{', '&&&-', '\\', '.\\', '[[[', ':not(', '.a:not(.b']) {
    for (const lang of ['css', 'scss', 'less']) parseCss(g, lang);
  }
});

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
