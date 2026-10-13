package ir.hanzodev1375.jadx;

import ir.hanzodev1375.ghostide.plugin.api.PluginSetting;
import ir.hanzodev1375.ghostide.plugin.api.PluginSettingsContributor;

import java.util.ArrayList;
import java.util.List;

/** Settings rows shown in the host's settings screen. */
final class JadxSettingsContributor implements PluginSettingsContributor {

  private final JadxSettings settings;

  JadxSettingsContributor(JadxSettings settings) {
    this.settings = settings;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.jadx.settings";
  }

  @Override
  public String getDisplayName() {
    return "JADX Decompiler";
  }

  @Override
  public List<PluginSetting> getSettings() {
    List<PluginSetting> rows = new ArrayList<>();
    rows.add(
        PluginSetting.toggle(
            JadxSettings.KEY_TAP_DEX,
            L.t("Open .dex on tap", "باز کردن .dex با یک لمس"),
            L.t(
                "Tapping a .dex file in the file manager opens its class list",
                "لمس فایل .dex در فایل‌منیجر، لیست کلاس‌های آن را باز می‌کند"),
            settings.isTapDex(),
            settings::setTapDex));
    rows.add(
        PluginSetting.toggle(
            JadxSettings.KEY_DEOBF,
            L.t("Deobfuscation", "دی‌آبفیوسکیشن"),
            L.t(
                "Give readable names to obfuscated classes, methods and fields",
                "اسم‌های خوانا برای کلاس/متد/فیلدهای مبهم‌شده"),
            settings.isDeobf(),
            settings::setDeobf));
    rows.add(
        PluginSetting.toggle(
            JadxSettings.KEY_BAD_CODE,
            L.t("Show inconsistent code", "نمایش کد ناقص"),
            L.t(
                "Keep code jadx could not restore cleanly instead of dropping it",
                "کدی که jadx نتوانسته کامل بازسازی کند حذف نشود"),
            settings.isBadCode(),
            settings::setBadCode));
    rows.add(
        PluginSetting.toggle(
            JadxSettings.KEY_FALLBACK,
            L.t("Fallback mode", "حالت Fallback"),
            L.t(
                "Raw bytecode-like output. Fast and never fails, but hard to read",
                "خروجی خام شبیه بایت‌کد؛ سریع و بدون خطا ولی سخت‌خوان"),
            settings.isFallback(),
            settings::setFallback));
    return rows;
  }
}
