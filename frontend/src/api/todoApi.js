// The React app only ever talks to "/api/...". It has no idea an Express
// server or a Java service exist behind that path - Vite's dev proxy (see
// vite.config.js) and, in production, whatever serves the app, are what
// route that path to the right place.

async function request(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });

  if (res.status === 204) return null;

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    throw new Error(body?.error || `Request failed with status ${res.status}`);
  }

  return body;
}

export const todoApi = {
  list: () => request("/todos"),
  create: (text) => request("/todos", { method: "POST", body: JSON.stringify({ text }) }),
  update: (id, changes) => request(`/todos/${id}`, { method: "PUT", body: JSON.stringify(changes) }),
  remove: (id) => request(`/todos/${id}`, { method: "DELETE" }),
};
