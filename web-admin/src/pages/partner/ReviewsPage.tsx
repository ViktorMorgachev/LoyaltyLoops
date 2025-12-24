// @ts-nocheck
import React, { useEffect, useMemo, useState } from 'react';
import { 
    Container, Typography, Box, Paper, Tabs, Tab, 
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, 
    Chip, LinearProgress, Card, CardContent,
    FormControl, InputLabel, Select, MenuItem, OutlinedInput, Stack, Checkbox, Button, Dialog, DialogTitle, DialogContent, TextField, Skeleton
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/axiosConfig';
import type { AnalyticsDataDto, ReviewDto } from '../../types/reviews';
import { Star, StarBorder } from '@mui/icons-material';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend } from 'recharts';
import { TableSkeleton } from '../../components/common/TableSkeleton';

// Helpers to avoid TZ shift for date inputs
const formatLocalDate = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${dd}`;
};

const parseLocalDate = (s: string) => {
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y || 1970, (m || 1) - 1, d || 1);
};

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

function TabPanel(props: TabPanelProps) {
    const { children, value, index, ...other } = props;
    return (
        <div role="tabpanel" hidden={value !== index} {...other}>
            {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
        </div>
    );
}

export const ReviewsPage = () => {
    const { t } = useTranslation();
    const [tabValue, setTabValue] = useState(0);
    const [analytics, setAnalytics] = useState<AnalyticsDataDto | null>(null);
    const [serviceReviews, setServiceReviews] = useState<ReviewDto[]>([]);
    const [clientRatings, setClientRatings] = useState<ReviewDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [tagFilter, setTagFilter] = useState<string[]>([]);
    const [ratingFilter, setRatingFilter] = useState<string>('ALL');
    const [sortOrder, setSortOrder] = useState<'DESC' | 'ASC'>('DESC');
    const [weekFilter, setWeekFilter] = useState<string | null>(null);
    const [weekLabel, setWeekLabel] = useState<string | null>(null);
    const [openWeekDialog, setOpenWeekDialog] = useState(false);
    const [openPointDialog, setOpenPointDialog] = useState(false);
    const [selectedPoint, setSelectedPoint] = useState<string | null>(null);
    const [dateFrom, setDateFrom] = useState<string>("");
    const [dateTo, setDateTo] = useState<string>(formatLocalDate(new Date()));
    const [analyticsPoint, setAnalyticsPoint] = useState<string>("ALL");

    useEffect(() => {
        // Инициализация дефолтного диапазона (последние 30 дней) с учетом локали
        const today = new Date();
        const fromDefault = new Date();
        fromDefault.setDate(today.getDate() - 30);
        const fromStr = formatLocalDate(fromDefault);
        const toStr = formatLocalDate(today);
        setDateFrom(fromStr);
        setDateTo(toStr);

        // Мгновенный первичный запрос НЕ НУЖЕН, так как useEffect ниже сработает при изменении dateFrom/dateTo
        // loadData(fromDefault, today, "ALL");

        // DEBUG: Check raw data types
        api.get('/partners/reviews/summary')
           .then(res => console.log('[DEBUG_DUMP] Raw DB Data:', res.data))
           .catch(err => console.error('[DEBUG_DUMP] Failed:', err));
    }, []);

    // Запросы при наличии диапазона и точки
    useEffect(() => {
        if (!dateFrom || !dateTo) return;
        const from = parseLocalDate(dateFrom);
        const to = parseLocalDate(dateTo);
        loadData(from, to, analyticsPoint);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [dateFrom, dateTo, analyticsPoint]);

    const loadData = async (from?: Date, to?: Date, pointId?: string) => {
        setLoading(true);
        try {
            // fallback, если не передали явные даты
            const today = new Date();
            const fallbackFrom = new Date();
            fallbackFrom.setDate(today.getDate() - 30);
            const fromDate = from || (dateFrom ? parseLocalDate(dateFrom) : fallbackFrom);
            const toDate = to || (dateTo ? parseLocalDate(dateTo) : today);
            
            // Важно: Устанавливаем конец дня для "toDate", иначе данные за "сегодня" не попадут
            toDate.setHours(23, 59, 59, 999);

            const qs: string[] = [];
            qs.push(`from=${fromDate.getTime()}`);
            qs.push(`to=${toDate.getTime()}`);
            if (pointId && pointId !== 'ALL') qs.push(`pointId=${encodeURIComponent(pointId)}`);
            const suffix = qs.length ? `?${qs.join('&')}` : '';

            const [analyticsRes, reviewsRes, ratingsRes] = await Promise.all([
                api.get<AnalyticsDataDto>(`/partners/reviews/summary${suffix}`),
                api.get<ReviewDto[]>('/partners/reviews'),
                api.get<ReviewDto[]>('/partners/client-ratings')
            ]);
            setAnalytics(analyticsRes.data);
            setServiceReviews(reviewsRes.data);
            setClientRatings(ratingsRes.data);
        } catch (error) {
            console.error("Failed to load reviews data", error);
        } finally {
            setLoading(false);
        }
    };

    const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
        setTabValue(newValue);
    };

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
            <Typography variant="h4" fontWeight="bold" mb={4}>{t('analytics.title')}</Typography>

            <Tabs value={tabValue} onChange={handleTabChange} sx={{ mb: 2 }}>
                <Tab label={t('analytics.tabs.dashboard')} />
                <Tab label={t('analytics.tabs.service_reviews')} />
                <Tab label={t('analytics.tabs.client_ratings')} />
            </Tabs>

            {loading && <LinearProgress sx={{ mb: 2 }} />}

            {/* DASHBOARD */}
            <TabPanel value={tabValue} index={0}>
                <DashboardView 
                    analytics={analytics} 
                    t={t} 
                    serviceReviews={serviceReviews}
                    onWeekClick={(weekKey, startIso, endIso, label) => { 
                        setWeekFilter(weekKey);
                        setWeekLabel(label || weekKey);
                        setDateFrom(startIso);
                        setDateTo(endIso);
                        // setTabValue(1); // Не переключаем вкладку, чтобы остаться на дашборде
                        setOpenWeekDialog(true);
                    }}
                    onPointClick={(pointId, pointName) => {
                        setSelectedPoint(pointName || pointId);
                        setOpenPointDialog(true);
                    }}
                    onRangeChange={(from, to, pointId) => {
                        setDateFrom(formatLocalDate(from));
                        setDateTo(formatLocalDate(to));
                        setAnalyticsPoint(pointId);
                        loadData(from, to, pointId);
                    }}
                    dateFrom={dateFrom}
                    dateTo={dateTo}
                    analyticsPoint={analyticsPoint}
                />
            </TabPanel>

            {/* SERVICE REVIEWS */}
            <TabPanel value={tabValue} index={1}>
                <ReviewsTable 
                    reviews={serviceReviews} 
                    t={t} 
                    tagFilter={tagFilter} 
                    ratingFilter={ratingFilter} 
                    sortOrder={sortOrder}
                    weekFilter={weekFilter}
                    dateFrom={dateFrom}
                    dateTo={dateTo}
                    onFilterChange={{ setTagFilter, setRatingFilter, setSortOrder, setWeekFilter, setDateFrom, setDateTo }}
                    loading={loading}
                />
            </TabPanel>

            {/* CLIENT RATINGS */}
            <TabPanel value={tabValue} index={2}>
                <ReviewsTable 
                    reviews={clientRatings} 
                    t={t} 
                    isClientRating 
                    tagFilter={tagFilter} 
                    ratingFilter={ratingFilter} 
                    sortOrder={sortOrder}
                    weekFilter={weekFilter}
                    dateFrom={dateFrom}
                    dateTo={dateTo}
                    onFilterChange={{ setTagFilter, setRatingFilter, setSortOrder, setWeekFilter, setDateFrom, setDateTo }}
                    loading={loading}
                />
            </TabPanel>

            {/* Week dialog: shows same filters applied to service reviews */}
            <Dialog open={openWeekDialog} onClose={() => setOpenWeekDialog(false)} maxWidth="lg" fullWidth>
                <DialogTitle>{weekLabel || weekFilter || t('analytics.weekly_title')}</DialogTitle>
                <DialogContent>
                    <ReviewsTable
                        reviews={serviceReviews}
                        t={t}
                        tagFilter={tagFilter}
                        ratingFilter={ratingFilter}
                        sortOrder={sortOrder}
                        weekFilter={weekFilter}
                        dateFrom={dateFrom}
                        dateTo={dateTo}
                        onFilterChange={{ setTagFilter, setRatingFilter, setSortOrder, setWeekFilter, setDateFrom, setDateTo }}
                    />
                </DialogContent>
            </Dialog>

            {/* Point dialog */}
            <Dialog open={openPointDialog} onClose={() => setOpenPointDialog(false)} maxWidth="lg" fullWidth>
                <DialogTitle>{selectedPoint || t('analytics.heatmap_title')}</DialogTitle>
                <DialogContent>
                    <PointTimeline reviews={serviceReviews} pointName={selectedPoint} t={t} />
                </DialogContent>
            </Dialog>
        </Container>
    );
};

// --- SUB-COMPONENTS ---

const DashboardView = ({ 
    analytics, 
    t, 
    serviceReviews, 
    onWeekClick, 
    onPointClick,
    onRangeChange,
    dateFrom,
    dateTo,
    analyticsPoint
}: { 
    analytics: AnalyticsDataDto | null, 
    t: any, 
    serviceReviews: ReviewDto[], 
    onWeekClick: (key: string, startIso: string, endIso: string, label?: string) => void, 
    onPointClick: (id: string, name?: string) => void,
    onRangeChange: (from: Date, to: Date, pointId: string) => void,
    dateFrom: string,
    dateTo: string,
    analyticsPoint: string
}) => {
    if (!analytics) return null;

    const weekly = useMemo(() => aggregateWeekly(serviceReviews, t), [serviceReviews, t]);
    const pointOptions = useMemo(() => {
        const points = analytics.heatmap.map(p => ({ id: p.pointId, name: p.pointName }));
        return [{ id: 'ALL', name: t('common.all') }, ...points];
    }, [analytics, t]);

    return (
        <Box>
            {/* KPI Cards */}
            <Grid container spacing={3} mb={4}>
                <Grid item xs={12} md={4}>
                    {analytics ? (
                        <>
                        <KpiCard 
                            title={t('analytics.nps_title')} 
                            value={analytics.nps} 
                            color={analytics.nps > 50 ? '#2e7d32' : analytics.nps > 0 ? '#ed6c02' : '#d32f2f'} 
                            suffix="%"
                        />
                         <Typography variant="caption" color="text.secondary">{t('analytics.nps_hint')}</Typography>
                         </>
                    ) : <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 4 }} />}
                </Grid>
                <Grid item xs={12} md={4}>
                    {analytics ? (
                        <KpiCard 
                            title={t('analytics.avg_rating')} 
                            value={analytics.averageRating.toFixed(1)} 
                            icon={<Star sx={{ color: '#ffc107' }} />}
                        />
                    ) : <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 4 }} />}
                </Grid>
                <Grid item xs={12} md={4}>
                    {analytics ? (
                    <KpiCard 
                        title={t('analytics.total_reviews')} 
                        value={analytics.totalReviews} 
                    />
                    ) : <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 4 }} />}
                </Grid>
            </Grid>

            {/* NPS Trend */}
            <Paper sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider', mb: 4 }} elevation={0}>
                {analytics ? (
                    <>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} mb={2} alignItems="flex-start">
                        <TextField
                            size="small"
                            type="date"
                            label={t('analytics.filters.date_from')}
                            InputLabelProps={{ shrink: true }}
                            value={dateFrom}
                            onChange={(e) => {
                                const from = parseLocalDate(e.target.value);
                                const to = parseLocalDate(dateTo);
                                onRangeChange(from, to, analyticsPoint);
                            }}
                        />
                        <TextField
                            size="small"
                            type="date"
                            label={t('analytics.filters.date_to')}
                            InputLabelProps={{ shrink: true }}
                            value={dateTo}
                            onChange={(e) => {
                                const to = parseLocalDate(e.target.value);
                                const from = parseLocalDate(dateFrom);
                                onRangeChange(from, to, analyticsPoint);
                            }}
                        />
                        <FormControl size="small" sx={{ minWidth: 180 }}>
                            <InputLabel>{t('analytics.filters.point')}</InputLabel>
                            <Select
                                value={analyticsPoint}
                                label={t('analytics.filters.point')}
                                onChange={(e) => {
                                    const from = parseLocalDate(dateFrom);
                                    const to = parseLocalDate(dateTo);
                                    onRangeChange(from, to, e.target.value);
                                }}
                            >
                                {pointOptions.map(p => (
                                    <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Stack>
                    {analytics.series && analytics.series.length > 0 ? (
                        <Box sx={{ height: 320 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={analytics.series}>
                                    <CartesianGrid strokeDasharray="3 3" />
                                    <XAxis dataKey="date" tickFormatter={(v: number) => new Date(v).toLocaleDateString()} />
                                    <YAxis yAxisId="left" label={{ value: 'NPS', angle: -90, position: 'insideLeft' }} />
                                    <YAxis yAxisId="right" orientation="right" label={{ value: t('analytics.avg_rating'), angle: -90, position: 'insideRight' }} />
                                    <Tooltip labelFormatter={(v) => new Date(Number(v)).toLocaleString()} />
                                    <Legend />
                                    <Line yAxisId="left" type="monotone" dataKey="nps" stroke="#1976d2" dot={false} name="NPS" />
                                    <Line yAxisId="right" type="monotone" dataKey="averageRating" stroke="#ffa000" dot={false} name={t('analytics.avg_rating')} />
                                </LineChart>
                            </ResponsiveContainer>
                        </Box>
                    ) : (
                        <Typography color="text.secondary">{t('analytics.empty_reviews')}</Typography>
                    )}
                    </>
                ) : <Skeleton variant="rectangular" height={400} />}
            </Paper>

            {/* Heatmap */}
            <Typography variant="h6" fontWeight="bold" mb={2}>{t('analytics.heatmap_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>{t('analytics.heatmap_subtitle')}</Typography>
            
            <Box sx={{ maxHeight: 420, overflowY: 'auto', pr: 1 }}>
                <Grid container spacing={3}>
                    {analytics ? (
                        analytics.heatmap.map(point => (
                        <Grid item xs={12} md={6} key={point.pointId}>
                            <Card 
                                variant="outlined" 
                                sx={{ borderRadius: 3, cursor: 'pointer' }}
                                onClick={() => onPointClick(point.pointId, point.pointName)}
                            >
                                <CardContent>
                                    <Typography variant="h6" fontWeight="600" mb={2}>{point.pointName}</Typography>
                                    <Box display="flex" flexWrap="wrap" gap={1}>
                                        {point.tagStats.map(stat => (
                                            <Chip 
                                                key={stat.tag}
                                                label={`${t(`analytics.tags.${stat.tag}`, stat.tag)}: ${stat.count}`}
                                                color={getChipColor(stat.tag)}
                                                variant={getChipVariant(stat.tag)}
                                                size="small"
                                            />
                                        ))}
                                        {point.tagStats.length === 0 && <Typography variant="body2" color="text.secondary">{t('analytics.empty_reviews')}</Typography>}
                                    </Box>
                                </CardContent>
                            </Card>
                        </Grid>
                    ))
                    ) : (
                        Array.from({ length: 4 }).map((_, i) => (
                            <Grid item xs={12} md={6} key={i}>
                                <Skeleton variant="rectangular" height={150} sx={{ borderRadius: 3 }} />
                            </Grid>
                        ))
                    )}
                </Grid>
            </Box>

            {/* Weekly Dynamics */}
            <Box mt={5}>
                <Typography variant="h6" fontWeight="bold" mb={2}>{t('analytics.weekly_title')}</Typography>
                <Typography variant="body2" color="text.secondary" mb={3}>{t('analytics.weekly_subtitle')}</Typography>
                {analytics ? (
                    weekly.length === 0 ? (
                        <Typography color="text.secondary">{t('analytics.empty_reviews')}</Typography>
                    ) : (
                        <Grid container spacing={2}>
                            {weekly.map(week => (
                                <Grid item xs={12} md={4} key={week.key}>
                                    <Paper 
                                        sx={{ p: 2, borderRadius: 3, border: '1px solid', borderColor: 'divider', cursor: 'pointer' }} 
                                        elevation={0}
                                        onClick={() => onWeekClick(week.key, week.startIso, week.endIso, week.label)}
                                    >
                                        <Typography variant="subtitle1" fontWeight="700">{week.label}</Typography>
                                        <Typography variant="body2" color="text.secondary" mb={1}>{t('analytics.weekly_count')}: {week.count}</Typography>
                                        <Typography variant="body2" color="text.secondary">{t('analytics.weekly_avg')}: {week.avg.toFixed(1)}</Typography>
                                    </Paper>
                                </Grid>
                            ))}
                        </Grid>
                    )
                ) : <Skeleton variant="rectangular" height={100} />}
            </Box>
        </Box>
    );
};

const KpiCard = ({ title, value, color, suffix, icon }: any) => (
    <Paper sx={{ p: 3, borderRadius: 4, height: '100%', border: '1px solid', borderColor: 'divider' }} elevation={0}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
            <Box>
                <Typography variant="h3" fontWeight="800" sx={{ color: color || 'text.primary' }}>
                    {value}{suffix}
                </Typography>
                <Typography variant="subtitle2" color="text.secondary" mt={1}>{title}</Typography>
            </Box>
            {icon && <Box sx={{ p: 1, bgcolor: '#fff8e1', borderRadius: '50%' }}>{icon}</Box>}
        </Box>
    </Paper>
);

const ReviewsTable = ({ 
    reviews, 
    t, 
    isClientRating, 
    tagFilter = [], 
    ratingFilter = 'ALL', 
    sortOrder = 'DESC',
    weekFilter = null,
    dateFrom,
    dateTo,
    onFilterChange,
    loading
}: { 
    reviews: ReviewDto[], 
    t: any, 
    isClientRating?: boolean,
    tagFilter?: string[],
    ratingFilter?: string,
    sortOrder?: 'DESC' | 'ASC',
    weekFilter?: string | null,
    dateFrom?: string,
    dateTo?: string,
    onFilterChange?: { setTagFilter: (v: string[]) => void; setRatingFilter: (v: string) => void; setSortOrder: (v: 'DESC' | 'ASC') => void; setWeekFilter?: (v: string | null) => void; setDateFrom?: (v: string) => void; setDateTo?: (v: string) => void },
    loading?: boolean
}) => {
    if (loading) {
        return (
            <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3 }}>
                <Table>
                    <TableHead sx={{ bgcolor: '#f8fafc' }}>
                        <TableRow>
                             <TableCell width="15%">{t('analytics.reviews_table.date')}</TableCell>
                             <TableCell width="15%">{isClientRating ? t('staff.role_cashier') : t('analytics.reviews_table.author')}</TableCell>
                             {isClientRating && <TableCell width="15%">{t('analytics.reviews_table.target')}</TableCell>}
                             <TableCell width="15%">{t('analytics.reviews_table.point')}</TableCell>
                             <TableCell width="10%">{t('analytics.reviews_table.rating')}</TableCell>
                             <TableCell width="30%">{t('analytics.reviews_table.comment')} / {t('analytics.reviews_table.tags')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                         <TableSkeleton cols={isClientRating ? 6 : 5} rows={5} />
                    </TableBody>
                </Table>
            </TableContainer>
        );
    }

    if (!reviews.length) {
        return <Typography color="text.secondary" align="center" py={4}>{t('analytics.empty_reviews')}</Typography>;
    }

    const allTags = useMemo(() => Array.from(new Set(reviews.flatMap(r => r.tags))).sort(), [reviews]);

    const filtered = reviews
        .filter(r => {
            if (tagFilter.length > 0 && !r.tags.some(tag => tagFilter.includes(tag))) return false;
            if (ratingFilter === 'GOOD' && r.rating < 4) return false;
            if (ratingFilter === 'NEUTRAL' && (r.rating < 3 || r.rating >= 4)) return false;
            if (ratingFilter === 'BAD' && r.rating >= 3) return false;
            if (weekFilter) {
                const key = getISOWeekKey(new Date(r.createdAt));
                if (key !== weekFilter) return false;
            }
            if (dateFrom) {
                const df = parseLocalDate(dateFrom);
                if (new Date(r.createdAt) < df) return false;
            }
            if (dateTo) {
                const dt = parseLocalDate(dateTo);
                dt.setHours(23,59,59,999);
                if (new Date(r.createdAt) > dt) return false;
            }
            return true;
        })
        .sort((a, b) => sortOrder === 'DESC' ? b.rating - a.rating : a.rating - b.rating);

    return (
        <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3 }}>
            <Table>
                <TableHead sx={{ bgcolor: '#f8fafc' }}>
                    <TableRow>
                        <TableCell colSpan={5}>
                            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="flex-start">
                                <FormControl size="small" sx={{ minWidth: 220 }}>
                                    <InputLabel>{t('analytics.filters.tags')}</InputLabel>
                                    <Select
                                        multiple
                                        value={tagFilter}
                                        onChange={(e) => onFilterChange?.setTagFilter(e.target.value as string[])}
                                        input={<OutlinedInput label={t('analytics.filters.tags')} />}
                                        renderValue={(selected) => {
                                            const sel = selected as string[];
                                            if (!sel.length) return t('common.not_selected');
                                            const labels = sel.map(tag => t(`analytics.tags.${tag}`, tag));
                                            if (labels.length <= 2) return labels.join(', ');
                                            return `${labels.slice(0, 2).join(', ')} +${labels.length - 2}`;
                                        }}
                                    >
                                        {allTags.map(tag => (
                                            <MenuItem key={tag} value={tag}>
                                                <Checkbox checked={tagFilter.indexOf(tag) > -1} />
                                                {t(`analytics.tags.${tag}`, tag)}
                                            </MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>

                                <FormControl size="small" sx={{ minWidth: 160 }}>
                                    <InputLabel>{t('analytics.filters.rating')}</InputLabel>
                                    <Select
                                        value={ratingFilter}
                                        label={t('analytics.filters.rating')}
                                        onChange={(e) => onFilterChange?.setRatingFilter(e.target.value)}
                                    >
                                        <MenuItem value="ALL">{t('analytics.filters.rating_all')}</MenuItem>
                                        <MenuItem value="GOOD">{t('analytics.filters.rating_good')}</MenuItem>
                                        <MenuItem value="NEUTRAL">{t('analytics.filters.rating_neutral')}</MenuItem>
                                        <MenuItem value="BAD">{t('analytics.filters.rating_bad')}</MenuItem>
                                    </Select>
                                </FormControl>

                                <FormControl size="small" sx={{ minWidth: 160 }}>
                                    <InputLabel>{t('analytics.filters.sort')}</InputLabel>
                                    <Select
                                        value={sortOrder}
                                        label={t('analytics.filters.sort')}
                                        onChange={(e) => onFilterChange?.setSortOrder(e.target.value as 'DESC' | 'ASC')}
                                    >
                                        <MenuItem value="DESC">{t('analytics.filters.sort_best')}</MenuItem>
                                        <MenuItem value="ASC">{t('analytics.filters.sort_worst')}</MenuItem>
                                    </Select>
                                </FormControl>

                                <TextField
                                    size="small"
                                    type="date"
                                    label={t('analytics.filters.date_from')}
                                    InputLabelProps={{ shrink: true }}
                                    value={dateFrom || ''}
                                    onChange={(e) => onFilterChange?.setDateFrom?.(e.target.value)}
                                />
                                <TextField
                                    size="small"
                                    type="date"
                                    label={t('analytics.filters.date_to')}
                                    InputLabelProps={{ shrink: true }}
                                    value={dateTo || ''}
                                    onChange={(e) => onFilterChange?.setDateTo?.(e.target.value)}
                                />

                                <Button 
                                    size="small" 
                                    onClick={() => {
                                        onFilterChange?.setTagFilter([]);
                                        onFilterChange?.setRatingFilter('ALL');
                                        onFilterChange?.setSortOrder('DESC');
                                        onFilterChange?.setWeekFilter?.(null);
                                        onFilterChange?.setDateFrom?.("");
                                        onFilterChange?.setDateTo?.(formatLocalDate(new Date()));
                                    }}
                                >
                                    {t('common.reset')}
                                </Button>
                            </Stack>
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableCell width="15%">{t('analytics.reviews_table.date')}</TableCell>
                        <TableCell width="15%">{isClientRating ? t('staff.role_cashier') : t('analytics.reviews_table.author')}</TableCell>
                        {isClientRating && <TableCell width="15%">{t('analytics.reviews_table.target')}</TableCell>}
                        <TableCell width="15%">{t('analytics.reviews_table.point')}</TableCell>
                        <TableCell width="10%">{t('analytics.reviews_table.rating')}</TableCell>
                        <TableCell width="30%">{t('analytics.reviews_table.comment')} / {t('analytics.reviews_table.tags')}</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {filtered.map((row) => (
                        <TableRow key={row.id}>
                            <TableCell>{new Date(row.createdAt).toLocaleDateString()}</TableCell>
                            <TableCell>
                                <Typography variant="body2" fontWeight="600">{row.authorName}</Typography>
                                {/* Removed author phone for privacy */}
                            </TableCell>
                            {isClientRating && (
                                <TableCell>
                                    <Typography variant="body2" fontWeight="600">{row.targetName || 'Client'}</Typography>
                                    {/* Removed target phone for privacy */}
                                </TableCell>
                            )}
                            <TableCell>{row.pointName}</TableCell>
                            <TableCell>
                                <Box display="flex">
                                    {[1,2,3,4,5].map(star => (
                                        star <= row.rating ? 
                                        <Star key={star} sx={{ fontSize: 16, color: '#ffc107' }} /> : 
                                        <StarBorder key={star} sx={{ fontSize: 16, color: '#e0e0e0' }} />
                                    ))}
                                </Box>
                            </TableCell>
                            <TableCell>
                                <Box display="flex" flexWrap="wrap" gap={0.5} mb={0.5}>
                                    {row.tags.map(tag => (
                                        <Chip 
                                            key={tag} 
                                            label={t(`analytics.tags.${tag}`, tag)} 
                                            size="small" 
                                            variant="outlined" 
                                            color={getChipColor(tag)}
                                        />
                                    ))}
                                </Box>
                                {row.comment && <Typography variant="body2">{row.comment}</Typography>}
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    );
};

// Helpers for tag colors
const getChipColor = (tag: string): "default" | "error" | "primary" | "secondary" | "info" | "success" | "warning" => {
    const negative = ['SLOW', 'DIRTY', 'RUDE_STAFF', 'RUDE', 'NON_PAYMENT', 'FRAUD', 'BAD_QUALITY'];
    const positive = ['TASTY', 'FAST', 'FRIENDLY', 'POLITE', 'TIPS', 'GOOD_SERVICE'];
    
    if (negative.includes(tag)) return 'error';
    if (positive.includes(tag)) return 'success';
    return 'default';
};

const getChipVariant = (tag: string): "filled" | "outlined" => {
    // Fill negative/positive tags for emphasis in heatmap
    const emphatic = ['SLOW', 'DIRTY', 'RUDE_STAFF', 'FRAUD', 'TIPS'];
    return emphatic.includes(tag) ? 'filled' : 'outlined';
};

// --- Helpers ---
function aggregateWeekly(reviews: ReviewDto[], t: any) {
    const weeks = new Map<string, { count: number; total: number; label: string; startIso: string; endIso: string }>();
    reviews.forEach(r => {
        const d = new Date(r.createdAt);
        const { year, week, start, end, startIso, endIso } = getISOWeek(d);
        const key = getISOWeekKey(d);
        const label = `${year}-W${week} (${start} - ${end})`;
        const prev = weeks.get(key) || { count: 0, total: 0, label, startIso, endIso };
        prev.count += 1;
        prev.total += r.rating;
        weeks.set(key, prev);
    });
    return Array.from(weeks.entries())
        .map(([key, v]) => ({
            key,
            label: v.label,
            count: v.count,
            avg: v.count > 0 ? v.total / v.count : 0,
            startIso: v.startIso,
            endIso: v.endIso
        }))
        .sort((a, b) => a.key.localeCompare(b.key));
}

function getISOWeek(date: Date) {
    const tmp = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    // Thursday in current week decides the year.
    tmp.setUTCDate(tmp.getUTCDate() + 4 - (tmp.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((tmp.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    // start/end (Monday-Sunday)
    const start = new Date(tmp);
    start.setUTCDate(start.getUTCDate() - (start.getUTCDay() || 7) + 1);
    const end = new Date(start);
    end.setUTCDate(start.getUTCDate() + 6);
    const fmt = (d: Date) => d.toLocaleDateString();
    const toIso = (d: Date) => formatLocalDate(d);
    return { year: tmp.getUTCFullYear(), week: weekNo, start: fmt(start), end: fmt(end), startIso: toIso(start), endIso: toIso(end) };
}

function getISOWeekKey(date: Date) {
    const tmp = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    tmp.setUTCDate(tmp.getUTCDate() + 4 - (tmp.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((tmp.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    return `${tmp.getUTCFullYear()}-W${weekNo}`;
}

// Timeline by point
const PointTimeline = ({ reviews, pointName, t }: { reviews: ReviewDto[], pointName: string | null, t: any }) => {
    const filtered = useMemo(() => {
        return reviews
            .filter(r => !pointName || r.pointName === pointName)
            .sort((a, b) => b.createdAt - a.createdAt);
    }, [reviews, pointName]);

    if (!filtered.length) {
        return <Typography color="text.secondary" py={2}>{t('analytics.empty_reviews')}</Typography>;
    }

    return (
        <Box sx={{ maxHeight: 500, overflowY: 'auto', pr: 1 }}>
            {filtered.map(r => (
                <Paper key={r.id} sx={{ p: 2, mb: 2, borderRadius: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
                    <Typography variant="subtitle2" fontWeight="700">{new Date(r.createdAt).toLocaleString()}</Typography>
                    <Box display="flex" alignItems="center" gap={1} mt={1} mb={1}>
                        <Box display="flex">
                            {[1,2,3,4,5].map(star => (
                                star <= r.rating ? 
                                <Star key={star} sx={{ fontSize: 16, color: '#ffc107' }} /> : 
                                <StarBorder key={star} sx={{ fontSize: 16, color: '#e0e0e0' }} />
                            ))}
                        </Box>
                        <Typography variant="body2" color="text.secondary">{r.authorName}</Typography>
                    </Box>
                    <Box display="flex" flexWrap="wrap" gap={0.5} mb={0.5}>
                        {r.tags.map(tag => (
                            <Chip 
                                key={tag} 
                                label={t(`analytics.tags.${tag}`, tag)} 
                                size="small" 
                                variant={getChipVariant(tag)}
                                color={getChipColor(tag)}
                            />
                        ))}
                        {r.tags.length === 0 && <Typography variant="body2" color="text.secondary">{t('analytics.empty_reviews')}</Typography>}
                    </Box>
                    {r.comment && <Typography variant="body2">{r.comment}</Typography>}
                </Paper>
            ))}
        </Box>
    );
}

