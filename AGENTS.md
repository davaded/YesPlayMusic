# Repository Guidelines

## Project Structure & Module Organization
- `src/`: main Vue 2 application code (components, views, router, store, utils).
- `public/`: static assets and HTML template.
- `assets/` under `src/`: icons, CSS, fonts, and UI resources.
- `build/` and `images/`: build-time and documentation assets.
- `docker/`, `Dockerfile`, `docker-compose.yml`: container setups.
- Config files: `vue.config.js`, `babel.config.js`, `jsconfig.json`, `.env.example`.

## Build, Test, and Development Commands
- `npm install`: install dependencies.
- `npm run serve`: start local dev server (Vue CLI) on port `8080`.
- `npm run build`: create production build in `dist/`.
- `npm run lint`: run ESLint via Vue CLI.
- `npm run netease_api:run`: run the Netease API helper (for local API proxy use).

## Coding Style & Naming Conventions
- Indentation: 2 spaces.
- JavaScript/Vue style is enforced by ESLint + Prettier.
- Vue SFCs use `PascalCase.vue` filenames (e.g., `TrackListItem.vue`).
- Use `camelCase` for JS variables/functions and `kebab-case` for CSS classes.
- Formatting: run `npm run lint` and `npx prettier --write ./src` when touching UI code.

## Testing Guidelines
- No dedicated unit/integration test framework is configured.
- Quality checks rely on linting (`npm run lint`).
- If you add tests, place them near the feature (e.g., `src/**/__tests__`).

## Commit & Pull Request Guidelines
- No explicit commit message convention is defined in the repo.
- Use concise, present-tense commits (e.g., "fix login cookie persistence").
- PRs should include:
  - A short summary of changes.
  - Steps to verify (commands and expected behavior).
  - Screenshots for UI-facing changes.

## Security & Configuration Tips
- API URLs and keys should go in `.env` (see `.env.example`).
- Avoid committing secrets; prefer environment variables for runtime config.
