import React, { useState } from 'react';
import { Container, Paper, Typography, TextField, Button, Box } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useNavigate } from 'react-router-dom';

export const CreateBusinessPage = () => {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCreate = async () => {
    if (!name) return alert("Введите название");

    setLoading(true);
    try {
      // Вызываем API создания партнера
      await api.post('/partner/create', {
        businessName: name,
        countryCode: "KG" // Пока хардкод, потом можно добавить Select
      });

      alert('Бизнес успешно создан!');
      // После успеха идем в Диспетчер, он увидит новую роль и кинет на Дашборд
      navigate('/dashboard');
      // Перезагрузка страницы нужна, чтобы обновить меню (так как роли поменялись)
      window.location.href = '/dashboard';
    } catch (e) {
      alert('Ошибка создания бизнеса. Возможно, у вас уже есть бизнес.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 8 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" gutterBottom>Запуск LoyaltyLoop</Typography>
        <Typography color="textSecondary" paragraph>
          У вас пока нет активного бизнеса. Давайте его создадим!
        </Typography>

        <TextField
          label="Название компании"
          fullWidth
          margin="normal"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Например: Кофейня Sierra"
        />

        <Button
          variant="contained"
          fullWidth
          size="large"
          sx={{ mt: 3 }}
          onClick={handleCreate}
          disabled={loading}
        >
          {loading ? "Создание..." : "Создать Бизнес"}
        </Button>
      </Paper>
    </Container>
  );
};