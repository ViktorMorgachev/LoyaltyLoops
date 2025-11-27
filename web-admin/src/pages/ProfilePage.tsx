import React, { useEffect, useMemo, useState } from 'react';
import { Paper, Typography, Box, TextField, Button, IconButton, Tooltip, List, ListItem, ListItemText, ListItemAvatar, Avatar, Divider, Chip, Alert } from '@mui/material';
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
    const { user, workspaces, currentWorkspace, selectWorkspace, refreshUser, loading } = useUser();
    const navigate = useNavigate();
    
    const [profile, setProfile] = useState<any>(user ?? {});
    const [pinForm, setPinForm] = useState({ current: '', next: '', confirm: '' });
    const [pinLoading, setPinLoading] = useState(false);
    const [resetLoading, setResetLoading] = useState(false);
    const uniqueWorkspaces = useMemo(() => {
        const list: any[] = [];
        const seen = new Set<string>();
        workspaces.forEach((ws: any) => {
            const key = `${ws.id}-${ws.role}`;
            if (!seen.has(key)) {
                seen.add(key);
                list.push(ws);
            }
        });
        return list;
    }, [workspaces]);

    const hasPlatformManager = workspaces.some((ws: any) => ws.role === 'PLATFORM_MANAGER');
    const hasPartnerManager = workspaces.some((ws: any) => ws.role === 'PARTNER_MANAGER');
    const isPartnerOwner = workspaces.some((ws: any) => ws.role === 'PARTNER_ADMIN');
    const isFrozen = Boolean(profile?.isFrozenUntil && profile.isFrozenUntil > Date.now());
    const hasEmail = Boolean(profile?.email && profile.email.trim().length > 0);
    const frozenLabel = isFrozen && profile?.isFrozenUntil
        ? new Date(profile.isFrozenUntil).toLocaleString()
        : null;

    useEffect(() => {
        if (!user && !loading) {
            refreshUser();
        } else if (user) {
            setProfile(user);
        }
    }, [user, loading, refreshUser]);

    const copyId = () => {
        if (profile.userId) {
            navigator.clipboard.writeText(profile.userId);
            showSuccess(t('common.copied'));
        }
    };

    const handleSave = async () => {
        try {
            await api.post('/client/profile', { firstName: profile.firstName, email: profile.email });
            showSuccess(t('common.save') + " OK");
            await refreshUser();
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

    const handlePinChange = async () => {
        if (pinForm.next.length < 4) {
            showError(t('profile.pin_length_error'));
            return;
        }
        if (pinForm.next !== pinForm.confirm) {
            showError(t('profile.pin_confirm_error'));
            return;
        }
        setPinLoading(true);
        try {
            await api.put('/partners/pin', {
                currentPin: pinForm.current || undefined,
                newPin: pinForm.next
            });
            setPinForm({ current: '', next: '', confirm: '' });
            showSuccess(t('profile.pin_change_success'));
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setPinLoading(false);
        }
    };

    const handlePinResetRequest = async () => {
        setResetLoading(true);
        try {
            await api.post('/partners/pin/reset/request');
            showSuccess(t('profile.pin_reset_email_sent'));
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setResetLoading(false);
        }
    };

    return (
        <Box maxWidth={800}>
            <Typography variant="h4" gutterBottom>{t('profile.title')}</Typography>

            <Paper sx={{ p: 3, mb: 4 }}>
                {isFrozen && frozenLabel && (
                    <Alert severity="warning" sx={{ mb: 2 }}>
                        {t('profile.freeze_alert', { date: frozenLabel })}
                    </Alert>
                )}
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

                <TextField
                    label={t('profile.email_label')}
                    value={profile.email || ''}
                    onChange={(e) => setProfile({ ...profile, email: e.target.value })}
                    fullWidth
                    margin="normal"
                    type="email"
                />

                <Button variant="contained" sx={{ mt: 2 }} onClick={handleSave}>
                    {t('profile.save_btn')}
                </Button>
            </Paper>

            {isPartnerOwner && (
                <Paper sx={{ p: 3, mb: 4 }}>
                    <Typography variant="h6" gutterBottom>{t('profile.pin_section_title')}</Typography>
                    <Box display="flex" flexDirection="column" gap={2}>
                        <TextField
                            label={t('profile.pin_current_label')}
                            type="password"
                            value={pinForm.current}
                            onChange={(e) => setPinForm({ ...pinForm, current: e.target.value.replaceAll(" ", "") })}
                            inputProps={{ maxLength: 12, inputMode: 'numeric', pattern: '[0-9]*' }}
                            disabled={pinLoading || resetLoading || isFrozen}
                        />
                        <TextField
                            label={t('profile.pin_new_label')}
                            type="password"
                            value={pinForm.next}
                            onChange={(e) => setPinForm({ ...pinForm, next: e.target.value.replaceAll(" ", "") })}
                            inputProps={{ maxLength: 12, inputMode: 'numeric', pattern: '[0-9]*' }}
                            disabled={pinLoading || resetLoading || isFrozen}
                        />
                        <TextField
                            label={t('profile.pin_confirm_label')}
                            type="password"
                            value={pinForm.confirm}
                            onChange={(e) => setPinForm({ ...pinForm, confirm: e.target.value.replaceAll(" ", "") })}
                            inputProps={{ maxLength: 12, inputMode: 'numeric', pattern: '[0-9]*' }}
                            disabled={pinLoading || resetLoading || isFrozen}
                        />
                        <Button variant="contained" onClick={handlePinChange} disabled={pinLoading || resetLoading || isFrozen}>
                            {pinLoading ? t('common.loading') : t('profile.pin_change_btn')}
                        </Button>
                        <Button color="error" variant="outlined" onClick={handlePinResetRequest} disabled={resetLoading || !hasEmail}>
                            {resetLoading ? t('common.loading') : t('profile.pin_reset_btn')}
                        </Button>
                        {!hasEmail && (
                            <Typography variant="body2" color="textSecondary">
                                {t('profile.pin_email_hint')}
                            </Typography>
                        )}
                    </Box>
                </Paper>
            )}

            <Typography variant="h5" gutterBottom>{t('profile.my_workspaces')}</Typography>
            <Paper>
                <List>
                    {uniqueWorkspaces.map((ws: any) => {
                        const isCurrent = currentWorkspace?.id === ws.id && currentWorkspace?.role === ws.role;
                        return (
                            <React.Fragment key={`${ws.id}-${ws.role}`}>
                                <ListItem
                                    secondaryAction={
                                        isCurrent
                                            ? <Chip label={t('profile.current_role')} color="success" size="small" icon={<CheckIcon />} />
                                            : <Button size="small" variant="outlined" onClick={() => handleSwitch(ws)}>{t('profile.switch_role')}</Button>
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

            <Typography variant="h5" gutterBottom sx={{ mt: 4 }}>{t('profile.join_section')}</Typography>
            <Paper sx={{ p: 3 }}>
                <Box display="flex" flexDirection={{ xs: 'column', sm: 'row' }} gap={2} flexWrap="wrap">
                    {!hasPlatformManager && (
                        <Button variant="outlined" onClick={() => navigate('/join/platform-manager')}>
                            {t('menu.join_platform_manager')}
                        </Button>
                    )}
                    {!hasPartnerManager && (
                        <Button variant="outlined" onClick={() => navigate('/join/partner')}>
                            {t('menu.join_partner_manager')}
                        </Button>
                    )}
                    <Button variant="text" onClick={() => navigate('/about')}>
                        {t('menu.about_project')}
                    </Button>
                </Box>
            </Paper>
        </Box>
    );
};
