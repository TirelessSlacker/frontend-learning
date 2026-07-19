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
│   • src/services/todoStore.js owns storage  │
│     directly via better-sqlite3             │
└───────────────▲──────────────────────────────┘
                 │ HTTP
              browser

                data/todos.db (SQLite file, inside server/)
```

Each layer only knows about its immediate neighbor:

- **`frontend/`** — React UI. It only ever calls `/api/...`. It has no idea
  what's behind that path, and it's never reached directly by the browser —
  Express is the one serving it, whether that's Vite transforming it on the
  fly in dev or a prebuilt bundle in production.
- **`server/`** — Express.js. This is the API / business-logic layer
  (request validation, HTTP status codes) *and* the storage layer: a single
  module (`src/services/todoStore.js`) is the *only* place that knows todos
  live in a local SQLite file. Everywhere else just calls its functions
  (`listTodos` / `createTodo` / `updateTodo` / `deleteTodo`) and gets back
  plain JavaScript objects.

Express used to call out to a separate Java service (`persistence-service/`)
over HTTP for storage — that code is still in the repo (see below), just not
used by the app anymore. Database is now in-process instead of a network
call away.

## Running it

The browser only ever talks to Express, on `http://localhost:3001` — there's
no separate frontend port to open, and no other process to start first.

### Running it in development

```bash
cd server
npm install
npm run dev
```

`npm run dev` first runs `predev`, which installs `frontend/`'s dependencies
too — so these three commands are the entire setup on a fresh clone, with no
separate `frontend/` install step and no second terminal. You should see
`server (Express) listening on http://localhost:3001`.

On first run, `todoStore.js` creates `server/data/todos.db` (and the `todos`
table inside it) automatically — nothing to install or start beforehand.

Open `http://localhost:3001`. Editing anything under `frontend/src` hot-reloads
in the browser, because Express is running Vite in middleware mode inside the
same process — there's no separate frontend dev server or port to reach
directly.

### Running it in production

Build the frontend once, then run the server against that build — no Vite,
no hot reload, no `frontend/node_modules` needed at runtime. This is the mode
you'd actually deploy.

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
- **Replaceable persistence.** Because `todoStore.js` is the only file that
  knows todos are stored in SQLite, you could replace it with Postgres, a
  call out to a separate service again, or an in-memory mock for tests, and
  only that one file would change. `routes/todos.js` calls the same four
  functions no matter what's underneath.
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

## A note on `persistence-service/` (unused, kept for reference)

This repo used to run storage as a separate Java process that Express called
over HTTP (`src/services/persistenceClient.js`, also still in the repo but
no longer imported by `routes/todos.js`). That Java service still exists at
`persistence-service/` and still runs standalone if you want to poke at it —
it just isn't part of the running app anymore.

`persistence-service` avoids frameworks — no Spring, no Spring Data. It uses:
- `com.sun.net.httpserver.HttpServer`, built into the JDK, for the HTTP layer
- A ~150-line hand-rolled JSON reader/writer (`Json.java`) instead of a
  library, for the HTTP request/response bodies
- Plain JPA (`jakarta.persistence`, implemented by Hibernate) for storage —
  `Todo` is an `@Entity` (see `Todo.java`), and `TodoRepository` talks to an
  `EntityManager` directly: `em.find`, `em.persist`, `em.remove`, and a JPQL
  query for the list endpoint. There's no repository-generation magic like
  Spring Data — every method is a few lines you can read end to end.
- H2 as the actual database — it runs embedded inside its own JVM and keeps
  its data in `persistence-service/data/tododb.mv.db`, so there's nothing
  extra to install or start, unlike Postgres.

To run it standalone (Java 17+ and Maven required):

```bash
cd persistence-service
mvn compile exec:java
```

You should see `persistence-service listening on http://localhost:8080`. To
actually wire it back up to Express, point `routes/todos.js` at
`persistenceClient.js` instead of `todoStore.js`, and set `PERSISTENCE_URL`
if it's not running on the default `http://localhost:8080`.

Notice that `TodoHttpHandler.java` *still* hasn't changed, across four
different storage decisions before this one (a JSON file → raw JDBC → JPA →
swapping the database engine under JPA). It only ever depended on
`TodoRepository`'s public methods (`findAll`, `insert`, `update`, `delete`),
never on how they were implemented or which database sat underneath — the
same lesson `todoStore.js` now demonstrates on the Express side.

Maven's job is fetching the two dependencies this needs: the JPA provider
(`org.hibernate.orm:hibernate-core`) and the H2 database
(`com.h2database:h2`). The connection and schema settings live in
`src/main/resources/META-INF/persistence.xml` — the standard place JPA
looks for them — with `Database.java` overriding the connection details
from environment variables at runtime.

## Extending this sample

Natural next steps if you want to keep learning from this:
- Point `todoStore.js` at Postgres instead of SQLite (e.g. via `pg`), or
  swap it back to calling `persistence-service` over HTTP, to see how little
  of the rest of the app needs to change either way.
- Add a second table with a relationship (e.g. `tags`) to see how that
  reshapes `todoStore.js`.
- Add authentication at the Express layer.
- Add automated tests: unit tests for `todoStore.js` (SQLite in particular
  makes this easy — point it at `:memory:` for a disposable database per
  test run), integration tests for the Express routes.
- If you want to keep exploring the Java side specifically,
  `persistence-service/` still has its own "Extending this sample"-style
  options: swap H2 for Postgres by changing `pom.xml` and
  `persistence.xml`, add a `@ManyToMany` relationship, or swap
  `hibernate.hbm2ddl.auto=update` for a real migration tool (Flyway or
  Liquibase).
