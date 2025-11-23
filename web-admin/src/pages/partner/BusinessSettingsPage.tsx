import React, { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';

export const BusinessSettingsPage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();

    const [name, setName] = useState('');
    const [color, setColor] = useState('#4F46E5'); // Дефолтный Индиго
    const [logo, setLogo] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            // Загружаем текущие настройки
            const res = await api.get('/partners/me');
            const data = res.data;
            setName(data.name); // В PartnerEntity поле называется name
            setColor(data.color || '#4F46E5');
            setLogo(data.logoUrl || '');
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        try {
            await api.put('/partners/me', {
                businessName: name,
                color: color,
                logoUrl: logo
            });
            showSuccess(t('settings.save_success'));
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    return (
        <Box>
            <Typography variant="h4" gutterBottom>{t('settings.title')}</Typography>

            <Paper sx={{ p: 4, maxWidth: 600 }}>

                {/* Название */}
                <TextField
                    label={t('settings.name_label')}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    fullWidth margin="normal"
                />

                {/* Выбор цвета (Native Color Picker) */}
                <Box mt={2} mb={2}>
                    <Typography variant="body2" gutterBottom>{t('settings.color_label')}</Typography>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <input
                            type="color"
                            value={color}
                            onChange={(e) => setColor(e.target.value)}
                            style={{ width: '60px', height: '40px', cursor: 'pointer', border: 'none' }}
                        />
                        <Typography variant="caption" color="textSecondary">
                            {t('settings.color_helper')}
                        </Typography>
                    </div>
                </Box>

                {/* Логотип */}
                <TextField
                    label={t('settings.logo_label')}
                    value={logo}
                    onChange={(e) => setLogo(e.target.value)}
                    fullWidth margin="normal"
                    placeholder="https://example.com/logo.png"
                />

                <Button variant="contained" sx={{ mt: 3 }} onClick={handleSave} disabled={loading}>
                    {t('common.save')}
                </Button>
            </Paper>
        </Box>
    );
};