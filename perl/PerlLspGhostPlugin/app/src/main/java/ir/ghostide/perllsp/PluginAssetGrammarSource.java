package ir.ghostide.perllsp;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.eclipse.tm4e.core.registry.IGrammarSource;

final class PluginAssetGrammarSource implements IGrammarSource {

  private final Context context;
  private final String assetPath;

  PluginAssetGrammarSource(Context context, String assetPath) {
    this.context = context;
    this.assetPath = assetPath;
  }

  @Override
  public Reader getReader() throws IOException {
    InputStream in = context.getAssets().open(assetPath);
    return new InputStreamReader(in, StandardCharsets.UTF_8);
  }

  @Override
  public String getFilePath() {
    return assetPath;
  }
}