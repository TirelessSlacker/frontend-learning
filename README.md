# Todo — a full-stack sample with a swappable storage layer

A deliberately small todo app that exists to show one thing clearly: **how
a thin storage-abstraction module lets you swap out an entire database
layer without touching the routes or business logic above it.**

```
┌────────────────────────────────────────────┐
│   server/ (Express, port 3001)              │
│   • serves the React UI                     │
│     (Vite middleware in dev,                │
│      prebuilt static files in prod)         │
│   • handles all /api/... calls              │
│   • src/services/persistenceClient.js       │
│     forwards storage calls over HTTP        │
└───────────────▲──────────────┬───────────────┘
                 │ HTTP          │ HTTP
              browser   persistence-service/ (Java, port 8080)
                                 │
                        tododb.mv.db (H2, embedded)
```

Each layer only knows about its immediate neighbor:

- **`frontend/`** — React UI. It only ever calls `/api/...`. It has no idea
  what's behind that path, and it's never reached directly by the browser —
  Express is the one serving it, whether that's Vite transforming it on the
  fly in dev or a prebuilt bundle in production.
- **`server/`** — Express.js. This is the API / business-logic layer
  (request validation, HTTP status codes). A single module
  (`src/services/persistenceClient.js`) is the *only* place that knows
  storage lives in a separate Java process reachable over HTTP. Everywhere
  else just calls its functions (`listTodos` / `createTodo` / `updateTodo` /
  `deleteTodo`) and gets back plain JavaScript objects.
- **`persistence-service/`** — a standalone Java process (JPA/Hibernate over
  an embedded H2 database) that owns storage. It's a separate service, not
  an Express dependency, so it needs to be started on its own before Express
  (see below).

Express can also own storage directly in-process instead of calling out to
a separate service — `src/services/todoStore.js` (SQLite via
`better-sqlite3`) is still in the repo for that, just not used by the app
right now (see below).

## Running it

The browser only ever talks to Express, on `http://localhost:3001` — but
Express itself talks to `persistence-service` on `http://localhost:8080`,
so that has to be running first.

### Running it in development

```bash
# Terminal 1 — start the persistence service (Java 21+ and Maven required)
cd persistence-service
mvn compile exec:java
```

You should see `persistence-service listening on http://localhost:8080`.

```bash
# Terminal 2 — start Express + the React dev server
cd server
npm install
npm run dev
```

`npm run dev` first runs `predev`, which installs `frontend/`'s dependencies
too — so these two commands are the entire Express-side setup on a fresh
clone. You should see `server (Express) listening on http://localhost:3001`
followed by a line confirming it's forwarding persistence calls to
`http://localhost:8080`.

Open `http://localhost:3001`. Editing anything under `frontend/src` hot-reloads
in the browser, because Express is running Vite in middleware mode inside the
same process — there's no separate frontend dev server or port to reach
directly.

### Running it in production

Build the frontend once, then run the server against that build — no Vite,
no hot reload, no `frontend/node_modules` needed at runtime. This is the mode
you'd actually deploy. `persistence-service` still needs to be running
separately.

```bash
# Start persistence-service (Java 21+ and Maven required)
cd persistence-service
mvn compile exec:java
```

```bash
# Build the frontend once
cd frontend
npm install
npm run build              # writes frontend/dist

# Run the server against the prebuilt bundle
cd ../server
npm install
NODE_ENV=production npm start
```

Open `http://localhost:3001` — Express serves the static files in
`frontend/dist` directly for everything except `/api/...`. If `frontend/dist`
doesn't exist yet, `npm start` exits immediately with a message telling you
to build first, instead of starting in a broken state.

## Why it's shaped this way

- **Separation of concerns.** The React app never talks to the database
  layer directly — that's a smell in real projects (it leaks internal
  implementation details to the client and makes the API impossible to
  evolve independently). Every request goes through Express.
- **Replaceable persistence.** Because `persistenceClient.js` is the only
  file that knows storage happens over HTTP against a separate Java
  service, you could replace it with `todoStore.js` (in-process SQLite),
  Postgres, or an in-memory mock for tests, and only that one file would
  change. `routes/todos.js` calls the same four functions no matter what's
  underneath.
