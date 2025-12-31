import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// Функция для получения версии приложения из build.gradle.kts
const getAppVersion = () => {
  return '1.2.0'; // Fallback
};

const appVersion = getAppVersion();

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },

  // (Опционально) Удобные импорты через @
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  // Настройки для 'npm run dev'
  server: {
    port: 3000,
    strictPort: true,
  },

  // Настройки для 'npm run preview' (тестирование собранного билда)
  // Это полезно, если вы захотите запустить собранную версию локально или в Docker
  preview: {
    port: 3000,
    strictPort: true,
    host: true, // Разрешает доступ по сети (0.0.0.0), нужно для Docker/Railway
  },

  esbuild: {
    // Удаляем console.log и debugger только в продакшене
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  } as any
}))