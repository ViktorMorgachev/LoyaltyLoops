import React from 'react';
import { Button, Container, Typography, Box } from '@mui/material';
import { useNavigate } from 'react-router-dom';

export const DashboardPage = () => {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <Container>
      <Box mt={5}>
        <Typography variant="h4">Добро пожаловать в Админку!</Typography>
        <Button variant="outlined" color="error" onClick={handleLogout} sx={{ mt: 2 }}>
            Выйти
        </Button>
      </Box>
    </Container>
  );
};