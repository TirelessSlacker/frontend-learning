# Project context for Claude Code

This is a learning sample demonstrating a three-tier architecture:

React (`frontend/`) → Express.js (`server/`) → Java + JPA (`persistence-service/`) → H2 (embedded database)

See the root `README.md` for the full architecture explanation and run
instructions. This file is for picking up where a prior planning
conversation with Claude (in claude.ai) left off.

## How this got built, in order

1. Started as a plain file-based (JSON) Java persistence layer: a
   hand-rolled JSON parser/writer, zero dependencies.
2. Switched `persistence-service` to raw JDBC against Postgres.
3. Switched `persistence-service` to JPA (Hibernate) against Postgres —
   first use of Maven, with exactly two dependencies (JDBC driver + Hibernate).
4. Switched from Postgres to H2 (embedded, file-based) to remove the need
   for a separate database server — only `pom.xml` and `persistence.xml`
   changed; zero Java code changed.

Across all four stages, `TodoHttpHandler.java` never changed. It only
depends on `TodoRepository`'s public method signatures (`findAll` /
`insert` / `update` / `delete`), never on the storage implementation
underneath. That's a deliberate throughline of this project, worth
preserving if you extend it further.

## Current status: UNTESTED — this is the actual first task

Every file was written and hand-reviewed (XML validity, brace balance,
manual tracing of method signatures) in a sandboxed environment with no
`javac`, no Maven, and no network access — so **none of this has actually
been compiled or run yet**. Specific things that were chosen from memory
and were never verified against a live registry:

- Dependency versions in `persistence-service/pom.xml`: `hibernate-core
  6.4.4.Final`, `com.h2database:h2 2.2.224`, `exec-maven-plugin 3.2.0`
- Package versions in `frontend/package.json` and `server/package.json`
  (react, vite, express, cors, etc.)
- Whether `hibernate.hbm2ddl.auto=update` produces a schema H2 is happy
  with on first run, and whether the `TEXT` `columnDefinition` on
  `Todo.text` is valid H2 syntax (it should be — H2 aliases `TEXT` to
  `CLOB` — but this was never confirmed by actually running it)

## First task

Get this building and running end to end, fixing whatever's actually
broken as you find it (dependency resolution failures, typos, mapping
mismatches, etc.) — don't just assume the above is correct:

1. `cd persistence-service && mvn compile exec:java` — this is the one
   most likely to need a fix, since it has the most unverified surface
   area (Hibernate/JPA mapping, Maven dependency resolution).
2. `cd server && npm install && npm start`
3. `cd frontend && npm install && npm run dev`
4. Confirm the todo app works end to end in the browser: add a todo,
   toggle it complete, delete one, then restart `persistence-service` and
   confirm the data survived (it's stored in
   `persistence-service/data/tododb.mv.db`).

Report back what was actually wrong, if anything, rather than assuming it
all worked.
