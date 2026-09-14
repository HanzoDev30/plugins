# پلاگین Rust LSP — تست

این پلاگین `.rs` رو به `rust-analyzer` واقعی وصل می‌کنه (completion/diagnostics/hover)، از طریق
همون proot rootfs که JDT-LS جاوا هم داخلش اجرا می‌شه — کد جدیدی برای proot/rootfs ننوشتم، از یه
سرویس جدید به‌نام `ProotProcessLauncher` استفاده کردم که تازه به `ide-ui-api` اضافه شده.

## قبل از ساخت

سه فایل رو بریز تو `app/libs/` (همون‌جوری که برای HelloGhostPlugin گفتم — از artifact
`ghostide-plugin-sdk` تو CI، یا لوکال با `./gradlew :plugin-api:jar :ide-api:jar
:ide-ui-api:assembleRelease`):
- `plugin-api-0.1.0.jar`
- `ide-api-0.1.0.jar`
- `ide-ui-api-release.aar`

## ساخت و نصب

```
./gradlew :app:assembleDebug
```
خروجی رو (`app-debug.apk`) به `rustlsp.gpl` تغییر اسم بده، از همون مسیر قبلی (مدیریت پلاگین‌ها
-> + -> انتخاب فایل) نصبش کن.

## نصب rust-analyzer داخل rootfs

این پلاگین یه `PluginSetupAction` اعلام کرده (نصب `rust-analyzer` با apt). بعد از نصب `.gpl`،
یه دیالوگ باید بیاد با دکمه‌ی «اجرا در ترمینال» — می‌زنی، ترمینال با دستور از پیش پرشده باز
می‌شه، خودت Enter می‌زنی و تایید می‌کنی (این عمداً خودکار نیست، شما باید دستور رو ببینی و تایید
کنی).

## تست

یه فایل `.rs` تو GhostIDE باز کن. اگه `rust-analyzer` نصب شده باشه، باید completion/diagnostics
واقعی بیاد (نه اون auto-complete ساده‌ی قبلی که فقط keyword-based بود).

## محدودیت شناخته‌شده

فرمتر مخصوص Rust (`RustLanguage.getFormatter()`) به این LSP وصل نیست — کاری که `JavaServer`
برای جاوا با `setWrapperLanguage` انجام می‌ده، برای زبان‌های ثبت‌شده از طریق رجیستری هنوز پیاده
نشده. یعنی format-on-save رو از rust-analyzer نمی‌گیره، ولی completion/diagnostics/hover چرا.
