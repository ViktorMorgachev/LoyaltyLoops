import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Paper, Typography, Box, TextField, Button, IconButton, Tooltip, List, ListItem, ListItemText, ListItemAvatar, Avatar, Divider, Chip, Alert, LinearProgress } from '@mui/material';
import { ContentCopy as ContentCopyIcon, Store as StoreIcon, AdminPanelSettings as AdminIcon, CheckCircle as CheckIcon } from '@mui/icons-material';
import { api } from '../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useUser } from '../context/UserContext';
import type { Workspace } from '../context/UserContext';
import { useNavigate } from 'react-router-dom';
import { usePinVerification } from '../hooks/usePinVerification';

export const ProfilePage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();
    const { user, workspaces, currentWorkspace, selectWorkspace, refreshUser, loading } = useUser();
    const navigate = useNavigate();
    
    const [profile, setProfile] = useState<any>(user ?? {});
    const [pinForm, setPinForm] = useState({ current: '', next: '', confirm: '' });
    const [pinLoading, setPinLoading] = useState(false);
    const [resetLoading, setResetLoading] = useState(false);
    const [hasOwnerPin, setHasOwnerPin] = useState<boolean | null>(null);
    const [pinMetaLoading, setPinMetaLoading] = useState(false);
    const uniqueWorkspaces = useMemo(() => {
        const list: Workspace[] = [];
        const seen = new Set<string>();
        workspaces.forEach((ws) => {
            const key = `${ws.id}-${ws.role}`;
            if (!seen.has(key)) {
                seen.add(key);
                list.push(ws);
            }
        });
        return list;
    }, [workspaces]);

    const hasPlatformRole = workspaces.some(
        (ws) => ws.role === 'PLATFORM_MANAGER' || ws.role === 'PLATFORM_SUPER_ADMIN'
    );
    const hasPartnerRole = workspaces.some(
        (ws) => ws.role === 'PARTNER_MANAGER' || ws.role === 'PARTNER_ADMIN'
    );
    const isPartnerOwner = workspaces.some((ws) => ws.role === 'PARTNER_ADMIN');
    const isFrozen = Boolean(profile?.isFrozenUntil && profile.isFrozenUntil > Date.now());
    const hasEmail = Boolean(profile?.email && profile.email.trim().length > 0);
    const frozenLabel = isFrozen && profile?.isFrozenUntil
        ? new Date(profile.isFrozenUntil).toLocaleString()
        : null;
    const requiresCurrentPin = hasOwnerPin !== false;
    const pinInputsDisabled = pinLoading || resetLoading || isFrozen || pinMetaLoading;

    useEffect(() => {
        if (!user && !loading) {
            refreshUser();
        } else if (user) {
            setProfile(user);
        }
    }, [user, loading, refreshUser]);

    const loadPartnerMeta = useCallback(async () => {
        if (!isPartnerOwner) return;
        setPinMetaLoading(true);
        try {
            const res = await api.get('/partners/me');
            setHasOwnerPin(Boolean(res.data?.hasPin));
        } catch (error) {
            console.error('Failed to load partner data', error);
            setHasOwnerPin(true);
        } finally {
            setPinMetaLoading(false);
        }
    }, [isPartnerOwner]);

    useEffect(() => {
        if (isPartnerOwner) {
            loadPartnerMeta();
        } else {
            setHasOwnerPin(null);
        }
    }, [isPartnerOwner, loadPartnerMeta]);

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

    const proceedWorkspaceSwitch = useCallback((ws: Workspace) => {
        selectWorkspace(ws);

        if (ws.role === 'PLATFORM_SUPER_ADMIN') navigate('/admin/partners');
        else if (ws.role === 'PARTNER_ADMIN') navigate('/partner/dashboard');
        else navigate('/dashboard');

        showSuccess(t('profile.switched_success', { name: ws.title }));
    }, [navigate, selectWorkspace, showSuccess, t]);

    const { requestPinVerification, PinDialog } = usePinVerification(proceedWorkspaceSwitch);

    const handleSwitch = (ws: Workspace) => {
        requestPinVerification(ws);
    };

    const handlePinChange = async () => {
        if (requiresCurrentPin && pinForm.current.trim().length < 4) {
            showError(t('profile.pin_current_required'));
            return;
        }
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
                currentPin: requiresCurrentPin ? pinForm.current : undefined,
                newPin: pinForm.next
            });
            setPinForm({ current: '', next: '', confirm: '' });
            if (!requiresCurrentPin) {
                setHasOwnerPin(true);
                showSuccess(t('profile.pin_set_success'));
            } else {
                showSuccess(t('profile.pin_change_success'));
            }
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
        <>
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
                    <Typography variant="h6" gutterBottom>
                        {hasOwnerPin === false ? t('profile.pin_set_title') : t('profile.pin_section_title')}
                    </Typography>
                    {pinMetaLoading ? (
                        <LinearProgress />
                    ) : (
                        <Box display="flex" flexDirection="column" gap={2}>
                            {hasOwnerPin === false && (
                                <Typography variant="body2" color="text.secondary">
                                    {t('profile.pin_set_hint')}
                                </Typography>
                            )}
                            {requiresCurrentPin && (
                                <TextField
                                    label={t('profile.pin_current_label')}
                                    type="password"
                                    value={pinForm.current}
                            autoComplete="off"
                                    onChange={(e) => {
                                        const digitsOnly = e.target.value.replace(/\D+/g, '');
                                        setPinForm({ ...pinForm, current: digitsOnly });
                                    }}
                                    inputProps={{ maxLength: 12, inputMode: 'numeric' }}
                                    disabled={pinInputsDisabled}
                                />
                            )}
                            <TextField
                                label={t('profile.pin_new_label')}
                                type="password"
                                value={pinForm.next}
                            autoComplete="new-password"
                                onChange={(e) => {
                                    const digitsOnly = e.target.value.replace(/\D+/g, '');
                                    setPinForm({ ...pinForm, next: digitsOnly });
                                }}
                                inputProps={{ maxLength: 12, inputMode: 'numeric' }}
                                disabled={pinInputsDisabled}
                            />
                            <TextField
                                label={t('profile.pin_confirm_label')}
                                type="password"
                                value={pinForm.confirm}
                            autoComplete="new-password"
                                onChange={(e) => {
                                    const digitsOnly = e.target.value.replace(/\D+/g, '');
                                    setPinForm({ ...pinForm, confirm: digitsOnly });
                                }}
                                inputProps={{ maxLength: 12, inputMode: 'numeric' }}
                                disabled={pinInputsDisabled}
                            />
                            <Button variant="contained" onClick={handlePinChange} disabled={pinInputsDisabled}>
                                {pinLoading ? t('common.loading') : hasOwnerPin === false ? t('profile.pin_set_btn') : t('profile.pin_change_btn')}
                            </Button>
                            {requiresCurrentPin && (
                                <>
                                    <Button
                                        color="error"
                                        variant="outlined"
                                        onClick={handlePinResetRequest}
                                        disabled={resetLoading || !hasEmail || isFrozen || pinMetaLoading}
                                    >
                                        {resetLoading ? t('common.loading') : t('profile.pin_reset_btn')}
                                    </Button>
                                    {!hasEmail && (
                                        <Typography variant="body2" color="text.secondary">
                                            {t('profile.pin_email_hint')}
                                        </Typography>
                                    )}
                                </>
                            )}
                        </Box>
                    )}
                </Paper>
            )}

            <Typography variant="h5" gutterBottom>{t('profile.my_workspaces')}</Typography>
            <Paper>
                <List>
                    {uniqueWorkspaces.map((ws) => {
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
                    {!hasPlatformRole && (
                        <Button variant="outlined" onClick={() => navigate('/join/platform-manager')}>
                            {t('menu.join_platform_manager')}
                        </Button>
                    )}
                    {!hasPartnerRole && (
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
        {PinDialog}
        </>
    );
};
