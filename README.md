# Todo — a three-tier architecture sample

A deliberately small todo app that exists to show one thing clearly: **how a
React frontend, an Express.js API, and a persistence layer talk to each
other** as three independent processes.

```
┌────────────────────────────────────┐        ┌───────────────────────┐
│   server/ (Express, port 3001)      │  HTTP  │  persistence-service/  │
│   • serves the React UI              │ /todos │  Java + JPA            │
│     (Vite middleware in dev,         │───────▶│  port 8080             │
│      prebuilt static files in prod)  │◀───────│                        │
│   • handles all /api/... calls       │        └───────────┬────────────┘
└───────────────▲──────────────────────┘                    │ JPA (via Hibernate)
                 │ HTTP                                       ▼
              browser                                   H2 (embedded)
                                                    data/tododb.mv.db file
```

Each layer only knows about its immediate neighbor:

- **`frontend/`** — React UI. It only ever calls `/api/...`. It has no idea
  Express or Java exist behind that path, and it's never reached directly by
  the browser — Express is the one serving it, whether that's Vite
  transforming it on the fly in dev or a prebuilt bundle in production.
- **`server/`** — Express.js. This is the API / business-logic layer: request
  validation, HTTP status codes, and a single client module
  (`src/services/persistenceClient.js`) that's the *only* place aware the
  persistence layer happens to be a Java service.
- **`persistence-service/`** — a Java program that owns the data: it exposes
  a small REST API and maps todos onto a database through JPA (with
  Hibernate as the provider). It uses H2, an embedded database that runs
  inside the same JVM and stores its data in a local file — no separate
  database server to install or start. There's still no Spring — it talks
  to `EntityManager` directly, and Maven exists only to pull in the
  JPA/Hibernate and H2 jars.

This mirrors a very common real-world shape: a browser-facing app server
(Node/Express, or any "BFF") calling into a separate backend service that owns
the actual data, often written in a different language.

## Running it

The browser only ever talks to Express, on `http://localhost:3001` — there's
no separate frontend port to open. How Express gets the UI onto that port
differs between development and production, so there are two workflows below.
Either way, start `persistence-service` first: both workflows talk to it.

### Running it in development

Two terminals.

**1. Persistence service (Java 17+ and Maven required)**

```bash
cd persistence-service
mvn compile exec:java
```

You should see `persistence-service listening on http://localhost:8080`. On
first run it creates `persistence-service/data/tododb.mv.db` and the `todos`
table inside it automatically via Hibernate's schema update (see
`src/main/resources/schema.sql` for the equivalent DDL, and `Todo.java` for
the `@Entity` mapping it's derived from).

> You may see one harmless startup line like `SLF4J: No SLF4J providers
> were found` — Hibernate logs through SLF4J, and this project doesn't
> bother adding a logging backend for a learning sample. It's just a
> warning, not an error.

> Want to point this at a real Postgres instance instead of the embedded
> H2 file? Set `DB_URL` / `DB_USER` / `DB_PASSWORD` env vars before
> starting — see "Extending this sample" below.

**2. Server + UI (Node 18+)**

```bash
cd server
npm install
npm run dev
```

`npm run dev` first runs `predev`, which installs `frontend/`'s dependencies
too — so these three commands are the entire setup on a fresh clone, with no
separate `frontend/` install step and no third terminal. You should see
`server (Express) listening on http://localhost:3001`.

Open `http://localhost:3001`. Editing anything under `frontend/src` hot-reloads
in the browser, because Express is running Vite in middleware mode inside the
same process — there's no separate frontend dev server or port to reach
directly.

### Running it in production

Build the frontend once, then run the server against that build — no Vite,
no hot reload, no `frontend/node_modules` needed at runtime. This is the mode
you'd actually deploy.

