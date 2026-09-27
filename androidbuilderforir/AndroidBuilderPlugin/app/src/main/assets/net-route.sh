#!/usr/bin/env bash
# AndroidBuilder for ir - shared network probe: can the direct source be reached from here?
#
# The plugin is meant to work everywhere, so no upstream is hard wired to a mirror. Before an install
# step reaches for a host, this asks the host itself:
#
#   ab_route <url> [expect-regex]   -> "direct" | "blocked <code>"
#   ab_route_pick <url>...          -> the first url that answers directly, or nothing
#
# "direct" means the host answered 200/206 *with the content that was asked for*. Everything else
# counts as blocked: 403, 404, no route at all. Both matter, because a sanctioned network does not
# refuse the connection politely - dl.google.com answers 404 with a page that is not the file, and
# other links answer 403. Checking the body as well keeps a plain "this file does not exist" (GitHub
# answers 404 for an unknown repository) from being mistaken for a blocked network.

# shellcheck shell=bash

ab_route() {
  local url="$1" expect="${2:-}" code body
  body="$(mktemp "${TMPDIR:-/tmp}/androidbuilder-route.XXXXXX" 2>/dev/null)" || body=""
  if [ -z "$body" ]; then
    # No mktemp: fall back to a status only check, which is slightly less strict but never wrong in
    # the direction that matters (a blocked host is never reported as direct).
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -L "$url" 2>/dev/null || printf '000')"
    case "$code" in
      200 | 206) printf 'direct\n' ;;
      *) printf 'blocked %s\n' "$code" ;;
    esac
    return 0
  fi

  code="$(curl -s -o "$body" -w '%{http_code}' --max-time 25 -L "$url" 2>/dev/null || printf '000')"
  case "$code" in
    200 | 206) ;;
    *)
      rm -f "$body" 2>/dev/null || true
      printf 'blocked %s\n' "$code"
      return 0
      ;;
  esac

  if [ -n "$expect" ] && ! grep -q "$expect" "$body" 2>/dev/null; then
    rm -f "$body" 2>/dev/null || true
    printf 'blocked %s (unexpected content)\n' "$code"
    return 0
  fi

  rm -f "$body" 2>/dev/null || true
  printf 'direct\n'
}

# The first candidate that answers directly, so a list of mirrors can be tried in order instead of
# trusting one that may itself be unreachable.
ab_route_pick() {
  local url code
  for url in "$@"; do
    code="$(ab_route "$url")"
    case "$code" in
      direct) printf '%s\n' "$url"; return 0 ;;
    esac
  done
  return 1
}
