import { useEffect, useMemo, useState } from 'react';
import { 
    Container, Typography, Paper, Box, Button, Table, TableHead, TableBody, 
    TableRow, TableCell, Tabs, Tab, Dialog, DialogTitle, DialogContent, 
    DialogActions, Tooltip, Chip
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import { Add as AddIcon } from '@mui/icons-material';
import { useUser } from '../../context/UserContext';

export const PartnerStaffPage = () => {
    const { t } = useTranslation();
    const { currentWorkspace } = useUser();
    const canManage = currentWorkspace?.role === 'PARTNER_ADMIN';
    const [tab, setTab] = useState(0);
    const [managers, setManagers] = useState<any[]>([]);
    const [cashiers, setCashiers] = useState<any[]>([]);
    const [inviteCode, setInviteCode] = useState<string | null>(null);
    const [openInvite, setOpenInvite] = useState(false);

    useEffect(() => {
        loadManagers();
    }, []);

    useEffect(() => {
        if (tab === 1 && cashiers.length === 0) {
            loadCashiers();
        }
    }, [tab]);

    const loadManagers = async () => {
        try {
            const res = await api.get('/partners/managers');
            setManagers(res.data);
        } catch (e) {
            console.error(e);
        }
    };

    const loadCashiers = async () => {
        try {
            const res = await api.get('/partners/cashiers');
            setCashiers(res.data);
        } catch (e) {
            console.error(e);
        }
    };

    // Group cashiers by userId to avoid duplicates
    const uniqueCashiers = useMemo(() => {
        const map = new Map();
        cashiers.forEach(c => {
            if (!map.has(c.userId)) {
                map.set(c.userId, {
                    userId: c.userId,
                    name: c.name,
                    phone: c.phone,
                    active: c.active,
                    points: []
                });
            }
            const entry = map.get(c.userId);
            if (c.pointName) {
                entry.points.push({ name: c.pointName, id: c.id });
            }
        });
        return Array.from(map.values());
    }, [cashiers]);

    const handleGenerateInvite = async () => {
        try {
            const res = await api.post('/partners/managers/invite');
            setInviteCode(res.data.inviteCode);
            setOpenInvite(true);
        } catch (e) {
            console.error(e);
            alert('Error generating code');
        }
    };

    const handleFireManager = async (id: string) => {
        if (!confirm(t('staff.confirm_fire'))) return;
        try {
            await api.delete(`/partners/managers/${id}`);
            loadManagers();
        } catch (e) {
            console.error(e);
        }
    };

    const handleFireEmployee = async (user: any) => {
        if (!confirm(t('staff.confirm_fire'))) return;
        try {
            // Delete all cashier entries for this user
            await Promise.all(user.points.map((p: any) => api.delete(`/partners/cashiers/${p.id}`)));
            loadCashiers();
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <Container maxWidth="lg" sx={{ mt: 4 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Typography variant="h4" fontWeight="bold">{t('staff.title')}</Typography>
                {tab === 0 && canManage && (
                    <Button variant="contained" startIcon={<AddIcon />} onClick={handleGenerateInvite} sx={{ borderRadius: 2 }}>
                        {t('staff.invite_manager')}
                    </Button>
                )}
            </Box>

            <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
                <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
                    <Tabs value={tab} onChange={(_, v) => setTab(v)}>
                        <Tab label={t('staff.managers')} sx={{ fontWeight: 600 }} />
                        <Tab label={t('staff.cashiers')} sx={{ fontWeight: 600 }} />
                    </Tabs>
                </Box>

                {tab === 0 && (
                    <Box sx={{ overflowX: 'auto' }}>
                        <Table sx={{ minWidth: 650 }}>
                            <TableHead sx={{ bgcolor: 'action.hover' }}>
                            <TableRow>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.name')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.phone')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.role')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.active')}</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 600 }}>{t('common.actions')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {managers.length === 0 ? (
                                <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('staff.empty_managers')}</TableCell></TableRow>
                            ) : (
                                managers.map((m) => (
                                    <TableRow key={m.id} hover>
                                        <TableCell>{m.name}</TableCell>
                                        <TableCell>{m.phone}</TableCell>
                                        <TableCell><Chip label={t('staff.role_manager') || 'Manager'} size="small" color="primary" variant="outlined" /></TableCell>
                                        <TableCell>
                                            <Chip 
                                                label={m.active ? t('common.yes') : t('common.no')} 
                                                color={m.active ? 'success' : 'error'} 
                                                size="small" 
                                            />
                                        </TableCell>
                                        <TableCell align="right">
                                            <Button color="error" size="small" disabled={!canManage} onClick={() => handleFireManager(m.id)}>
                                                {t('staff.fire')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                  </Box>
                )}

                {tab === 1 && (
                    <Box sx={{ overflowX: 'auto' }}>
                        <Table sx={{ minWidth: 650 }}>
                            <TableHead sx={{ bgcolor: 'action.hover' }}>
                            <TableRow>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.name')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.phone')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.role')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('history.table_point')}</TableCell>
                                <TableCell sx={{ fontWeight: 600 }}>{t('staff.active')}</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 600 }}>{t('common.actions')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {uniqueCashiers.length === 0 ? (
                                <TableRow><TableCell colSpan={6} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('staff.empty_cashiers')}</TableCell></TableRow>
                            ) : (
                                uniqueCashiers.map((u: any) => (
                                    <TableRow key={u.userId} hover>
                                        <TableCell>{u.name}</TableCell>
                                        <TableCell>{u.phone}</TableCell>
                                        <TableCell><Chip label={t('staff.role_cashier') || 'Cashier'} size="small" color="secondary" variant="outlined" /></TableCell>
                                        <TableCell>
                                            {u.points.length > 1 ? (
                                                 <Tooltip title={u.points.map((p: any) => p.name).join(', ')}>
                                                     <Chip label={t('staff.multi_points', { count: u.points.length })} size="small" clickable />
                                                 </Tooltip>
                                            ) : (
                                                 u.points[0]?.name || '-'
                                            )}
                                        </TableCell>
                                        <TableCell>
                                             <Chip 
                                                label={u.active ? t('common.yes') : t('common.no')} 
                                                color={u.active ? 'success' : 'error'} 
                                                size="small" 
                                            />
                                        </TableCell>
                                        <TableCell align="right">
                                            <Button color="error" size="small" disabled={!canManage} onClick={() => handleFireEmployee(u)}>
                                                {t('staff.fire')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                  </Box>
                )}
            </Paper>

            {/* Invite Dialog */}
            <Dialog 
                open={openInvite} 
                onClose={() => setOpenInvite(false)} 
                maxWidth="xs" 
                fullWidth
                PaperProps={{ sx: { borderRadius: 3 } }}
            >
                <DialogTitle sx={{ textAlign: 'center', fontWeight: 'bold' }}>{t('staff.invite_manager')}</DialogTitle>
                <DialogContent>
                    <Typography gutterBottom align="center" color="text.secondary">{t('staff.invite_hint')}</Typography>
                    <Box sx={{ p: 3, bgcolor: 'primary.50', borderRadius: 3, textAlign: 'center', mt: 2, border: '1px dashed', borderColor: 'primary.main' }}>
                        <Typography variant="h4" letterSpacing={6} fontWeight="bold" color="primary">
                            {inviteCode}
                        </Typography>
                    </Box>
                </DialogContent>
                <DialogActions sx={{ justifyContent: 'center', pb: 3 }}>
                    <Button onClick={() => setOpenInvite(false)} variant="contained" sx={{ borderRadius: 2, px: 4 }}>
                        {t('common.close') || 'OK'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Container>
    );
};