```bash
# Persistence service, same as dev
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

> `persistence-service/` uses Maven (`mvn compile exec:java`) instead of
> `npm`/`javac` directly, since it needs the H2 and Hibernate jars on its
> classpath.

## Why it's shaped this way

- **Separation of concerns.** The React app never talks to the database
  layer directly — that's a smell in real projects (it leaks internal
  implementation details to the client and makes the API impossible to
  evolve independently). Every request goes through Express.
- **Replaceable persistence.** Because `persistenceClient.js` is the only
  file that knows the storage layer is a Java service, you could replace
  `persistence-service/` with a service in another language, a different
  database, or an in-memory mock for tests, and only that one file would
  change.
- **Independent deployability.** In a real system these would typically be
  three separate deployable units (e.g. three containers), which is why
  they're three separate folders with their own dependency management here
  rather than one monorepo package.
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

Java (`persistence-service/`), what Express calls — same shape, mounted at
`/todos` instead of `/api/todos`, with no request validation (that's the
API layer's job, not the persistence layer's) and backed by a `todos` table
via JPA/Hibernate instead of an in-memory list.

## A note on the Java service

`persistence-service` avoids frameworks — no Spring, no Spring Data. It uses:
- `com.sun.net.httpserver.HttpServer`, built into the JDK, for the HTTP layer
- A ~150-line hand-rolled JSON reader/writer (`Json.java`) instead of a
  library, for the HTTP request/response bodies
- Plain JPA (`jakarta.persistence`, implemented by Hibernate) for storage —
  `Todo` is an `@Entity` (see `Todo.java`), and `TodoRepository` talks to an
  `EntityManager` directly: `em.find`, `em.persist`, `em.remove`, and a JPQL
  query for the list endpoint. There's no repository-generation magic like
  Spring Data — every method is a few lines you can read end to end.
- H2 as the actual database — it runs embedded inside this same JVM and
  keeps its data in `persistence-service/data/tododb.mv.db`, so there's
  nothing extra to install or start, unlike Postgres.

Maven's job is fetching the two dependencies this needs: the JPA provider
(`org.hibernate.orm:hibernate-core`) and the H2 database
(`com.h2database:h2`). The connection and schema settings live in
`src/main/resources/META-INF/persistence.xml` — the standard place JPA
looks for them — with `Database.java` overriding the connection details
from environment variables at runtime.

Notice that `TodoHttpHandler.java` *still* hasn't changed, across four
different storage decisions now (a JSON file → raw JDBC → JPA → swapping
the database engine under JPA). It only ever depended on
`TodoRepository`'s public methods (`findAll`, `insert`, `update`, `delete`),
never on how they were implemented or which database sat underneath.

Swapping the database itself — H2 for Postgres, or back again — didn't even
touch `TodoRepository.java` or `Todo.java`. It was two files: the driver
dependency in `pom.xml`, and the URL/dialect/credentials in
`persistence.xml`. That's what JPA is for: the same entity mapping and
repository code work against either database.

## Extending this sample

Natural next steps if you want to keep learning from this:
- Point this at Postgres instead of H2 without changing any Java: swap the
  `com.h2database:h2` dependency in `pom.xml` for
  `org.postgresql:postgresql`, and update the four `jakarta.persistence.jdbc.*`
  properties plus `hibernate.dialect` (to `org.hibernate.dialect.PostgreSQLDialect`)
  in `persistence.xml` — or just set `DB_URL`/`DB_USER`/`DB_PASSWORD` env
  vars to point at a running Postgres instance instead.
- Add a second entity with a relationship (e.g. `Tag` with a
  `@ManyToMany` to `Todo`) to see how JPA handles associations.
- Swap `hibernate.hbm2ddl.auto=update` for a real migration tool
  (Flyway or Liquibase) — the more common choice once a schema needs to
  evolve carefully in production.
- Add authentication at the Express layer.
- Add automated tests: unit tests for `TodoRepository` (H2 in particular
  makes this easy — point a test persistence unit at `jdbc:h2:mem:` for a
  disposable in-memory database per test run), integration tests for the
  Express routes.
