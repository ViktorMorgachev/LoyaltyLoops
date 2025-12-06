import React, { useState, useEffect } from 'react';
import {
  Container, Button, Box, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem,
  Alert, Switch, FormControlLabel
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useNavigate } from 'react-router-dom';
import { LocationPicker } from '../../components/LocationPicker';
import SearchIcon from '@mui/icons-material/Search';
import { useUser } from '../../context/UserContext';
import { geocodeAddress, reverseGeocode as reverseGeocodeYandex } from '../../utils/yandexGeocode';
import { PhoneInput } from '../../components/inputs/PhoneInput';

export const PointsPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { currentWorkspace } = useUser();
  const canManage = currentWorkspace?.role === 'PARTNER_ADMIN';
  const mapApiKey = (import.meta.env.VITE_YMAPS_API_KEY as string | undefined);

  const [points, setPoints] = useState<any[]>([]);
  const [open, setOpen] = useState(false);

  const [name, setName] = useState('');
  const [type, setPointType] = useState('COFFEE_SHOP');
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState<string>('');
  const [longitude, setLongitude] = useState<string>('');
  const [contactPhone, setContactPhone] = useState('');
  const [contactLink, setContactLink] = useState('');
  const [additionalInfo, setAdditionalInfo] = useState('');
  const countryOptions = React.useMemo(() => ([
    { code: 'KG', currency: 'KGS', label: t('countries.KG') },
    { code: 'KZ', currency: 'KZT', label: t('countries.KZ') },
    { code: 'UZ', currency: 'UZS', label: t('countries.UZ') },
    { code: 'BY', currency: 'BYN', label: t('countries.BY') },
  ]), [t]);
  const [country, setCountry] = useState('KG');
  const [currency, setCurrency] = useState('KGS');

  const [strategy, setStrategy] = useState('TIERED_LTV');
  const [defaultVisitsTarget, setDefaultVisitsTarget] = useState('10');
  const [cashback, setCashback] = useState('5');
  const [locationDialogOpen, setLocationDialogOpen] = useState(false);
  const [mapSearch, setMapSearch] = useState('');
  const [tempCoords, setTempCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [tempAddress, setTempAddress] = useState('');
  const [awardOnMixedPayment, setAwardOnMixedPayment] = useState(false);

  useEffect(() => {
    loadPoints();
    loadPartnerDefaults();
  }, []);

  const loadPoints = async () => {
    try {
      const res = await api.get('/partners/points');
      setPoints(res.data);
    } catch (e: any) {
      if (e.response && e.response.status !== 404) {
          showError(getErrorMessage(e));
      }
    }
  };

  const loadPartnerDefaults = async () => {
    try {
      const res = await api.get('/partners/me');
      const target = res.data?.defaultVisitsTarget;
      if (target !== undefined && target !== null) {
        setDefaultVisitsTarget(String(target));
      }
    } catch (_ignored) {
      // Managers may not have access; ignore errors here
    }
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
      console.warn('Geocoding error', error);
    }
    return null;
  };

  const geocodeAddressFallback = async (query: string): Promise<{ lat: number; lng: number; address: string } | null> => {
    try {
      const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`;
      const response = await fetch(url, { headers: { 'User-Agent': 'LoyaltyLoop-Admin/1.0' } });
      if (response.ok) {
        const data = await response.json();
        if (data && data.length > 0) {
          const lat = parseFloat(data[0].lat);
          const lng = parseFloat(data[0].lon);
          if (!isNaN(lat) && !isNaN(lng)) {
            return {
              lat,
              lng,
              address: data[0].display_name || query,
            };
          }
        }
      }
    } catch (error) {
      console.warn('Geocoding error', error);
    }
    return null;
  };

  const resolveAddress = React.useCallback(
    async (lat: number, lng: number): Promise<string | null> => {
      if (mapApiKey) {
        const address = await reverseGeocodeYandex(mapApiKey, lat, lng);
        if (address) return address;
      }
      return reverseGeocodeFallback(lat, lng);
    },
    [mapApiKey]
  );

  const openLocationDialog = () => {
    setLocationDialogOpen(true);
    if (latitude && longitude) {
      const lat = parseFloat(latitude);
      const lng = parseFloat(longitude);
      if (!isNaN(lat) && !isNaN(lng)) {
        setTempCoords({ lat, lng });
      }
    } else {
      setTempCoords(null);
    }
    setTempAddress(address);
    setMapSearch(address);
  };

  const closeLocationDialog = () => {
    setLocationDialogOpen(false);
  };

  const handleMapLocationChange = React.useCallback(
    async (lat: number, lng: number) => {
      setTempCoords({ lat, lng });
      const addr = await resolveAddress(lat, lng);
      if (addr) setTempAddress(addr);
    },
    [resolveAddress]
  );

  const handleAddressSearch = async () => {
    if (!mapSearch.trim()) return;
    try {
      let result: { lat: number; lng: number; address?: string } | null = null;
      if (mapApiKey) {
        result = await geocodeAddress(mapApiKey, mapSearch.trim());
      }
      if (!result) {
        result = await geocodeAddressFallback(mapSearch.trim());
      }

      if (result) {
        setTempCoords({ lat: result.lat, lng: result.lng });
        setTempAddress(result.address || mapSearch.trim());
      } else {
        showError(t('point_details.search_not_found', 'Nothing found for this query'));
      }
    } catch (error) {
      showError(t('point_details.search_error', 'Failed to search address'));
    }
  };

  const applySelectedLocation = () => {
    if (!tempCoords) {
      showError(t('point_details.map_required', 'Please select a location on the map'));
      return;
    }
    setLatitude(tempCoords.lat.toString());
    setLongitude(tempCoords.lng.toString());
    if (tempAddress) {
      setAddress(tempAddress);
    }
    setLocationDialogOpen(false);
  };

  const handleCreatePoint = async () => {
    if (!canManage) {
      showError(t('errors.FORBIDDEN'));
      return;
    }
    try {
      if (!name.trim()) {
        showError(`${t('dashboard.label_point_name')}: ${t('common.required', 'Required')}`);
        return;
      }
      if (!address.trim()) {
        showError(`${t('point_details.address_label')}: ${t('common.required', 'Required')}`);
        return;
      }
      const lat = parseFloat(latitude);
      const lng = parseFloat(longitude);
      if (isNaN(lat) || isNaN(lng)) {
        showError(t('point_details.map_required', 'Please select a location on the map'));
        return;
      }

      const isTiered = strategy === 'TIERED_LTV' || strategy === 'HYBRID';
      const baseCashbackValue = parseFloat(cashback || '0') || 0;

      const visitsGoal = Math.max(1, parseInt(defaultVisitsTarget || '10', 10) || 10);
      const visitTargetForPayload = (strategy === 'VISIT_COUNTER' || strategy === 'HYBRID') ? visitsGoal : undefined;
      const trimmedPhone = contactPhone.trim();
      const trimmedLink = contactLink.trim();
      const trimmedInfo = additionalInfo.trim().slice(0, 20);

      const payload = {
        name,
        type,
        address,
        latitude: lat,
        longitude: lng,
        currency,
        programType: strategy,
        baseCashback: isTiered ? baseCashbackValue : 0,
        awardOnMixedPayment,
        contactPhone: trimmedPhone || undefined,
        contactLink: trimmedLink || undefined,
        additionalInfo: trimmedInfo ? trimmedInfo : undefined,
        ...(visitTargetForPayload !== undefined ? { visitsTarget: visitTargetForPayload } : {})
      };

      await api.post('/partners/points', payload);

      setOpen(false);
      setName('');
      setAddress('');
      setLatitude('');
      setLongitude('');
      setAwardOnMixedPayment(false);
      setContactPhone('');
      setContactLink('');
      setAdditionalInfo('');
      showSuccess(t('common.create') + " OK");
      loadPoints();
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  const copyInvite = (e: React.MouseEvent, code: string) => {
    e.stopPropagation(); // Prevent row click
    navigator.clipboard.writeText(code);
    showSuccess(`${t('common.copied')}: ${code}`);
  };

  const getLocalizedType = (typeEnum: string) => {
      return t(`dashboard.types.${typeEnum}`, typeEnum);
  };

  const base = parseFloat(cashback || '0') || 0;
  const mid = base + 2;
  const max = base + 5;

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">{t('dashboard.title')}</Typography>
        {canManage && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            {t('dashboard.add_point')}
          </Button>
        )}
      </Box>

      <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
        <Box sx={{ overflowX: 'auto' }}>
            <Table sx={{ minWidth: 650 }}>
              <TableHead sx={{ bgcolor: 'action.hover' }}>
            <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_name')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_type')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_invite')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('common.status')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {points.length === 0 && (
              <TableRow>
                    <TableCell colSpan={4} align="center" sx={{ py: 8, color: 'text.secondary' }}>
                        {t('dashboard.empty')}
                    </TableCell>
              </TableRow>
            )}
            {points.map((p) => (
              <TableRow 
                key={p.id} 
                hover 
                onClick={() => navigate(`/partner/points/${p.id}`)} 
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>{p.name}</TableCell>
                <TableCell>{getLocalizedType(p.type)}</TableCell>
                <TableCell>
                  {p.inviteCode ? (
                    <Chip
                      label={p.inviteCode}
                      onClick={(e) => copyInvite(e, p.inviteCode)}
                      icon={<ContentCopyIcon />}
                      color="primary"
                      clickable
                      variant="outlined"
                          size="small"
                    />
                  ) : "—"}
                </TableCell>
                <TableCell>
                  <Chip
                    label={p.active ? t('common.status_active') : t('common.status_not_paid')}
                    color={p.active ? "success" : "error"}
                    size="small"
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        </Box>
      </Paper>

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ px: 3, pt: 3 }}>{t('dashboard.create_title')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2, px: 3, maxHeight: '70vh', overflowY: 'auto' }}>

            <TextField
                label={t('dashboard.label_point_name')}
                fullWidth value={name} onChange={e => setName(e.target.value)} sx={{ mt: 1 }}
            />
          <TextField
              label={t('point_details.address_label')}
              fullWidth value={address} onChange={e => setAddress(e.target.value)} sx={{ mt: 1 }}
          />
        
        <Box sx={{ mt: 1 }}>
            <PhoneInput 
            label={t('point_details.contact_phone_label')}
                value={contactPhone}
                onChange={setContactPhone}
            fullWidth
        />
        </Box>

        <TextField
            label={t('point_details.contact_link_label')}
            fullWidth
            value={contactLink}
            onChange={(e) => setContactLink(e.target.value)}
            helperText={t('point_details.contact_link_hint')}
            sx={{ mt: 1 }}
            inputProps={{ maxLength: 80 }}
        />
        <TextField
            label={t('point_details.additional_info_label')}
            fullWidth
            value={additionalInfo}
            onChange={(e) => setAdditionalInfo(e.target.value)}
            helperText={`${t('point_details.additional_info_hint')} (${additionalInfo.length}/20)`}
            sx={{ mt: 1 }}
            inputProps={{ maxLength: 20 }}
        />
          <Box mt={1} display="flex" flexDirection="column" gap={1}>
              <Button variant="outlined" onClick={openLocationDialog}>
                  {latitude && longitude ? t('point_details.edit_on_map', 'Edit on map') : t('point_details.select_on_map', 'Select on map')}
              </Button>
              <Typography variant="caption" color="textSecondary">
                  {latitude && longitude ? t('point_details.location_selected', 'Location selected via map') : t('point_details.map_hint', 'Click on the map to set coordinates. You can edit the address manually afterwards.')}
              </Typography>
          </Box>
          <FormControl fullWidth sx={{ mt: 1 }}>
              <InputLabel>{t('dashboard.country_label')}</InputLabel>
              <Select
                value={country}
                label={t('dashboard.country_label')}
                onChange={(e) => {
                    const code = e.target.value as string;
                    setCountry(code);
                    const option = countryOptions.find(o => o.code === code);
                    if (option) {
                        setCurrency(option.currency);
                    }
                }}
              >
                  {countryOptions.map((option) => (
                      <MenuItem key={option.code} value={option.code}>
                          {option.label} ({option.currency})
                      </MenuItem>
                  ))}
              </Select>
          </FormControl>

            <FormControl fullWidth sx={{ mt: 1 }}>
                <InputLabel>{t('dashboard.label_point_type')}</InputLabel>
                <Select value={type} label={t('dashboard.label_point_type')} onChange={e => setPointType(e.target.value)}>
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

             <FormControl fullWidth sx={{ mt: 1 }}>
                <InputLabel>{t('dashboard.label_strategy')}</InputLabel>
                <Select value={strategy} label={t('dashboard.label_strategy')} onChange={e => setStrategy(e.target.value)}>
                    <MenuItem value="TIERED_LTV">{t('dashboard.strategies.TIERED_LTV')}</MenuItem>
                    <MenuItem value="VISIT_COUNTER">{t('dashboard.strategies.VISIT_COUNTER')}</MenuItem>
                    <MenuItem value="HYBRID">{t('dashboard.strategies.HYBRID')}</MenuItem>
                </Select>
            </FormControl>

            {(strategy === 'VISIT_COUNTER' || strategy === 'HYBRID') && (
                <TextField
                    label={t('dashboard.label_target')}
                    type="number"
                    value={defaultVisitsTarget}
                    InputProps={{ readOnly: true }}
                    disabled
                    helperText={t('point_details.visits_target_locked_hint')}
                    sx={{ mt: 1 }}
                />
            )}

            {(strategy === 'TIERED_LTV' || strategy === 'HYBRID') && (
                <>
                    <TextField
                        label={t('dashboard.label_cashback')}
                        type="number"
                        value={cashback}
                        onChange={e => setCashback(e.target.value)}
                        helperText={t('dashboard.hint_cashback')}
                        sx={{ mt: 1 }}
                    />
                    <Alert severity="info" sx={{ mt: 1 }}>
                        {t('dashboard.hint_tiered_levels', { base: base, mid: mid, max: max })}
                    </Alert>
                    <FormControlLabel
                        control={
                            <Switch
                                checked={awardOnMixedPayment}
                                onChange={(e) => setAwardOnMixedPayment(e.target.checked)}
                                color="primary"
                            />
                        }
                        sx={{ mt: 1 }}
                        label={<Box>
                            <Typography variant="body2">{t('point_details.award_mixed_label', 'Award cashback on mixed payment')}</Typography>
                            <Typography variant="caption" color="text.secondary">
                                {t('point_details.award_mixed_hint', 'When enabled, the system will accrue bonuses on the money part even if the client spends points.')}
                            </Typography>
                        </Box>}
                    />
                </>
            )}

        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
            <Button onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreatePoint} variant="contained">{t('common.add')}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={locationDialogOpen} onClose={closeLocationDialog} fullWidth maxWidth="md">
        <DialogTitle>{t('point_details.map_dialog_title', 'Select location on map')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <Box display="flex" gap={1}>
                <TextField
                    label={t('point_details.search_placeholder', 'Enter address')}
                    fullWidth
                    value={mapSearch}
                    onChange={e => setMapSearch(e.target.value)}
                />
                <Button variant="contained" onClick={handleAddressSearch} startIcon={<SearchIcon />}>
                    {t('point_details.search_button', 'Search')}
                </Button>
            </Box>
            <Typography variant="caption" color="textSecondary">
                {t('point_details.map_hint', 'Click on the map to set coordinates. You can edit the address manually afterwards.')}
            </Typography>
            <LocationPicker
                initialLat={tempCoords?.lat ?? (latitude ? parseFloat(latitude) : undefined)}
                initialLng={tempCoords?.lng ?? (longitude ? parseFloat(longitude) : undefined)}
                onLocationChange={handleMapLocationChange}
                height={500}
            />
            <Typography variant="body2">
                {t('point_details.selected_address', 'Selected address')}: {tempAddress || address || '—'}
            </Typography>
        </DialogContent>
        <DialogActions>
            <Button onClick={closeLocationDialog}>{t('common.cancel')}</Button>
            <Button variant="contained" onClick={applySelectedLocation}>{t('common.save')}</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};
