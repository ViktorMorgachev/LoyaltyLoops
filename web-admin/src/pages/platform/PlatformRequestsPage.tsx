import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Container, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip, Box, Button, Tabs, Tab, Dialog, DialogTitle, DialogContent, TextField, DialogActions, IconButton } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useUser } from '../../context/UserContext';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import RefreshIcon from '@mui/icons-material/Refresh';
import { formatCurrency } from '../../utils/currency';

export const PlatformRequestsPage = () => {
    const { t } = useTranslation();
    const { showError, showSuccess } = useNotification();
    const { isSuperAdmin, isSuperManager } = useUser();
    
    const [requests, setRequests] = useState<any[]>([]);
    const [tab, setTab] = useState('PENDING');
    const [loading, setLoading] = useState(false);
    
    // Reject Dialog
    const [rejectId, setRejectId] = useState<string | null>(null);
    const [rejectReason, setRejectReason] = useState('');
    const [processing, setProcessing] = useState(false);

    // Permissions
    const canApprove = isSuperAdmin || isSuperManager;

    useEffect(() => {
        loadRequests();
    }, [tab]);

    const loadRequests = async () => {
        setLoading(true);
        try {
            const res = await api.get('/platform/requests', { params: { status: tab } });
            setRequests(res.data);
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (id: string) => {
        if (!confirm(t('platform.confirm_approve'))) return;
        
        try {
            await api.post(`/platform/requests/${id}/approve`);
            showSuccess(t('platform.request_approved'));
            loadRequests();
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    const handleRejectSubmit = async () => {
        if (!rejectId) return;
        setProcessing(true);
        try {
            await api.post(`/platform/requests/${rejectId}/reject`, { reason: rejectReason });
            showSuccess(t('platform.request_rejected'));
            setRejectId(null);
            setRejectReason('');
            loadRequests();
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setProcessing(false);
        }
    };

    const getDurationLabel = (d: string) => {
        if (!d) return '-';
        return t(`common.${d.toLowerCase()}`);
    };

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Typography variant="h4" fontWeight="800" sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                    {t('platform.requests_title')}
                </Typography>
                <IconButton onClick={loadRequests} disabled={loading}>
                    <RefreshIcon />
                </IconButton>
            </Box>

            <Paper sx={{ borderRadius: 4, mb: 3 }} elevation={0} variant="outlined">
                <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 2, pt: 1 }}>
                    <Tab label={t('platform.tab_pending')} value="PENDING" />
                    <Tab label={t('platform.tab_approved')} value="APPROVED" />
                    <Tab label={t('platform.tab_rejected')} value="REJECTED" />
                </Tabs>
            </Paper>

            <Paper sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }} elevation={0}>
                <Table>
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                        <TableRow>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.req_date')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.req_requester')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.req_type')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.req_details')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.partner')}</TableCell>
                            <TableCell sx={{ fontWeight: 600 }}>{t('platform.req_status')}</TableCell>
                            {canApprove && tab === 'PENDING' && (
                                <TableCell sx={{ fontWeight: 600 }}>{t('platform.actions')}</TableCell>
                            )}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {requests.map(r => (
                            <TableRow key={r.id} hover>
                                <TableCell>{new Date(r.createdAt).toLocaleDateString()}</TableCell>
                                <TableCell>
                                    <Typography variant="body2" fontWeight="600">{r.requesterName}</Typography>
                                    {r.requesterPhone && (
                                        <Typography variant="caption" display="block" color="text.secondary">
                                            {r.requesterPhone}
                                        </Typography>
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Chip label={t(`platform.${r.type}`) || r.type} size="small" />
                                </TableCell>
                                <TableCell>
                                    <Box mb={1}>
                                        {r.targetPointName && (
                                            <Typography variant="body2" display="block" fontWeight="bold">
                                                {r.targetPointName}
                                            </Typography>
                                        )}
                                    </Box>
                                    {r.isTrial ? (
                                        <Chip label={t('platform.trial_label')} color="info" size="small" variant="outlined" />
                                    ) : (
                                        <Box>
                                            <Typography variant="body2" fontWeight="bold">
                                                {formatCurrency(r.amount, r.currency)}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                                {getDurationLabel(r.duration)}
                                            </Typography>
                                        </Box>
                                    )}
                                </TableCell>
                                <TableCell>
                                    {r.targetPartnerName && (
                                        <Button
                                            variant="outlined"
                                            size="small"
                                            component={Link}
                                            to={`/admin/partners/${r.targetPartnerId}`}
                                            sx={{ textTransform: 'none' }}
                                        >
                                            {r.targetPartnerName}
                                        </Button>
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Chip 
                                        label={t(`platform.status_${r.status.toLowerCase()}`) || r.status}
                                        color={r.status === 'APPROVED' ? 'success' : r.status === 'REJECTED' ? 'error' : 'warning'} 
                                        size="small" 
                                    />
                                    {r.rejectReason && (
                                        <Typography variant="caption" display="block" color="error" mt={0.5}>
                                            {r.rejectReason}
                                        </Typography>
                                    )}
                                </TableCell>
                                {canApprove && tab === 'PENDING' && (
                                    <TableCell>
                                        <Box display="flex" gap={1}>
                                            <Button 
                                                variant="contained" 
                                                color="success" 
                                                size="small" 
                                                startIcon={<CheckCircleIcon />}
                                                onClick={() => handleApprove(r.id)}
                                            >
                                                {t('platform.approve_btn')}
                                            </Button>
                                            <Button 
                                                variant="outlined" 
                                                color="error" 
                                                size="small" 
                                                startIcon={<CancelIcon />}
                                                onClick={() => setRejectId(r.id)}
                                            >
                                                {t('platform.reject_btn')}
                                            </Button>
                                        </Box>
                                    </TableCell>
                                )}
                            </TableRow>
                        ))}
                        {requests.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={6} align="center" sx={{ py: 6, color: 'text.secondary' }}>
                                    {t('platform.no_requests')}
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </Paper>

            {/* Reject Dialog */}
            <Dialog open={!!rejectId} onClose={() => setRejectId(null)} fullWidth maxWidth="xs">
                <DialogTitle>{t('platform.reject_title')}</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        margin="dense"
                        label={t('platform.reject_reason')}
                        fullWidth
                        multiline
                        rows={3}
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRejectId(null)} color="inherit">{t('platform.cancel')}</Button>
                    <Button onClick={handleRejectSubmit} color="error" variant="contained" disabled={!rejectReason || processing}>
                        {t('platform.reject_confirm')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Container>
    );
};
