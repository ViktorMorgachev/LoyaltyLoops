import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
// 1. УБРАЛИ AlertColor из импорта, оставили только компоненты
import { Snackbar, Alert } from '@mui/material';

// 2. ОБЪЯВИЛИ ТИП САМИ (Это решает ошибку)
type AlertColor = 'success' | 'info' | 'warning' | 'error';

interface NotificationContextType {
  showError: (msg: string) => void;
  showSuccess: (msg: string) => void;
  showInfo: (msg: string) => void;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [severity, setSeverity] = useState<AlertColor>('info');

  const handleClose = (_event?: React.SyntheticEvent | Event, reason?: string) => {
    if (reason === 'clickaway') return;
    setOpen(false);
  };

  const show = (msg: string, sev: AlertColor) => {
    setMessage(msg);
    setSeverity(sev);
    setOpen(true);
  };

  const showError = (msg: string) => show(msg, 'error');
  const showSuccess = (msg: string) => show(msg, 'success');
  const showInfo = (msg: string) => show(msg, 'info');

  return (
    <NotificationContext.Provider value={{ showError, showSuccess, showInfo }}>
      {children}
      <Snackbar
        open={open}
        autoHideDuration={4000}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {/* Передаем severity, и теперь TypeScript и Vite довольны */}
        <Alert onClose={handleClose} severity={severity} variant="filled" sx={{ width: '100%' }}>
          {message}
        </Alert>
      </Snackbar>
    </NotificationContext.Provider>
  );
};

export const useNotification = () => {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('useNotification must be used within a NotificationProvider');
  return context;
};