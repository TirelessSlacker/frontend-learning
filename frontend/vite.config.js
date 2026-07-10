import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The dev-server proxy below is what lets the React app call fetch("/api/...")
// without worrying about CORS or hardcoding a port: Vite forwards anything
// under /api to the Express server. In production you'd instead have
// Express serve the built frontend, or put a reverse proxy in front of both.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:3001",
        changeOrigin: true,
      },
    },
  },
});
