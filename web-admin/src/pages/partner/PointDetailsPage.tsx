import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Container, Typography, Paper, Box, Tabs, Tab, TextField, Button, 
  Grid, Table, TableHead, TableRow, TableCell, TableBody, Chip,
  Select, MenuItem, FormControl, InputLabel, Switch, FormControlLabel,
  Alert
} from '@mui/material';
import { Delete as DeleteIcon, Save as SaveIcon } from '@mui/icons-material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { LocationPicker } from '../../components/LocationPicker';
import { useUser } from '../../context/UserContext';

export const PointDetailsPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { showSuccess, showError } = useNotification();
  const { currentWorkspace } = useUser();
  const canEdit = currentWorkspace?.role === 'PARTNER_ADMIN';

  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [details, setDetails] = useState<any>(null);
  const [cashiers, setCashiers] = useState<any[]>([]);
  
  // Form State
  const [formData, setFormData] = useState<any>({
    name: '',
    address: '',
    type: 'COFFEE_SHOP',
    visitsTarget: 6,
    latitude: null,
    longitude: null
  });
  
  const [programType, setProgramType] = useState('TIERED_LTV');
  const [tiers, setTiers] = useState<any[]>([]);
  const [maxBurnPercentage, setMaxBurnPercentage] = useState(100);
  const countryOptions = React.useMemo(() => ([
    { code: 'KG', currency: 'KGS', label: t('countries.KG') },
    { code: 'KZ', currency: 'KZT', label: t('countries.KZ') },
    { code: 'UZ', currency: 'UZS', label: t('countries.UZ') },
    { code: 'BY', currency: 'BYN', label: t('countries.BY') },
  ]), [t]);
  const [country, setCountry] = useState('KG');
  const [currency, setCurrency] = useState('KGS');
  const [awardOnMixedPayment, setAwardOnMixedPayment] = useState(false);

  const normalizePercentForDisplay = (value: any) => {
    if (value === null || value === undefined || value === '') return '';
    const numeric = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(numeric)) return '';
    if (numeric > 0 && numeric < 1) {
      return (numeric * 100).toString();
    }
    return numeric.toString();
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
        longitude: data.point.longitude
      });
      
      setProgramType(data.settings.programType);
      setMaxBurnPercentage(data.settings.maxBurnPercentage || 100);
      setCurrency(data.point.currency || 'KGS');
      const matchedCountry = countryOptions.find(opt => opt.currency === (data.point.currency || 'KGS'));
      setCountry(matchedCountry?.code || 'KG');
      setAwardOnMixedPayment(Boolean(data.settings.awardOnMixedPayment));

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

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTab(newValue);
    if (newValue === 2) loadCashiers();
  };

  const handleLocationChange = async (lat: number, lng: number) => {
      if (!canEdit) {
          return;
      }
      setFormData((prev: any) => ({...prev, latitude: lat, longitude: lng}));
      
      try {
          const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
          // Nominatim requires User-Agent
          const response = await fetch(url, { headers: { 'User-Agent': 'LoyaltyLoop-Admin/1.0' } });
          if (response.ok) {
              const data = await response.json();
              if (data && data.display_name) {
                  setFormData((prev: any) => ({...prev, address: data.display_name, latitude: lat, longitude: lng}));
              }
          }
      } catch (error) {
          console.error("Geocoding error", error);
      }
  };

  const handleSaveSettings = async () => {
     if (!canEdit) {
        showError(t('point_details.edit_forbidden'));
        return;
     }
     try {
        const normalizedTiers = tiers.map((tier: any) => ({
            ...tier,
            threshold: sanitizeNumber(tier.threshold),
            cashbackPercent: sanitizeNumber(tier.cashbackPercent)
        }));

        const payload = {
            name: formData.name,
            type: formData.type,
            address: formData.address || '',
            latitude: formData.latitude ?? 0,
            longitude: formData.longitude ?? 0,
            currency: currency,
            settings: {
                programType: programType, 
                tiers: normalizedTiers, 
                visitsTarget: formData.visitsTarget,
                maxBurnPercentage: maxBurnPercentage,
                awardOnMixedPayment
            }
        };

        await api.put(`/partners/points/${id}`, payload);
        showSuccess(t('settings.save_success'));
        loadData();
     } catch (e: any) {
        showError(getErrorMessage(e));
     }
  };

  const handleDeletePoint = async () => {
      if (!canEdit) {
          showError(t('point_details.edit_forbidden'));
          return;
      }
      if (confirm(t('point_details.confirm_delete'))) {
          try {
            await api.delete(`/partners/points/${id}`);
            showSuccess("Deleted");
            navigate('/partner/points'); // Back to list
          } catch (e: any) {
            showError(getErrorMessage(e));
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
            await api.delete(`/partners/cashiers/${cashierId}`);
            showSuccess("Fired");
            loadCashiers();
          } catch (e: any) {
            showError(getErrorMessage(e));
          }
      }
  };

  if (loading) return <Container sx={{ mt: 4 }}><Typography>{t('common.loading')}</Typography></Container>;
  if (!details) return <Container sx={{ mt: 4 }}><Typography>Not found</Typography></Container>;

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
       {/* HEADER */}
       <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Box>
            <Typography variant="h4">{details.point.name}</Typography>
            <Typography color="textSecondary">{details.point.address || "No address"}</Typography>
          </Box>
          <Box textAlign="right">
             <Paper elevation={3} sx={{ p: 1, px: 2, bgcolor: 'primary.light', color: 'white' }}>
                <Typography variant="h6" fontWeight="bold">{details.point.inviteCode}</Typography>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>{t('point_details.invite_code')}</Typography>
             </Paper>
          </Box>
       </Box>

       {!canEdit && (
        <Alert severity="info" sx={{ mb: 3 }}>
            {t('point_details.readonly_hint')}
        </Alert>
       )}

       <Paper sx={{ mb: 3 }}>
         <Tabs value={tab} onChange={handleTabChange} centered>
           <Tab label={t('point_details.tab_overview')} />
           <Tab label={t('point_details.tab_settings')} />
           <Tab label={t('point_details.tab_staff')} />
         </Tabs>
       </Paper>

       {/* TAB 0: OVERVIEW */}
       {tab === 0 && (
         <Box>
            <Paper sx={{ p: 3 }}>
                <Typography variant="h6" gutterBottom>Details</Typography>
                <Typography component="div"><strong>Type:</strong> {t(`dashboard.types.${details.point.type}`)}</Typography>
                <Typography component="div">
                    <strong>Status:</strong>{" "}
                    <Chip
                        label={details.point.active ? t('common.active') : t('common.blocked')}
                        color={details.point.active ? "success" : "default"}
                        size="small"
                        sx={{ ml: 1 }}
                    />
                </Typography>
                <Typography component="div"><strong>Program:</strong> {t(`dashboard.strategies.${details.settings.programType}`)}</Typography>
                <Typography component="div"><strong>Currency:</strong> {details.point.currency}</Typography>
            </Paper>
         </Box>
       )}

       {/* TAB 1: SETTINGS */}
       {tab === 1 && (
         <Paper sx={{ p: 3, maxWidth: 600, mx: 'auto' }}>
            <Typography variant="h6" mb={2}>{t('point_details.tab_settings')}</Typography>
            
            <TextField 
                label={t('dashboard.label_point_name')} 
                fullWidth margin="normal" 
                value={formData.name} 
                onChange={e => setFormData({...formData, name: e.target.value})}
            />
             <TextField 
                label={t('point_details.address_label')}
                fullWidth margin="normal" 
                value={formData.address} 
                onChange={e => setFormData({...formData, address: e.target.value})}
            />

            <FormControl fullWidth margin="normal" disabled={!canEdit}>
                <InputLabel>{t('point_details.country_label', 'Country')}</InputLabel>
                <Select
                    value={country}
                    label={t('point_details.country_label', 'Country')}
                    onChange={(e) => {
                        const code = e.target.value as string;
                        setCountry(code);
                        const option = countryOptions.find(o => o.code === code);
                        if (option) {
                            setCurrency(option.currency);
                        }
                    }}
                >
                     {countryOptions.map(option => (
                        <MenuItem key={option.code} value={option.code}>
                            {option.label} ({option.currency})
                        </MenuItem>
                     ))}
                </Select>
            </FormControl>

            <Typography variant="subtitle2" mt={1} mb={0.5}>{t('point_details.map_location')}</Typography>
            <LocationPicker 
                initialLat={formData.latitude}
                initialLng={formData.longitude}
                onLocationChange={handleLocationChange}
            />
            
            {(programType === 'VISIT_COUNTER' || programType === 'HYBRID') && (
                <TextField 
                    label={t('dashboard.label_target')} 
                    type="number"
                    fullWidth margin="normal"
                    value={formData.visitsTarget}
                    onChange={e => setFormData({...formData, visitsTarget: parseInt(e.target.value)})}
                />
            )}

            {(programType === 'TIERED_LTV' || programType === 'HYBRID') && (
                <Box mt={3}>
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
                                <Typography variant="body2">{t('point_details.award_mixed_label', 'Award cashback on mixed payment')}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                    {t('point_details.award_mixed_hint', 'Accrue bonuses on the money part even if the client spends points.')}
                                </Typography>
                            </Box>
                        }
                        sx={{ mt: 1 }}
                    />

                    <Typography variant="subtitle1" gutterBottom sx={{ mt: 2 }}>{t('point_details.levels_config')}</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        {t('point_details.cashback_rule_hint')}
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('point_details.lvl_name')}</TableCell>
                                <TableCell>{t('point_details.lvl_threshold')}</TableCell>
                                <TableCell>{t('point_details.lvl_percent')}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {tiers.map((tier, idx) => (
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
                                            InputProps={{ endAdornment: <span style={{marginLeft: 4}}>%</span> }}
                                        />
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Box>
            )}
            
            <Box mt={4} display="flex" justifyContent="space-between">
                <Button color="error" onClick={handleDeletePoint} startIcon={<DeleteIcon />}>
                    {t('point_details.delete_point')}
                </Button>
                <Button variant="contained" onClick={handleSaveSettings} startIcon={<SaveIcon />} disabled={!canEdit}>
                    {t('common.save')}
                </Button>
            </Box>
         </Paper>
       )}

       {/* TAB 2: STAFF */}
       {tab === 2 && (
         <Paper>
             <Table>
                 <TableHead>
                     <TableRow>
                         <TableCell>Name</TableCell>
                         <TableCell>Phone</TableCell>
                         <TableCell align="right">Actions</TableCell>
                     </TableRow>
                 </TableHead>
                 <TableBody>
                     {cashiers.length === 0 ? (
                         <TableRow><TableCell colSpan={3} align="center">{t('point_details.staff_empty')}</TableCell></TableRow>
                     ) : (
                         cashiers.map(c => (
                             <TableRow key={c.id}>
                                 <TableCell>{c.name}</TableCell>
                                 <TableCell>{c.phone}</TableCell>
                                 <TableCell align="right">
                                     <Button color="error" size="small" onClick={() => handleFireCashier(c.id)}>
                                        {t('point_details.fire_cashier')}
                                     </Button>
                                 </TableCell>
                             </TableRow>
                         ))
                     )}
                 </TableBody>
             </Table>
         </Paper>
       )}
    </Container>
  );
};
