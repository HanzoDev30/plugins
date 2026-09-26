# Pip Installer — دو کرش پنل

## کرش ۲ (فعلی): `Fragment ... must be a public static class`

```
Exception: java.lang.IllegalStateException:
  Fragment ir.hanzodev1375.ghostide.plugin.PluginPanelHost.PanelBottomSheetFragment
  must be a public static class to be properly recreated from instance state.

  at androidx.fragment.app.FragmentTransaction.doAddOp(FragmentTransaction.java:306)
  at androidx.fragment.app.DialogFragment.show(DialogFragment.java:506)
  at PluginPanelHost.showBottomSheetFragment(PluginPanelHost.java:227)
  at PluginPanelHost.showPanel(PluginPanelHost.java:168)
```

علت: host در حالت `BOTTOMSHERTFRAGMENT` یک fragment داخلی `private static` می‌سازد و
`DialogFragment.show()` صدا می‌زند. androidx در `doAddOp` کلاس fragment را چک می‌کند و چون
public نیست، قبل از ساختن هیچ UI‌ای `IllegalStateException` می‌دهد ⇒ کرش با **هر بار** باز کردن پنل.
این باگ سمت اپ است (`PanelBottomSheetFragment` در PluginPanelHost.java:368 باید `public static`
باشد) و از سمت پلاگین قابل رفع نبود، پس مسیر fragment کلاً حذف شد.

## راه‌حل (فقط سمت پلاگین)

`PipPanel`: حالت نمایش روی `BOTTOMSHEETDIALOG` قفل شد — همان `BaseSheet` (گلس + بلور +
`STATE_EXPANDED` + `skipCollapsed`) ولی به‌صورت `Dialog` ساده، نه fragment.

```java
PipPanel(PipPanelView view) {
  this.view = view;
  setState(PluginStateMod.BOTTOMSHEETDIALOG);
}

@Override
public PluginStateMod getState() {          // override، نه فقط setState
  return PluginStateMod.BOTTOMSHEETDIALOG;
}
```

`getState()` عمداً override شد تا اگر `EditorPanelStateStore` مقدار کهنه‌ای داشت، مسیر fragment
برنگردد. نتیجه: هیچ‌کدام از `DIALOGFRAGMENT` / `FRAGMENT` / `BOTTOMSHERTFRAGMENT` استفاده نمی‌شوند
(هر سه با همین باگ private-fragment می‌میرند).

| مقدار | وضعیت |
|---|---|
| `BOTTOMSHEETDIALOG` | ✅ استفاده می‌شود (BaseSheet، گلس، بدون fragment) |
| `BOTTOMSHERTFRAGMENT` | ❌ کرش قطعی |
| `DIALOGFRAGMENT` / `FRAGMENT` | ❌ همان باگ |

## کرش ۱ (قبلی): `The specified child already has a parent`

host کش view را per-host نگه می‌دارد ولی `PipPanelView` یک سینگلتون سراسری است. با هر Activity
تازه، `createView()` همان view قدیمی را برمی‌گرداند که هنوز parent دارد و `buildWrapper` می‌ترکد.
قاعده: **هر view برگشتی از `createView()` باید بدون parent باشد.**

```java
@Override
public View createView() {
  View root = view.getRoot();
  ViewParent parent = root.getParent();
  if (parent instanceof ViewGroup) {
    ((ViewGroup) parent).removeView(root);   // همیشه detached تحویل بده
  }
  root.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
  return root;
}
```

## بیلد

```bash
cd /storage/emulated/0/apk/ghostideplugins-main/pipui/PipUiGhostPlugin
bash build.sh          # -> PipUiGhostPlugin/pipui.gpl
cp pipui.gpl ../pipui.gpl
```

خروجی: `pipui.gpl` — javac `--release 17` + d8 (min-api 26)؛ بدون gradle.

## گزارش سمت اپ (دست‌نخورده، فقط برای اطلاع)

`app/src/main/java/ir/hanzodev1375/ghostide/plugin/PluginPanelHost.java:368`

```java
public static final class PanelBottomSheetFragment extends BaseBlurBottomSheet { ... }
```

`PanelDialogFragment` (خط 350) و `PanelHostFragment` (خط 332) هم `private` هستند و همان کرش را
می‌دهند. با public شدن هر سه، حالت‌های fragment برای همهٔ پلاگین‌ها برمی‌گردند.
