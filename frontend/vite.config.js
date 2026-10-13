import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the browser talks only to the Vite server, which forwards each prefix to the right
// backend service. That avoids CORS entirely. When the API Gateway exists, replace this with one proxy.
const service = (port) => ({
  target: `http://localhost:${port}`,
  changeOrigin: true,
  rewrite: (path) => path.replace(/^\/[a-z]+-api/, ''),
})

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/auth-api': service(8081),
      '/catalog-api': service(8082),
      '/cart-api': service(8083),
      '/order-api': service(8084),
      '/payment-api': service(8085),
    },
  },
})
