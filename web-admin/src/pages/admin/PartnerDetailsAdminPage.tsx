import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../api/axiosConfig';
import { Container, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip, Box, Button, Dialog, DialogTitle, DialogContent, DialogActions, FormControlLabel, Checkbox, TextField, MenuItem, Alert, Skeleton, CircularProgress } from '@mui/material';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useTranslation } from 'react-i18next';
import { useUser } from '../../context/UserContext';
import { TableSkeleton } from '../../components/common/TableSkeleton';
import { PointDetailsDialog } from '../../components/admin/PointDetailsDialog';

import { Store as StoreIcon, People as PeopleIcon, Receipt as ReceiptIcon, ShoppingCart as CartIcon, Info as InfoIcon } from '@mui/icons-material';

export const PartnerDetailsAdminPage = () => {
    const { id } = useParams();
    const { t } = useTranslation();
    const { showError, showSuccess } = useNotification();
    const { isSuperAdmin, isSuperManager, isPlatformManager, currentWorkspace, isPlatformStaff } = useUser();
    const hideSensitiveStats = isSuperManager || isPlatformManager;

    // Guard: only super admin/manager with selected workspace
    if (!currentWorkspace || (!isSuperAdmin && !isSuperManager && !isPlatformManager)) {
        return null;
    }

    const [stats, setStats] = useState<any>(null);
    const [points, setPoints] = useState<any[]>([]);
    const [partner, setPartner] = useState<any>(null);
    const [pendingRequests, setPendingRequests] = useState<any[]>([]);
    const [pointDetailsOpen, setPointDetailsOpen] = useState(false);
    const [pointDetailsLoading, setPointDetailsLoading] = useState(false);
    const [pointDetails, setPointDetails] = useState<any>(null);
    
    // Activation Dialog State
    const [openActivate, setOpenActivate] = useState(false);
    const [targetPointId, setTargetPointId] = useState<string | null>(null);
    const [amount, setAmount] = useState('');
    const [duration, setDuration] = useState('MONTH_1');
    const [isTrial, setIsTrial] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, [id]);

    const loadData = async () => {
        if (!id) return;
        try {
            setLoading(true);
            const [statsRes, pointsRes, reqRes, partnerRes] = await Promise.all([
                api.get(`/admin/partners/${id}/stats`),
                api.get(`/admin/partners/${id}/points`),
                api.get('/platform/requests', { params: { targetPartnerId: id, status: 'PENDING' } }),
                api.get(`/admin/partners/${id}`)
            ]);
            setStats(statsRes.data);
            setPoints(pointsRes.data);
            setPendingRequests(reqRes.data);
            setPartner(partnerRes.data);
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    const handleViewPoint = async (pointId: string) => {
        if (!id) return;
        try {
            setPointDetailsLoading(true);
            setPointDetailsOpen(true);
            const res = await api.get(`/admin/points/${pointId}`);
            setPointDetails(res.data);
        } catch (e: any) {
            setPointDetails(null);
            showError(getErrorMessage(e));
        } finally {
            setPointDetailsLoading(false);
        }
    };

    const handleTogglePoint = async (pointId: string, currentStatus: boolean) => {
        // If turning OFF -> Direct action (Super Admin only? Or Manager via Request?)
        // If turning ON -> Needs Payment/Request
        
        if (currentStatus) {
            // Deactivation (Block) logic or just toggle? 
            // For now, keep old logic for SuperAdmin, but Managers need request?
            if (!isPlatformStaff) return;

            try {
                await api.put(`/admin/points/${pointId}/status`, { isActive: false });
                showSuccess(t('common.status_updated'));
                loadData();
            } catch (e: any) {
                showError(getErrorMessage(e));
            }
        } else {
            // Activation -> Open Modal
            setTargetPointId(pointId);
            setOpenActivate(true);
        }
    };

    const handleCreateRequest = async () => {
        if (!targetPointId || !id) return;
        setSubmitting(true);
        try {
            // Payload
            const payload = {
                type: 'ACTIVATE_POINT',
                targetPointId: targetPointId,
                amount: isTrial ? 0 : parseFloat(amount),
                duration: isTrial ? null : duration, // Backend ignores duration if Trial (defaults to 14 days)
                isTrial: isTrial
            };

            await api.post('/platform/requests', payload);
            showSuccess(t('admin.request_created'));
            setOpenActivate(false);
            // Reset
            setAmount('');
            setDuration('MONTH_1');
            setIsTrial(false);
            loadData(); // Reload to update pending status
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setSubmitting(false);
        }
    };

    const handlePartnerStatus = async (newStatus: string) => {
        if (!isPlatformStaff) return;
        try {
            await api.post(`/admin/partners/${id}/status`, { status: newStatus });
            showSuccess(t('common.status_updated'));
            loadData();
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    const DURATIONS = [
        { value: 'WEEK_2', label: t('common.week_2') },
        { value: 'MONTH_1', label: t('common.month_1') },
        { value: 'MONTH_3', label: t('common.month_3') },
        { value: 'MONTH_6', label: t('common.month_6') },
        { value: 'YEAR_1', label: t('common.year_1') },
        { value: 'YEAR_2', label: t('common.year_2') },
        { value: 'YEAR_3', label: t('common.year_3') },
        { value: 'YEAR_5', label: t('common.year_5') },
    ];

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Box mb={4} display="flex" justifyContent="space-between" alignItems="flex-start">
                <Box>
                    {loading ? (
                        <>
                            <Skeleton variant="text" width={300} height={60} />
                            <Skeleton variant="text" width={200} />
                        </>
                    ) : (
                        <>
                            <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                                {partner?.businessName || t('admin.details_title')}
                            </Typography>
                            <Typography variant="subtitle1" color="text.secondary" sx={{ fontFamily: 'monospace', bgcolor: 'grey.100', display: 'inline-block', px: 1, borderRadius: 1 }}>
                                ID: {id}
                            </Typography>
                        </>
                    )}
                </Box>
                {!loading && partner && (
                    <Box display="flex" flexDirection="column" alignItems="flex-end" gap={1}>
                        <Chip 
                            label={t(`common.${partner.status.toLowerCase()}`) || partner.status}
                            color={partner.status === 'ACTIVE' ? 'success' : partner.status === 'BLOCKED' ? 'error' : 'warning'}
                            sx={{ fontWeight: 'bold', px: 1 }}
                        />
                        {isPlatformStaff && (
                            <Box display="flex" gap={1}>
                                {partner.status === 'PENDING' && (
                                    <Button 
                                        variant="contained" 
                                        color="success" 
                                        size="small"
                                        onClick={() => handlePartnerStatus('ACTIVE')}
                                    >
                                        {t('common.approve')}
                                    </Button>
                                )}
                                {partner.status === 'ACTIVE' && (
                                    <Button 
                                        variant="outlined" 
                                        color="error" 
                                        size="small"
                                        onClick={() => handlePartnerStatus('BLOCKED')}
                                    >
                                        {t('common.block')}
                                    </Button>
                                )}
                                {partner.status === 'BLOCKED' && (
                                    <Button 
                                        variant="contained" 
                                        color="warning" 
                                        size="small"
                                        onClick={() => handlePartnerStatus('ACTIVE')}
                                    >
                                        {t('common.unblock')}
                                    </Button>
                                )}
                            </Box>
                        )}
                    </Box>
                )}
            </Box>

            {/* STATS */}
            <Box
                mb={5}
                display="grid"
                gridTemplateColumns={{ xs: '1fr', md: hideSensitiveStats ? '1fr' : 'repeat(3, 1fr)' }}
                gap={3}
            >
                <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Box sx={{ p: 1.5, bgcolor: 'primary.50', borderRadius: 3, color: 'primary.main' }}>
                        <StoreIcon fontSize="large" />
                    </Box>
                    <Box>
                        {loading ? (
                            <Skeleton variant="text" width={60} height={40} />
                        ) : (
                            <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.pointsCount || 0}</Typography>
                        )}
                        <Typography variant="body2" color="text.secondary">{t('admin.total_points')}</Typography>
                    </Box>
                </Paper>
                {!hideSensitiveStats && (
                    <>
                        <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                            <Box sx={{ p: 1.5, bgcolor: 'success.50', borderRadius: 3, color: 'success.main' }}>
                                <PeopleIcon fontSize="large" />
                            </Box>
                            <Box>
                                {loading ? (
                                    <Skeleton variant="text" width={60} height={40} />
                                ) : (
                                    <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.cardsCount || 0}</Typography>
                                )}
                                <Typography variant="body2" color="text.secondary">{t('admin.total_clients')}</Typography>
                            </Box>
                        </Paper>
                        <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                            <Box sx={{ p: 1.5, bgcolor: 'warning.50', borderRadius: 3, color: 'warning.main' }}>
                                <ReceiptIcon fontSize="large" />
                            </Box>
                            <Box>
                                {loading ? (
                                    <Skeleton variant="text" width={60} height={40} />
                                ) : (
                                    <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.transactionsCount || 0}</Typography>
                                )}
                                <Typography variant="body2" color="text.secondary">{t('admin.total_transactions')}</Typography>
                            </Box>
                        </Paper>
                    </>
                )}
            </Box>

            {/* POINTS LIST */}
            <Typography variant="h5" fontWeight="bold" mb={3}>{t('admin.trading_points')}</Typography>
            <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
                <Table>
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                        <TableRow>
                            <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_name')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>Address</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_type')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('common.status')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>Info</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('common.actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {loading ? (
                            <TableSkeleton cols={6} />
                        ) : points.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={6} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('admin.no_points')}</TableCell>
                            </TableRow>
                        ) : (
                        points.map(p => {
                            const hasPending = pendingRequests.some(r => r.targetPointId === p.id && r.type === 'ACTIVATE_POINT');
                            return (
                                <TableRow key={p.id} hover>
                                    <TableCell>
                                        <Box>
                                            <Button 
                                                variant="text" 
                                                sx={{ p: 0, textTransform: 'none', fontWeight: 600 }} 
                                                onClick={() => handleViewPoint(p.id)}
                                            >
                                                {p.name}
                                            </Button>
                                            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 0.5 }} onClick={() => navigator.clipboard.writeText(p.id)} title={t('common.click_to_copy', 'Click to copy')}>
                                                {p.id.substring(0, 8)}...
                                            </Typography>
                                        </Box>
                                    </TableCell>
                                    <TableCell color="text.secondary">{p.address}</TableCell>
                                    <TableCell>{t(`dashboard.types.${p.type}`)}</TableCell>
                                    <TableCell>
                                        <Chip 
                                            label={p.active ? t('common.active') : t('point_details.status_inactive')} 
                                            color={p.active ? "success" : "error"} 
                                            size="small"
                                            variant="outlined"
                                            sx={{ fontWeight: 600 }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Box display="flex" flexDirection="column" gap={0.5}>
                                            <Typography variant="caption" display="flex" alignItems="center" gap={0.5}>
                                                <InfoIcon fontSize="inherit" color="action" />
                                                Currency: {p.currency || '—'}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                                Phone: {p.contactPhone || '-'}
                                            </Typography>
                                        </Box>
                                    </TableCell>
                                    <TableCell>
                                        <Box display="flex" gap={1} alignItems="center">
                                            <Button 
                                                variant="outlined" 
                                                size="small"
                                                onClick={() => handleViewPoint(p.id)}
                                            >
                                                {t('common.details', 'Details')}
                                            </Button>
                                            {/* Use Button instead of Switch for better UX on activation */}
                                            <Button 
                                                variant="contained" 
                                                size="small"
                                                color={p.active ? "error" : (hasPending ? "warning" : "success")}
                                                startIcon={!p.active && !hasPending && <CartIcon />}
                                                disabled={!p.active && hasPending}
                                                onClick={() => handleTogglePoint(p.id, p.active)}
                                            >
                                                {p.active ? t('common.deactivate') : (hasPending ? t('admin.pending_request') : t('admin.activate_extend_btn'))}
                                            </Button>
                                        </Box>
                                    </TableCell>
                                </TableRow>
                            );
                        }))}
                    </TableBody>
                </Table>
            </Paper>

            {/* Activation Dialog */}
            <Dialog open={openActivate} onClose={() => setOpenActivate(false)} maxWidth="xs" fullWidth>
                <DialogTitle sx={{ fontWeight: 'bold' }}>{t('admin.create_activation_request')}</DialogTitle>
                <DialogContent>
                    <Box pt={1} display="flex" flexDirection="column" gap={2}>
                        <FormControlLabel 
                            control={<Checkbox checked={isTrial} onChange={(e) => setIsTrial(e.target.checked)} />}
                            label={t('admin.trial_period')}
                        />

                        {!isTrial && (
                            <>
                                <TextField 
                                    label={t('admin.amount_label')}
                                    type="number" 
                                    fullWidth 
                                    value={amount} 
                                    onChange={(e) => setAmount(e.target.value)} 
                                    placeholder="e.g. 5000"
                                />
                                <TextField
                                    select
                                    label={t('admin.duration_label')}
                                    fullWidth
                                    value={duration}
                                    onChange={(e) => setDuration(e.target.value)}
                                >
                                    {DURATIONS.map(opt => (
                                        <MenuItem key={opt.value} value={opt.value}>
                                            {opt.label}
                                        </MenuItem>
                                    ))}
                                </TextField>
                            </>
                        )}
                        
                        {isTrial && (
                            <Alert severity="info">
                                {t('admin.trial_alert')}
                            </Alert>
                        )}
                    </Box>
                </DialogContent>
                <DialogActions sx={{ p: 3 }}>
                    <Button onClick={() => setOpenActivate(false)} color="inherit">{t('common.cancel')}</Button>
                    <Button 
                        onClick={handleCreateRequest} 
                        variant="contained" 
                        disabled={submitting || (!isTrial && !amount)}
                        startIcon={submitting ? <CircularProgress size={20} color="inherit" /> : null}
                    >
                        {submitting ? t('admin.creating') : t('admin.create_request_btn')}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Point details dialog */}
            <PointDetailsDialog 
                open={pointDetailsOpen} 
                onClose={() => setPointDetailsOpen(false)} 
                loading={pointDetailsLoading} 
                data={pointDetails} 
            />
        </Container>
    );
};
