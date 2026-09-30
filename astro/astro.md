# Astro Plugin — وضعیت و نکات

## چیست

پلاگین GhostIDE برای پشتیبانی Astro: گرامر TextMate + language server.
مدل کاری از پلاگین `vue` کپی شده (TextMate + LSP + Setup Action).

```
astro/
  astrolsp.gpl                 خروجی نصب‌شدنی
  astro.png                    آیکون (۲۵۶x۲۵۶، کاربر قرار است بعداً عوضش کند)
  doc.json
  test/                        پروژه Astro نمونه برای تست دستی
  AstroLspGhostPlugin/
    build.sh                   بیلدر (نه gradle، نه toolchain)
    app/libs/                  همان ۶ فایل پلاگین vue (کپی‌شده)
    app/src/main/assets/
      plugin.json
      install-astro-lsp.sh     اسکریپت Setup Action
      grammars/*.json          ۶ گرامر TextMate
    app/src/main/java/ir/hanzodev1375/astro/
      AstroPlugin.java         فعال‌سازی، Setup Action، اعمال گرammar روی .astro
      AstroLspProvider.java    provider مربوط به LSP
      AstroTextmateHost.java   بارگذاری گرامر + اعمال روی CodeEditor
      PluginAssetGrammarSource.java
```

## بیلد

```bash
cd astro/AstroLspGhostPlugin && bash build.sh && mv astrolsp.gpl ../
```

۸ تا ۱۰ ثانیه. `javac` + `d8` + بسته‌بندی. نیازی به gradle یا دانلود toolchain نیست.

## گرامرهای باندل‌شده

| فایل | scope |
|---|---|
| `astro.tmLanguage.json` | `source.astro` |
| `TypeScript.tmLanguage.json` | `source.ts` |
| `TypeScriptReact.tmLanguage.json` | `source.tsx` |
| `JavaScript.tmLanguage.json` | `source.js` |
| `css.tmLanguage.json` | `source.css` |
| `JSON.tmLanguage.json` | `source.json` |

همه از ریپوهای رسمی دانلود شدند و فرمت‌شان JSON است (tm4e `TMParserJSON` پشتیبانی می‌کند).

## سه تله‌ای که سر راه بود

### ۱. `embeddedLanguages` اجباری است

`GrammarRegistry.doLoadGrammar` ریپازیتوریِ include های هر گرامر را **فقط** از
`grammarDefinition.getEmbeddedLanguages()` می‌سازد:

```java
registry.addGrammar(source, null, getOrPullGrammarId(scopeName), findGrammarIds(embeddedLanguages))
```

اگر map خالی باشد، ریپازیتوری فقط خودِ گرامر را دارد و **همه‌ی include های خارجی
بی‌صدا حذف می‌شوند**. برای astro یعنی فرانت‌متر (ts)، اسکریپت (js) و استایل (css)
همه بی‌رنگ می‌شدند و لاگ این را می‌داد:

```
W: RuleFactory: REMOVING BeginEndRule{id=3884,name=meta.embedded.block.astro}
   ENTIRELY DUE TO EMPTY PATTERNS THAT ARE MISSING
```

راه‌حل در `AstroTextmateHost.load()`:

```java
definition = ((DefaultGrammarDefinition) definition).withEmbeddedLanguages(asset.embedded);
```

`asset.embedded` نقشه‌ی `scope -> scope` است (astro ۱۰ اسکوپ دارد: ۵ تای باندل‌شده +
scss/less/sass/stylus/postcss که ادیتور خودش دارد). کلید = اسکوپی که در `include`
می‌آید، مقدار = اسکوپ/نام گرامری که باید embed شود — هم‌ترتیب با `embeddedLanguages`
خود افزونه‌ی VSCode‌ی astro.

نکته: در این نسخه `withGrammarSource(source, name, scope)` خروجی‌اش `GrammarDefinition`
است ولی `withEmbeddedLanguages` فقط روی `DefaultGrammarDefinition` — پس cast لازم است.

### ۲. `umask` این دستگاه 0077 است

هر چیزی که اسکریپت نصب می‌سازد `600/700 root-only` درمی‌آید، ولی ادیتور سرور را با
**uid خودش، نه root** اجرا می‌کند → `EACCES` → پروسه بلافاصله می‌میرد → ادیتور ۱۰ ثانیه
صبر می‌کند و می‌گوید `Capabilities are null` و `متصل نشد`.

نشانه: فایل‌هایی که کار می‌کنند `755`اند و فایل‌های خراب `700`:

```
-rwxr-xr-x  black, pylsp, kmp-lsp
-rwx------  astro-language-server, python-language-server
drwx------  /opt/astro-lsp
```

