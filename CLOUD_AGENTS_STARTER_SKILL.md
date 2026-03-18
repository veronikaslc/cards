# Cloud Agents Starter Skill (CARDS)

Use this as the first-run playbook for Cloud agents working in this repository.

## 0) Fast setup and first login (do this first)

1. Ensure required tools exist:
   - `java -version` (Java 21)
   - `mvn -version` (Maven 3.8+)
   - `python3 --version`
2. Ensure `python` exists (the scripts expect it in some environments):
   - `python --version` (if missing, create a `python -> python3` symlink in your environment image)
3. Ensure `~/.mailcap` exists:
   - If missing, copy from `distribution/mailcap`
4. Start app in developer + test mode:
   - `./start_cards.sh --dev --test`
5. Wait for boot health:
   - `curl --fail http://localhost:8080/system/sling/info.sessionInfo.json`
6. Login:
   - Open `http://localhost:8080`
   - Default credentials: `admin` / `admin`
   - Dev JCR browser: `http://localhost:8080/bin/browser.html`

## 1) Common feature toggles and environment mocks

Use startup flags as your primary "feature flags":

- `--test` enables test questionnaires + test modules.
- `--dev` enables Composum browser.
- `--demo` enables demo banner/demo forms.
- `--locking` enables sign-off/locking.
- `--clarity` adds Clarity integration feature.
- `--saml` enables SAML support.
- `--permissions open|trusted|ownership` switches permission mode.

Useful run examples:

- Baseline local workflow: `./start_cards.sh --dev --test`
- Locking flow validation: `./start_cards.sh --dev --test --locking`
- Demo UI checks: `./start_cards.sh --dev --test --demo`

Environment toggles commonly used in cloud runs:

- `COMPUTED_ANSWERS_DISABLED=true` (disable computed answers path)
- `BIOPORTAL_APIKEY=<key>` (optional; app still starts without it)
- `GOOGLE_APIKEY=<key>` (optional; app still starts without it)

Practical mock strategy:

- For day-to-day backend/frontend work, leave external integration keys unset unless your task is specifically about those integrations.
- Validate that the app starts and core flows work without those keys; this avoids flaky external dependency coupling.

## 2) By codebase area: edit + run + test workflow

### A) Backend Java / OSGi bundles (`modules/*` Java code)

When to use: service logic, Sling endpoints, processors, backend module changes.

Workflow:

1. Build backend-focused changes quickly:
   - `mvn install -Pskip-webpack`
2. Run unit tests (enabled profile + targeted modules):
   - `mvn test -Ptests -pl modules/utils,modules/healthcheck,modules/vocabularies -Denforcer.skip=true -Dcheckstyle.skip=true -Drat.skip=true`
3. If app is already running, hot deploy:
   - `mvn install -PautoInstallBundle`
4. Smoke verify runtime:
   - `curl --fail http://localhost:8080/system/sling/info.sessionInfo.json`
5. Exercise changed endpoint/behavior with `curl` or browser workflow tied to your ticket.

### B) Frontend React code (source in `modules/*`, not `aggregated-frontend/`)

When to use: UI components, client logic, styling.

Critical rule:

- Do not edit `aggregated-frontend/` directly; it is generated.

Workflow:

1. Edit frontend source under the relevant module in `modules/`.
2. Rebuild frontend bundle:
   - `mvn install -Pquick`
3. Optional lint check:
   - `cd aggregated-frontend/src/main/frontend && ./node_modules/.bin/eslint --max-warnings 0 src/`
4. Run app and manually verify UI:
   - Start with `./start_cards.sh --dev --test`
   - Login as `admin/admin`
   - Navigate to the changed page and validate behavior + regressions.

### C) Integration and environment-sensitive flows (`tests/`, feature modules)

When to use: cross-module behavior, startup composition, integration profiles.

Workflow:

1. Start app with the exact feature composition your issue needs (flags above).
2. Run integration suite when required:
   - `mvn install -PintegrationTests`
3. Verify auth + session health:
   - Browser login (`admin/admin`)
   - `curl --fail http://localhost:8080/system/sling/info.sessionInfo.json`
4. If your task depends on external systems (SAML, Clarity, BioPortal, Google APIs), explicitly document whether you used real credentials, stubs, or skipped external calls.

## 3) Quick troubleshooting checklist

- Port busy: restart with `-p <PORT>`.
- Startup fails before bind checks: verify Python + `psutil`.
- Email-enabled feature complains: ensure `~/.mailcap` exists.
- Vocabulary/google warnings at startup: expected when API keys are unset.
- Mongo/permissions behavior differs from local default: check startup flags/environment values.

## 4) How to update this skill (keep it useful)

When you discover a new reliable runbook step, update this file in the same PR that proved it.

Update rules:

1. Add new knowledge under the relevant codebase area (A/B/C), not as random notes.
2. Keep each new tip command-first (exact command + expected signal).
3. Prefer "works in Cloud agent" steps over local-machine-only guidance.
4. If a step needs secrets or external services, mark it clearly as optional and add a no-secret fallback.
5. Remove stale or superseded steps as soon as better workflows are validated.
