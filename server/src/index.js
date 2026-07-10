import express from "express";
import cors from "cors";
import todosRouter from "./routes/todos.js";

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

// Every todo route is mounted under /api so it's obvious, from the frontend's
// point of view, that it's talking to a backend API rather than to static
// files or server-rendered pages.
app.use("/api", todosRouter);

app.get("/api/health", (req, res) => {
  res.json({ status: "ok" });
});

// Centralized error handler: translates errors bubbled up from the
// persistence client (e.g. "Todo not found", connection refused) into
// sensible HTTP responses, instead of leaking stack traces to the client.
app.use((err, req, res, next) => {
  console.error(err);
  const status = err.status && err.status >= 400 && err.status < 600 ? err.status : 502;
  res.status(status).json({ error: err.message || "Unexpected error" });
});

app.listen(PORT, () => {
  console.log(`server (Express) listening on http://localhost:${PORT}`);
  console.log(`Forwarding persistence calls to ${process.env.PERSISTENCE_URL || "http://localhost:8080"}`);
});
