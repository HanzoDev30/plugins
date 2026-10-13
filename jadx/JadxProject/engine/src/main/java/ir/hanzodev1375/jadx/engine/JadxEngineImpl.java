package ir.hanzodev1375.jadx.engine;

import ir.hanzodev1375.jadx.bridge.JadxEngine;
import ir.hanzodev1375.jadx.bridge.JadxOptions;
import ir.hanzodev1375.jadx.bridge.JadxSession;

import java.io.File;

/** Instantiated by reflection from the plugin: keep the public no-arg constructor. */
public final class JadxEngineImpl implements JadxEngine {

  public JadxEngineImpl() {}

  @Override
  public JadxSession open(File dexFile, File outputDir, File workDir, JadxOptions options)
      throws Exception {
    return new JadxSessionImpl(dexFile, outputDir, workDir, options);
  }
}
