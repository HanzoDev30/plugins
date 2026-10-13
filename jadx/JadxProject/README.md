# GhostJadx — پلاگین JADX برای Ghost IDE

لیست فایل‌های `.dex` داخل یک پوشه را می‌دهد و هر کدام را که انتخاب کنی با jadx دیکامپایل می‌کند.
jadx به‌صورت Gradle dependency (`io.github.skylot:jadx-core`) اضافه شده؛ هیچ jar ای به اپ میزبان اضافه نمی‌شود.

## ساختار

| ماژول | کار |
|---|---|
| `bridge` | چند interface کوچک (بدون اندروید و بدون jadx) که بین پلاگین و موتور مشترک است |
| `engine` | `jadx-core` + `jadx-dex-input` از Maven. فقط برای گرفتن dex و resourceهای jadx بیلد می‌شود، هیچ‌وقت نصب نمی‌شود |
| `app` | خود پلاگین Ghost IDE (UI). `engine` را به‌صورت `assets/jadx-engine.apk` داخل `.gpl` می‌گذارد |

چرا موتور جدا؟ jadx-core فایل‌های `clst/core.jcst` و `android/res-map.txt` را با `getResourceAsStream` می‌خواند، ولی
لودر پلاگین‌های `.gpl` (InMemoryDexClassLoader) resource نمی‌دهد. پلاگین موقع اولین استفاده engine را از assets
کپی می‌کند (read-only برای اندروید ۱۴+) و با یک `DexClassLoader` child-first لود می‌کند.

وابستگی‌های `app`: `plugin-api`، `ide-ui-api`، `thememanager` (M3Theme) و `components` (DialogCompat) فقط `compileOnly` هستند (در `app/libs/`).

## بیلد

```
gradle :app:packageGpl
```

خروجی: `build/gpl/jadx-decompiler.gpl` ← داخل `filesDir/gpl_plugins/` بگذار و اپ را ری‌استارت کن.
(پوشه‌ی `.github/workflows/build.yml` همین کار را در GitHub Actions انجام می‌دهد و `.gpl` را آرتیفکت می‌کند.)

نیاز: JDK 17، Gradle 8.9+، اینترنت برای Maven Central/Google. نسخه‌ی jadx در `gradle.properties` (`jadxVersion`).

## استفاده

- آیکون `</>` در نوار ابزار فایل‌منیجر: پوشه‌ی انتخاب‌شده (یا ریشه) را اسکن می‌کند و لیست dexها را نشان می‌دهد.
- لمس مستقیم یک فایل `.dex`: لیست کلاس‌های همان dex باز می‌شود (از تنظیمات قابل خاموش شدن است).
- داخل لیست کلاس‌ها: جستجو، لمس یک کلاس ← دیکامپایل و نمایش کد (دکمه‌ی «باز کردن در ادیتور»)، یا «دیکامپایل همه».
- خروجی کنار خود dex در پوشه‌ی `<نام>_jadx/sources/` ذخیره می‌شود.
- تنظیمات: deobfuscation، show inconsistent code، fallback mode.
