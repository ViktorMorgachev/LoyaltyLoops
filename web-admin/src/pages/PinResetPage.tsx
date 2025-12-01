import { useState, useEffect } from 'react';
import { Box, Paper, Typography, TextField, Button } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { api } from '../api/axiosConfig';
import { useSearchParams, useNavigate } from 'react-router-dom';

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
    <Box maxWidth={480} mx="auto" mt={8}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" gutterBottom>{t('pin_reset.title')}</Typography>
        <Typography color="textSecondary" paragraph>
          {t('pin_reset.subtitle')}
        </Typography>

        <TextField
          label={t('pin_reset.token_label')}
          value={token}
          onChange={(e) => setToken(e.target.value)}
          fullWidth
          margin="normal"
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
          sx={{ mt: 3 }}
          onClick={handleSubmit}
          disabled={loading}
        >
          {loading ? t('common.loading') : t('pin_reset.submit')}
        </Button>
      </Paper>
    </Box>
  );
};

