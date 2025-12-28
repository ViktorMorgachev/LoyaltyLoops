import React from 'react';
import { 
    Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography, 
    Table, TableHead, TableBody, TableRow, TableCell, Chip, Paper
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { Map as MapIcon, Store as StoreIcon, AccessTime as TimeIcon, ContactPhone as PhoneIcon, Language as WebIcon } from '@mui/icons-material';

interface PointDetailsDialogProps {
    open: boolean;
    onClose: () => void;
    loading: boolean;
    data: any; // TradingPointDetailsDto
}

export const PointDetailsDialog: React.FC<PointDetailsDialogProps> = ({ open, onClose, loading, data }) => {
    const { t } = useTranslation();

    if (!open) return null;

    const buildMapUrl = (point: any) => {
        const lat = point?.latitude;
        const lng = point?.longitude;
        if (lat !== null && lat !== undefined && lng !== null && lng !== undefined) {
            return `https://yandex.com/maps/?pt=${lng},${lat}&z=17&l=map`;
        }
        if (point?.address) {
            return `https://yandex.com/maps/?text=${encodeURIComponent(point.address)}`;
        }
        return null;
    };

    const mapUrl = data?.point ? buildMapUrl(data.point) : null;
    const scheduleNotSetText = String(t('point_details.schedule_not_set', { defaultValue: 'Schedule not set' }));

    const content = loading ? (
        <Box p={3} textAlign="center">
            <Typography color="text.secondary">{t('common.loading')}...</Typography>
        </Box>
    ) : !data ? (
        <Box p={3} textAlign="center">
            <Typography color="text.secondary">{t('common.not_found')}</Typography>
        </Box>
    ) : (
        <Box>
            {/* Header Section */}
            <Box display="flex" flexDirection="column" gap={1} mb={3}>
                <Typography variant="h5" fontWeight="bold" gutterBottom>
                    {data.point.name}
                </Typography>
                
                <Box display="flex" gap={1} flexWrap="wrap">
                    <Chip 
                        label={t(`dashboard.types.${data.point.type}`) || data.point.type} 
                        size="small" 
                        color="primary" 
                        variant="outlined" 
                        icon={<StoreIcon />}
                    />
                    <Chip 
                        label={data.point.active ? t('common.active') : t('common.inactive')} 
                        size="small" 
                        color={data.point.active ? 'success' : 'default'} 
                    />
                    <Chip
                        label={t(`dashboard.strategies.${data.settings.programType}`)}
                        size="small"
                        color="info"
                        variant="outlined"
                    />
                </Box>
            </Box>

            <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: '1fr 1fr' }} gap={3}>
                {/* Left Column: Info */}
                <Box>
                    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
                        <Typography variant="subtitle2" gutterBottom color="text.secondary" fontWeight="bold">
                            {t('point_details.info_title')}
                        </Typography>
                        
                        <Box display="flex" flexDirection="column" gap={1.5}>
                            <Box>
                                <Typography variant="caption" color="text.secondary" display="block">{t('point_details.address_label')}</Typography>
                                <Typography variant="body2">{data.point.address || '—'}</Typography>
                            </Box>
                            
                            {data.point.contactPhone && (
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('point_details.contact_phone_label')}</Typography>
                                    <Box display="flex" alignItems="center" gap={0.5}>
                                        <PhoneIcon fontSize="small" color="action" />
                                        <Typography variant="body2">{data.point.contactPhone}</Typography>
                                    </Box>
                                </Box>
                            )}

                            {data.point.contactLink && (
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('point_details.contact_link_label')}</Typography>
                                    <Box display="flex" alignItems="center" gap={0.5}>
                                        <WebIcon fontSize="small" color="action" />
                                        <Typography variant="body2" component="a" href={data.point.contactLink} target="_blank" rel="noopener">
                                            {data.point.contactLink}
                                        </Typography>
                                    </Box>
                                </Box>
                            )}

                            <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('dashboard.label_currency')}</Typography>
                                    <Typography variant="body2" fontWeight="bold">{data.point.currency || '—'}</Typography>
                                </Box>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('point_details.schedule_timezone', { tz: '' }).replace(': ', '')}</Typography>
                                    <Typography variant="body2">{data.point.timezone || data.settings?.timezone || '—'}</Typography>
                                </Box>
                            </Box>

                            {data.point.additionalInfo && (
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('point_details.additional_info_label')}</Typography>
                                    <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', bgcolor: 'grey.50', p: 1, borderRadius: 1 }}>
                                        {data.point.additionalInfo}
                                    </Typography>
                                </Box>
                            )}

                            {(data.point.latitude || data.point.longitude) && (
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block">{t('point_details.map_point_label')}</Typography>
                                    <Typography variant="body2" fontFamily="monospace">
                                        {data.point.latitude}, {data.point.longitude}
                                    </Typography>
                                    {mapUrl && (
                                        <Button 
                                            variant="outlined" 
                                            size="small" 
                                            startIcon={<MapIcon />} 
                                            onClick={() => window.open(mapUrl, '_blank', 'noopener,noreferrer')}
                                            sx={{ mt: 1, textTransform: 'none' }}
                                        >
                                            {t('point_details.map_preview_cta')}
                                        </Button>
                                    )}
                                </Box>
                            )}
                        </Box>
                    </Paper>
                </Box>

                {/* Right Column: Schedule & Loyalty */}
                <Box>
                    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mb: 3 }}>
                        <Typography variant="subtitle2" gutterBottom color="text.secondary" fontWeight="bold" display="flex" alignItems="center" gap={1}>
                            <TimeIcon fontSize="small" />
                            {t('point_details.schedule_title')}
                        </Typography>
                        
                        {data.point.schedule?.days?.length ? (
                            <Table size="small">
                                <TableBody>
                                    {data.point.schedule.days.map((d: any, idx: number) => {
                                        const intervals = d.intervals || [];
                                        const hours = intervals.length
                                            ? intervals.map((i: any) => `${i.opensAt ?? ''}–${i.closesAt ?? ''}`).join(', ')
                                            : String(t('point_details.schedule_day_off'));
                                        return (
                                            <TableRow key={`${d.day}-${idx}`} sx={{ '& td': { border: 0, py: 0.5, px: 1 } }}>
                                                <TableCell sx={{ color: 'text.secondary', fontWeight: 500 }}>
                                                    {String(t(`point_details.schedule_days.${(d.day?.toLowerCase?.() || '').substring(0, 3)}`, { defaultValue: d.day }))}
                                                </TableCell>
                                                <TableCell align="right">{hours}</TableCell>
                                            </TableRow>
                                        );
                                    })}
                                </TableBody>
                            </Table>
                        ) : (
                            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                                {scheduleNotSetText}
                            </Typography>
                        )}
                    </Paper>

                    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                        <Typography variant="subtitle2" gutterBottom color="text.secondary" fontWeight="bold">
                            {t('dashboard.label_strategy')}
                        </Typography>

                        {data.settings.programType === 'VISIT_COUNTER' || data.settings.programType === 'HYBRID' ? (
                            <Box mb={2}>
                                <Typography variant="caption" color="text.secondary" display="block">{t('dashboard.label_target')}</Typography>
                                <Typography variant="body1" fontWeight="bold">
                                    {data.settings.visitsTarget}
                                </Typography>
                            </Box>
                        ) : null}

                        {(data.settings.programType === 'TIERED_LTV' || data.settings.programType === 'HYBRID') && (
                            <>
                                <Box mb={2} display="flex" gap={2}>
                                    <Box>
                                        <Typography variant="caption" color="text.secondary" display="block">{t('point_details.max_burn_label')}</Typography>
                                        <Typography variant="body2">{data.settings.maxBurnPercentage}%</Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" color="text.secondary" display="block">{t('point_details.award_mixed_label')}</Typography>
                                        <Typography variant="body2">{data.settings.awardOnMixedPayment ? t('common.yes') : t('common.no')}</Typography>
                                    </Box>
                                </Box>

                                {data.settings.tiers && (
                                    <Box>
                                        <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                                            {t('point_details.levels_config')}
                                        </Typography>
                                        <Table size="small" sx={{ '& th, & td': { px: 1 } }}>
                                            <TableHead sx={{ bgcolor: 'grey.50' }}>
                                                <TableRow>
                                                    <TableCell>{t('point_details.lvl_name')}</TableCell>
                                                    <TableCell>{t('point_details.lvl_threshold')}</TableCell>
                                                    <TableCell>{t('point_details.lvl_percent')}</TableCell>
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {data.settings.tiers.map((tier: any, idx: number) => (
                                                    <TableRow key={idx}>
                                                        <TableCell>{tier.loyaltyTier?.descr || tier.loyaltyTier?.level || `Tier ${tier.levelIndex}`}</TableCell>
                                                        <TableCell>{tier.threshold} {data.baseCurrency || ''}</TableCell>
                                                        <TableCell>{tier.cashbackPercent}%</TableCell>
                                                    </TableRow>
                                                ))}
                                            </TableBody>
                                        </Table>
                                    </Box>
                                )}
                            </>
                        )}
                    </Paper>
                </Box>
            </Box>
        </Box>
    );

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                {t('point_details.admin_title', 'Trading Point Details')}
                {!loading && data && (
                    <Typography 
                        variant="caption" 
                        color="text.secondary" 
                        sx={{ fontFamily: 'monospace', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 0.5 }}
                        onClick={() => navigator.clipboard.writeText(data.point.id)}
                        title={t('common.click_to_copy', 'Click to copy')}
                    >
                        {t('admin.id', 'ID')}: {data.point.id.substring(0, 8)}...
                    </Typography>
                )}
            </DialogTitle>
            <DialogContent dividers>
                {content}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>{t('common.close')}</Button>
            </DialogActions>
        </Dialog>
    );
};
