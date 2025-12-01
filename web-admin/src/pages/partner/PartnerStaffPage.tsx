import { useEffect, useMemo, useState } from 'react';
import { 
    Container, Typography, Paper, Box, Button, Table, TableHead, TableBody, 
    TableRow, TableCell, Tabs, Tab, Dialog, DialogTitle, DialogContent, 
    DialogActions, Tooltip 
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
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h4" fontWeight="bold">{t('staff.title')}</Typography>
                {tab === 0 && canManage && (
                    <Button variant="contained" startIcon={<AddIcon />} onClick={handleGenerateInvite}>
                        {t('staff.invite_manager')}
                    </Button>
                )}
            </Box>

            <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3 }}>
                <Tab label={t('staff.managers')} />
                <Tab label={t('staff.cashiers')} />
            </Tabs>

            {tab === 0 && (
                <Paper>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('staff.name')}</TableCell>
                                <TableCell>{t('staff.phone')}</TableCell>
                                <TableCell>{t('staff.role')}</TableCell>
                                <TableCell>{t('staff.active')}</TableCell>
                                <TableCell align="right">{t('common.actions')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {managers.length === 0 ? (
                                <TableRow><TableCell colSpan={5} align="center">{t('staff.empty_managers')}</TableCell></TableRow>
                            ) : (
                                managers.map((m) => (
                                    <TableRow key={m.id}>
                                        <TableCell>{m.name}</TableCell>
                                        <TableCell>{m.phone}</TableCell>
                                        <TableCell>{t('staff.role_manager') || 'Manager'}</TableCell>
                                        <TableCell>{m.active ? t('common.yes') : t('common.no')}</TableCell>
                                        <TableCell align="right">
                                            <Button color="error" disabled={!canManage} onClick={() => handleFireManager(m.id)}>
                                                {t('staff.fire')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </Paper>
            )}

            {tab === 1 && (
                <Paper>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('staff.name')}</TableCell>
                                <TableCell>{t('staff.phone')}</TableCell>
                                <TableCell>{t('staff.role')}</TableCell>
                                <TableCell>{t('history.table_point')}</TableCell>
                                <TableCell>{t('staff.active')}</TableCell>
                                <TableCell align="right">{t('common.actions')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {uniqueCashiers.length === 0 ? (
                                <TableRow><TableCell colSpan={8} align="center">{t('staff.empty_cashiers')}</TableCell></TableRow>
                            ) : (
                                uniqueCashiers.map((u: any) => (
                                    <TableRow key={u.userId}>
                                        <TableCell>{u.name}</TableCell>
                                        <TableCell>{u.phone}</TableCell>
                                        <TableCell>{t('staff.role_cashier') || 'Cashier'}</TableCell>
                                        <TableCell>
                                            {u.points.length > 1 ? (
                                                 <Tooltip title={u.points.map((p: any) => p.name).join(', ')}>
                                                     <span style={{ borderBottom: '1px dashed gray', cursor: 'help' }}>
                                                         {t('staff.multi_points', { count: u.points.length })}
                                                     </span>
                                                 </Tooltip>
                                            ) : (
                                                 u.points[0]?.name || '-'
                                            )}
                                        </TableCell>
                                        <TableCell>{u.active ? t('common.yes') : t('common.no')}</TableCell>
                                        <TableCell align="right">
                                            <Button color="error" disabled={!canManage} onClick={() => handleFireEmployee(u)}>
                                                {t('staff.fire')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </Paper>
            )}

            {/* Invite Dialog */}
            <Dialog open={openInvite} onClose={() => setOpenInvite(false)} maxWidth="xs" fullWidth>
                <DialogTitle>{t('staff.invite_manager')}</DialogTitle>
                <DialogContent>
                    <Typography gutterBottom>{t('staff.invite_hint')}</Typography>
                    <Box sx={{ p: 2, bgcolor: '#f5f5f5', borderRadius: 2, textAlign: 'center', mt: 2 }}>
                        <Typography variant="h4" letterSpacing={4} fontWeight="bold">
                            {inviteCode}
                        </Typography>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpenInvite(false)}>OK</Button>
                </DialogActions>
            </Dialog>
        </Container>
    );
};
