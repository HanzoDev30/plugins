# CSS Peek (GhostIDE)

در فایل `.html` / `.htm` روی نام کلاس یا id داخل `class="..."` / `id="..."` (و `className`) بایست:

- **Go to definition** (و declaration): می‌پره به سلکتور تو CSS. خروجی همیشه `Location[]` است (چندتا اگه چند جا تعریف شده، به ترتیب cascade).
- **Hover**: خود قانون CSS (تا ۳ تا) + مستندات Bootstrap (فقط پروژه‌ی Bootstrap‌دار) زیر جواب سرور HTML.

## استایل‌ها از کجا پیدا می‌شن (به ترتیب)
1. `<link rel="stylesheet">` (و `preload as=style`) در همون سند؛ `@import` / `@use` / `@forward` تا عمق ۴ دنبال می‌شه (`_partial.scss`, `index`, `~pkg`).
2. `<style>` داخل همون سند (`lang="scss"` / `less` هم).
3. اگه هیچ‌کدوم پیدا نشد: همه‌ی `.css/.scss/.less` پروژه (بدون node_modules, dist, build, `.min.css`؛ حداکثر ۲۰۰۰ فایل).

لینک‌های راه دور (`https://`, `//cdn`) بی‌صدا نادیده گرفته می‌شن.

## فهمیدن SCSS / Less
`&-title`, `&__body`, `&--mod`, `&.active`، nesting عمیق BEM، `@media`/`@include` شفاف، interpolation (`#{}` / `@{}`) بدون خراب کردن پارس، mixin های Less، escape ها (`.md\:flex`, `.\31 2`)، `:not()/:is()/:where()/:has()`، کامنت/استرینگ/`url()` که `{ } ; .` دارن.

## چرا همه‌چیز توی یک سرور است
هاست برای هر فایل فقط یک LSP نگه می‌داره (بالاترین اولویت). پس این سرور (اولویت **۶۰۰**) خودش HTML + Emmet + Bootstrap رو هم پشت یک پراکسی اجرا می‌کنه. اگه پلاگین Bootstrap (۵۰۰) هم نصبه، روی `.html` این برنده است و کار اونو هم انجام می‌ده. پروژه‌های Angular (`angular.json`) عمداً کنار گذاشته می‌شن تا سرور Angular (۴۰۰) کار کنه.

## نصب
اکشن setup رو اجرا کن، بعد اپ رو کامل ببند و باز کن. `server.js` و `css-peek-core.js` فقط Node استاندارد لازم دارن (۱۸+). HTML و Emmet سیستمی (`/usr/local/bin` یا `/usr/bin`) اگه باشن استفاده می‌شن.

## تست
```
node test/unit.test.js   # پارسر
node test/e2e.test.js    # سرور کامل با پروژه‌ی نمونه
```
