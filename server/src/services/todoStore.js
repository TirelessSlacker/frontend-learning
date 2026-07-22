// This file is no longer used by the main app — see persistenceClient.js,
// which forwards these same calls over HTTP to the separate Java
// persistence-service instead. This is kept around as a reference for the
// embedded-SQLite architecture, where Express owned storage directly
// instead of calling out to another process.
//
// This is the ONLY file that knows todos are stored in a local SQLite file
// via better-sqlite3. Everywhere else in the Express app just calls these
// functions and gets plain JavaScript objects back.

import Database from "better-sqlite3";
import path from "node:path";
import { fileURLToPath } from "node:url";
import fs from "node:fs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, "..", "..", "data");
fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, "todos.db"));
db.pragma("journal_mode = WAL");

db.exec(`
  CREATE TABLE IF NOT EXISTS todos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    completed INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
  )
`);

function toTodo(row) {
  return {
    id: row.id,
    text: row.text,
    completed: Boolean(row.completed),
    createdAt: row.created_at,
  };
}

export function listTodos() {
  const rows = db.prepare("SELECT * FROM todos ORDER BY id").all();
  return rows.map(toTodo);
}

export function createTodo(text) {
  const createdAt = Date.now();
  const { lastInsertRowid } = db
    .prepare("INSERT INTO todos (text, completed, created_at) VALUES (?, 0, ?)")
    .run(text, createdAt);
  return toTodo(db.prepare("SELECT * FROM todos WHERE id = ?").get(lastInsertRowid));
}

export function updateTodo(id, changes) {
  const existing = db.prepare("SELECT * FROM todos WHERE id = ?").get(id);
  if (!existing) {
    const error = new Error("Todo not found");
    error.status = 404;
    throw error;
  }

  const text = changes.text !== undefined ? changes.text : existing.text;
  const completed = changes.completed !== undefined ? changes.completed : Boolean(existing.completed);

  db.prepare("UPDATE todos SET text = ?, completed = ? WHERE id = ?").run(text, completed ? 1 : 0, id);
  return toTodo(db.prepare("SELECT * FROM todos WHERE id = ?").get(id));
}

export function deleteTodo(id) {
  const { changes } = db.prepare("DELETE FROM todos WHERE id = ?").run(id);
  if (changes === 0) {
    const error = new Error("Todo not found");
    error.status = 404;
    throw error;
  }
}
