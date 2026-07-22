import express from "express";

// A minimal stand-in for persistence-service's HTTP/JSON contract, backed by
// an in-memory array. Tests point PERSISTENCE_URL at this instead of a real
// Java process, and can force a canned failure response to exercise
// downstream-error handling without needing the real service to actually
// fail. Binds to an ephemeral port, never the real :8080, so a test run
// never risks talking to a developer's actually-running persistence-service.
export async function startStubPersistenceServer() {
  const app = express();
  app.use(express.json());

  let todos = [];
  let nextId = 1;
  let failure = null; // { status, body } - set via setFailure()

  function maybeFail(res) {
    if (failure) {
      res.status(failure.status).json(failure.body);
      return true;
    }
    return false;
  }

  app.get("/todos", (req, res) => {
    if (maybeFail(res)) return;
    res.json(todos);
  });

  app.post("/todos", (req, res) => {
    if (maybeFail(res)) return;
    const { text } = req.body ?? {};
    if (typeof text !== "string" || !text.trim()) {
      return res.status(400).json({ error: "Field 'text' is required" });
    }
    const todo = { id: nextId++, text: text.trim(), completed: false, createdAt: Date.now() };
    todos.push(todo);
    res.status(201).json(todo);
  });

  app.put("/todos/:id", (req, res) => {
    if (maybeFail(res)) return;
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) {
      return res.status(400).json({ error: "Invalid id" });
    }
    const todo = todos.find((t) => t.id === id);
    if (!todo) {
      return res.status(404).json({ error: "Todo not found" });
    }
    const { text, completed } = req.body ?? {};
    if (text !== undefined) todo.text = text;
    if (completed !== undefined) todo.completed = completed;
    res.json(todo);
  });

  app.delete("/todos/:id", (req, res) => {
    if (maybeFail(res)) return;
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) {
      return res.status(400).json({ error: "Invalid id" });
    }
    const index = todos.findIndex((t) => t.id === id);
    if (index === -1) {
      return res.status(404).json({ error: "Todo not found" });
    }
    todos.splice(index, 1);
    res.status(204).end();
  });

  const server = await new Promise((resolve) => {
    const s = app.listen(0, () => resolve(s));
  });
  const { port } = server.address();

  return {
    url: `http://localhost:${port}`,
    reset() {
      todos = [];
      nextId = 1;
      failure = null;
    },
    setFailure(status, body) {
      failure = { status, body };
    },
    close() {
      return new Promise((resolve) => server.close(resolve));
    },
  };
}
