import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../api/axiosConfig';
import { Container, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip, Switch, Box } from '@mui/material';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useTranslation } from 'react-i18next';

import { Store as StoreIcon, People as PeopleIcon, Receipt as ReceiptIcon } from '@mui/icons-material';

export const PartnerDetailsAdminPage = () => {
    const { id } = useParams();
    const { t } = useTranslation();
    const { showError, showSuccess } = useNotification();
    const [stats, setStats] = useState<any>(null);
    const [points, setPoints] = useState<any[]>([]);

    useEffect(() => {
        loadData();
    }, [id]);

    const loadData = async () => {
        if (!id) return;
        try {
            const [statsRes, pointsRes] = await Promise.all([
                api.get(`/admin/partners/${id}/stats`),
                api.get(`/admin/partners/${id}/points`)
            ]);
            setStats(statsRes.data);
            setPoints(pointsRes.data);
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    const togglePoint = async (pointId: string, currentStatus: boolean) => {
        try {
            await api.put(`/admin/points/${pointId}/status`, { isActive: !currentStatus });
            showSuccess("Status updated");
            loadData(); // Reload
        } catch (e: any) {
            showError(getErrorMessage(e));
        }
    };

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Box mb={4}>
                <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                    {t('admin.details_title')}
                </Typography>
                <Typography variant="subtitle1" color="text.secondary" sx={{ fontFamily: 'monospace', bgcolor: 'grey.100', display: 'inline-block', px: 1, borderRadius: 1 }}>
                    ID: {id}
                </Typography>
            </Box>

            {/* STATS */}
            <Box
                mb={5}
                display="grid"
                gridTemplateColumns={{ xs: '1fr', md: 'repeat(3, 1fr)' }}
                gap={3}
            >
                <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Box sx={{ p: 1.5, bgcolor: 'primary.50', borderRadius: 3, color: 'primary.main' }}>
                        <StoreIcon fontSize="large" />
                    </Box>
                    <Box>
                        <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.pointsCount || 0}</Typography>
                        <Typography variant="body2" color="text.secondary">{t('admin.total_points')}</Typography>
                    </Box>
                </Paper>
                <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Box sx={{ p: 1.5, bgcolor: 'success.50', borderRadius: 3, color: 'success.main' }}>
                        <PeopleIcon fontSize="large" />
                    </Box>
                    <Box>
                        <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.cardsCount || 0}</Typography>
                        <Typography variant="body2" color="text.secondary">{t('admin.total_clients')}</Typography>
                    </Box>
                </Paper>
                <Paper elevation={0} sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Box sx={{ p: 1.5, bgcolor: 'warning.50', borderRadius: 3, color: 'warning.main' }}>
                        <ReceiptIcon fontSize="large" />
                    </Box>
                    <Box>
                        <Typography variant="h4" fontWeight="bold" color="text.primary">{stats?.transactionsCount || 0}</Typography>
                        <Typography variant="body2" color="text.secondary">{t('admin.total_transactions')}</Typography>
                    </Box>
                </Paper>
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
                            <TableCell sx={{ fontWeight: 600 }}>{t('common.actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {points.map(p => (
                            <TableRow key={p.id} hover>
                                <TableCell sx={{ fontWeight: 500 }}>{p.name}</TableCell>
                                <TableCell color="text.secondary">{p.address}</TableCell>
                                <TableCell>{p.type}</TableCell>
                                <TableCell>
                                    <Chip 
                                        label={p.active ? t('common.active') : "Disabled"} 
                                        color={p.active ? "success" : "error"} 
                                        size="small"
                                        variant="outlined"
                                        sx={{ fontWeight: 600 }}
                                    />
                                </TableCell>
                                <TableCell>
                                    <Switch 
                                        checked={p.active} 
                                        onChange={() => togglePoint(p.id, p.active)} 
                                        color="success"
                                    />
                                </TableCell>
                            </TableRow>
                        ))}
                        {points.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={5} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('admin.no_points')}</TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </Paper>
        </Container>
    );
};
