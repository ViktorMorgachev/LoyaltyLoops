import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { CircularProgress, Box, Typography } from '@mui/material';
import { useUser } from '../context/UserContext'; // <-- ИМПОРТ

export const DashboardPage = () => {
  const navigate = useNavigate();
  const { isSuperAdmin, isPartner, loading } = useUser(); // <-- БЕРЕМ ИЗ КОНТЕКСТА

  useEffect(() => {
    if (loading) return;

    if (isSuperAdmin) {
      navigate('/admin/partners');
    } else if (isPartner) {
      navigate('/partner/dashboard');
    } else {
      navigate('/partner/onboarding');
    }
  }, [isSuperAdmin, isPartner, loading, navigate]);

  return (
    <Box display="flex" flexDirection="column" alignItems="center" mt={10}>
      <CircularProgress />
      <Typography variant="caption" sx={{ mt: 2 }}>Загрузка...</Typography>
    </Box>
  );
};