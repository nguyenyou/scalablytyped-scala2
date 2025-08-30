# Repository Guidelines

## Project Structure & Module Organization
- `build.mill` and `./mill`: Mill build and wrapper.
- Modules (Scala 2.12): `core`, `logging`, `ts`, `scalajs`, `phases`, ``importer-portable``, `importer`, `runtime` (Scala.js), `cli`.
- Source layout: `<module>/src/...` (Scala), tests under `<module>/test/src` (Mill default). Generated sources go to `out/`; local publish cache defaults to `facades/`.
- JS/TS: minimal entry at `index.ts`, config in `package.json`, `tsconfig.json`.

## Build, Test, and Development Commands
- Compile all: `./mill __.compile` — compiles every module.
- Run CLI (generate sources):
  - `./mill cli.runMain org.scalablytyped.converter.cli.SourceOnlyMain -o ./my-sources`
  - `./mill cli.runMain org.scalablytyped.converter.cli.ImportScalajsDefinitions`
- Convert from node_modules (TypeScript required in `node_modules`):
  - `./mill cli.runMain org.scalablytyped.converter.cli.Main --directory . typescript`
- Tests (uTest): `./mill <module>.test` or `./mill __.test`.

## Coding Style & Naming Conventions
- Scala: match existing style; prefer clear, idiomatic Scala 2.12. Use meaningful names, `CamelCase` for types/objects, `lowerCamelCase` for vals/defs, package prefix `org.scalablytyped...`.
- Imports: keep explicit; avoid unused. Keep functions small and pure where possible.
- Formatting: no enforced tool here; keep diffs minimal and consistent with nearby code.

## Testing Guidelines
- Framework: uTest (`utest.runner.Framework`).
- Location: `<module>/test/src`. Name tests with `*Tests.scala` or similar.
- Scope: add unit tests for new logic and edge cases; keep fast. Run with `./mill <module>.test` locally.

## Commit & Pull Request Guidelines
- Commit messages: prefer clear, imperative summaries (e.g., `fix(cli): handle missing typescript`). Avoid generic messages like “update”.
- PRs: include a concise description, rationale, and examples (commands run, before/after). Link issues where relevant. Add screenshots only if UI/UX output is relevant (CLI logs/snippets are sufficient).
- CI/readiness: ensure `./mill __.compile` and `./mill __.test` pass.

## Security & Configuration Tips
- Do not commit secrets. AWS SDK is present; rely on environment credentials when needed.
- Ensure `typescript@5.9.x` is installed in `node_modules` (`npm i -D typescript@5.9.2`) as the CLI expects it.
