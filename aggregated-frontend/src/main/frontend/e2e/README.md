# E2E tests (Playwright)

End-to-end tests run against a live CARDS instance.

## Prerequisites

- CARDS running (e.g. `./start_cards2.sh` or Sling at default port 8080).
- Dependencies installed: from `aggregated-frontend/src/main/frontend/` run `yarn install`.
- Playwright browsers (one-time): `npx playwright install`.

## Run tests

From `aggregated-frontend/src/main/frontend/`:

```bash
yarn test:e2e          # headless, all projects (chromium, firefox, webkit)
yarn test:e2e:ui       # interactive UI mode
yarn test:e2e:headed   # headed browsers
yarn test:e2e:debug    # debug mode
```

## Configuration

- **Base URL**: Default is `http://localhost:8080`. Override with `CARDS_URL`:

  ```bash
  CARDS_URL=http://localhost:9090 yarn test:e2e
  ```

- **Config file**: `playwright.config.js` in the same frontend directory.

## Adding tests

Add `*.spec.js` (or `*.spec.ts`) files under `e2e/`. Use `page.goto('/path')` for paths relative to `baseURL`.
