import { useEffect, useState, useCallback } from "react";
import { todoApi } from "./api/todoApi.js";
import TodoForm from "./components/TodoForm.jsx";
import TodoList from "./components/TodoList.jsx";

export default function App() {
  const [todos, setTodos] = useState([]);
  const [status, setStatus] = useState("loading"); // "loading" | "ready" | "error"
  const [errorMessage, setErrorMessage] = useState("");

  const loadTodos = useCallback(async () => {
    setStatus("loading");
    try {
      const data = await todoApi.list();
      setTodos(data);
      setStatus("ready");
    } catch (err) {
      setErrorMessage(err.message);
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    loadTodos();
  }, [loadTodos]);

  async function handleAdd(text) {
    const created = await todoApi.create(text);
    setTodos((current) => [...current, created]);
  }

  async function handleToggle(todo) {
    const updated = await todoApi.update(todo.id, { completed: !todo.completed });
    setTodos((current) => current.map((t) => (t.id === todo.id ? updated : t)));
  }

  async function handleDelete(todo) {
    await todoApi.remove(todo.id);
    setTodos((current) => current.filter((t) => t.id !== todo.id));
  }

  const remaining = todos.filter((t) => !t.completed).length;

  return (
    <div className="page">
      <header className="page-header">
        <h1>Todo</h1>
        <p className="subtitle">
          React&nbsp;→&nbsp;Express, with storage owned right inside the server.
        </p>
      </header>

      <main className="card">
        <TodoForm onAdd={handleAdd} />

        {status === "loading" && <p className="status-line">Loading todos…</p>}

        {status === "error" && (
          <div className="status-line status-error">
            <p>Couldn't reach the API: {errorMessage}</p>
            <button type="button" onClick={loadTodos}>
              Try again
            </button>
          </div>
        )}

        {status === "ready" && (
          <>
            <TodoList todos={todos} onToggle={handleToggle} onDelete={handleDelete} />
            <p className="count-line">
              {remaining === 0 ? "All caught up" : `${remaining} remaining`}
            </p>
          </>
        )}
      </main>
    </div>
  );
}
