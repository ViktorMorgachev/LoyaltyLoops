import { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button, Divider, Alert, Stack, FormControl, InputLabel, Select, MenuItem, Table, TableHead, TableRow, TableCell, TableBody, Skeleton, CircularProgress } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useNavigate } from 'react-router-dom';

import { LoyaltyCardPreview } from '../../components/LoyaltyCardPreview';

export const BusinessSettingsPage = () => {
    const { t } = useTranslation();
    const { showSuccess, showError } = useNotification();
    const navigate = useNavigate();

    const [name, setName] = useState('');
    const [baseCurrency, setBaseCurrency] = useState('USD');
    const [color, setColor] = useState('#4F46E5'); // Дефолтный Индиго
    const [logo, setLogo] = useState('');
    const [burnBonusesDays, setBurnBonusesDays] = useState('');
    const [downgradeTierDays, setDowngradeTierDays] = useState('');
    const [defaultVisitsTarget, setDefaultVisitsTarget] = useState('10');
    const [tiers, setTiers] = useState<any[]>([]);
    const [subscriptionWarnings, setSubscriptionWarnings] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    const normalizePercentForDisplay = (value: any) => {
        if (value === null || value === undefined || value === '') return '';
        const numeric = typeof value === 'string' ? parseFloat(value) : value;
        if (isNaN(numeric)) return '';
        if (numeric > 0 && numeric < 1) {
            return parseFloat((numeric * 100).toFixed(4)).toString();
        }
        return parseFloat(numeric.toFixed(4)).toString();
    };

    const sanitizeNumber = (value: any) => {
        if (value === '' || value === null || value === undefined) return 0;
        const numeric = typeof value === 'number' ? value : parseFloat(value);
        if (isNaN(numeric) || numeric < 0) return 0;
        return numeric;
    };

    const updateTierValue = (index: number, field: 'threshold' | 'cashbackPercent', value: string) => {
        setTiers((prev) =>
            prev.map((tier, idx) => (idx === index ? { ...tier, [field]: value } : tier))
        );
    };

    const clampTierValue = (index: number, field: 'threshold' | 'cashbackPercent', value: string) => {
        if (value === '') return;
        const numeric = sanitizeNumber(value);
        updateTierValue(index, field, numeric.toString());
    };

    const renderTierLabel = (tier: any) => {
        if (!tier) return '-';
        return tier.loyaltyTier?.descr || tier.loyaltyTier?.level || `Tier ${tier.levelIndex}`;
    };

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            const res = await api.get('/partners/me');
            const data = res.data;
            setName(data.businessName || '');
            setBaseCurrency(data.baseCurrency);
            setColor(data.color);
            setLogo(data.logoUrl || '');
            setBurnBonusesDays(data.burnBonusesDays !== null && data.burnBonusesDays !== undefined ? String(data.burnBonusesDays) : '');
            setDowngradeTierDays(data.downgradeTierDays !== null && data.downgradeTierDays !== undefined ? String(data.downgradeTierDays) : '');
            setDefaultVisitsTarget(data.defaultVisitsTarget !== null && data.defaultVisitsTarget !== undefined ? String(data.defaultVisitsTarget) : '10');
            setSubscriptionWarnings(data.subscriptionWarnings || []);

            const sortedTiers = (data.tiers || []).sort((a: any, b: any) => a.levelIndex - b.levelIndex);
            const normalizedTiers = sortedTiers.map((tier: any) => ({
                ...tier,
                threshold: tier.threshold !== undefined && tier.threshold !== null ? tier.threshold.toString() : '0',
                cashbackPercent: normalizePercentForDisplay(tier.cashbackPercent)
            }));
            setTiers(normalizedTiers);
        } catch (e: any) {
            if (e.response && e.response.status === 404) {
                // No business yet -> Redirect to create
                navigate('/partner/onboarding');
            } else {
                showError(getErrorMessage(e));
            }
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            const parsedVisits = Math.max(1, parseInt(defaultVisitsTarget || '10', 10) || 10);
            
            const normalizedTiers = tiers.map((tier: any) => ({
                ...tier,
                threshold: sanitizeNumber(tier.threshold),
                cashbackPercent: sanitizeNumber(tier.cashbackPercent)
            }));

            await api.put('/partners/me', {
                businessName: name,
                baseCurrency: baseCurrency,
                color: color,
                logoUrl: logo,
                burnBonusesDays: burnBonusesDays ? parseInt(burnBonusesDays, 10) : null,
                downgradeTierDays: downgradeTierDays ? parseInt(downgradeTierDays, 10) : null,
                defaultVisitsTarget: parsedVisits,
                tiers: normalizedTiers
            });
            showSuccess(t('settings.save_success'));
            await loadData();
        } catch (e: any) {
            showError(getErrorMessage(e));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Box maxWidth="lg" mx="auto" mt={4}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>{t('settings.title')}</Typography>

            {subscriptionWarnings.length > 0 && (
                <Stack spacing={2} mb={4}>
                    {subscriptionWarnings.map((warning, idx) => {
                        const dateStr = new Date(warning.endDate).toLocaleDateString();
                        const now = Date.now();
                        const diff = warning.endDate - now;
                        const isCritical = diff < 24 * 60 * 60 * 1000; // < 24h

                        return (
                            <Alert 
                                key={idx} 
                                severity={isCritical ? "error" : "warning"} 
                                variant="filled"
                            >
                                {isCritical
                                    ? t('settings.subscription_expiring_critical', { point: warning.pointName, date: dateStr })
                                    : t('settings.subscription_expiring', { point: warning.pointName, date: dateStr })
                                }
                            </Alert>
                        );
                    })}
                </Stack>
            )}

            <Paper elevation={0} sx={{ p: 4, maxWidth: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>

                <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: '1.5fr 1fr' }} gap={6}>
                    {/* ЛЕВАЯ КОЛОНКА: Настройки */}
                    <Box>
                        <Typography variant="h6" gutterBottom fontWeight="600">Основная информация</Typography>
                        {loading ? (
                            <>
                                <Skeleton variant="rectangular" height={56} sx={{ mb: 2, borderRadius: 1 }} />
                                <Skeleton variant="rectangular" height={56} sx={{ mb: 2, borderRadius: 1 }} />
                                <Skeleton variant="rectangular" height={56} sx={{ mb: 2, borderRadius: 1 }} />
                            </>
                        ) : (
                            <>
                <TextField
                    label={t('settings.name_label')}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    fullWidth margin="normal"
                            variant="outlined"
                        />
                <TextField
                    label={t('settings.logo_label')}
                    value={logo}
                    onChange={(e) => setLogo(e.target.value)}
                    fullWidth margin="normal"
                    placeholder="https://example.com/logo.png"
                            helperText="URL ссылки на изображение"
                />

                <FormControl fullWidth margin="normal">
                    <InputLabel>{t('dashboard.label_base_currency', 'Base Currency')}</InputLabel>
                    <Select
                        value={baseCurrency}
                        label={t('dashboard.label_base_currency', 'Base Currency')}
                        onChange={(e) => setBaseCurrency(e.target.value)}
                    >
                    <MenuItem value="RUB">{t('currency.RUB', 'RUB (Рубль)')}</MenuItem>
                        <MenuItem value="USD">{t('currency.USD', 'USD (Доллар)')}</MenuItem>
                        <MenuItem value="KGS">{t('currency.KGS', 'KGS (Сом)')}</MenuItem>
                        <MenuItem value="KZT">{t('currency.KZT', 'KZT (Тенге)')}</MenuItem>
                        <MenuItem value="UZS">{t('currency.UZS', 'UZS (Сум)')}</MenuItem>
                        <MenuItem value="BYN">{t('currency.BYN', 'BYN (Бел. рубль)')}</MenuItem>
                    </Select>
                </FormControl>
                            </>
                        )}

                        <Divider sx={{ my: 4 }} />

                        <Typography variant="h6" gutterBottom fontWeight="600">Настройки Лояльности</Typography>
                        
                        <Box maxWidth={400}>
                            {loading ? (
                                <Skeleton variant="rectangular" height={56} sx={{ mb: 2, borderRadius: 1 }} />
                            ) : (
                <TextField
                    label={t('settings.visits_target_label')}
                    value={defaultVisitsTarget}
                    onChange={(e) => setDefaultVisitsTarget(e.target.value)}
                    type="number"
                    inputProps={{ min: 1 }}
                    fullWidth
                    margin="normal"
                    helperText={t('settings.visits_target_hint')}
                />
                            )}
                        </Box>

                        <Typography variant="subtitle1" gutterBottom sx={{ mt: 3, fontWeight: 600 }}>{t('point_details.levels_config')}</Typography>
                        <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden', mb: 4 }}>
                            <Table size="small">
                                <TableHead sx={{ bgcolor: 'grey.100' }}>
                                    <TableRow>
                                        <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_name')}</TableCell>
                                        <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_threshold')}</TableCell>
                                        <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_percent')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {loading ? (
                                        Array.from({ length: 3 }).map((_, i) => (
                                            <TableRow key={i}>
                                                <TableCell><Skeleton variant="text" /></TableCell>
                                                <TableCell><Skeleton variant="rectangular" height={32} /></TableCell>
                                                <TableCell><Skeleton variant="rectangular" height={32} /></TableCell>
                                            </TableRow>
                                        ))
                                    ) : (
                                    tiers.map((tier, idx) => (
                                        <TableRow key={idx}>
                                            <TableCell>{renderTierLabel(tier)}</TableCell>
                                            <TableCell>
                                                <TextField
                                                    type="number"
                                                    size="small"
                                                    value={tier.threshold}
                                                    disabled={tier.levelIndex === 1}
                                                    inputProps={{ min: 0, inputMode: 'decimal' }}
                                                    onChange={(e) => updateTierValue(idx, 'threshold', e.target.value)}
                                                    onBlur={(e) => clampTierValue(idx, 'threshold', e.target.value)}
                                                    InputProps={{
                                                        endAdornment: (
                                                            <span style={{ marginLeft: 4, fontSize: '0.8rem', color: 'gray' }}>
                                                                {baseCurrency || ''}
                                                            </span>
                                                        )
                                                    }}
                                                    sx={{ maxWidth: 160 }}
                                                />
                                            </TableCell>
                                            <TableCell>
                                                <TextField
                                                    type="number"
                                                    size="small"
                                                    value={tier.cashbackPercent}
                                                    inputProps={{ min: 0, inputMode: 'decimal' }}
                                                    onChange={(e) => updateTierValue(idx, 'cashbackPercent', e.target.value)}
                                                    onBlur={(e) => clampTierValue(idx, 'cashbackPercent', e.target.value)}
                                                    InputProps={{ endAdornment: <span style={{ marginLeft: 4, fontSize: '0.8rem', color: 'gray' }}>%</span> }}
                                                    sx={{ maxWidth: 100 }}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))
                                    )}
                                </TableBody>
                            </Table>
                        </Paper>

                        <Typography variant="subtitle1" sx={{ mt: 4, mb: 1, fontWeight: 600 }}>{t('settings.expiration_policy')}</Typography>
                        <Paper variant="outlined" sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                            <Typography variant="body2" color="text.secondary" mb={2}>{t('settings.expiration_hint')}</Typography>
                            {loading ? (
                                <Box display="flex" gap={2}>
                                    <Skeleton variant="rectangular" width="50%" height={56} />
                                    <Skeleton variant="rectangular" width="50%" height={56} />
                                </Box>
                            ) : (
                            <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2}>
                    <TextField 
                        label={t('point_details.burn_bonuses')} 
                        type="number"
                        fullWidth 
                        value={burnBonusesDays} 
                        onChange={e => setBurnBonusesDays(e.target.value)}
                        helperText={t('settings.burn_hint')}
                                    InputProps={{ endAdornment: <span style={{ fontSize: '0.8rem', color: 'gray' }}>дней</span> }}
                    />
                    <TextField 
                        label={t('point_details.downgrade_tier')} 
                        type="number"
                        fullWidth 
                        value={downgradeTierDays} 
                        onChange={e => setDowngradeTierDays(e.target.value)}
                        helperText={t('settings.downgrade_hint')}
                                    InputProps={{ endAdornment: <span style={{ fontSize: '0.8rem', color: 'gray' }}>дней</span> }}
                                />
                            </Box>
                            )}
                        </Paper>
                    </Box>
                    
                    {/* ПРАВАЯ КОЛОНКА: Визуал + Цвет */}
                    <Box>
                        <Typography variant="h6" gutterBottom fontWeight="600">{t('settings.color_label')}</Typography>
                        {loading ? (
                            <>
                                <Skeleton variant="rectangular" height={60} sx={{ mb: 2, borderRadius: 2 }} />
                                <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 4 }} />
                            </>
                        ) : (
                            <>
                        <Box mb={4} display="flex" alignItems="center" gap={2}>
                             <input
                                type="color"
                                value={color}
                                onChange={(e) => setColor(e.target.value)}
                                style={{ width: '60px', height: '60px', cursor: 'pointer', border: 'none', padding: 0, backgroundColor: 'transparent', borderRadius: '8px' }}
                            />
                            <Box>
                                <Typography variant="body2" fontWeight="bold">Выберите цвет бренда</Typography>
                                <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                                    {color}
                                </Typography>
                            </Box>
                        </Box>

                        <Typography variant="h6" gutterBottom fontWeight="600" sx={{ mb: 2 }}>Превью карты</Typography>
                        <LoyaltyCardPreview 
                            businessName={name}
                            color={color}
                            logoUrl={logo}
                            levelName="Gold"
                            balance={1250}
                        />
                        <Typography variant="caption" color="text.secondary" display="block" mt={2} textAlign="center" maxWidth={340}>
                            Так ваша карта будет выглядеть в кошельке клиента
                        </Typography>
                            </>
                        )}
                    </Box>
                </Box>

                <Box mt={6} display="flex" justifyContent="flex-end" pt={4} borderTop="1px solid" borderColor="divider">
                    <Button variant="contained" onClick={handleSave} disabled={loading || saving} size="large" sx={{ px: 6, borderRadius: 2 }} startIcon={saving ? <CircularProgress size={20} color="inherit" /> : null}>
                    {t('common.save')}
                </Button>
                </Box>
            </Paper>
        </Box>
    );
};
