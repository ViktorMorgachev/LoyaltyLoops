import React from 'react';
import { Box, Typography, Button, Paper } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export const NotFoundPage = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <Box minHeight="100vh" display="flex" alignItems="center" justifyContent="center" bgcolor="#f5f5f5" px={2}>
      <Paper sx={{ p: 4, textAlign: 'center', maxWidth: 480 }}>
        <Typography variant="h3" fontWeight="bold">
          404
        </Typography>
        <Typography variant="h5" gutterBottom>
          {t('not_found.title')}
        </Typography>
        <Typography variant="body1" color="text.secondary" gutterBottom>
          {t('not_found.subtitle')}
        </Typography>
        <Box mt={3} display="flex" gap={2} justifyContent="center">
          <Button variant="contained" onClick={() => navigate('/profile')}>
            {t('not_found.to_profile')}
          </Button>
          <Button variant="outlined" onClick={() => navigate('/login')}>
            {t('not_found.to_login')}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

