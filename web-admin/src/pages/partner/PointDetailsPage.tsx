import React, { useEffect, useState } from 'react';
import type { TFunction } from 'i18next';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Container, Typography, Paper, Box, Tabs, Tab, TextField, Button, 
  Table, TableHead, TableRow, TableCell, TableBody, Chip,
  Select, MenuItem, FormControl, InputLabel, Switch, FormControlLabel,
  Alert, Divider, Dialog, DialogTitle, DialogContent, DialogActions,
  List, ListItem, ListItemText, CircularProgress, Skeleton
} from '@mui/material';
import { Delete as DeleteIcon, Save as SaveIcon } from '@mui/icons-material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { LocationPicker } from '../../components/LocationPicker';
import { useUser } from '../../context/UserContext';
import { reverseGeocode as reverseGeocodeYandex } from '../../utils/yandexGeocode';
import { PublicPointsPreviewDialog } from '../../components/map/PublicPointsPreviewDialog';
import { PhoneInput } from '../../components/inputs/PhoneInput';
import { TimezoneSelect } from '../../components/common/TimezoneSelect';

type WeekDayId =
    | 'MONDAY'
    | 'TUESDAY'
    | 'WEDNESDAY'
    | 'THURSDAY'
    | 'FRIDAY'
    | 'SATURDAY'
    | 'SUNDAY';

interface DayScheduleForm {
    enabled: boolean;
    workStart: string;
    workEnd: string;
    lunchStart: string;
    lunchEnd: string;
}

type ScheduleFormState = Record<WeekDayId, DayScheduleForm>;

interface WorkingIntervalPayload {
    opensAt?: string;
    closesAt?: string;
}

interface WorkingDayPayload {
    day: WeekDayId;
    intervals?: WorkingIntervalPayload[];
}

interface WeeklySchedulePayload {
    timezone?: string;
    days?: WorkingDayPayload[];
}

const DEFAULT_TIMEZONE = 'Asia/Bishkek';

const WEEK_DAYS: { id: WeekDayId; titleKey: string; shortKey: string }[] = [
    { id: 'MONDAY', titleKey: 'point_details.schedule_days.mon', shortKey: 'point_details.schedule_days_short.mon' },
    { id: 'TUESDAY', titleKey: 'point_details.schedule_days.tue', shortKey: 'point_details.schedule_days_short.tue' },
    { id: 'WEDNESDAY', titleKey: 'point_details.schedule_days.wed', shortKey: 'point_details.schedule_days_short.wed' },
    { id: 'THURSDAY', titleKey: 'point_details.schedule_days.thu', shortKey: 'point_details.schedule_days_short.thu' },
    { id: 'FRIDAY', titleKey: 'point_details.schedule_days.fri', shortKey: 'point_details.schedule_days_short.fri' },
    { id: 'SATURDAY', titleKey: 'point_details.schedule_days.sat', shortKey: 'point_details.schedule_days_short.sat' },
    { id: 'SUNDAY', titleKey: 'point_details.schedule_days.sun', shortKey: 'point_details.schedule_days_short.sun' },
];

const createDayState = (): DayScheduleForm => ({
    enabled: false,
    workStart: '09:00',
    workEnd: '18:00',
    lunchStart: '',
    lunchEnd: '',
});

const createEmptyScheduleState = (): ScheduleFormState =>
    WEEK_DAYS.reduce((acc, day) => {
        acc[day.id] = createDayState();
        return acc;
    }, {} as ScheduleFormState);

const timeToMinutes = (value?: string): number | null => {
    if (!value || !/^\d{2}:\d{2}$/.test(value)) return null;
    const [hours, minutes] = value.split(':').map((n) => parseInt(n, 10));
    if (Number.isNaN(hours) || Number.isNaN(minutes)) return null;
    return hours * 60 + minutes;
};

const getDayLabel = (dayId: WeekDayId, t: TFunction<'translation'>) => {
    const meta = WEEK_DAYS.find((d) => d.id === dayId);
    return meta ? t(meta.titleKey) : dayId;
};

const formatScheduleDisplay = (entry: DayScheduleForm, t: TFunction<'translation'>) => {
    if (!entry.enabled) {
        return t('point_details.schedule_day_off');
    }
    const baseRange = `${entry.workStart} — ${entry.workEnd}`;
    if (entry.lunchStart && entry.lunchEnd) {
        return t('point_details.schedule_preview_with_lunch', {
            work: baseRange,
            lunch: `${entry.lunchStart}–${entry.lunchEnd}`,
        });
    }
    return baseRange;
};

const getScheduleValidationError = (
    state: ScheduleFormState,
    t: TFunction<'translation'>
): string | null => {
    const enabledDays = WEEK_DAYS.filter(({ id }) => state[id].enabled);
    if (!enabledDays.length) {
        return t('point_details.schedule_validation_required');
    }

    for (const { id } of enabledDays) {
        const entry = state[id];
        const workStart = timeToMinutes(entry.workStart);
        const workEnd = timeToMinutes(entry.workEnd);
        if (workStart === null || workEnd === null || workStart >= workEnd) {
            return t('point_details.schedule_validation_range', { day: getDayLabel(id, t) });
        }
        const lunchStart = entry.lunchStart ? timeToMinutes(entry.lunchStart) : null;
        const lunchEnd = entry.lunchEnd ? timeToMinutes(entry.lunchEnd) : null;
        if ((lunchStart === null) !== (lunchEnd === null)) {
            return t('point_details.schedule_validation_lunch_pair', { day: getDayLabel(id, t) });
        }
        if (lunchStart !== null && lunchEnd !== null) {
            if (lunchStart >= lunchEnd || lunchStart <= workStart || lunchEnd >= workEnd) {
                return t('point_details.schedule_validation_lunch_range', { day: getDayLabel(id, t) });
            }
        }
    }
    return null;
};

