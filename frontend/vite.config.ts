import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    react({
      include: "**/*.{js,jsx,ts,tsx}"
    })
  ],
  server: {
    host: "localhost",
    port: 5173,
    strictPort: false,
    open: true
  }
});
