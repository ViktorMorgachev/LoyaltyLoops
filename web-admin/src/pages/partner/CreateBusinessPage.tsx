import React, { useState } from 'react';
import { Container, Paper, Typography, TextField, Button, Box } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../../context/UserContext';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';

export const CreateBusinessPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { refreshUser } = useUser();

  const [name, setName] = useState('');
  const [color, setColor] = useState('#4F46E5');
  const [logo, setLogo] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCreate = async () => {
    if (!name) return showError(t('dashboard.modal_biz_name') + " is required");

    setLoading(true);
    try {
      // Отправляем расширенный запрос (Нужно обновить CreatePartnerRequest на сервере!)
      // Если на сервере пока нет полей color/logo в CreateRequest, они проигнорируются,
      // но мы подготовим фронт.
      await api.post('/partners/create', {
        businessName: name,
        countryCode: "KG",
        // color: color, // <-- Добавить в DTO на сервере
        // logoUrl: logo // <-- Добавить в DTO на сервере
      });

      // Если сервер пока не принимает цвет при создании, мы можем сразу сделать update
      // Но лучше обновить DTO. Для MVP пока просто создаем.
      // Если хочешь сразу обновить цвет - придется делать второй запрос, но давай лучше обновим сервер позже.

      showSuccess(t('common.create') + " OK");
      await refreshUser();
      navigate('/partner/dashboard');
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 8 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" gutterBottom>{t('dashboard.create_business_title')}</Typography>
        <Typography color="textSecondary" paragraph>
          {t('dashboard.create_business_subtitle')}
        </Typography>

        <TextField
          label={t('dashboard.modal_biz_name')}
          fullWidth margin="normal"
          value={name} onChange={(e) => setName(e.target.value)}
        />

        <Box mt={2} mb={1}>
             <Typography variant="body2">{t('settings.color_label')}</Typography>
             <input
                  type="color"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  style={{ width: '100%', height: '40px', cursor: 'pointer', marginTop: '8px' }}
             />
        </Box>

        <TextField
          label={t('dashboard.modal_biz_logo')}
          fullWidth margin="normal"
          value={logo} onChange={(e) => setLogo(e.target.value)}
          placeholder="https://..."
        />

        <Button
          variant="contained" fullWidth size="large" sx={{ mt: 3 }}
          onClick={handleCreate} disabled={loading}
        >
          {loading ? t('common.loading') : t('dashboard.create_business_btn')}
        </Button>
      </Paper>
    </Container>
  );
};