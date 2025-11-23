import React, { useEffect, useState } from 'react';
import { Container, Paper, Typography, Box, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import { Group as GroupIcon, Store as StoreIcon, Receipt as ReceiptIcon } from '@mui/icons-material';
import { RevenueChart } from '../../components/dashboard/RevenueChart';
import { AnalyticsPeriod } from '../../types/analytics';
import type { AnalyticsResponse } from '../../types/analytics';

// Компонент KPI карточки
const KpiCard = ({ title, value, icon, color }: any) => (
    <Paper sx={{ p: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', height: '100%' }}>
        <Box>
            <Typography variant="h4" fontWeight="bold" sx={{ color }}>{value}</Typography>
            <Typography color="textSecondary" variant="subtitle2">{title}</Typography>
        </Box>
        <Box sx={{ bgcolor: `${color}15`, p: 1.5, borderRadius: '12px', display: 'flex' }}>
            {icon}
        </Box>
    </Paper>
);

export const PartnerDashboardPage = () => {
    const { t } = useTranslation();
    const [period, setPeriod] = useState<AnalyticsPeriod>(AnalyticsPeriod.WEEK);
    const [analytics, setAnalytics] = useState<AnalyticsResponse | null>(null);
    const [pointsCount, setPointsCount] = useState(0);

    useEffect(() => {
        loadData();
    }, [period]);

    const loadData = async () => {
        try {
            const res = await api.get(`/partners/analytics?period=${period}`);
            setAnalytics(res.data);
            
            const pointsRes = await api.get('/partners/points');
            setPointsCount(pointsRes.data.length);
        } catch(e: any) { 
            // Ignore 404 (New user)
            if(e.response?.status !== 404) console.error(e); 
        }
    };

    const handlePeriodChange = (event: React.MouseEvent<HTMLElement>, newPeriod: AnalyticsPeriod | null) => {
        if (newPeriod !== null) {
            setPeriod(newPeriod);
        }
    };

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={2}>
                <Typography variant="h4" fontWeight="bold">{t('menu.dashboard')}</Typography>
                
                <ToggleButtonGroup
                    value={period}
                    exclusive
                    onChange={handlePeriodChange}
                    color="primary"
                    size="small"
                    aria-label="analytics period"
                >
                    <ToggleButton value={AnalyticsPeriod.WEEK}>{t('common.week')}</ToggleButton>
                    <ToggleButton value={AnalyticsPeriod.MONTH}>{t('common.month')}</ToggleButton>
                    <ToggleButton value={AnalyticsPeriod.SIX_MONTHS}>6 {t('common.months')}</ToggleButton>
                    <ToggleButton value={AnalyticsPeriod.YEAR}>{t('common.year')}</ToggleButton>
                </ToggleButtonGroup>
            </Box>
            
            {/* Используем CSS Grid */}
            <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: '1fr 1fr 1fr' }} gap={3}>
                <Box>
                    <KpiCard 
                        title={t('menu.my_points')} 
                        value={pointsCount} 
                        icon={<StoreIcon sx={{ color: '#1976d2', fontSize: 32 }} />}
                        color="#1976d2"
                    />
                </Box>
                <Box>
                    <KpiCard 
                        title={t('admin.total_transactions')} 
                        value={analytics?.totalTransactions || 0} 
                        icon={<ReceiptIcon sx={{ color: '#2e7d32', fontSize: 32 }} />}
                        color="#2e7d32"
                    />
                </Box>
                <Box>
                    <KpiCard 
                        title={t('admin.total_revenue')} 
                        value={analytics?.totalRevenue.toFixed(0) || 0} 
                        icon={<GroupIcon sx={{ color: '#ed6c02', fontSize: 32 }} />} 
                        color="#ed6c02"
                    />
                </Box>
            </Box>

            {/* График */}
            <Box mt={4}>
                <Paper sx={{ p: 3 }}>
                    <Typography variant="h6" mb={3}>{t('common.revenue_chart')}</Typography>
                    {analytics?.chartData && analytics.chartData.length > 0 ? (
                        <RevenueChart data={analytics.chartData} />
                    ) : (
                        <Box height={300} display="flex" alignItems="center" justifyContent="center">
                            <Typography color="textSecondary">
                                {t('history.empty')}
                            </Typography>
                        </Box>
                    )}
                </Paper>
            </Box>
        </Container>
    );
};
