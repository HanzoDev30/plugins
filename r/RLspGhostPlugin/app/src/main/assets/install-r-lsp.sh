#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. Install R
if ! command -v R &>/dev/null; then
  apt update && apt install -y r-base r-base-dev
fi

# 1. Install the languageserver R package
Rscript -e 'if (!requireNamespace("languageserver", quietly = TRUE)) install.packages("languageserver", repos = "https://cloud.r-project.org")'

# 2. Locate the r-languageserver binary inside the installed package
RBIN=$(Rscript -e 'cat(system.file("bin", "r-languageserver", package = "languageserver"))' 2>/dev/null || true)

# 3. Wrapper launcher
cat > /usr/local/bin/r-languageserver <<WRAPPER
#!/usr/bin/env bash
RLS="$RBIN"
if [ -z "\$RLS" ] || [ ! -x "\$RLS" ]; then
  RLS=\$(command -v r-languageserver 2>/dev/null || true)
fi
for c in /usr/bin/r-languageserver /usr/local/bin/r-languageserver /usr/lib/R/site-library/languageserver/bin/r-languageserver; do
  [ -x "\$c" ] && RLS="\$c" && break
done
if [ -z "\$RLS" ]; then
  echo 'r-languageserver: not found' >&2
  exit 1
fi
exec "\$RLS"
WRAPPER
chmod +x /usr/local/bin/r-languageserver

echo 'R LSP installed.'