import React, { useState } from 'react';
import { api } from '../api/axiosConfig';
import {
  Button, TextField, Typography, Paper, Box,
  Alert, CircularProgress, InputAdornment
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
// Иконки (если npm install @mui/icons-material выполнен)
import PhoneIcon from '@mui/icons-material/Phone';
import LockIcon from '@mui/icons-material/Lock';
import LoyaltyIcon from '@mui/icons-material/Loyalty'; // Или любая другая

export const LoginPage = () => {
  const navigate = useNavigate();

  const [phone, setPhone] = useState('+996');
  const [code, setCode] = useState('');
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSendCode = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.post('/auth/send-code', { phone });
      setStep(2);
    } catch (e: any) {
      console.error("Login Error:", e); // Пишем в консоль для отладки
      const msg = e.response?.data?.message || 'Ошибка соединения с сервером';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.post('/auth/login', { phone, code });
      const { accessToken, refreshToken, workspaces } = res.data;

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('workspaces', JSON.stringify(workspaces));

      navigate('/dashboard');
    } catch (e: any) {
      console.error("Login Error:", e);
      const msg = e.response?.data?.message || 'Неверный код';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh', // На весь экран
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: '#f0f2f5' // Светло-серый фон
      }}
    >
      <Paper
        elevation={4}
        sx={{
          p: 4,
          width: '100%',
          maxWidth: 400,
          borderRadius: 3,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center'
        }}
      >
        {/* Логотип / Иконка */}
        <Box sx={{
          bgcolor: 'primary.light',
          p: 2,
          borderRadius: '50%',
          mb: 2,
          color: 'primary.contrastText'
        }}>
          <LoyaltyIcon fontSize="large" />
        </Box>

        <Typography component="h1" variant="h5" fontWeight="bold" gutterBottom>
          LoyaltyLoop Admin
        </Typography>

        <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
          Вход в панель управления
        </Typography>

        {error && (
          <Alert severity="error" sx={{ width: '100%', mb: 2 }}>
            {error}
          </Alert>
        )}

        {step === 1 ? (
          // --- ВВОД ТЕЛЕФОНА ---
          <Box width="100%">
            <TextField
              fullWidth
              label="Номер телефона"
              variant="outlined"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <PhoneIcon color="action" />
                  </InputAdornment>
                ),
              }}
            />
            <Button
              fullWidth
              variant="contained"
              size="large"
              sx={{ mt: 3, borderRadius: 2 }}
              onClick={handleSendCode}
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Получить код'}
            </Button>
          </Box>
        ) : (
          // --- ВВОД КОДА ---
          <Box width="100%">
          <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
            Код отправлен (Смотри логи сервера)
          </Typography>
            <TextField
              fullWidth
              label="Код подтверждения"
              variant="outlined"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockIcon color="action" />
                  </InputAdornment>
                ),
              }}
            />
            <Button
              fullWidth
              variant="contained"
              size="large"
              sx={{ mt: 3, borderRadius: 2 }}
              onClick={handleLogin}
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Войти'}
            </Button>
            <Button
              fullWidth
              sx={{ mt: 1 }}
              onClick={() => setStep(1)}
              disabled={loading}
            >
              Изменить номер
            </Button>
          </Box>
        )}
      </Paper>

      {/* Футер */}
      <Typography variant="caption" color="textSecondary" sx={{ position: 'absolute', bottom: 20 }}>
        © 2025 LoyaltyLoop Inc.
      </Typography>
    </Box>
  );
};