#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Node.js + npm (کنسுபط بودن از پلاگین vue یا اینجا نصب می شود)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Perl (برای syntax check و پارس) - هست اگر از قبل نصب شده باشد
if ! command -v perl &>/dev/null; then
  apt install -y perl
fi

# 3. Perl Navigator - سرور LSP مبتنی بر Node (باینری: perlnavigator)
npm install -g --force perlnavigator-server

# 4. Wrapper locale-tolerant
cat > /usr/local/bin/pls <<'WRAPPER'
#!/usr/bin/env bash
export PATH="/root/perl5/bin:/usr/local/perl5/bin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"
export HOME="${HOME:-/root}"

NAV=""
for c in "$HOME/perl5/bin/perlnavigator" /usr/local/bin/perlnavigator /usr/bin/perlnavigator /bin/perlnavigator; do
  [ -x "$c" ] && NAV="$c" && break
done
if [ -z "$NAV" ]; then
  NAV=$(command -v perlnavigator 2>/dev/null || true)
fi
if [ -z "$NAV" ]; then
  echo 'perlnavigator (Perl Navigator) not found - re-run the installer or check `npm root -g`' >&2
  exit 1
fi

exec "$NAV" "$@"
WRAPPER
chmod +x /usr/local/bin/pls

echo 'Perl LSP installed (Perl Navigator).'