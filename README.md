# API compatibility tests

This repository verifies that the new Kotlin API behaves like the old Python API for the same requests, permissions, database side effects, and important response shapes.

The same test suite can be run against either target:

- **Python**: `../python/money-to-prisoners-api`
- **Kotlin**: `../money-to-prisoners-api`

## Purpose

Use this repo to answer two questions:

1. **What does the old API actually do?**  
   Run the suite against Python to confirm the current behaviour and, when needed, generate Python coverage for the exercised code paths.
2. **Does the Kotlin API match it?**  
   Run the same suite against Kotlin and fix any mismatches until both targets behave the same.

## Expected repository layout

These paths are baked into the compose files and helper script:

```text
prisoner-monies/
├── api-compatibility-tests/
├── money-to-prisoners-api/
└── python/
    └── money-to-prisoners-api/
```

## What the tests depend on

- Java 25
- Docker / Docker Compose
- PostgreSQL via Docker
- For Python baseline runs: a Python venv in `../python/money-to-prisoners-api/venv`

## How the suite works

- Tests run with Gradle and JUnit 5.
- They call a **live API** over HTTP.
- They also query the **shared PostgreSQL database** to verify side effects.
- Both APIs are expected to use the same Django-shaped `mtp_api` schema.
- The suite seeds reusable test tokens in `oauth2_provider_accesstoken` so it can hit secured endpoints without needing a live auth service.

By default the suite targets Kotlin:

```bash
./gradlew test
```

Equivalent defaults:

- `API_TARGET=kotlin`
- `API_BASE_URL=http://localhost:8080`
- `DB_URL=jdbc:postgresql://localhost:5432/mtp_api`
- `DB_USER=postgres`
- `DB_PASS=postgres`

## Quick start: verify Kotlin matches Python

### 1. Start the shared database

From this repository:

```bash
docker compose up -d
```

This starts Postgres on `localhost:5432` in a container called `mtp-compatibility-db`.

### 2. Start the Kotlin API against that database

If you want Docker to run the Kotlin API too:

```bash
docker compose -f docker-compose.yml -f docker-compose.kotlin.yml up --build -d
```

This expects the Kotlin API repo at `../money-to-prisoners-api`.

### 3. Run the compatibility suite against Kotlin

```bash
./gradlew test --rerun
```

If your Kotlin API is already running elsewhere, point the suite at it:

```bash
API_TARGET=kotlin \
API_BASE_URL=http://localhost:8080 \
DB_URL=jdbc:postgresql://localhost:5432/mtp_api \
DB_USER=postgres \
DB_PASS=postgres \
./gradlew test --rerun
```

## Verify the Python baseline

There are two useful ways to run against Python.

### Option A: run Python manually with Docker Compose

```bash
docker compose up -d
docker compose -f docker-compose.yml -f docker-compose.python.yml up --build -d

API_TARGET=python ./gradlew test --rerun
```

### Option B: use the coverage helper script

This is the most useful baseline workflow because it also shows which Python code paths are being exercised:

```bash
./scripts/run-python-coverage.sh
```

That script:

- uses `../python/money-to-prisoners-api/venv`
- prepares the shared `mtp_api` database
- runs Django migrations
- loads Python test data
- seeds the test tokens used by this suite
- starts the Python API under `coverage.py`
- runs this compatibility suite with `API_TARGET=python`
- writes an HTML coverage report to `reports/coverage/html`

## Recommended workflow when porting an endpoint

1. Run the relevant tag against **Python** to confirm the current behaviour.
2. Run the same tag against **Kotlin**.
3. Fix the Kotlin API until the same tests pass.
4. Re-run the focused tag.
5. Re-run the full suite before considering the work complete.

## Running a focused area

The suite uses JUnit tags. Set `TEST_TAGS` to run one area at a time:

```bash
TEST_TAGS=credits ./gradlew test --rerun
TEST_TAGS=private-estate-batch-lifecycle ./gradlew test --rerun
TEST_TAGS=account-request-lifecycle ./gradlew test --rerun
TEST_TAGS=account-request-create-validation ./gradlew test --rerun
```

To discover tags:

```bash
rg '@Tag\("' src/test/kotlin
```

## Useful environment variables

| Variable | Default | Meaning |
| --- | --- | --- |
| `API_TARGET` | `kotlin` | Which API the suite is targeting: `kotlin` or `python` |
| `API_BASE_URL` | `http://localhost:8080` | Base URL for the target API |
| `DB_URL` | `jdbc:postgresql://localhost:5432/mtp_api` | JDBC URL used for DB assertions and fixture setup |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | `postgres` | Database password |
| `TEST_TAGS` | unset | JUnit tag filter |
| `HMPPS_AUTH_URL` | `http://localhost:8090/auth` | Present for compatibility with older auth flows; most current tests use seeded DB tokens |

## What counts as compatibility

These tests are not just smoke tests. Depending on the endpoint, they check:

- HTTP status codes
- auth and permission behaviour
- pagination and filtering
- request validation
- create/update/delete side effects in PostgreSQL
- role and prison mapping side effects
- selected response body fields and shapes

When a Kotlin mismatch is already known, some tests may temporarily record that as an explicit divergence. The goal is still to remove those divergences and make Kotlin match Python exactly.

## Troubleshooting

### The suite cannot reach the API

Check:

- the API is running
- `API_BASE_URL` is correct
- the target API is using the same database as the suite

### Tests fail because of missing data or auth

Make sure you are using the shared `mtp_api` database and not a different local DB. The suite relies on seeded Django-style data and access tokens.

### Python coverage script says the venv is missing

Create it inside the Python repo and install the Python app dependencies there:

```bash
cd ../python/money-to-prisoners-api
python3 -m venv venv
```

### Start clean

To reset the compatibility database:

```bash
docker compose down -v
docker compose up -d
```

## Main files

- `build.gradle.kts` - Gradle test configuration and environment variables
- `docker-compose.yml` - shared Postgres
- `docker-compose.kotlin.yml` - Kotlin target container
- `docker-compose.python.yml` - Python target container
- `scripts/run-python-coverage.sh` - Python baseline + coverage helper
