#!/usr/bin/env bash
#
# Run the Python API under coverage.py, run the compatibility test suite against it,
# then generate and open an HTML coverage report.
#
# This script is fully self-contained: it installs coverage into the Python venv if
# missing and generates its own .coveragerc, so the Python project's repo is never
# modified.
#
# Usage: ./scripts/run-python-coverage.sh
#
# Prerequisites:
#   * Python venv set up at ../python/money-to-prisoners-api/venv/
#   * Python project's runtime dependencies installed in the venv
#   * PostgreSQL running and accessible per the API's DB config

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PY_API="$(cd "$REPO_ROOT/../python/money-to-prisoners-api" && pwd)"
PY_VENV="${PY_VENV:-$PY_API/venv}"
PY_BIN="${PYTHON_BIN:-$PY_VENV/bin/python}"
PORT="${PORT:-8000}"

# Both APIs now run against the same Django-shaped `mtp_api` DB — the Kotlin
# Flyway V1 is the Django pg_dump, so the schemas are identical. Use that
# shared DB here so the same fixture data + tokens drive tests against either
# target.
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
COVERAGE_DB="${COVERAGE_DB:-mtp_api}"
DB_CONTAINER="${DB_CONTAINER:-mtp-compatibility-db}"

COVERAGE_DIR="$REPO_ROOT/reports/coverage"
COVERAGE_RC="$COVERAGE_DIR/.coveragerc"
COVERAGE_DATA="$COVERAGE_DIR/.coverage"
COVERAGE_HTML="$COVERAGE_DIR/html"
COVERAGE_LOG="$COVERAGE_DIR/runserver.log"

export DB_NAME="$COVERAGE_DB"
export DB_HOST DB_PORT DB_USERNAME DB_PASSWORD

# Use a custom Django settings module that monkey-patches mtp_common.notify so
# user-creation endpoints don't try to reach GOV.UK Notify (would 500 with a real
# 403 when running locally without a valid Notify API key). Lives next to this
# script so the Python project repo stays untouched.
export PYTHONPATH="$REPO_ROOT/scripts${PYTHONPATH:+:$PYTHONPATH}"
export DJANGO_SETTINGS_MODULE="${DJANGO_SETTINGS_MODULE:-coverage_settings}"
# Notify client still constructs at import time; give it a syntactically-valid
# stub key so the assertions inside notifications_python_client pass before the
# monkey-patched class takes over.
export GOVUK_NOTIFY_API_KEY="${GOVUK_NOTIFY_API_KEY:-stub-00000000-0000-0000-0000-000000000000-00000000-0000-0000-0000-000000000000}"

if [ ! -x "$PY_BIN" ]; then
  echo "ERROR: Python venv not found at $PY_VENV. Run 'python3 -m venv venv' inside $PY_API first."
  exit 1
fi

echo "==> Preparing coverage workspace at $COVERAGE_DIR"
mkdir -p "$COVERAGE_DIR"

echo "==> Ensuring coverage package is installed in venv"
if ! "$PY_BIN" -m pip show coverage >/dev/null 2>&1; then
  "$PY_BIN" -m pip install 'coverage~=7.6.0'
else
  echo "    coverage already installed"
fi

echo "==> Writing dynamic .coveragerc to $COVERAGE_RC"
cat > "$COVERAGE_RC" <<EOF
[run]
source = mtp_api
parallel = True
concurrency = thread
data_file = $COVERAGE_DATA
branch = True
sigterm = True
omit =
    */migrations/*
    */tests/*
    */test_*.py
    */settings/*
    */management/commands/*
    */wsgi.py
    */asgi.py
    */apps.py
    */admin.py
    */urls.py
    */__init__.py

[report]
exclude_lines =
    pragma: no cover
    raise NotImplementedError
    if __name__ == .__main__.:
    def __repr__
    def __str__
    if TYPE_CHECKING:
show_missing = True
skip_covered = False
precision = 1

[html]
directory = $COVERAGE_HTML
title = Money-to-Prisoners API Coverage (integration tests)
EOF

cd "$PY_API"

echo "==> Stopping any running Django runserver processes"
pkill -f "manage.py runserver" 2>/dev/null || true
sleep 2

echo "==> Ensuring dedicated coverage DB '$COVERAGE_DB' exists on $DB_HOST:$DB_PORT"
if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  PSQL="docker exec -e PGPASSWORD=$DB_PASSWORD $DB_CONTAINER psql -U $DB_USERNAME"
else
  PSQL="env PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME"
fi
if ! $PSQL -lqt | cut -d \| -f 1 | grep -qw "$COVERAGE_DB"; then
  echo "    creating database $COVERAGE_DB"
  $PSQL -c "CREATE DATABASE $COVERAGE_DB"
else
  echo "    database $COVERAGE_DB already exists"
fi

echo "==> Running Django migrations against $COVERAGE_DB"
"$PY_BIN" manage.py migrate --noinput

echo "==> Loading test data via manage.py load_test_data (so all tests have fixtures)"
"$PY_BIN" manage.py load_test_data

echo "==> Seeding role-specific tokens used by compatibility tests"
# Map each token name to (username, client_id). Tokens are referenced in the test
# suite via ApiClient.authenticatedAs("test-token-<role>"). The default token used
# by PythonAuthProvider is test-token-admin.
read -r -d '' SEED_TOKENS_SQL <<'SQL' || true
-- Ensure a no-roles user exists for negative auth tests.
INSERT INTO auth_user (password, username, email, first_name, last_name, is_staff, is_superuser, is_active, date_joined)
SELECT '!unusable', 'no-roles-test', 'no-roles-test@mtp.local', '', '', false, false, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM auth_user WHERE username = 'no-roles-test');

