// This is the ONLY file that knows the persistence-service exists, or that
// it happens to be written in Java, or that it happens to store data as a
// JSON file. Everywhere else in the Express app just calls these functions
// and gets plain JavaScript objects back.
//
// Swap this file out (point it at Postgres, at a different language's
// service, at an in-memory mock for tests) and nothing in routes/ would
// need to change.

const PERSISTENCE_URL = process.env.PERSISTENCE_URL || "http://localhost:8080";

/**
 * Wraps fetch with JSON handling and turns non-2xx responses into errors
 * that the route layer can catch and translate into HTTP responses.
 */
async function request(path, options = {}) {
  const res = await fetch(`${PERSISTENCE_URL}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });

  if (res.status === 204) return null;

  const text = await res.text();
  const body = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const message = body?.error || `persistence-service returned ${res.status}`;
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }

  return body;
}

export function listTodos() {
  return request("/todos");
}

export function createTodo(text) {
  return request("/todos", {
    method: "POST",
    body: JSON.stringify({ text }),
  });
}

export function updateTodo(id, changes) {
  return request(`/todos/${id}`, {
    method: "PUT",
    body: JSON.stringify(changes),
  });
}

export function deleteTodo(id) {
  return request(`/todos/${id}`, { method: "DELETE" });
}
