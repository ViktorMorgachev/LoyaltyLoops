import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/axiosConfig';
import { CircularProgress, Box, Typography } from '@mui/material';

export const DashboardPage = () => {
  const navigate = useNavigate();

  useEffect(() => {
    const checkRole = async () => {
      try {
        // Запрашиваем профиль, чтобы узнать актуальные права
        const res = await api.get('/client/me');
        const workspaces = res.data.workspaces || [];

        // Проверяем роли
        const isSuper = workspaces.some((w: any) => w.role === 'PLATFORM_SUPER_ADMIN');
        const isPartner = workspaces.some((w: any) => w.role === 'PARTNER_ADMIN');

        if (isSuper) {
          // Если Админ -> идем в админку
          navigate('/admin/partners');
        } else if (isPartner) {
          // Если Партнер -> идем к своим точкам
          navigate('/partner/dashboard');
        } else {
          // Если просто юзер -> идем создавать бизнес
          navigate('/partner/onboarding');
        }
      } catch (e) {
        console.error("Auth check failed", e);
        navigate('/login');
      }
    };

    checkRole();
  }, [navigate]);

  return (
    <Box display="flex" flexDirection="column" alignItems="center" mt={10}>
      <CircularProgress />
      <Typography variant="caption" sx={{ mt: 2 }}>Проверка прав доступа...</Typography>
    </Box>
  );
};