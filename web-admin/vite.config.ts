import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
   plugins: [react()],
   server: {
     port: 3000,       // Жестко задаем порт
     strictPort: true, // Если порт 3000 занят — выдать ошибку, а не переходить на 3001
   }
 })
