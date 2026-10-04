# CSV Viewer Plugin

## چیست
پلاگین GhostIDE برای فایل‌های `.csv/.tsv/.psv/.tab`:
- **ویوور جدولی** (EditorPanel): هدر ثابت، مرتب‌سازی با لمس هدر، جستجو، تیک سرستون، تشخیص خودکار جداکننده (`, ; Tab |`)، لمس سلول = نمایش کامل + کپی
- **گرامر TextMate** رنگین‌کمانی (هر ستون یک رنگ، ۱۲ ستون، هدر جدا): `source.csv`، `source.csv.semicolon`، `source.tsv`، `source.csv.pipe`

## بیلد
```bash
cd csv/CsvViewerGhostPlugin && bash build.sh && mv csvviewer.gpl ../
```
گرامرها با `python3 gen_grammars.py` ساخته می‌شوند (تعداد ستون رنگی = `COLS` بالای فایل).

## ساختار
```
app/src/main/assets/grammars/*.tmLanguage.json   ۴ گرامر
app/src/main/java/ir/hanzodev1375/csv/
  CsvPlugin.java          فعال‌سازی، FILE_EVENT، اعمال گرامر (با sniff جداکننده)
  CsvTextmateHost.java    بارگذاری گرامر + setEditorLanguage (کپی الگوی Astro)
  CsvViewerPanel.java     EditorPanel (getState = DIALOG)
  CsvViewerView.java      خود جدول (ListView + HorizontalScrollView)
  CsvParser.java          پارسر RFC-4180 + تشخیص جداکننده
```

## نکات
- `PluginScreen` استفاده نشد چون `Fragment` در classpath بیلد (`libs/`) نیست؛ پنل `EditorPanel` فقط `View` می‌خواهد.
- حداکثر ۲۰۰٬۰۰۰ ردیف و فایل ۶۴MB (بعدش «truncated»).
- گرامر هم مثل Astro داخل پروسه‌ی ادیتور ثبت می‌شود: بعد از نصب اپ را کامل ببند و باز کن.
