package ir.hanzodev1375.javaicons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ir.hanzodev1375.filetreelib.vectormaster.VectorMasterDrawable;

public final class ResIcons {

  private static final String[] EXTS = {".xml", ".png", ".webp", ".jpg", ".jpeg"};
  private static final int MAX_BITMAPS = 160;

  private final Context context;
  private final int size;
  private final ConcurrentMap<String, Bitmap> bitmaps = new ConcurrentHashMap<>();

  private File resDir;
  private List<File> dirs = Collections.emptyList();

  public ResIcons(Context context) {
    this.context = context;
    this.size = Math.max(1, Math.round(24 * context.getResources().getDisplayMetrics().density));
  }

  public synchronized void setResDir(File newRes) {
    if (newRes != null && newRes.equals(resDir)) return;
    resDir = newRes;
    dirs = newRes == null ? Collections.emptyList() : listDrawableDirs(newRes);
  }

  public synchronized int dirCount() {
    return dirs.size();
  }

  private static List<File> listDrawableDirs(File res) {
    List<File> out = new ArrayList<>();
    File[] children = res.listFiles();
    if (children == null) return out;
    for (File c : children) {
      if (!c.isDirectory()) continue;
      String n = c.getName();
      if (n.startsWith("drawable") || n.startsWith("mipmap")) {
        out.add(c);
      }
    }
    return out;
  }

  public synchronized File find(String name) {
    for (File dir : dirs) {
      for (String ext : EXTS) {
        File f = new File(dir, name + ext);
        if (f.isFile()) return f;
      }
    }
    return null;
  }

  public void preload(File f) {
    bitmap(f);
  }

  public Bitmap get(String path) {
    if (path == null) return null;
    Bitmap b = bitmaps.get(path);
    return b != null ? b : bitmap(new File(path));
  }

  public Bitmap bitmap(File f) {
    if (f == null || !f.isFile()) return null;
    String key = f.getAbsolutePath();
    Bitmap b = bitmaps.get(key);
    if (b != null) return b;
    if (bitmaps.size() >= MAX_BITMAPS) return null;
    b = render(f);
    if (b != null) bitmaps.put(key, b);
    return b;
  }

  private Bitmap render(File f) {
    String n = f.getName().toLowerCase(Locale.ROOT);
    try {
      if (n.endsWith(".xml")) {
        VectorMasterDrawable d = new VectorMasterDrawable(context);
        d.setInputStream(new FileInputStream(f));
        d.update();
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(size / (float) w, size / (float) h);
        d.setBounds(0, 0, w, h);
        d.setAlpha(255);
        d.draw(c);
        return bmp;
      }
      if (n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
        return decode(f);
      }
    } catch (Throwable t) {
      return null;
    }
    return null;
  }

  private Bitmap decode(File f) {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    if (o.outWidth <= 0 || o.outHeight <= 0) return null;
    int sample = 1;
    while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2;
    BitmapFactory.Options d = new BitmapFactory.Options();
    d.inSampleSize = sample;
    Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), d);
    if (src == null) return null;
    try {
      float s = Math.min((float) size / src.getWidth(), (float) size / src.getHeight());
      int dw = Math.max(1, Math.round(src.getWidth() * s));
      int dh = Math.max(1, Math.round(src.getHeight() * s));
      Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      new Canvas(out)
          .drawBitmap(
              src,
              new Rect(0, 0, src.getWidth(), src.getHeight()),
              new RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f),
              null);
      return out;
    } finally {
      src.recycle();
    }
  }
}
