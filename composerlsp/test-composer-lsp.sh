#!/usr/bin/env bash
# Personal smoke test for the composerlsp plugin install.
# Usage: bash test-composer-lsp.sh [path/to/composer-json-language-server]
set -u

SERVER="${1:-/usr/local/bin/composer-json-language-server}"
FAILURES=0

pass() { printf '  \033[32mok\033[0m   %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

echo "== binaries =="
for b in php composer node npm; do
  if command -v "$b" >/dev/null 2>&1; then
    pass "$b -> $(command -v "$b")"
  else
    fail "$b not found"
  fi
done
composer --version 2>/dev/null | head -1 | sed 's/^/       /'

if [ -x "$SERVER" ]; then
  pass "wrapper -> $SERVER"
else
  fail "wrapper not executable: $SERVER"
  echo
  echo "result: $FAILURES failure(s)"
  exit 1
fi

echo
echo "== lsp session =="
SERVER="$SERVER" python3 - <<'PY'
import json, os, subprocess, sys, time

SERVER = os.environ["SERVER"]
fails = []


def ok(msg):
    print("  \033[32mok\033[0m   " + msg)


def bad(msg):
    print("  \033[31mFAIL\033[0m " + msg)
    fails.append(msg)


class Client:
    def __init__(self, argv):
        self.p = subprocess.Popen(
            argv,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def send(self, msg):
        body = json.dumps(msg).encode()
        self.p.stdin.write(b"Content-Length: %d\r\n\r\n" % len(body) + body)
        self.p.stdin.flush()

    def read(self, timeout=20):
        header = b""
        deadline = time.time() + timeout
        while b"\r\n\r\n" not in header:
            if time.time() > deadline:
                return None
            ch = self.p.stdout.read(1)
            if not ch:
                return None
            header += ch
        length = 0
        for line in header.decode("ascii", "replace").split("\r\n"):
            if line.lower().startswith("content-length"):
                length = int(line.split(":")[1])
        body = b""
        while len(body) < length:
            chunk = self.p.stdout.read(length - len(body))
            if not chunk:
                return None
            body += chunk
        return json.loads(body)

    def await_id(self, want, timeout=20):
        deadline = time.time() + timeout
        while time.time() < deadline:
            msg = self.read(timeout=max(0.5, deadline - time.time()))
            if msg is None:
                return None
            if msg.get("id") == want:
                return msg
        return None

    def close(self):
        self.p.kill()
        err = self.p.stderr.read().decode("utf-8", "replace").strip()
        if err:
            print("       stderr: " + err.replace("\n", " ")[:300])


c = Client([SERVER])
c.send({
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {
        "processId": None,
        "rootUri": "file:///tmp",
        "capabilities": {"textDocument": {"publishDiagnostics": {}, "hover": {}}},
    },
})
init = c.await_id(1)
if init is None or "result" not in init:
    bad("initialize: no response within 20s")
    c.close()
    sys.exit(1)
caps = init["result"].get("capabilities", {})
ok("initialize responded")
for cap in ("hoverProvider", "documentSymbolProvider", "foldingRangeProvider"):
    if caps.get(cap):
        ok(f"capability {cap}")
    else:
        bad(f"capability {cap} missing")
c.send({"jsonrpc": "2.0", "method": "initialized", "params": {}})

URI = "file:///tmp/proj/composer.json"
DOC = '{\n    "name": "acme/demo",\n    "require": {\n        "php": "^8.2"\n    }\n}\n'
c.send({"jsonrpc": "2.0", "method": "textDocument/didOpen", "params": {"textDocument": {
    "uri": URI, "languageId": "json", "version": 1, "text": DOC}}})

# hover over a known key
c.send({"jsonrpc": "2.0", "id": 2, "method": "textDocument/hover",
        "params": {"textDocument": {"uri": URI}, "position": {"line": 1, "character": 6}}})
hover = c.await_id(2)
if hover and hover.get("result"):
    contents = hover["result"].get("contents")
    text = contents.get("value", "") if isinstance(contents, dict) else str(contents)
    ok("hover on \"name\" -> " + text.split("\n")[0][:70])
else:
    # property hover comes from the schema, so it is expected to be empty
    # until a client registers the composer schema.
    print("  \033[33mnote\033[0m hover on \"name\" is empty (schema dependent, not a failure)")

# generic JSON intelligence still works (brace matching / word completion)
c.send({"jsonrpc": "2.0", "id": 3, "method": "textDocument/completion",
        "params": {"textDocument": {"uri": URI}, "position": {"line": 3, "character": 8}}})
comp = c.await_id(3)
labels = []
if comp and comp.get("result"):
    res = comp["result"]
    items = res if isinstance(res, list) else res.get("items", [])
    labels = [i.get("label") for i in items]
    ok(f"completion inside require -> {len(labels)} item(s): {', '.join(map(str, labels[:8])) or '-'}")
else:
    bad("completion request failed")

# symbols
c.send({"jsonrpc": "2.0", "id": 4, "method": "textDocument/documentSymbol",
        "params": {"textDocument": {"uri": URI}}})
syms = c.await_id(4)
if syms and syms.get("result"):
    names = [s.get("name") for s in syms["result"] if isinstance(s, dict)]
    ok(f"documentSymbol -> {names}")
else:
    bad("documentSymbol returned nothing")

# diagnostics: valid file first
c.send({"jsonrpc": "2.0", "method": "textDocument/didOpen", "params": {"textDocument": {
    "uri": "file:///tmp/proj/ok.json", "languageId": "json", "version": 1,
    "text": '{\n    "name": "a/b",\n    "description": "x"\n}\n'}}})
seen_ok, seen_bad = False, False
BAD_URI = "file:///tmp/proj/broken.json"
c.send({"jsonrpc": "2.0", "method": "textDocument/didOpen", "params": {"textDocument": {
    "uri": BAD_URI, "languageId": "json", "version": 1,
    "text": '{\n    "name": "a/b",\n    "require": {\n        "php": "not a version"\n    },\n}\n'}}})
deadline = time.time() + 10
while time.time() < deadline and not (seen_ok and seen_bad):
    msg = c.read(timeout=max(0.5, deadline - time.time()))
    if msg is None or msg.get("method") != "textDocument/publishDiagnostics":
        continue
    uri = msg["params"]["uri"]
    if uri.endswith("ok.json"):
        seen_ok = True
        if msg["params"]["diagnostics"]:
            bad(f"valid file reported {len(msg['params']['diagnostics'])} diagnostic(s)")
        else:
            ok("valid file -> no diagnostics")
    elif uri == BAD_URI:
        seen_bad = True
        diags = msg["params"]["diagnostics"]
        if diags:
            ok(f"broken file -> {len(diags)} diagnostic(s), first: "
               + diags[0].get("message", "").replace("\n", " ")[:60])
        else:
            bad("broken file produced no diagnostics")
if not seen_ok:
    bad("never got diagnostics for the valid file")
if not seen_bad:
    bad("never got diagnostics for the broken file")

c.close()

schema_keys = {"license", "autoload-dev", "minimum-stability", "require-dev", "prefer-stable"}
if schema_keys & set(labels):
    ok("composer schema is active")
else:
    print("  \033[33mnote\033[0m no composer schema keys in completion "
          "(the raw json server ships none; the Market Plus extension "
          "devsense.composer-php-vscode supplies it)")

sys.exit(1 if fails else 0)
PY
LSP_RC=$?

echo
if [ "$FAILURES" -eq 0 ] && [ "$LSP_RC" -eq 0 ]; then
  printf '\033[32mall checks passed\033[0m\n'
  exit 0
fi
printf '\033[31m%d shell failure(s), lsp exit %d\033[0m\n' "$FAILURES" "$LSP_RC"
exit 1
