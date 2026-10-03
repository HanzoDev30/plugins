package ir.ghostide.glsllsp;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * GLSL code runner implementing {@link CodeRunnerHost}.
 *
 * <p>A shader cannot be executed on its own - it is compiled. Running a shader therefore means
 * building it, and the runner picks the best compiler the rootfs happens to have:
 *
 * <ul>
 *   <li>{@code glslangValidator} present &rarr; a real SPIR-V build with {@code -V}, which is the
 *       only path that reports type and linkage errors the analyzer does not model
 *   <li>otherwise the {@code glsl_analyzer} this plugin already installs &rarr; {@code --parse-file},
 *       which needs nothing extra and still reports every parse and semantic diagnostic
 *   <li>neither present &rarr; the host's own runner, so the button is never a dead end
 * </ul>
 *
 * <p>For every other file type delegates to the host's default runner.
 *
 * @author Ghost
 */
public final class GlslRunner implements CodeRunnerHost {

  private static final Set<String> EXTENSIONS =
      Set.of("glsl", "glsles", "vert", "frag", "geom", "comp", "tesc", "tese");

  private static final String GLSLANG = "/usr/bin/glslangValidator";
  private static final String ANALYZER = "/usr/local/bin/glsl_analyzer";
  private static final String OUTPUT_DIR = "/root/glsl-output";

  private static final Set<String> STAGE_EXTENSIONS =
      Set.of("vert", "frag", "geom", "comp", "tesc", "tese");

  /**
   * The stage a neutral extension is assumed to be. glsl_analyzer makes the same assumption, so the
   * runner and the language server never disagree about what such a file is.
   */
  private static final String NEUTRAL_STAGE = "vert";

  private final PluginContext context;
  private final ProotProcessLauncher launcher;

  public GlslRunner(PluginContext context, ProotProcessLauncher launcher) {
    this.context = context;
    this.launcher = launcher;
  }

  @Override
  public void runShell(String command, boolean asBottomSheet) {
    host().runShell(command, asBottomSheet);
  }

  @Override
  public void runCurrentFile(boolean asBottomSheet) {
    host().runCurrentFile(asBottomSheet);
  }

  @Override
  public void runFile(String filePath, boolean asBottomSheet) {
    if (!isSupported(filePath)) {
      host().runFile(filePath, asBottomSheet);
      return;
    }
    host().runShell(buildCommand(filePath), asBottomSheet);
  }

  @Override
  public boolean isSupported(String filePath) {
    if (filePath == null) {
      return false;
    }
    return EXTENSIONS.contains(extension(filePath));
  }

  @Override
  public ExecResult exec(String command, Consumer<String> onOutputLine) {
    return host().exec(command, onOutputLine);
  }

  private String buildCommand(String filePath) {
    if (launcher.isInstalled(GLSLANG)) {
      return glslangCommand(filePath);
    }
    if (launcher.isInstalled(ANALYZER)) {
      return ANALYZER
          + " --parse-file "
          + shellEscape(filePath)
          + " && echo ':: no diagnostics - the shader parses clean'";
    }
    return "echo 'glsl: no shader compiler found. Install glslangValidator (apt-get install -y"
        + " glslang-tools) or run the \"Install GLSL LSP\" setup action for the analyzer.'";
  }

  /**
   * A full GLSL semantic check, which is what actually running a shader means: it catches the type,
   * linkage and entry-point errors a parse-only pass cannot see. SPIR-V generation is left out on
   * purpose - {@code -V} switches the compiler to Vulkan semantics, where every non-opaque uniform
   * has to live in a block with an explicit binding, so it would reject perfectly valid OpenGL
   * shaders. Validate here, build SPIR-V in the terminal when a Vulkan pipeline needs it.
   *
   * <p>Two shapes of problem have to be worked around, both a consequence of glslangValidator taking
   * the shader stage from the file extension and of its include handling being limited to
   * preprocessing:
   *
   * <ul>
   *   <li>a neutral {@code .glsl}/{@code .glsles} file is rejected outright, so it is validated
   *       through a copy that carries a stage extension - the same stage the language server assumes
   *   <li>{@code #include} only resolves in preprocessing mode, so the source is flattened with
   *       {@code -E} first and the result is what gets validated. The {@code #line} directives that
   *       expansion leaves behind keep every error pointing at the file it really came from
   * </ul>
   */
  private static String glslangCommand(String filePath) {
    String extension = extension(filePath);
    String stage = STAGE_EXTENSIONS.contains(extension) ? extension : NEUTRAL_STAGE;
    String stem = outputStem(filePath);
    String staged = OUTPUT_DIR + "/" + stem + "." + stage;
    String flattened = OUTPUT_DIR + "/" + stem + ".flat." + stage;

    return "mkdir -p "
        + OUTPUT_DIR
        + " && cp "
        + shellEscape(filePath)
        + " "
        + staged
        + " && glslangValidator -E -I"
        + shellEscape(parentDirectory(filePath))
        + " "
        + staged
        + " > "
        + flattened
        + " && glslangValidator "
        + flattened
        + " && echo ':: validated as "
        + stage
        + " - no GLSL errors'";
  }

  private static String parentDirectory(String filePath) {
    int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    return slash > 0 ? filePath.substring(0, slash) : ".";
  }

  private static String outputStem(String filePath) {
    int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    return stem.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private CodeRunnerHost host() {
    return context.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
  }

  private static String extension(String filePath) {
    int dot = filePath.lastIndexOf('.');
    if (dot < 0 || dot == filePath.length() - 1) {
      return "";
    }
    return filePath.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String shellEscape(String path) {
    return "\"" + path.replace("\"", "\\\"") + "\"";
  }
}