راه‌حل در `install-astro-lsp.sh`: `umask 022` در خط اول، بعد
`chmod -R a+rX` روی پکیج language server و `/opt/astro-lsp`، و `chmod 755` روی
wrapper و پروکسی.

### ۳. محیط اجرای ادیتور PATH ندارد

wrapper قدیمی `exec node ...` داشت و می‌مرد. الان wrapper مسیر مطلق node را
داخل خودش ذخیره می‌کند (`NODE="$(command -v node)"`) و پروکسی را اجرا می‌کند.
تست مرجع همیشه باید با `env={}` و یک uid غیر root زده شود:

```bash
setpriv --reuid=10225 --regid=10225 --clear-groups python3 probe.py
```

## لاگ ترافیک LSP

پروکسی `/usr/local/lib/astro-language-server.js` هر فریم رفت و برگشت را می‌نویسد به:

```
/opt/astro-lsp/lsp-traffic.log
```

اگر LSP وصل نشد، اول همین فایل را بخوان. `>>` یعنی چیزی که ادیتور فرستاده،
`<<` یعنی جواب سرور. اگر هیچ خطی اضافه نشد یعنی پروسه اصلاً اجرا نشده (مشکل دسترسی).

## چرا tsdk لازم است

`astro-ls` (v2، بر پایه‌ی Volar) بدون `initializationOptions.typescript.tsdk` جواب
initialize نمی‌دهد:

```
Request initialize failed with message: The `typescript.tsdk` init option is required.
```

و روی این دستگاه `typescript@7` نصب است که **اصلاً `typescript.js` ندارد**
(پورت Go است؛ `lib/` فقط چند فایل tsc دارد). برای همین اسکریپت نصب یک TS5 جدا در
پریفیکس خودمان می‌سازد و گلوبال را دست نمی‌زند (بقیه‌ی ابزارها به TS7 وابسته‌اند):

```bash
npm install --prefix /opt/astro-lsp --force --no-audit --no-fund typescript@5
# tsdk = /opt/astro-lsp/node_modules/typescript/lib
```

دو لایه محافظت: `AstroLspProvider` مقدار را در `initializationOptions` می‌فرستد
(کار درست از نظر API) و پروکسی اگر نبود، خودش تزریق می‌کند.

پروکسی یک کار دیگر هم می‌کند: اگر سرور بدون فریم‌های کامل JSON-RPC جواب بدهد
(خطای Content-Length ناقص در بعضی نسخه‌های vscode-languageserver) بافر را نگه
می‌دارد تا بدنه کامل برسد.

## بعد از نصب: اپ را کامل ببند

`GrammarRegistry` داخل پروسه‌ی ادیتور زندگی می‌کند. اگر گرامر خراب قبلی لود شده
باشد، نصب مجدد پلاگین آن را نمی‌پراند و لاگ می‌گوید:

```
astro: grammar already present, skipping source.astro
```

برای پاک شدن باید پروسه‌ی اپ کامل کشته و دوباره باز شود.

## نکات API که در مسیر فهمیدم

- `ProotProcessLauncher`: فقط دو متد —
  `isInstalled(String)` و `launch(String projectRoot, String executable, List<String> args)`.
  مسیر `projectRoot` همان cwd است، `args` هم مستقیم به فرمان می‌رود.
- پلاگین php با `Collections.emptyList()` launch می‌کند چون wrapper خودش `--stdio` دارد.
  الگوی استاندارد مخزن: wrapper در `/usr/local/bin` + بدون آرگومان اضافه.
- `LspServerDefinition.Builder` → `grammarScopeName`، `textMateGrammarLink`،
  `initializationOptions`، `enableInlayHints`، `enableSignatureHelp`،
  `initializationTimeoutMillis`. فیلد `expectedCapabilities` استفاده نشد.
- `DefaultGrammarDefinition.withGrammarSource(source, name, scopeName)` — آرگومان دوم
  اسم گرامر است و در `grammarFileName2ScopeName` کلید می‌شود، پس باید یکتا باشد.
- کد `LanguageServerWrapper` و `LspExtensionBridge` داخل اپ است، توی aarها نیست؛
  پس برای فهم رفتار اتصال باید از ترافیک واقعی استفاده کرد نه از حدس.

## کارهای باقی‌مانده

- [ ] تست نهایی: نصب `astrolsp.gpl`، بستن کامل اپ، باز کردن یک `.astro`
- [ ] اگر completion خالی بود: پروژه‌ی واقعی Astro با `node_modules` لازم است
      (سرور برای کار کردن `astro` را از پروژه لود می‌کند؛ پوشه‌ی `test/` پریفیکس ندارد)
- [ ] آیکون را کاربر خودش می‌گذارد
- [ ] در `open.json` چیزی نوشته نشد و نباید نوشت
