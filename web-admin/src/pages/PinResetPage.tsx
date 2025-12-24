import { useState, useEffect } from 'react';
import { Box, Paper, Typography, TextField, Button, Avatar, CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { api } from '../api/axiosConfig';
import { useSearchParams, useNavigate } from 'react-router-dom';
import LockResetIcon from '@mui/icons-material/LockReset';

export const PinResetPage = () => {
  const { t } = useTranslation();
  const { showSuccess, showError } = useNotification();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const initialToken = params.get('token') || '';
  const [token, setToken] = useState(initialToken);
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (initialToken) {
      setToken(initialToken);
    }
  }, [initialToken]);

  const handleSubmit = async () => {
    if (!token.trim()) {
      showError(t('pin_reset.token_required'));
      return;
    }
    if (pin.length < 4) {
      showError(t('profile.pin_length_error'));
      return;
    }
    if (pin !== confirmPin) {
      showError(t('profile.pin_confirm_error'));
      return;
    }

    setLoading(true);
    try {
      await api.post('/partners/pin/reset/confirm', { token, newPin: pin });
      showSuccess(t('pin_reset.success'));
      navigate('/login');
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box 
        sx={{ 
            minHeight: '100vh', 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center', 
            bgcolor: '#f0f2f5',
            p: 2
        }}
    >
      <Paper elevation={0} sx={{ p: 5, width: '100%', maxWidth: 440, borderRadius: 4, textAlign: 'center', border: '1px solid', borderColor: 'divider' }}>
        <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main', width: 64, height: 64, mx: 'auto', mb: 3 }}>
            <LockResetIcon fontSize="large" />
        </Avatar>

        <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {t('pin_reset.title')}
        </Typography>
        <Typography color="text.secondary" paragraph sx={{ mb: 4 }}>
          {t('pin_reset.subtitle')}
        </Typography>

        <TextField
          label={t('pin_reset.token_label')}
          value={token}
          onChange={(e) => setToken(e.target.value)}
          fullWidth
          margin="normal"
          variant="outlined"
        />

        <TextField
          label={t('profile.pin_new_label')}
          type="password"
          value={pin}
          onChange={(e) => setPin(e.target.value.replaceAll(' ', ''))}
          inputProps={{ maxLength: 12, inputMode: 'numeric', pattern: '[0-9]*' }}
          fullWidth
          margin="normal"
        />

        <TextField
          label={t('profile.pin_confirm_label')}
          type="password"
          value={confirmPin}
          onChange={(e) => setConfirmPin(e.target.value.replaceAll(' ', ''))}
          inputProps={{ maxLength: 12, inputMode: 'numeric', pattern: '[0-9]*' }}
          fullWidth
          margin="normal"
        />

        <Button
          variant="contained"
          fullWidth
          size="large"
          sx={{ mt: 4, borderRadius: 3, py: 1.5, fontSize: '1.1rem' }}
          onClick={handleSubmit}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={20} color="inherit" /> : null}
        >
          {loading ? t('common.loading') : t('pin_reset.submit')}
        </Button>
        
        <Button variant="text" sx={{ mt: 2 }} onClick={() => navigate('/login')}>
            {t('common.cancel')}
        </Button>
      </Paper>
    </Box>
  );
};