INSERT INTO mtp_auth_applicationusermapping (application_id, user_id, created, modified)
SELECT (SELECT id FROM oauth2_provider_application WHERE client_id = 'cashbook'),
       (SELECT id FROM auth_user WHERE username = 'no-roles-test'),
       NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM mtp_auth_applicationusermapping
  WHERE application_id = (SELECT id FROM oauth2_provider_application WHERE client_id = 'cashbook')
    AND user_id = (SELECT id FROM auth_user WHERE username = 'no-roles-test')
);

WITH token_map(token, username, client_id) AS (VALUES
  ('test-token-admin',                   'admin',                   'cashbook'),
  ('test-token-admin-bank-admin',        'admin',                   'bank-admin'),
  ('test-token-admin-noms-ops',          'admin',                   'noms-ops'),
  ('test-token-admin-send-money',        'admin',                   'send-money'),
  ('test-token-bank-admin',              'bank-admin',              'bank-admin'),
  ('test-token-disbursement-admin',      'disbursement-bank-admin', 'bank-admin'),
  ('test-token-fiu',                     'security-fiu-0',          'noms-ops'),
  ('test-token-no-roles',                'no-roles-test',           'cashbook'),
  ('test-token-prison-clerk',            'test-prison-1',           'cashbook'),
  ('test-token-prison-clerk-ua',         'test-prison-1-ua',        'cashbook'),
  ('test-token-prisoner-location-admin', 'prisoner-location-admin', 'noms-ops'),
  ('test-token-security',                'prison-security',         'noms-ops'),
  ('test-token-send-money',              'send-money',              'send-money')
)
INSERT INTO oauth2_provider_accesstoken
  (token, expires, scope, application_id, user_id, created, updated, token_checksum)
SELECT
  tm.token,
  NOW() + INTERVAL '10 years',
  'read write',
  app.id,
  u.id,
  NOW(),
  NOW(),
  encode(sha256(tm.token::bytea), 'hex')
FROM token_map tm
JOIN auth_user u ON u.username = tm.username
JOIN oauth2_provider_application app ON app.client_id = tm.client_id
ON CONFLICT (token_checksum) DO UPDATE
  SET expires = EXCLUDED.expires,
      user_id = EXCLUDED.user_id,
      application_id = EXCLUDED.application_id,
      updated = EXCLUDED.updated;
SQL
$PSQL -d "$COVERAGE_DB" -v ON_ERROR_STOP=1 -c "$SEED_TOKENS_SQL"
$PSQL -d "$COVERAGE_DB" -t -c "SELECT '    seeded ' || count(*) || ' tokens' FROM oauth2_provider_accesstoken WHERE token LIKE 'test-token-%';"

echo "==> Erasing prior coverage data"
"$PY_BIN" -m coverage erase --rcfile="$COVERAGE_RC"

echo "==> Starting Python API under coverage on port $PORT (background)"
"$PY_BIN" -m coverage run --rcfile="$COVERAGE_RC" manage.py runserver "0:${PORT}" --noreload \
  > "$COVERAGE_LOG" 2>&1 &
SERVER_PID=$!
echo "    server PID=$SERVER_PID, logs in $COVERAGE_LOG"

trap 'echo "==> Stopping API (PID=$SERVER_PID)"; kill -INT "$SERVER_PID" 2>/dev/null || true; wait "$SERVER_PID" 2>/dev/null || true' EXIT

echo "==> Waiting for API to be ready on port $PORT"
for i in $(seq 1 60); do
  # Python's /ping.json returns 501 (Not Implemented) but means the app is up.
  # The Kotlin /health/ping returns 200. Accept either.
  status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/ping.json" 2>/dev/null || echo "000")
  if [ "$status" = "200" ] || [ "$status" = "501" ]; then
    echo "    ready after ${i}s (status=$status)"
    break
  fi
  sleep 1
  if [ "$i" = "60" ]; then
    echo "ERROR: API didn't become ready within 60s. Last status=$status. Last 30 lines of log:"
    tail -30 "$COVERAGE_LOG"
    exit 1
  fi
done

echo "==> Running compatibility tests against Python API"
cd "$REPO_ROOT"
API_TARGET=python \
  API_BASE_URL="http://localhost:${PORT}" \
  DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${COVERAGE_DB}" \
  DB_USER="$DB_USERNAME" \
  DB_PASS="$DB_PASSWORD" \
  ./gradlew test --rerun || \
  echo "    (tests had failures — coverage report will still be generated)"

echo "==> Stopping API to flush coverage data"
# Try SIGINT first, then SIGTERM (coverage's sigterm=True flushes on either)
kill -INT "$SERVER_PID" 2>/dev/null || true
for i in $(seq 1 10); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then break; fi
  sleep 1
done
if kill -0 "$SERVER_PID" 2>/dev/null; then
  echo "    SIGINT didn't take, sending SIGTERM"
  kill -TERM "$SERVER_PID" 2>/dev/null || true
  for i in $(seq 1 10); do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then break; fi
    sleep 1
  done
fi
wait "$SERVER_PID" 2>/dev/null || true
trap - EXIT

cd "$PY_API"

echo "==> Combining parallel coverage data files"
"$PY_BIN" -m coverage combine --rcfile="$COVERAGE_RC" || true

echo "==> Coverage summary"
"$PY_BIN" -m coverage report --rcfile="$COVERAGE_RC" || true

echo "==> Generating HTML report"
"$PY_BIN" -m coverage html --rcfile="$COVERAGE_RC"

REPORT="$COVERAGE_HTML/index.html"
echo "==> Report at: $REPORT"
if [ -f "$REPORT" ]; then
  if command -v open >/dev/null 2>&1; then
    open "$REPORT"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$REPORT"
  fi
fi
