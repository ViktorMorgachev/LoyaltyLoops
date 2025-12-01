import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './i18n'; // Конфигурация языков
import { NotificationProvider } from './context/NotificationContext';
import { UserProvider } from './context/UserContext'; // <-- ВАЖНЫЙ ИМПОРТ
import { ConfigProvider } from './context/ConfigContext';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider>
      {/* 1. Сначала Провайдер Уведомлений */}
      <NotificationProvider>
        {/* 2. ВНУТРИ него - Провайдер Пользователя */}
        {/* Без этой обертки useUser() работать не будет! */}
        <UserProvider>
          <App />
        </UserProvider>
      </NotificationProvider>
    </ConfigProvider>
  </React.StrictMode>,
);