const parseSchedulePayload = (payload?: WeeklySchedulePayload | null): { state: ScheduleFormState; timezone: string } => {
    const base = createEmptyScheduleState();
    const timezone = payload?.timezone || DEFAULT_TIMEZONE;

    if (!payload?.days?.length) {
        return { state: base, timezone };
    }

    payload.days.forEach((dayPayload) => {
        if (!dayPayload || !base[dayPayload.day]) {
            return;
        }
        const intervals = dayPayload.intervals || [];
        if (!intervals.length) {
            base[dayPayload.day] = { ...createDayState(), enabled: false };
            return;
        }
        const first = intervals[0];
        const last = intervals[intervals.length - 1];
        const lunchGap =
            intervals.length >= 2
                ? {
                      start: intervals[0].closesAt ?? '',
                      end: intervals[1].opensAt ?? '',
                  }
                : null;

        base[dayPayload.day] = {
            enabled: true,
            workStart: first.opensAt ?? '09:00',
            workEnd: last.closesAt ?? '18:00',
            lunchStart: lunchGap?.start || '',
            lunchEnd: lunchGap?.end || '',
        };
    });

    return { state: base, timezone };
};

const scheduleStateToPayload = (state: ScheduleFormState, timezone: string): WeeklySchedulePayload | null => {
    const days: WorkingDayPayload[] = [];

    WEEK_DAYS.forEach(({ id }) => {
        const entry = state[id];
        if (!entry || !entry.enabled) {
            return;
        }
        if (!entry.workStart || !entry.workEnd) {
            return;
        }
        const intervals: WorkingIntervalPayload[] = [];
        if (entry.lunchStart && entry.lunchEnd) {
            intervals.push({ opensAt: entry.workStart, closesAt: entry.lunchStart });
            intervals.push({ opensAt: entry.lunchEnd, closesAt: entry.workEnd });
        } else {
            intervals.push({ opensAt: entry.workStart, closesAt: entry.workEnd });
        }
        days.push({ day: id, intervals });
    });

    if (!days.length) {
        return null;
    }

    return {
        timezone,
        days,
    };
};

