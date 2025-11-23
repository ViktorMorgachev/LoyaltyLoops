import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../api/axiosConfig';
import { Container, Typography, Grid, Paper, Box, Table, TableHead, TableRow, TableCell, TableBody, Chip, Button, Switch } from '@mui/material';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useTranslation } from 'react-i18next';

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
        <Container maxWidth="lg" sx={{ mt: 4 }}>
            <Typography variant="h4" mb={3}>{t('admin.details_title')}</Typography>
            <Typography variant="subtitle1" mb={3} color="textSecondary">{t('admin.id')}: {id}</Typography>

            {/* STATS */}
            <Grid container spacing={3} mb={4}>
                <Grid item xs={12} md={4}>
                    <Paper sx={{ p: 2, textAlign: 'center' }}>
                        <Typography variant="h4" color="primary">{stats?.pointsCount || 0}</Typography>
                        <Typography variant="subtitle2">{t('admin.total_points')}</Typography>
                    </Paper>
                </Grid>
                <Grid item xs={12} md={4}>
                    <Paper sx={{ p: 2, textAlign: 'center' }}>
                        <Typography variant="h4" color="primary">{stats?.cardsCount || 0}</Typography>
                        <Typography variant="subtitle2">{t('admin.total_clients')}</Typography>
                    </Paper>
                </Grid>
                <Grid item xs={12} md={4}>
                    <Paper sx={{ p: 2, textAlign: 'center' }}>
                        <Typography variant="h4" color="primary">{stats?.transactionsCount || 0}</Typography>
                        <Typography variant="subtitle2">{t('admin.total_transactions')}</Typography>
                    </Paper>
                </Grid>
            </Grid>

            {/* POINTS LIST */}
            <Typography variant="h5" mb={2}>{t('admin.trading_points')}</Typography>
            <Paper>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('dashboard.table_name')}</TableCell>
                            <TableCell>Address</TableCell>
                            <TableCell>{t('dashboard.table_type')}</TableCell>
                            <TableCell>{t('common.status')}</TableCell>
                            <TableCell>{t('common.actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {points.map(p => (
                            <TableRow key={p.id}>
                                <TableCell>{p.name}</TableCell>
                                <TableCell>{p.address}</TableCell>
                                <TableCell>{p.type}</TableCell>
                                <TableCell>
                                    <Chip label={p.active ? t('common.active') : "Disabled"} color={p.active ? "success" : "error"} size="small"/>
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
                                <TableCell colSpan={5} align="center">{t('admin.no_points')}</TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </Paper>
        </Container>
    );
};
