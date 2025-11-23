import React, { useState } from 'react';
import { Box, Paper, Typography, TextField, Button, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';

export const JoinPlatformManagerPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const [inviteCode, setInviteCode] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteCode.trim()) return;
    setLoading(true);
    try {
      await api.post('/admin/platform/manager/join', { inviteCode: inviteCode.trim() });
      showSuccess(t('join_platform_manager.success'));
      setInviteCode('');
    } catch (error: any) {
      showError(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box maxWidth="sm">
      <Typography variant="h4" fontWeight="bold" gutterBottom>
        {t('join_platform_manager.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" gutterBottom>
        {t('join_platform_manager.subtitle')}
      </Typography>

      <Paper sx={{ p: 3, mt: 2 }} component="form" onSubmit={handleSubmit}>
        <TextField
          fullWidth
          label={t('join_platform_manager.code_label')}
          value={inviteCode}
          onChange={(e) => setInviteCode(e.target.value)}
          margin="normal"
        />
        <Button
          variant="contained"
          type="submit"
          disabled={loading || !inviteCode.trim()}
        >
          {t('join_platform_manager.submit')}
        </Button>

        <Alert severity="info" sx={{ mt: 3 }}>
          {t('join_platform_manager.info')}
        </Alert>
      </Paper>
    </Box>
  );
};

