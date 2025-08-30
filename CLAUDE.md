# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Scalablytyped is a sophisticated Scala tool that converts TypeScript definition files (`.d.ts`) into Scala.js type definitions, enabling Scala.js developers to use JavaScript libraries with full type safety.

## Build System & Commands

This project uses **Mill** as the primary build tool for Scala modules and **Bun** for TypeScript/frontend components.

### Core Development Commands

**Mill Commands (Scala modules):**
```bash
# Build all modules
mill __.compile

# Run tests for all modules
mill __.test

# Run specific module tests
mill core.test
mill importer.test

# Generate sources only (common development task)
mill cli.runMain org.scalablytyped.converter.cli.SourceOnlyMain -o ./my-sources

# Import Scala.js definitions (full pipeline)
mill cli.runMain org.scalablytyped.converter.cli.ImportScalajsDefinitions

# Run the main CLI
mill cli.runMain org.scalablytyped.converter.cli.Main

# Clean build artifacts
mill clean
```

**Bun Commands (TypeScript/frontend):**
```bash
# Install dependencies
bun install

# Run TypeScript/frontend code
bun index.ts

# Run tests (if any TypeScript tests exist)
bun test

# Build frontend components
bun build index.ts
```

### Module Architecture

The codebase follows a multi-phase pipeline architecture with these key modules:

**Core Infrastructure:**
- `core` - Fundamental data structures, utilities, and type definitions
- `logging` - Logging infrastructure and utilities  
- `phases` - Pipeline framework for multi-stage processing

**Language Processing:**
- `ts` - TypeScript AST representation and parsing logic
- `scalajs` - Scala.js AST representation and code generation

**Import Pipeline:**
- `importer-portable` - Core conversion logic and phases (with build info)
- `importer` - Full importer with CI/CD capabilities
- `cli` - Command-line interfaces (`Main.scala`, `SourceOnlyMain.scala`, `ImportScalajsDefinitions.scala`)

**Runtime:**
- `runtime` - Scala.js runtime components (ScalaJSModule)

### Module Dependencies

```
cli -> importer -> importer-portable -> (phases, ts, scalajs) -> (core, logging)
                                                                      ^
runtime (ScalaJSModule) -------------------------------------------|
```

## Key CLI Entry Points

1. **Full Pipeline**: `mill cli.runMain org.scalablytyped.converter.cli.Main`
2. **Source Generation Only**: `mill cli.runMain org.scalablytyped.converter.cli.SourceOnlyMain`  
3. **Scala.js Definition Import**: `mill cli.runMain org.scalablytyped.converter.cli.ImportScalajsDefinitions`

## Technology Stack

- **Scala 2.12.18** with Scala.js 1.19.0
- **Mill 1.0.4** for build management
- **Dependencies**: Ammonite, OS-Lib, Circe, Bloop, Coursier, utest
- **AWS S3** integration for storage
- **Bun** for TypeScript/frontend development

## Testing

- Scala tests use **utest** framework: `mill __.test`
- Test framework: `utest.runner.Framework`
- TypeScript tests use Bun: `bun test`

## Development Workflow

1. The system processes TypeScript definitions through multiple phases
2. Input sources: NPM packages, DefinitelyTyped repo, local TypeScript definitions  
3. Output formats: Scala source files, compiled JARs, SBT projects
4. Use `SourceOnlyMain` for development/testing, full `Main` for production builds

---
description: Use Bun instead of Node.js, npm, pnpm, or vite.
globs: "*.ts, *.tsx, *.html, *.css, *.js, *.jsx, package.json"
alwaysApply: false
---

Default to using Bun instead of Node.js.

- Use `bun <file>` instead of `node <file>` or `ts-node <file>`
- Use `bun test` instead of `jest` or `vitest`
- Use `bun build <file.html|file.ts|file.css>` instead of `webpack` or `esbuild`
- Use `bun install` instead of `npm install` or `yarn install` or `pnpm install`
- Use `bun run <script>` instead of `npm run <script>` or `yarn run <script>` or `pnpm run <script>`
- Bun automatically loads .env, so don't use dotenv.

## APIs

- `Bun.serve()` supports WebSockets, HTTPS, and routes. Don't use `express`.
- `bun:sqlite` for SQLite. Don't use `better-sqlite3`.
- `Bun.redis` for Redis. Don't use `ioredis`.
- `Bun.sql` for Postgres. Don't use `pg` or `postgres.js`.
- `WebSocket` is built-in. Don't use `ws`.
- Prefer `Bun.file` over `node:fs`'s readFile/writeFile
- Bun.$`ls` instead of execa.

## Testing

Use `bun test` to run tests.

```ts#index.test.ts
import { test, expect } from "bun:test";

test("hello world", () => {
  expect(1).toBe(1);
});
```

## Frontend

Use HTML imports with `Bun.serve()`. Don't use `vite`. HTML imports fully support React, CSS, Tailwind.

Server:

```ts#index.ts
import index from "./index.html"

Bun.serve({
  routes: {
    "/": index,
    "/api/users/:id": {
      GET: (req) => {
        return new Response(JSON.stringify({ id: req.params.id }));
      },
    },
  },
  // optional websocket support
  websocket: {
    open: (ws) => {
      ws.send("Hello, world!");
    },
    message: (ws, message) => {
      ws.send(message);
    },
    close: (ws) => {
      // handle close
    }
  },
  development: {
    hmr: true,
    console: true,
  }
})
```

HTML files can import .tsx, .jsx or .js files directly and Bun's bundler will transpile & bundle automatically. `<link>` tags can point to stylesheets and Bun's CSS bundler will bundle.

```html#index.html
<html>
  <body>
    <h1>Hello, world!</h1>
    <script type="module" src="./frontend.tsx"></script>
  </body>
</html>
```

With the following `frontend.tsx`:

```tsx#frontend.tsx
import React from "react";

// import .css files directly and it works
import './index.css';

import { createRoot } from "react-dom/client";

const root = createRoot(document.body);

export default function Frontend() {
  return <h1>Hello, world!</h1>;
}

root.render(<Frontend />);
```

Then, run index.ts

```sh
bun --hot ./index.ts
```

For more information, read the Bun API docs in `node_modules/bun-types/docs/**.md`.