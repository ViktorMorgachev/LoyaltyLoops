import { useEffect, useState } from 'react';
import { Container, Paper, Typography, Box, ToggleButton, ToggleButtonGroup, Skeleton } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import { Group as GroupIcon, Store as StoreIcon, Receipt as ReceiptIcon } from '@mui/icons-material';
import { RevenueChart } from '../../components/dashboard/RevenueChart';
import { AnalyticsPeriod } from '../../types/analytics';
import type { AnalyticsResponse } from '../../types/analytics';

// Компонент KPI карточки
const KpiCard = ({ title, value, icon, color }: any) => (
    <Paper 
        elevation={0}
        sx={{ 
            p: 3, 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center', 
            height: '100%',
            borderRadius: 4,
            border: '1px solid',
            borderColor: 'divider'
        }}
    >
        <Box>
            <Typography variant="h4" fontWeight="800" sx={{ color }}>{value}</Typography>
            <Typography color="text.secondary" variant="subtitle2" fontWeight="500">{title}</Typography>
        </Box>
        <Box sx={{ bgcolor: `${color}15`, p: 1.5, borderRadius: '16px', display: 'flex' }}>
            {icon}
        </Box>
    </Paper>
);

export const PartnerDashboardPage = () => {
    const { t } = useTranslation();
    const [period, setPeriod] = useState<AnalyticsPeriod>(AnalyticsPeriod.WEEK);
    const [analytics, setAnalytics] = useState<AnalyticsResponse | null>(null);
    const [pointsCount, setPointsCount] = useState(0);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, [period]);

    const loadData = async () => {
        try {
            setLoading(true);
            const res = await api.get(`/partners/analytics?period=${period}`);
            setAnalytics(res.data);
            
            const pointsRes = await api.get('/partners/points');
            setPointsCount(pointsRes.data.length);
        } catch(e: any) { 
            // Ignore 404 (New user)
            if(e.response?.status !== 404) console.error(e); 
        } finally {
            setLoading(false);
        }
    };

    const handlePeriodChange = (_event: React.MouseEvent<HTMLElement>, newPeriod: AnalyticsPeriod | null) => {
        if (newPeriod !== null) {
            setPeriod(newPeriod);
        }
    };

    const formatRevenue = (value?: number | null) =>
        (value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

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
                {[0,1,2].map((idx) => (
                    <Box key={idx}>
                        {loading ? (
                            <>
                                <Skeleton variant="text" width="50%" height={40} />
                                <Skeleton variant="text" width="30%" />
                            </>
                        ) : idx === 0 ? (
                            <KpiCard 
                                title={t('menu.my_points')} 
                                value={pointsCount} 
                                icon={<StoreIcon sx={{ color: '#1976d2', fontSize: 32 }} />}
                                color="#1976d2"
                            />
                        ) : idx === 1 ? (
                            <KpiCard 
                                title={t('admin.total_transactions')} 
                                value={analytics?.totalTransactions || 0} 
                                icon={<ReceiptIcon sx={{ color: '#2e7d32', fontSize: 32 }} />}
                                color="#2e7d32"
                            />
                        ) : (
                            <KpiCard 
                                title={t('admin.total_revenue')} 
                                value={formatRevenue(analytics?.totalRevenue)} 
                                icon={<GroupIcon sx={{ color: '#ed6c02', fontSize: 32 }} />} 
                                color="#ed6c02"
                            />
                        )}
                    </Box>
                ))}
            </Box>

            {/* График */}
            <Box mt={4}>
                <Paper elevation={0} sx={{ p: 4, borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="h6" mb={3} fontWeight="bold">{t('common.revenue_chart')}</Typography>
                    {loading ? (
                        <Skeleton variant="rectangular" height={300} />
                    ) : analytics?.chartData && analytics.chartData.length > 0 ? (
                        <Box height={300}>
                            <RevenueChart data={analytics.chartData} />
                        </Box>
                    ) : (
                        <Box height={300} display="flex" alignItems="center" justifyContent="center">
                            <Typography color="text.secondary">
                                {t('history.empty')}
                            </Typography>
                        </Box>
                    )}
                </Paper>
            </Box>
        </Container>
    );
};
