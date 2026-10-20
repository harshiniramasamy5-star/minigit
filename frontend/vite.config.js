import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// /api calls go to Spring Boot in dev, so no CORS issues and the same paths work in prod.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
