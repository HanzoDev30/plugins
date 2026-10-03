# GLSL LSP test project

Open any file here in Ghost IDE with the GLSL LSP plugin installed; the providers claim
`vert`, `frag`, `geom`, `tesc`, `tese`, `comp`, `glsl` and `glsles`.

| File | What it exercises |
| --- | --- |
| `pbr.vert` | vertex stage, `layout(location=…)`, mat4 uniforms, `gl_Position` |
| `pbr.frag` | fragment stage, `#include`, `struct`/`Surface`, Cook-Torrance, `texture()` |
| `particles.comp` | compute stage, SSBOs, atomics-free buffer writes, `gl_GlobalInvocationID` |
| `lines.geom` | geometry stage, `EmitVertex`, `EndPrimitive`, arrayed varyings |
| `hull.tesc` | tessellation control, `gl_TessLevel*` |
| `domain.tese` | tessellation evaluation, `gl_TessCoord` barycentric math |
| `item1.glsl` | neutral suffix (assumed vertex), includes `include/noise.glsl`, `fbm()` call |
| `mobile.glsles` | GLSL ES 3.00, `precision` qualifier, `texture()` |
| `include/common.glsl` | header pulled in by `pbr.frag` — go-to-definition should land here |
| `include/noise.glsl` | header pulled in by `item1.glsl` |

Deliberate breakages to check diagnostics: rename a builtin, drop a `;`, or point an
`#include` at a missing path. `glsl-lsp-debug on` then reopen a file and read
`/tmp/glsl-analyzer.log` if nothing comes back at all.

Manual probe without the editor:

```sh
/opt/glsl-lsp/glsl_analyzer --parse-file pbr.frag
```

The run button does the same thing the FAB does, so a broken shader can be checked from the
terminal:

```sh
cp pbr.frag /tmp/pbr.frag \
  && glslangValidator -E -I. /tmp/pbr.frag > /tmp/pbr.flat.frag \
  && glslangValidator /tmp/pbr.flat.frag
```