- **Single browser-facing origin.** Like a real deployment, the browser only
  ever talks to Express. In dev that's Vite running in middleware mode inside
  the same process (you still get hot reload); in production it's a prebuilt
  static bundle. There's no second dev-only port or CORS story between
  frontend and API to explain.

## API reference

Express (`server/`), what the frontend calls:

| Method | Path             | Body                          | Notes                    |
|--------|------------------|--------------------------------|---------------------------|
| GET    | `/api/todos`     | —                               | List all todos            |
| POST   | `/api/todos`     | `{ "text": "..." }`             | Create a todo             |
| PUT    | `/api/todos/:id` | `{ "text"?, "completed"? }`     | Partial update            |
| DELETE | `/api/todos/:id` | —                               | Delete a todo             |

## `persistence-service/` in detail

`persistence-service` avoids frameworks — no Spring, no Spring Data. It uses:
- `com.sun.net.httpserver.HttpServer`, built into the JDK, for the HTTP layer
- Jackson (`jackson-databind`) for JSON request/response bodies
- Plain JPA (`jakarta.persistence`, implemented by Hibernate) for storage —
  `Todo` is an `@Entity` (see `Todo.java`), and `TodoRepository` talks to an
  `EntityManager` directly: `em.find`, `em.persist`, `em.remove`, and a JPQL
  query for the list endpoint. There's no repository-generation magic like
  Spring Data — every method is a few lines you can read end to end.
- H2 as the actual database — it runs embedded inside its own JVM and keeps
  its data in `persistence-service/data/tododb.mv.db`, so there's nothing
  extra to install or start, unlike Postgres.

Notice that `TodoHttpHandler.java` *still* hasn't changed, across four
different storage decisions before this one (a JSON file → raw JDBC → JPA →
swapping the database engine under JPA). It only ever depended on
`TodoRepository`'s public methods (`findAll`, `insert`, `update`, `delete`),
never on how they were implemented or which database sat underneath — the
same lesson `persistenceClient.js` demonstrates on the Express side.

Maven's job is fetching the three dependencies this needs: the JPA provider
(`org.hibernate.orm:hibernate-core`), the H2 database (`com.h2database:h2`),
and Jackson (`com.fasterxml.jackson.core:jackson-databind`). The connection
and schema settings live in `src/main/resources/META-INF/persistence.xml` —
the standard place JPA looks for them — with `Database.java` overriding the
connection details from environment variables at runtime.

## A note on `todoStore.js` / SQLite (unused, kept for reference)

This repo has also run storage in-process, directly inside Express, via
`src/services/todoStore.js` (SQLite through `better-sqlite3`, data in
`server/data/todos.db`). That file is still in the repo but no longer
imported by `routes/todos.js` — it's kept around as a reference for that
architecture, where there's no separate persistence process to start at
all.

To wire it back up, point `routes/todos.js` at `todoStore.js` instead of
`persistenceClient.js`, and add `better-sqlite3` back to `server/package.json`.

## Extending this sample

Natural next steps if you want to keep learning from this:
- Point `persistenceClient.js` at Postgres instead of the Java service (e.g.
  by writing a new module with the same four functions), or swap it back to
  `todoStore.js` for in-process SQLite, to see how little of the rest of the
  app needs to change either way.
- Add a second table with a relationship (e.g. `tags`) to see how that
  reshapes both `Todo.java`/`TodoRepository` and the Express-side client.
- Add authentication at the Express layer.
- Add automated tests: unit tests for `todoStore.js` (SQLite in particular
  makes this easy — point it at `:memory:` for a disposable database per
  test run), integration tests for the Express routes, and a way to run
  `persistence-service` against a disposable H2/Postgres instance for its
  own tests.
- If you want to keep exploring the Java side specifically,
  `persistence-service/` still has its own "Extending this sample"-style
  options: swap H2 for Postgres by changing `pom.xml` and
  `persistence.xml`, add a `@ManyToMany` relationship, or swap
  `hibernate.hbm2ddl.auto=update` for a real migration tool (Flyway or
  Liquibase).
