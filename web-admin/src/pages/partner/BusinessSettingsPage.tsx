import { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useNavigate } from 'react-router-dom';

export const BusinessSettingsPage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();
    const navigate = useNavigate();

    const [name, setName] = useState('');
    const [color, setColor] = useState('#4F46E5'); // Дефолтный Индиго
    const [logo, setLogo] = useState('');
    const [burnBonusesDays, setBurnBonusesDays] = useState('');
    const [downgradeTierDays, setDowngradeTierDays] = useState('');
    const [defaultVisitsTarget, setDefaultVisitsTarget] = useState('10');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            const res = await api.get('/partners/me');
            const data = res.data;
            setName(data.businessName || '');
            setColor(data.color || '#4F46E5');
            setLogo(data.logoUrl || '');
            setBurnBonusesDays(data.burnBonusesDays !== null && data.burnBonusesDays !== undefined ? String(data.burnBonusesDays) : '');
            setDowngradeTierDays(data.downgradeTierDays !== null && data.downgradeTierDays !== undefined ? String(data.downgradeTierDays) : '');
            setDefaultVisitsTarget(data.defaultVisitsTarget !== null && data.defaultVisitsTarget !== undefined ? String(data.defaultVisitsTarget) : '10');
        } catch (e: any) {
            if (e.response && e.response.status === 404) {
                // No business yet -> Redirect to create
                navigate('/partner/onboarding');
            } else {
                showError(getErrorMessage(e));
            }
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        try {
            const parsedVisits = Math.max(1, parseInt(defaultVisitsTarget || '10', 10) || 10);
            await api.put('/partners/me', {
                businessName: name,
                color: color,
                logoUrl: logo,
                burnBonusesDays: burnBonusesDays ? parseInt(burnBonusesDays, 10) : null,
                downgradeTierDays: downgradeTierDays ? parseInt(downgradeTierDays, 10) : null,
                defaultVisitsTarget: parsedVisits
            });
            showSuccess(t('settings.save_success'));
            await loadData();
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

                <TextField
                    label={t('settings.visits_target_label')}
                    value={defaultVisitsTarget}
                    onChange={(e) => setDefaultVisitsTarget(e.target.value)}
                    type="number"
                    inputProps={{ min: 1 }}
                    fullWidth
                    margin="normal"
                    helperText={t('settings.visits_target_hint')}
                />

                {/* Expiration Policy */}
                <Typography variant="h6" sx={{ mt: 4, mb: 1 }}>{t('settings.expiration_policy')}</Typography>
                <Typography variant="caption" color="textSecondary" display="block" mb={2}>{t('settings.expiration_hint')}</Typography>
                <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
                    <TextField 
                        label={t('point_details.burn_bonuses')} 
                        type="number"
                        fullWidth 
                        value={burnBonusesDays} 
                        onChange={e => setBurnBonusesDays(e.target.value)}
                        helperText={t('settings.burn_hint')}
                    />
                    <TextField 
                        label={t('point_details.downgrade_tier')} 
                        type="number"
                        fullWidth 
                        value={downgradeTierDays} 
                        onChange={e => setDowngradeTierDays(e.target.value)}
                        helperText={t('settings.downgrade_hint')}
                    />
                </Box>

                <Button variant="contained" sx={{ mt: 4 }} onClick={handleSave} disabled={loading}>
                    {t('common.save')}
                </Button>
            </Paper>
        </Box>
    );
};
