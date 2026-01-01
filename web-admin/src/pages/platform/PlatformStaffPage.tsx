import { useEffect, useState } from 'react';
import { Container, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip, Box, Button, Dialog, DialogTitle, DialogContent, DialogActions, IconButton, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useUser } from '../../context/UserContext';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import DeleteIcon from '@mui/icons-material/Delete';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { maskPhone } from '../../utils/maskPhone';

export const PlatformStaffPage = () => {
    const { t } = useTranslation();
    const { showError, showSuccess } = useNotification();
    const { isSuperAdmin, isSuperManager, user, currentWorkspace } = useUser();
    
    const [staff, setStaff] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    
    // Invite Dialog
    const [openInvite, setOpenInvite] = useState(false);
    const [inviteRole, setInviteRole] = useState('PLATFORM_MANAGER');
    const [generatedCode, setGeneratedCode] = useState<string | null>(null);

    useEffect(() => {
        loadStaff();
    }, []);

    const loadStaff = async () => {
        setLoading(true);
        try {
            const res = await api.get('/platform/staff');
            setStaff(res.data);
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    const handleGenerateInvite = async () => {
        try {
            const res = await api.post('/platform/invite', null, { params: { role: inviteRole } });
            setGeneratedCode(res.data.code);
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    const handleCopyCode = () => {
        if (generatedCode) {
            navigator.clipboard.writeText(generatedCode);
            showSuccess(t('platform.code_copied'));
        }
    };

    const handleDeleteStaff = async (id: string) => {
        if (!confirm(t('platform.confirm_delete_staff'))) return;
        try {
            await api.delete(`/platform/staff/${id}`);
            showSuccess(t('platform.staff_removed'));
            loadStaff();
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    const canInviteSuperManager = isSuperAdmin;

    // Guard: only super admin/manager with workspace
    if (!currentWorkspace || (!isSuperAdmin && !isSuperManager)) {
        return null;
    }

    // Guard: only super admin/manager with workspace
    if (!currentWorkspace || (!isSuperAdmin && !isSuperManager)) {
        return null;
    }

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Typography variant="h4" fontWeight="800" sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                    {t('platform.staff_title')}
                </Typography>
                {(isSuperAdmin || isSuperManager) && (
                    <Button 
                        variant="contained" 
                        startIcon={<PersonAddIcon />} 
                        onClick={() => { setOpenInvite(true); setGeneratedCode(null); }}
                        disabled={loading}
                    >
                        {t('platform.invite_member')}
                    </Button>
                )}
            </Box>

            <Paper sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }} elevation={0}>
                <Table>
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                        <TableRow>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.name')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.role')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.phone')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.status')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {staff.map(s => {
                            const isSelf = s.userId === user?.userId;
                            const targetRole = s.pointName; // Role is stored in pointName in DTO
                            
                            const canDelete = (() => {
                                if (isSelf) return false;
                                if (isSuperAdmin) return true;
                                if (isSuperManager) {
                                    return targetRole === 'PLATFORM_MANAGER';
                                }
                                return false;
                            })();

                            const showPhone = isSuperAdmin || isSuperManager || isSelf;

                            return (
                                <TableRow key={s.id} hover>
                                    <TableCell>{s.name}</TableCell>
                                    <TableCell>
                                        <Chip 
                                            label={s.pointName?.replace('PLATFORM_', '') || 'Unknown'} 
                                            color={s.pointName === 'PLATFORM_SUPER_ADMIN' ? 'error' : s.pointName === 'PLATFORM_SUPER_MANAGER' ? 'warning' : 'primary'} 
                                            size="small" 
                                            variant="outlined"
                                        />
                                    </TableCell>
                                    <TableCell>
                                        {showPhone ? s.phone : maskPhone(s.phone)}
                                    </TableCell>
                                    <TableCell>
                                        <Chip label={s.active ? t('platform.active') : t('platform.inactive')} color={s.active ? 'success' : 'default'} size="small" />
                                    </TableCell>
                                    <TableCell>
                                        {canDelete && (
                                            <IconButton 
                                                color="error" 
                                                onClick={() => handleDeleteStaff(s.id)}
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        )}
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                        {staff.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={5} align="center" sx={{ py: 6, color: 'text.secondary' }}>
                                    {t('platform.no_staff')}
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </Paper>

            {/* Invite Dialog */}
            <Dialog open={openInvite} onClose={() => setOpenInvite(false)} fullWidth maxWidth="xs">
                <DialogTitle>{t('platform.invite_dialog_title')}</DialogTitle>
                <DialogContent>
                    {!generatedCode ? (
                        <Box pt={1}>
                            <FormControl fullWidth margin="normal">
                                <InputLabel>{t('platform.invite_role_label')}</InputLabel>
                                <Select
                                    value={inviteRole}
                                    label={t('platform.invite_role_label')}
                                    onChange={(e) => setInviteRole(e.target.value)}
                                >
                                    <MenuItem value="PLATFORM_MANAGER">Manager</MenuItem>
                                    {canInviteSuperManager && (
                                        <MenuItem value="PLATFORM_SUPER_MANAGER">Super Manager</MenuItem>
                                    )}
                                </Select>
                            </FormControl>
                            <Typography variant="body2" color="text.secondary" mt={2}>
                                {t('platform.invite_desc')}
                            </Typography>
                        </Box>
                    ) : (
                        <Box textAlign="center" py={3}>
                            <Typography variant="h4" fontWeight="bold" color="primary" gutterBottom>
                                {generatedCode}
                            </Typography>
                            <Typography variant="body2" color="text.secondary" mb={2}>
                                {t('platform.share_code')}
                            </Typography>
                            <Button startIcon={<ContentCopyIcon />} onClick={handleCopyCode} variant="outlined">
                                {t('platform.copy_code')}
                            </Button>
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpenInvite(false)} color="inherit">{t('common.close')}</Button>
                    {!generatedCode && (
                        <Button onClick={handleGenerateInvite} variant="contained">
                            {t('platform.generate')}
                        </Button>
                    )}
                </DialogActions>
            </Dialog>
        </Container>
    );
};
