import app from "./app.js";

const PORT = process.env.PORT || 3001;

app.listen(PORT, () => {
  console.log(`server (Express) listening on http://localhost:${PORT}`);
  console.log(`Forwarding persistence calls to ${process.env.PERSISTENCE_URL || "http://localhost:8080"}`);
});
