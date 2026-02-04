# Repository Guidelines

## Project Structure & Module Organization
- `app/`: main Android application module (UI, playback, data providers).
- `build.gradle`, `settings.gradle`, `gradle.properties`: Gradle config.

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: build debug APK.
- `./gradlew installDebug`: install on connected device.
- `./gradlew lint`: run Android lint.

## Coding Style & Naming Conventions
- Kotlin files use `PascalCase.kt`.
- Use `camelCase` for variables and functions.
- Prefer `ViewBinding` for UI bindings.
- Keep UI text in `res/values/strings.xml`.

## Testing Guidelines
- No tests configured yet.
- If adding tests, place under `app/src/test` or `app/src/androidTest`.

## Security & Configuration Tips
- Do not commit credentials.
- Keep API base URLs configurable when possible.
