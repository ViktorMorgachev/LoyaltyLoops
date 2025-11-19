import React, { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button, IconButton, Tooltip } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api } from '../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';

export const ProfilePage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();
    const [profile, setProfile] = useState<any>({});

    useEffect(() => {
        api.get('/client/me')
           .then(res => setProfile(res.data))
           .catch(e => showError(getErrorMessage(e)));
    }, []);

    const copyId = () => {
        navigator.clipboard.writeText(profile.userId);
        showSuccess(t('common.copied'));
    };

    const handleSave = async () => {
        try {
            await api.post('/client/profile', { firstName: profile.firstName });
            showSuccess(t('common.save') + " OK");
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    return (
        <Box>
            <Typography variant="h4" gutterBottom>{t('profile.title')}</Typography>

            <Paper sx={{ p: 3, maxWidth: 600 }}>
                <Box display="flex" alignItems="center" gap={1}>
                    <TextField
                        label={t('profile.id_label')}
                        value={profile.userId || ''}
                        fullWidth margin="normal" disabled
                        InputProps={{
                            endAdornment: (
                                <Tooltip title={t('common.copied')}>
                                    <IconButton onClick={copyId} edge="end">
                                        <ContentCopyIcon />
                                    </IconButton>
                                </Tooltip>
                            )
                        }}
                    />
                </Box>

                <TextField label={t('profile.phone_label')} value={profile.phone || ''} fullWidth margin="normal" disabled />

                <TextField
                    label={t('profile.name_label')}
                    value={profile.firstName || ''}
                    onChange={(e) => setProfile({...profile, firstName: e.target.value})}
                    fullWidth margin="normal"
                />

                <Button variant="contained" sx={{ mt: 2 }} onClick={handleSave}>
                    {t('profile.save_btn')}
                </Button>
            </Paper>
        </Box>
    );
};