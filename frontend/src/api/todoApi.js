import axios from "axios";

// The React app only ever talks to "/api/...". It has no idea an Express
// server or a Java service exist behind that path - Vite's dev proxy (see
// vite.config.js) and, in production, whatever serves the app, are what
// route that path to the right place.

const client = axios.create({ baseURL: "/api" });

async function request(path, options = {}) {
  try {
    const res = await client.request({ url: path, ...options });
    return res.status === 204 ? null : res.data;
  } catch (err) {
    if (err.response) {
      throw new Error(err.response.data?.error || `Request failed with status ${err.response.status}`);
    }
    throw err;
  }
}

export const todoApi = {
  list: () => request("/todos"),
  create: (text) => request("/todos", { method: "POST", data: { text } }),
  update: (id, changes) => request(`/todos/${id}`, { method: "PUT", data: changes }),
  remove: (id) => request(`/todos/${id}`, { method: "DELETE" }),
};
