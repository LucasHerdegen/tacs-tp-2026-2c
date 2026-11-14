import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tailwindcss(),
    react(),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/otel': {
        target: 'http://localhost:4318',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/otel/, ''),
      },
    },
  },
})