export const PointDetailsPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { showSuccess, showError } = useNotification();
  const { currentWorkspace } = useUser();
  const canEdit = currentWorkspace?.role === 'PARTNER_ADMIN';
  const mapApiKey = import.meta.env.VITE_YMAPS_API_KEY as string | undefined;

  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [details, setDetails] = useState<any>(null);
  const [cashiers, setCashiers] = useState<any[]>([]);
  const [mapPreviewOpen, setMapPreviewOpen] = useState(false);
  
  // Form State
  const [formData, setFormData] = useState<any>({
    name: '',
    address: '',
    type: 'COFFEE_SHOP',
    visitsTarget: 6,
    latitude: null,
    longitude: null,
    contactPhone: '',
    contactLink: '',
    additionalInfo: ''
  });
  
  const [programType, setProgramType] = useState('TIERED_LTV');
  const [tiers, setTiers] = useState<any[]>([]);
  const [maxBurnPercentage, setMaxBurnPercentage] = useState(100);
  const [scheduleState, setScheduleState] = useState<ScheduleFormState>(() => createEmptyScheduleState());
  const [scheduleTimezone, setScheduleTimezone] = useState(DEFAULT_TIMEZONE);
  const [temporarilyPaused, setTemporarilyPaused] = useState(false);
  const countryOptions = React.useMemo(() => ([
    { code: 'KG', currency: 'KGS', label: t('countries.KG'), defaultTimezone: 'Asia/Bishkek' },
    { code: 'RU', currency: 'RUB', label: t('countries.RU'), defaultTimezone: 'Europe/Moscow' },
    { code: 'KZ', currency: 'KZT', label: t('countries.KZ'), defaultTimezone: 'Asia/Almaty' },
    { code: 'UZ', currency: 'UZS', label: t('countries.UZ'), defaultTimezone: 'Asia/Tashkent' },
    { code: 'BY', currency: 'BYN', label: t('countries.BY'), defaultTimezone: 'Europe/Minsk' },
  ]), [t]);
  const [currency, setCurrency] = useState('KGS');
  const [awardOnMixedPayment, setAwardOnMixedPayment] = useState(false);
  const additionalInfoLimit = 30;
  const additionalInfoLength = formData.additionalInfo?.length ?? 0;
  const overviewCurrency = React.useMemo(() => {
    const normalized = details?.point?.currency?.trim();
    if (!normalized) {
      return t('common.not_selected');
    }
    const upper = normalized.toUpperCase();
    const option = countryOptions.find(opt => opt.currency === upper || opt.code === upper);
    if (option) {
      return `${option.label} (${option.currency})`;
    }
    return normalized.toUpperCase();
  }, [details?.point?.currency, countryOptions, t]);
  const hasPublishedSchedule = Boolean(
    details?.point?.schedule?.days?.some((day: any) => Array.isArray(day?.intervals) && day.intervals.length > 0)
  );
  const overviewStatus = details?.point?.temporarilyPaused
    ? t('point_details.status_paused')
    : details?.point?.active
      ? hasPublishedSchedule
        ? t('point_details.status_active')
        : t('point_details.status_no_hours')
      : t('point_details.status_inactive');
  const overviewStatusColor: 'success' | 'default' | 'warning' = details?.point?.temporarilyPaused
    ? 'warning'
    : details?.point?.active && hasPublishedSchedule
      ? 'success'
      : 'default';

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

  const normalizeOptional = (value: any, limit?: number) => {
    if (value === null || value === undefined) return null;
    const trimmed = String(value).trim();
    if (!trimmed) return null;
    if (limit && trimmed.length > limit) {
      return trimmed.slice(0, limit);
    }
    return trimmed;
  };

  const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);
  const [partnerBaseCurrency, setPartnerBaseCurrency] = useState<string>(''); // Base currency of partner for thresholds

  const renderTierLabel = (tier: any) => {
    if (!tier) return '-';
    return tier.loyaltyTier?.descr || tier.loyaltyTier?.level || `Tier ${tier.levelIndex}`;
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    if(!id) return;
    try {
      setLoading(true);
      const res = await api.get(`/partners/points/${id}`);
      const data = res.data; // TradingPointDetailsDto
      setDetails(data);
      
      // Init Form
      setFormData({
        name: data.point.name,
        address: data.point.address || '',
        type: data.point.type,
        visitsTarget: data.settings.visitsTarget || 6,
        latitude: data.point.latitude,
        longitude: data.point.longitude,
        contactPhone: data.point.contactPhone || '',
        contactLink: data.point.contactLink || '',
        additionalInfo: data.point.additionalInfo || ''
      });
      
      setProgramType(data.settings.programType);
      setPartnerBaseCurrency(data.baseCurrency || '');
      setMaxBurnPercentage(data.settings.maxBurnPercentage || 100);

      const backendCurrency = data.point.currency?.trim();
      const normalizedCurrency = backendCurrency ? backendCurrency.toUpperCase() : '';
      const matchedCountry = countryOptions.find(
        opt => opt.currency === normalizedCurrency || opt.code === normalizedCurrency
      );

      if (matchedCountry) {
        setCurrency(matchedCountry.currency);
      } else {
        setCurrency('KGS');
      }

      setAwardOnMixedPayment(Boolean(data.settings.awardOnMixedPayment));

      const parsedSchedule = parseSchedulePayload(data.point.schedule);
      setScheduleState(parsedSchedule.state);
      setScheduleTimezone(data.point.timezone || parsedSchedule.timezone || DEFAULT_TIMEZONE);
      setTemporarilyPaused(Boolean(data.point.temporarilyPaused));

      // Sort tiers by index just in case
      const sortedTiers = (data.settings.tiers || []).sort((a: any, b: any) => a.levelIndex - b.levelIndex);
      const normalizedTiers = sortedTiers.map((tier: any) => ({
        ...tier,
        threshold: tier.threshold !== undefined && tier.threshold !== null ? tier.threshold.toString() : '0',
        cashbackPercent: normalizePercentForDisplay(tier.cashbackPercent)
      }));
      setTiers(normalizedTiers);

      // Load Cashiers if tab is 2
      if (tab === 2) loadCashiers();

    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  const loadCashiers = async () => {
    if(!id) return;
    try {
      const res = await api.get(`/partners/points/${id}/cashiers`);
      setCashiers(res.data);
    } catch (e: any) {
       showError(getErrorMessage(e));
    }
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTab(newValue);
    if (newValue === 2) loadCashiers();
  };

  const reverseGeocodeFallback = async (lat: number, lng: number): Promise<string | null> => {
      try {
          const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
          const response = await fetch(url, { headers: { 'User-Agent': 'LoyaltyLoop-Admin/1.0' } });
          if (response.ok) {
              const data = await response.json();
              if (data?.display_name) {
                  return data.display_name;
              }
          }
      } catch (error) {
          console.error('Geocoding error', error);
      }
      return null;
  };

  const resolveAddress = React.useCallback(
      async (lat: number, lng: number): Promise<string | null> => {
          if (mapApiKey) {
              try {
                  const addr = await reverseGeocodeYandex(mapApiKey, lat, lng);
                  if (addr) return addr;
              } catch (e) {
                  console.error('Yandex geocode failed:', e);
              }
          }
          return reverseGeocodeFallback(lat, lng);
      },
      [mapApiKey]
  );

  const handleLocationChange = React.useCallback(
      async (lat: number, lng: number) => {
          console.log('Location changed:', lat, lng, 'canEdit:', canEdit);
          if (!canEdit) {
              return;
          }
          setFormData((prev: any) => ({ ...prev, latitude: lat, longitude: lng }));
          
          try {
              const addr = await resolveAddress(lat, lng);
              console.log('Resolved address:', addr);
              if (addr) {
                  setFormData((prev: any) => ({ ...prev, address: addr, latitude: lat, longitude: lng }));
              }
          } catch (error) {
              console.error('Failed to resolve address:', error);
          }
      },
      [canEdit, resolveAddress]
  );

    const handleSaveSettings = async () => {
     if (!canEdit) {
        showError(t('point_details.edit_forbidden'));
        return;
     }
     try {
        setSaving(true);
        const validationError = getScheduleValidationError(scheduleState, t);
        if (validationError) {
            showError(validationError);
            return;
        }

        const normalizedTiers = tiers.map((tier: any) => ({
            ...tier,
            threshold: sanitizeNumber(tier.threshold),
            cashbackPercent: sanitizeNumber(tier.cashbackPercent)
        }));

        const schedulePayload = scheduleStateToPayload(scheduleState, scheduleTimezone);
        if (!schedulePayload) {
            showError(t('point_details.schedule_validation_required'));
            return;
        }

        const payload = {
            name: formData.name,
            type: formData.type,
            address: formData.address || '',
            latitude: formData.latitude ?? 0,
            longitude: formData.longitude ?? 0,
            currency: currency,
            timezone: scheduleTimezone, // Top-level timezone
            contactPhone: normalizeOptional(formData.contactPhone),
            contactLink: normalizeOptional(formData.contactLink),
            additionalInfo: normalizeOptional(formData.additionalInfo, additionalInfoLimit),
            settings: {
                programType: programType, 
                tiers: normalizedTiers, 
                maxBurnPercentage: maxBurnPercentage,
                awardOnMixedPayment
            },
            schedule: schedulePayload,
            temporarilyPaused
        };

        await api.put(`/partners/points/${id}`, payload);
        showSuccess(t('settings.save_success'));
        loadData();
     } catch (e: any) {
        showError(getErrorMessage(e));
     } finally {
        setSaving(false);
     }
  };

  const handleDeletePoint = async () => {
      if (!canEdit) {
          showError(t('point_details.edit_forbidden'));
          return;
      }
      if (confirm(t('point_details.confirm_delete'))) {
          try {
            setDeleting(true);
            await api.delete(`/partners/points/${id}`);
            showSuccess(t('point_details.delete_success'));
            navigate('/partner/points'); // Back to list
          } catch (e: any) {
            showError(getErrorMessage(e));
            setDeleting(false);
          }
      }
  };
  
  const handleFireCashier = async (cashierId: string) => {
       if (!canEdit) {
          showError(t('point_details.edit_forbidden'));
          return;
       }
       if (confirm(t('point_details.confirm_delete'))) {
          try {
            await api.delete(`/partners/cashiers/${id}/${cashierId}`);
            showSuccess(t('point_details.fire_success'));
            loadCashiers();
          } catch (e: any) {
            showError(getErrorMessage(e));
          }
      }
  };

  if (loading) return (
      <Container sx={{ mt: 4 }}>
          <Box display="flex" justifyContent="space-between" mb={4}>
              <Box>
                  <Skeleton variant="text" width={300} height={60} />
                  <Skeleton variant="text" width={200} />
              </Box>
              <Skeleton variant="rectangular" width={120} height={80} sx={{ borderRadius: 3 }} />
          </Box>
          <Skeleton variant="rectangular" height={60} sx={{ mb: 4, borderRadius: 3 }} />
          <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 4 }} />
      </Container>
  );
  if (!details) return <Container sx={{ mt: 4 }}><Typography>Not found</Typography></Container>;

  return (
    <>
        <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
       {/* HEADER */}
           <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={4} flexWrap="wrap" gap={2}>
          <Box>
                <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                    {details.point.name}
                </Typography>
                <Box display="flex" alignItems="center" gap={1}>
                     <Typography color="text.secondary" fontWeight="500">
                        {details.point.address || t('point_details.no_address')}
                     </Typography>
                     {overviewStatusColor === 'success' && <Chip label={t('common.status_active')} color="success" size="small" sx={{ height: 20, fontSize: '0.65rem' }} />}
                     {overviewStatusColor === 'warning' && <Chip label={t('point_details.status_paused')} color="warning" size="small" sx={{ height: 20, fontSize: '0.65rem' }} />}
          </Box>
              </Box>
              
              <Paper 
                elevation={0} 
                sx={{ 
                    p: 2, 
                    bgcolor: 'primary.50', 
                    color: 'primary.main', 
                    borderRadius: 3, 
                    border: '1px dashed', 
                    borderColor: 'primary.main',
                    textAlign: 'center',
                    minWidth: 120
                }}
              >
                 <Typography variant="caption" display="block" sx={{ textTransform: 'uppercase', letterSpacing: 1, fontWeight: 600, mb: 0.5 }}>
                    {t('point_details.invite_code')}
                 </Typography>
                 <Typography variant="h5" fontWeight="900" sx={{ letterSpacing: 2 }}>
                    {details.point.inviteCode}
                 </Typography>
             </Paper>
       </Box>

       {!canEdit && (
            <Alert severity="info" sx={{ mb: 3, borderRadius: 2 }}>
            {t('point_details.readonly_hint')}
        </Alert>
       )}

           <Paper elevation={0} sx={{ mb: 4, borderRadius: 3, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
             <Tabs 
                value={tab} 
                onChange={handleTabChange} 
                variant="fullWidth"
                sx={{ 
                    bgcolor: 'background.paper',
                    '& .MuiTab-root': { py: 3, fontWeight: 600 },
                    '& .Mui-selected': { color: 'primary.main' }
                }}
             >
           <Tab label={t('point_details.tab_overview')} />
           <Tab label={t('point_details.tab_settings')} />
           <Tab label={t('point_details.tab_staff')} />
         </Tabs>
       </Paper>

       {/* TAB 0: OVERVIEW */}
       {tab === 0 && (
         <Box>
                <Paper elevation={0} sx={{ p: { xs: 2, md: 4 }, borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="h6" gutterBottom fontWeight="bold">{t('point_details.block_title')}</Typography>
                    
                    <List disablePadding>
                        <ListItem disableGutters sx={{ py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                            <ListItemText primary={t('point_details.overview_type')} secondary={t(`dashboard.types.${details.point.type}`)} />
                        </ListItem>
                        <ListItem disableGutters sx={{ py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                            <ListItemText 
                                primary={t('point_details.overview_status')} 
                                secondary={
                                    <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                                        <Chip label={overviewStatus} color={overviewStatusColor} size="small" />
                                        {details.point.temporarilyPaused && <Chip label={t('point_details.status_paused')} color="warning" size="small" />}
                                    </Box>
                                } 
                            />
                        </ListItem>
                        <ListItem disableGutters sx={{ py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                            <ListItemText primary={t('point_details.overview_program')} secondary={t(`dashboard.strategies.${details.settings.programType}`)} />
                        </ListItem>
                        <ListItem disableGutters sx={{ py: 1.5 }}>
                            <ListItemText primary={t('point_details.overview_currency')} secondary={overviewCurrency} />
                        </ListItem>
                    </List>
            </Paper>
         </Box>
       )}

       {/* TAB 1: SETTINGS */}
       {tab === 1 && (
         <Paper elevation={0} sx={{ p: { xs: 2, md: 4 }, maxWidth: 800, mx: 'auto', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" mb={3} fontWeight="bold">{t('point_details.tab_settings')}</Typography>
            
            <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2}>
            
            {/* Country REMOVED from top as per user feedback */}

            <TextField 
                label={t('dashboard.label_point_name')} 
                fullWidth margin="normal" 
                value={formData.name} 
                onChange={e => setFormData({...formData, name: e.target.value})}
                    variant="outlined"
            />
            <FormControl fullWidth margin="normal">
                <InputLabel>{t('dashboard.label_point_type')}</InputLabel>
                <Select 
                    value={formData.type} 
                    label={t('dashboard.label_point_type')} 
                    onChange={e => setFormData({...formData, type: e.target.value})}
                    disabled={!canEdit}
                >
                    <MenuItem value="COFFEE_SHOP">{t('dashboard.types.COFFEE_SHOP')}</MenuItem>
                    <MenuItem value="RESTAURANT">{t('dashboard.types.RESTAURANT')}</MenuItem>
                    <MenuItem value="RETAIL">{t('dashboard.types.RETAIL')}</MenuItem>
                    <MenuItem value="SERVICE">{t('dashboard.types.SERVICE')}</MenuItem>
                    <MenuItem value="TIRE_SERVICE">{t('dashboard.types.TIRE_SERVICE')}</MenuItem>
                    <MenuItem value="AUTO_SERVICE">{t('dashboard.types.AUTO_SERVICE')}</MenuItem>
                    <MenuItem value="FLOWERS">{t('dashboard.types.FLOWERS')}</MenuItem>
                    <MenuItem value="GIFTS">{t('dashboard.types.GIFTS')}</MenuItem>
                    <MenuItem value="CAKES">{t('dashboard.types.CAKES')}</MenuItem>
                    <MenuItem value="BARBERSHOP">{t('dashboard.types.BARBERSHOP')}</MenuItem>
                    <MenuItem value="CLOTHING">{t('dashboard.types.CLOTHING')}</MenuItem>
                    <MenuItem value="TOYS">{t('dashboard.types.TOYS')}</MenuItem>
                    <MenuItem value="CAR_RENTAL">{t('dashboard.types.CAR_RENTAL')}</MenuItem>
                    <MenuItem value="SCOOTER_RENTAL">{t('dashboard.types.SCOOTER_RENTAL')}</MenuItem>
                    <MenuItem value="AUTO_PARTS">{t('dashboard.types.AUTO_PARTS')}</MenuItem>
                    <MenuItem value="BANK">{t('dashboard.types.BANK')}</MenuItem>
                    <MenuItem value="GROCERY_STORE">{t('dashboard.types.GROCERY_STORE')}</MenuItem>
                    <MenuItem value="BEAUTY_SALON">{t('dashboard.types.BEAUTY_SALON')}</MenuItem>
                    <MenuItem value="OTHER">{t('dashboard.types.OTHER')}</MenuItem>
                </Select>
            </FormControl>
            <TextField 
                label={t('point_details.address_label')}
                fullWidth margin="normal" 
                value={formData.address} 
                onChange={e => setFormData({...formData, address: e.target.value})}
            />
                <Box mt={2}>
                    <PhoneInput
                label={t('point_details.contact_phone_label')}
                value={formData.contactPhone ?? ''}
                        onChange={(val) => setFormData({ ...formData, contactPhone: val })}
                disabled={!canEdit}
                        fullWidth
            />
                </Box>
            <TextField
                label={t('point_details.contact_link_label')}
                fullWidth
                margin="normal"
                value={formData.contactLink ?? ''}
                onChange={(e) => setFormData({ ...formData, contactLink: e.target.value })}
                disabled={!canEdit}
                helperText={t('point_details.contact_link_hint')}
                inputProps={{ maxLength: 80 }}
            />
            
            <TextField
                label={t('point_details.additional_info_label')}
                fullWidth
                margin="normal"
                multiline
                rows={2}
                value={formData.additionalInfo ?? ''}
                onChange={(e) => setFormData({ ...formData, additionalInfo: e.target.value })}
                disabled={!canEdit}
                helperText={`${t('point_details.additional_info_hint')} (${additionalInfoLength}/${additionalInfoLimit})`}
                inputProps={{ maxLength: additionalInfoLimit }}
                sx={{ gridColumn: '1 / -1' }}
            />

            {/* Timezone & Currency Moved below contact info */}
            <Box mt={2}>
                <TimezoneSelect 
                    value={scheduleTimezone} 
                    onChange={(newTz) => {
                        setScheduleTimezone(newTz);
                        if (newTz.includes('Bishkek')) setCurrency('KGS');
                        else if (newTz.includes('Moscow')) setCurrency('RUB');
                        else if (newTz.includes('Almaty')) setCurrency('KZT');
                        else if (newTz.includes('Tashkent')) setCurrency('UZS');
                        else if (newTz.includes('Minsk')) setCurrency('BYN');
                    }} 
                    label={t('dashboard.timezone_label', 'Timezone')} 
                    disabled={!canEdit}
                    fullWidth
                />
            </Box>
            <FormControl fullWidth margin="normal" disabled={!canEdit}>
                <InputLabel>{t('dashboard.label_currency', 'Currency')}</InputLabel>
                <Select
                    value={currency}
                    label={t('dashboard.label_currency', 'Currency')}
                    onChange={(e) => setCurrency(e.target.value)}
                >
                    <MenuItem value="RUB">RUB (Рубль)</MenuItem>
                    <MenuItem value="KGS">KGS (Сом)</MenuItem>
                    <MenuItem value="KZT">KZT (Тенге)</MenuItem>
                    <MenuItem value="UZS">UZS (Сум)</MenuItem>
                    <MenuItem value="BYN">BYN (Бел. рубль)</MenuItem>
                    <MenuItem value="USD">USD (Доллар)</MenuItem>
                </Select>
            </FormControl>

            </Box>

            <Box mt={4}>
                <Typography variant="subtitle2" gutterBottom fontWeight="600">{t('point_details.map_location')}</Typography>
                <Paper variant="outlined" sx={{ overflow: 'hidden', borderRadius: 2 }}>
            <LocationPicker 
                initialLat={formData.latitude}
                initialLng={formData.longitude}
                onLocationChange={handleLocationChange}
                markerLabel={formData.name || t('point_details.map_point_label', 'Trading point')}
                        height={300}
            />
                </Paper>
            </Box>

            <Divider sx={{ my: 4 }} />

            <Box display="flex" flexDirection={{ xs: 'column', sm: 'row' }} gap={4} mb={4}>
                <Box flex={1}>
                    <Typography variant="h6" gutterBottom fontWeight="bold">{t('point_details.schedule_title')}</Typography>
                    <Typography variant="body2" color="text.secondary" mb={2}>
                        {t('point_details.schedule_hint')}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" display="block" mb={2} sx={{ bgcolor: 'grey.100', py: 0.5, px: 1, borderRadius: 1, display: 'inline-block' }}>
                        {t('point_details.schedule_timezone', { tz: scheduleTimezone })}
                    </Typography>
                    <Button variant="outlined" onClick={() => setScheduleDialogOpen(true)} sx={{ borderRadius: 2 }}>
                        {hasPublishedSchedule
                            ? t('point_details.schedule_edit_button_existing')
                            : t('point_details.schedule_edit_button')}
                    </Button>
                </Box>
                
                <Box flex={1} p={2} bgcolor="warning.50" borderRadius={3} border="1px dashed" borderColor="warning.main">
                <FormControlLabel
                        sx={{ alignItems: 'flex-start', ml: 0 }}
                    control={
                        <Switch
                            checked={temporarilyPaused}
                            onChange={(e) => setTemporarilyPaused(e.target.checked)}
                            color="warning"
                            disabled={!canEdit}
                                sx={{ mt: 0.5, mr: 1 }}
                        />
                    }
                    label={
                        <Box>
                                <Typography fontWeight={700} color="warning.dark">{t('point_details.pause_label')}</Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2, display: 'block', mt: 0.5 }}>
                                {t('point_details.pause_helper')}
                            </Typography>
                        </Box>
                    }
                />
                </Box>
            </Box>

            {temporarilyPaused && (
                <Alert severity="warning" sx={{ mb: 3, borderRadius: 2 }}>
                    {t('point_details.pause_warning')}
                </Alert>
            )}

            <Paper variant="outlined" sx={{ p: 3, mb: 4, borderRadius: 3, bgcolor: 'grey.50' }}>
                <Typography variant="subtitle2" gutterBottom>{t('point_details.schedule_preview_title')}</Typography>
                <List dense disablePadding sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1 }}>
                    {WEEK_DAYS.map(({ id, shortKey }) => {
                        const entry = scheduleState[id];
                        return (
                            <ListItem key={id} disableGutters>
                                <ListItemText
                                    primary={t(shortKey)}
                                    primaryTypographyProps={{ color: 'text.secondary', width: 40, display: 'inline-block' }}
                                    secondary={
                                        <Typography component="span" fontWeight={entry.enabled ? 600 : 400} color="text.primary">
                                    {formatScheduleDisplay(entry, t)}
                                </Typography>
                                    }
                                />
                            </ListItem>
                        );
                    })}
                </List>
            </Paper>

            <Box mt={4} mb={4}>
                <Typography variant="subtitle2" gutterBottom fontWeight="600">{t('point_details.map_preview_title')}</Typography>
                <Typography variant="body2" color="text.secondary" mb={2}>
                    {t('point_details.map_preview_hint')}
                </Typography>
                <Button variant="outlined" onClick={() => setMapPreviewOpen(true)} sx={{ borderRadius: 2 }}>
                    {t('point_details.map_preview_cta', 'Открыть карту клиентов')}
                </Button>
            </Box>
            
            <Divider sx={{ my: 4 }} />
            
            <Typography variant="h6" gutterBottom fontWeight="bold">{t('dashboard.label_strategy')}</Typography>
            
            {(programType === 'VISIT_COUNTER' || programType === 'HYBRID') && (
                <TextField 
                    label={t('dashboard.label_target')} 
                    type="number"
                    fullWidth margin="normal"
                    value={formData.visitsTarget}
                    InputProps={{ readOnly: true }}
                    disabled
                    helperText={t('point_details.visits_target_locked_hint')}
                />
            )}

            {(programType === 'TIERED_LTV' || programType === 'HYBRID') && (
                <Box mt={2}>
                    <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
                    <TextField
                        label={t('point_details.max_burn_label')}
                        type="number"
                        fullWidth
                        margin="normal"
                        value={maxBurnPercentage}
                        onChange={(e) => {
                            let val = parseInt(e.target.value);
                            if (val < 0) val = 0;
                            if (val > 100) val = 100;
                            setMaxBurnPercentage(val);
                        }}
                        helperText={t('point_details.max_burn_hint')}
                    />
                        <Box display="flex" alignItems="center">
                    <FormControlLabel
                        control={
                            <Switch
                                color="primary"
                                checked={awardOnMixedPayment}
                                onChange={(e) => setAwardOnMixedPayment(e.target.checked)}
                            />
                        }
                        label={
                            <Box>
                                        <Typography variant="body2" fontWeight="500">{t('point_details.award_mixed_label', 'Award cashback on mixed payment')}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                    {t('point_details.award_mixed_hint', 'Accrue bonuses on the money part even if the client spends points.')}
                                </Typography>
                            </Box>
                        }
                            />
                        </Box>
                    </Box>

                    <Typography variant="subtitle1" gutterBottom sx={{ mt: 3, fontWeight: 600 }}>{t('point_details.levels_config')}</Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                        {t('point_details.levels_locked_hint', 'Уровни и пороги задаются в настройках бизнеса (личный кабинет). На уровне торговой точки они доступны только для просмотра.')}
                    </Alert>
                    <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
                    <Table size="small">
                            <TableHead sx={{ bgcolor: 'grey.100' }}>
                            <TableRow>
                                    <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_name')}</TableCell>
                                    <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_threshold')}</TableCell>
                                    <TableCell sx={{ fontWeight: 600 }}>{t('point_details.lvl_percent')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {tiers.map((tier, idx) => (
                                <TableRow key={idx}>
                                    <TableCell>{renderTierLabel(tier)}</TableCell>
                                    <TableCell>
                                        <Typography variant="body2">
                                            {tier.threshold} {partnerBaseCurrency || ''}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Typography variant="body2">
                                            {tier.cashbackPercent}%
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                    </Paper>
                </Box>
            )}
            
            <Box mt={6} display="flex" justifyContent="space-between" alignItems="center" pt={4} borderTop="1px solid" borderColor="divider">
                <Button color="error" onClick={handleDeletePoint} startIcon={deleting ? <CircularProgress size={20} color="inherit" /> : <DeleteIcon />} disabled={deleting || saving}>
                    {t('point_details.delete_point')}
                </Button>
                <Button variant="contained" onClick={handleSaveSettings} startIcon={saving ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />} disabled={!canEdit || saving || deleting} size="large" sx={{ px: 4, borderRadius: 2 }}>
                    {t('common.save')}
                </Button>
            </Box>
         </Paper>
       )}

       {/* TAB 2: STAFF */}
       {tab === 2 && (
         <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
             <Box sx={{ overflowX: 'auto' }}>
                 <Table sx={{ minWidth: 600 }}>
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                    <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>{t('staff.staff_name')}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{t('staff.staff_phone')}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>{t('staff.staff_actions')}</TableCell>
                    </TableRow>
                </TableHead>
                 <TableBody>
                     {cashiers.length === 0 ? (
                         <TableRow><TableCell colSpan={3} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('point_details.staff_empty')}</TableCell></TableRow>
                     ) : (
                         cashiers.map(c => (
                             <TableRow key={c.id} hover>
                                 <TableCell>{c.name}</TableCell>
                                 <TableCell>{c.phone}</TableCell>
                                 <TableCell align="right">
                                     <Button color="error" size="small" variant="outlined" onClick={() => handleFireCashier(c.userId)}>
                                        {t('point_details.fire_cashier')}
                                     </Button>
                                 </TableCell>
                             </TableRow>
                         ))
                     )}
                 </TableBody>
             </Table>
             </Box>
         </Paper>
       )}
    </Container>
    <ScheduleEditorDialog
        open={scheduleDialogOpen}
        onClose={() => setScheduleDialogOpen(false)}
        canEdit={canEdit}
        initialState={scheduleState}
        timezone={scheduleTimezone}
        onSave={(nextState, nextTz) => {
            setScheduleState(nextState);
            setScheduleTimezone(nextTz);
            setScheduleDialogOpen(false);
        }}
    />
    <PublicPointsPreviewDialog
        open={mapPreviewOpen}
        onClose={() => setMapPreviewOpen(false)}
        initialCenter={
            formData.latitude && formData.longitude ? [formData.latitude, formData.longitude] : undefined
        }
        initialPoint={details?.point}
    />
    </>
  );
};

interface ScheduleEditorDialogProps {
    open: boolean;
    initialState: ScheduleFormState;
    timezone: string;
    canEdit: boolean;
    onClose: () => void;
    onSave: (state: ScheduleFormState, timezone: string) => void;
}

const ScheduleEditorDialog: React.FC<ScheduleEditorDialogProps> = ({
    open,
    initialState,
    timezone,
    canEdit,
    onClose,
    onSave,
}) => {
    const { t } = useTranslation();
    const [draftState, setDraftState] = useState<ScheduleFormState>(initialState);
    const [draftTimezone, setDraftTimezone] = useState(timezone);
    const [pendingDisableDay, setPendingDisableDay] = useState<WeekDayId | null>(null);
    const [validationError, setValidationError] = useState<string | null>(null);

    // eslint-disable-next-line react-hooks/set-state-in-effect
    useEffect(() => {
        if (open) {
            setDraftState(initialState);
            setDraftTimezone(timezone);
            setPendingDisableDay(null);
            setValidationError(null);
        }
    }, [open, initialState, timezone]);

    const handleDayToggle = (dayId: WeekDayId, enabled: boolean) => {
        if (!canEdit) return;
        if (!enabled) {
            setPendingDisableDay(dayId);
            return;
        }
        setDraftState((prev) => ({
            ...prev,
            [dayId]: {
                ...prev[dayId],
                enabled: true,
                workStart: prev[dayId].workStart || '09:00',
                workEnd: prev[dayId].workEnd || '18:00',
            },
        }));
    };

    const handleFieldChange = (dayId: WeekDayId, field: keyof DayScheduleForm, value: string) => {
        if (!canEdit) return;
        setDraftState((prev) => ({
            ...prev,
            [dayId]: {
                ...prev[dayId],
                [field]: value,
            },
        }));
    };

    const handleDisableConfirm = () => {
        if (!pendingDisableDay) return;
        setDraftState((prev) => ({
            ...prev,
            [pendingDisableDay]: { ...createDayState(), enabled: false },
        }));
        setPendingDisableDay(null);
    };

    const handleSave = () => {
        const error = getScheduleValidationError(draftState, t);
        if (error) {
            setValidationError(error);
            return;
        }
        onSave(draftState, draftTimezone);
    };

    return (
        <>
            <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
                <DialogTitle>{t('point_details.schedule_dialog_title')}</DialogTitle>
                <DialogContent dividers>
                    <Typography variant="body2" color="text.secondary" mb={1}>
                        {t('point_details.schedule_hint')}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        {t('point_details.schedule_timezone', { tz: draftTimezone })}
                    </Typography>
                    {validationError && (
                        <Alert severity="error" sx={{ mt: 2 }}>
                            {validationError}
                        </Alert>
                    )}
                    <Box
                        mt={2}
                        display="grid"
                        gridTemplateColumns={{ xs: '1fr', md: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' }}
                        gap={2}
                    >
                        {WEEK_DAYS.map(({ id, titleKey }) => {
                            const dayForm = draftState[id];
                            return (
                                <Paper key={id} variant="outlined" sx={{ p: 2 }}>
                                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                                        <Typography fontWeight={600}>{t(titleKey)}</Typography>
                                        <Switch
                                            size="small"
                                            checked={dayForm.enabled}
                                            onChange={(e) => handleDayToggle(id, e.target.checked)}
                                            disabled={!canEdit}
                                        />
                                    </Box>
                                    <TextField
                                        label={t('point_details.schedule_work_from')}
                                        type="time"
                                        fullWidth
                                        margin="dense"
                                        InputLabelProps={{ shrink: true }}
                                        value={dayForm.workStart}
                                        onChange={(e) => handleFieldChange(id, 'workStart', e.target.value)}
                                        disabled={!dayForm.enabled || !canEdit}
                                    />
                                    <TextField
                                        label={t('point_details.schedule_work_to')}
                                        type="time"
                                        fullWidth
                                        margin="dense"
                                        InputLabelProps={{ shrink: true }}
                                        value={dayForm.workEnd}
                                        onChange={(e) => handleFieldChange(id, 'workEnd', e.target.value)}
                                        disabled={!dayForm.enabled || !canEdit}
                                    />
                                    <Box mt={1}>
                                        <Typography variant="caption" color="text.secondary">
                                            {t('point_details.schedule_break_hint')}
                                        </Typography>
                                        <Box display="flex" gap={1} mt={0.5}>
                                            <TextField
                                                label={t('point_details.schedule_break_from')}
                                                type="time"
                                                fullWidth
                                                InputLabelProps={{ shrink: true }}
                                                value={dayForm.lunchStart}
                                                onChange={(e) => handleFieldChange(id, 'lunchStart', e.target.value)}
                                                disabled={!dayForm.enabled || !canEdit}
                                            />
                                            <TextField
                                                label={t('point_details.schedule_break_to')}
                                                type="time"
                                                fullWidth
                                                InputLabelProps={{ shrink: true }}
                                                value={dayForm.lunchEnd}
                                                onChange={(e) => handleFieldChange(id, 'lunchEnd', e.target.value)}
                                                disabled={!dayForm.enabled || !canEdit}
                                            />
                                        </Box>
                                    </Box>
                                </Paper>
                            );
                        })}
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose}>{t('common.cancel')}</Button>
                    <Button variant="contained" onClick={handleSave} disabled={!canEdit}>
                        {t('common.save')}
                    </Button>
                </DialogActions>
            </Dialog>
            <Dialog open={Boolean(pendingDisableDay)} onClose={() => setPendingDisableDay(null)}>
                <DialogTitle>{t('point_details.schedule_disable_title')}</DialogTitle>
                <DialogContent>
                    <Typography>
                        {t('point_details.schedule_disable_desc', {
                            day: pendingDisableDay ? getDayLabel(pendingDisableDay, t) : '',
                        })}
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setPendingDisableDay(null)}>{t('common.cancel')}</Button>
                    <Button color="error" onClick={handleDisableConfirm}>
                        {t('common.confirm')}
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
