import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './i18n';
import { NotificationProvider } from './context/NotificationContext';
import { UserProvider } from './context/UserContext';
import { ConfigProvider } from './context/ConfigContext';
import { Analytics } from './utils/analytics';

Analytics.init();

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