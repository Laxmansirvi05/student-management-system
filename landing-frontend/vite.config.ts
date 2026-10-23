import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  base: '/',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/login.html': 'http://localhost:8080',
      '/change-password.html': 'http://localhost:8080',
      '/privacy.html': 'http://localhost:8080',
      '/terms.html': 'http://localhost:8080',
      '/student': 'http://localhost:8080',
      '/admin': 'http://localhost:8080',
      '/css': 'http://localhost:8080',
      '/js': 'http://localhost:8080',
      '/img': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
      },
      output: {
        entryFileNames: 'js/landing/[name]-[hash].js',
        chunkFileNames: 'js/landing/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          if (assetInfo.name && assetInfo.name.endsWith('.css')) {
            return 'css/landing/[name]-[hash][extname]';
          }
          return 'js/landing/[name]-[hash][extname]';
        },
      },
    },
  },
})
