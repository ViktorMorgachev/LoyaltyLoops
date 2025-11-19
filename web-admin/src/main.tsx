import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './i18n'; // <--- ВАЖНО: Импорт конфигурации
import { NotificationProvider } from './context/NotificationContext';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <NotificationProvider>
      <App />
    </NotificationProvider>
  </React.StrictMode>,
);