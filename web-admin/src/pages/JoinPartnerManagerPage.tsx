import React, { useState } from 'react';
import { Box, Paper, Typography, TextField, Button, Alert, Avatar } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import StorefrontIcon from '@mui/icons-material/Storefront';

export const JoinPartnerManagerPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const [inviteCode, setInviteCode] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteCode.trim()) return;
    setLoading(true);
    try {
      await api.post('/partners/managers/join', { inviteCode: inviteCode.trim() });
      showSuccess(t('join_partner.success'));
      setInviteCode('');
    } catch (error: any) {
      showError(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box maxWidth="sm" mx="auto" mt={8}>
      <Paper sx={{ p: 5, borderRadius: 4, textAlign: 'center', border: '1px solid', borderColor: 'divider' }} elevation={0}>
        <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main', width: 64, height: 64, mx: 'auto', mb: 3 }}>
            <StorefrontIcon fontSize="large" />
        </Avatar>

        <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #3b82f6 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
        {t('join_partner.title')}
      </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 4, maxWidth: 400, mx: 'auto' }}>
        {t('join_partner.subtitle')}
      </Typography>

        <Box component="form" onSubmit={handleSubmit} noValidate>
        <TextField
          fullWidth
          label={t('join_partner.code_label')}
          value={inviteCode}
          onChange={(e) => setInviteCode(e.target.value)}
          margin="normal"
              variant="outlined"
              placeholder="INV-..."
        />
        <Button
          variant="contained"
          type="submit"
              fullWidth
              size="large"
          disabled={loading || !inviteCode.trim()}
              sx={{ mt: 3, py: 1.5, borderRadius: 2, fontSize: '1.1rem', fontWeight: 'bold' }}
        >
              {loading ? t('common.loading') : t('join_partner.submit')}
        </Button>

            <Alert severity="info" sx={{ mt: 4, borderRadius: 2, textAlign: 'left' }}>
          {t('join_partner.info')}
        </Alert>
        </Box>
      </Paper>
    </Box>
  );
};

