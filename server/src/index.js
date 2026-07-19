import express from "express";
import cors from "cors";
import path from "node:path";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import todosRouter from "./routes/todos.js";

const app = express();
const PORT = process.env.PORT || 3001;
const isProduction = process.env.NODE_ENV === "production";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.join(__dirname, "..", "..", "frontend");
const frontendDistPath = path.join(frontendRoot, "dist");
const frontendIndexPath = path.join(frontendDistPath, "index.html");

app.use(cors());
app.use(express.json());

// Every todo route is mounted under /api so it's obvious, from the frontend's
// point of view, that it's talking to a backend API rather than to static
// files or server-rendered pages.
app.use("/api", todosRouter);

app.get("/api/health", (req, res) => {
  res.json({ status: "ok" });
});

// Anything under /api that didn't match a route above is a bad API call, not
// a frontend asset — fail it here so it doesn't fall through to the SPA/static
// handling below and come back as an HTML page.
app.use("/api", (req, res) => {
  res.status(404).json({ error: "Not found" });
});

if (isProduction) {
  if (!fs.existsSync(frontendIndexPath)) {
    console.error(
      "[server] NODE_ENV=production but frontend/dist/index.html is missing. " +
        "Run `npm run build` first (or `cd frontend && npm run build`)."
    );
    process.exit(1);
  }
  app.use(express.static(frontendDistPath));
  app.get("*", (req, res) => {
    res.sendFile(frontendIndexPath);
  });
} else {
  // Vite runs inside this same process in middleware mode, so editing
  // frontend/src hot-reloads in the browser without a separate dev server
  // or port — Express is the only thing the browser ever talks to.
  const { createServer: createViteServer } = await import("vite");
  const vite = await createViteServer({
    root: frontendRoot,
    server: { middlewareMode: true },
    appType: "spa",
  });
  app.use(vite.middlewares);
}

// Centralized error handler: translates errors bubbled up from the
// todo store (e.g. "Todo not found") into sensible HTTP responses, instead
// of leaking stack traces to the client.
app.use((err, req, res, next) => {
  console.error(err);
  const status = err.status && err.status >= 400 && err.status < 600 ? err.status : 502;
  res.status(status).json({ error: err.message || "Unexpected error" });
});

app.listen(PORT, () => {
  console.log(`server (Express) listening on http://localhost:${PORT}`);
});
