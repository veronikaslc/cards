# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

CARDS (Clinical ARchive for Data Science) is a medical data collection platform built on Apache Sling with a React frontend. It uses a JCR repository (Apache Jackrabbit Oak) for data storage and is packaged as ~50 OSGi bundles.

### Prerequisites

- **Java 21**, **Maven 3.8+**, **Python 3** (with `psutil` module), and a `python` symlink to `python3`
- The `~/.mailcap` file must exist (copy from `distribution/mailcap`) or `start_cards.sh` will refuse to start when email features are enabled

### Build

- `mvn clean install -Pquick` — full build skipping tests, license checks, and RAT checks
- `mvn install -Pskip-webpack` — skip frontend rebuild (useful for backend-only changes)
- `mvn install -PautoInstallBundle` — hot-deploy a single bundle into a running instance
- The `frontend-maven-plugin` auto-downloads Node.js v23 and Yarn; no system Node/Yarn is needed for builds

### Lint

- **Java**: `mvn checkstyle:check -Pquick` (Checkstyle, runs across all modules)
- **Frontend (ESLint 9)**: `cd aggregated-frontend/src/main/frontend && ./node_modules/.bin/eslint --max-warnings 0 src/`

### Tests

- Tests are **disabled by default** (`skipTests=true`).
- **Unit tests**: `mvn test -Ptests -pl modules/utils,modules/healthcheck,modules/vocabularies -Denforcer.skip=true -Dcheckstyle.skip=true -Drat.skip=true`
- **Integration tests**: `mvn install -PintegrationTests` (requires a running instance)
- Test source dirs: `modules/utils`, `modules/healthcheck`, `modules/vocabularies`, `tests/`

### Run (development)

```
./start_cards.sh --dev --test
```

- Starts on port 8080 by default; use `-p PORT` for a different port
- `--dev` enables the Composum JCR browser at `/bin/browser.html`
- `--test` includes test questionnaires and extra modules
- Default credentials: `admin` / `admin`
- The startup script uses Python and `psutil` for TCP bind checks; without `psutil` it falls back to a less robust check

### Frontend architecture

The React frontend is **not authored inside `aggregated-frontend/`**. That directory is **generated code** — never edit files there directly. Instead, individual modules under `modules/` contain the source React components. During build, a Python aggregation script collects these into `aggregated-frontend/src/main/frontend/`, which is then compiled by Webpack.

To modify a UI component: find its source module, edit there, and rebuild (`mvn install -Pquick` or `-PautoInstallBundle` for hot-deploy).

### Agent guidelines

- **Respect module boundaries.** Each OSGi bundle is self-contained; avoid modifying multiple modules unless the change genuinely spans them.
- **Identify the change surface first.** Determine whether a change is backend (Java/OSGi) or frontend (React in a module) and run the appropriate build/test cycle.
- **Keep diffs minimal.** Prefer small, focused changes over broad refactors.
- **Run tests after backend changes.** See the Tests section above.
- **Rebuild frontend after UI changes.** A backend-only change can skip Webpack with `-Pskip-webpack`.
- **Do not break OSGi bundle structure.** Preserve `pom.xml` packaging, `bnd` headers, and Sling content paths when editing modules.
- **When uncertain, prefer the smallest safe change.**

### Gotchas

- The build requires a `python` command — on Ubuntu where only `python3` exists, create a symlink: `sudo ln -sf /usr/bin/python3 /usr/bin/python`
- The first Maven build downloads ~1 GB of dependencies; subsequent builds use the local `.m2` cache
- The Sling Feature Launcher downloads artifacts from multiple Maven repos at startup; `FileNotFoundException` messages for nexus.phenotips.org are expected and non-fatal (it falls back to Maven Central)
- To fully clean state: `mvn clean -Pclean-instance` removes the `sling/` data directory; `mvn clean -Pclean-node` removes compiled frontend code
- When running `start_cards.sh`, do **not** kill the script with `kill -9`; use Ctrl+C or `kill <PID>` so it can gracefully shut down CARDS
