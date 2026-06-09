#!/usr/bin/env bash
# Smoke test: verifies all SynchPlay API endpoints work end-to-end.
# Requires backend running on :8080 (./dev.sh or mvn spring-boot:run).
set -e

BASE="http://localhost:8080"
PASS=0
FAIL=0

ok()   { echo "  ✅  $1"; ((PASS++)); }
fail() { echo "  ❌  $1"; ((FAIL++)); }

check_status() {
  local label="$1" expected="$2" actual="$3"
  [ "$actual" = "$expected" ] && ok "$label (HTTP $actual)" || fail "$label — expected $expected, got $actual"
}

echo ""
echo "=== SynchPlay API Smoke Test ==="
echo ""

# ── Health ──────────────────────────────────────────────────────────────────
echo "── Health ──"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/health")
check_status "GET /api/health" "200" "$STATUS"

# ── Auth: register new test user ─────────────────────────────────────────────
echo ""
echo "── Auth ──"
RANDOM_USER="smoketest_$(date +%s)"
BODY=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"email\":\"$RANDOM_USER@test.com\",\"password\":\"Test1234!\"}")
HTTP=$(echo "$BODY" | tail -1)
check_status "POST /api/auth/register" "200" "$HTTP"

# ── Auth: duplicate register → 409 ───────────────────────────────────────────
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"email\":\"$RANDOM_USER@test.com\",\"password\":\"Test1234!\"}")
check_status "POST /api/auth/register (duplicate → 409)" "409" "$STATUS"

# ── Auth: login ───────────────────────────────────────────────────────────────
LOGIN_RESP=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"Test1234!\"}")
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  ok "POST /api/auth/login — token received"
  ((PASS++))
else
  fail "POST /api/auth/login — no token in response"
  echo "    Response: $LOGIN_RESP"
  ((FAIL++))
fi

# ── Auth: bad password → 401 ──────────────────────────────────────────────────
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"wrong\"}")
check_status "POST /api/auth/login (bad pw → 401)" "401" "$STATUS"

# ── Auth: /me ─────────────────────────────────────────────────────────────────
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/auth/me" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/auth/me" "200" "$STATUS"

# ── Auth: missing token → 401 ────────────────────────────────────────────────
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stats")
check_status "GET /api/stats (no token → 401)" "401" "$STATUS"

# ── Business endpoints (authenticated) ───────────────────────────────────────
echo ""
echo "── Business endpoints ──"
AUTH="-H \"Authorization: Bearer $TOKEN\""

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/stats" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/stats" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/recommend?alpha=0.5&beta=0.3&gamma=0.2&prMode=full&mode=foryou&top=10" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/recommend (foryou)" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/recommend?mode=explore&top=10" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/recommend (explore)" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/friends?top=10" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/friends" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/lcc" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/lcc" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/pagerank?prMode=full&top=15" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/pagerank (full)" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/pagerank?prMode=watch&top=15" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/pagerank (watch)" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/watch-history?limit=20" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/watch-history" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/videos/mine" \
  -H "Authorization: Bearer $TOKEN")
check_status "GET /api/videos/mine" "200" "$STATUS"

# ── Demo accounts ─────────────────────────────────────────────────────────────
echo ""
echo "── Demo accounts ──"
for u in demo1 demo2 demo3; do
  RESP=$(curl -s -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$u\",\"password\":\"demo123\"}")
  T=$(echo "$RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  [ -n "$T" ] && ok "Login as $u" || fail "Login as $u failed"
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
