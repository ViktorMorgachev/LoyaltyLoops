import React, { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button, IconButton, Tooltip, List, ListItem, ListItemText, ListItemAvatar, Avatar, Divider, Chip } from '@mui/material';
import { ContentCopy as ContentCopyIcon, Store as StoreIcon, AdminPanelSettings as AdminIcon, CheckCircle as CheckIcon } from '@mui/icons-material';
import { api } from '../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useUser } from '../context/UserContext';
import { useNavigate } from 'react-router-dom';

export const ProfilePage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();
    const { workspaces, currentWorkspace, selectWorkspace } = useUser();
    const navigate = useNavigate();
    
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

    const handleSwitch = (ws: any) => {
        selectWorkspace(ws);
        
        if (ws.role === 'PLATFORM_SUPER_ADMIN') navigate('/admin/partners');
        else if (ws.role === 'PARTNER_ADMIN') navigate('/partner/dashboard');
        else navigate('/dashboard');
        
        showSuccess(t('profile.switched_success', { name: ws.title }));
    };

    return (
        <Box maxWidth={800}>
            <Typography variant="h4" gutterBottom>{t('profile.title')}</Typography>

            <Paper sx={{ p: 3, mb: 4 }}>
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

            <Typography variant="h5" gutterBottom>{t('profile.my_workspaces')}</Typography>
            <Paper>
                <List>
                    {workspaces.map((ws: any) => {
                        const isCurrent = currentWorkspace?.id === ws.id;
                        return (
                            <React.Fragment key={ws.id}>
                                <ListItem 
                                    secondaryAction={
                                        isCurrent ? 
                                        <Chip label={t('profile.current_role')} color="success" size="small" icon={<CheckIcon />} /> :
                                        <Button size="small" variant="outlined" onClick={() => handleSwitch(ws)}>{t('profile.switch_role')}</Button>
                                    }
                                >
                                    <ListItemAvatar>
                                        <Avatar sx={{ bgcolor: ws.role === 'PLATFORM_SUPER_ADMIN' ? 'error.main' : 'primary.main' }}>
                                            {ws.role === 'PLATFORM_SUPER_ADMIN' ? <AdminIcon /> : <StoreIcon />}
                                        </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText 
                                        primary={ws.title} 
                                        secondary={
                                            <>
                                                <Typography component="span" variant="body2" color="text.primary">
                                                    {ws.role.replace('_', ' ')}
                                                </Typography>
                                                {ws.requirePin && ` • ${t('profile.pin_protected')}`}
                                            </>
                                        } 
                                    />
                                </ListItem>
                                <Divider variant="inset" component="li" />
                            </React.Fragment>
                        );
                    })}
                    {workspaces.length === 0 && (
                        <ListItem>
                            <ListItemText primary={t('profile.no_workspaces')} />
                        </ListItem>
                    )}
                </List>
            </Paper>
        </Box>
    );
};
