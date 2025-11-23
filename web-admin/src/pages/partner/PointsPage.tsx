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
import { IconButton } from '@mui/material';
import { useUser } from '../../context/UserContext';

export const PointsPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { currentWorkspace } = useUser();
  const canManage = currentWorkspace?.role === 'PARTNER_ADMIN';

  const [points, setPoints] = useState<any[]>([]);
  const [open, setOpen] = useState(false);

  const [name, setName] = useState('');
  const [type, setPointType] = useState('COFFEE_SHOP');
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState<string>('');
  const [longitude, setLongitude] = useState<string>('');
  const countryOptions = React.useMemo(() => ([
    { code: 'KG', currency: 'KGS', label: t('countries.KG') },
    { code: 'KZ', currency: 'KZT', label: t('countries.KZ') },
    { code: 'UZ', currency: 'UZS', label: t('countries.UZ') },
    { code: 'BY', currency: 'BYN', label: t('countries.BY') },
  ]), [t]);
  const [country, setCountry] = useState('KG');
  const [currency, setCurrency] = useState('KGS');

  const [strategy, setStrategy] = useState('TIERED_LTV');
  const [visitsTarget, setVisitsTarget] = useState('6');
  const [cashback, setCashback] = useState('5');
  const [locationDialogOpen, setLocationDialogOpen] = useState(false);
  const [mapSearch, setMapSearch] = useState('');
  const [tempCoords, setTempCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [tempAddress, setTempAddress] = useState('');
  const [mapSeed, setMapSeed] = useState(0);
  const [awardOnMixedPayment, setAwardOnMixedPayment] = useState(false);

  useEffect(() => { loadPoints(); }, []);

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

  const handleMapSelection = async (lat: number, lng: number) => {
    setLatitude(lat.toString());
    setLongitude(lng.toString());

    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
      const response = await fetch(url, { headers: { 'User-Agent': 'LoyaltyLoop-Admin/1.0' } });
      if (response.ok) {
        const data = await response.json();
        if (data?.display_name) {
          setAddress(data.display_name);
        }
      }
    } catch (error) {
      console.warn('Geocoding error', error);
    }
  };

  const openLocationDialog = () => {
    setLocationDialogOpen(true);
    if (latitude && longitude) {
      const lat = parseFloat(latitude);
      const lng = parseFloat(longitude);
      if (!isNaN(lat) && !isNaN(lng)) {
        setTempCoords({ lat, lng });
        setMapSeed(prev => prev + 1);
      }
    } else {
      setTempCoords(null);
      setMapSeed(prev => prev + 1);
    }
    setTempAddress(address);
    setMapSearch(address);
  };

  const closeLocationDialog = () => {
    setLocationDialogOpen(false);
  };

  const reverseGeocode = async (lat: number, lng: number): Promise<string | null> => {
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

  const handleMapLocationChange = async (lat: number, lng: number) => {
    setTempCoords({ lat, lng });
    const addr = await reverseGeocode(lat, lng);
    if (addr) setTempAddress(addr);
  };

  const handleAddressSearch = async () => {
    if (!mapSearch.trim()) return;
    try {
      const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(mapSearch)}&format=json&limit=1`;
      const response = await fetch(url, { headers: { 'User-Agent': 'LoyaltyLoop-Admin/1.0' } });
      if (response.ok) {
        const data = await response.json();
        if (data && data.length > 0) {
          const lat = parseFloat(data[0].lat);
          const lng = parseFloat(data[0].lon);
          if (!isNaN(lat) && !isNaN(lng)) {
            setTempCoords({ lat, lng });
            setTempAddress(data[0].display_name || mapSearch);
            setMapSeed(prev => prev + 1);
          }
        } else {
          showError(t('point_details.search_not_found', 'Nothing found for this query'));
        }
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

      const payload = {
        name,
        type,
        address,
        latitude: lat,
        longitude: lng,
        currency,
        programType: strategy,
        visitsTarget: (strategy === 'VISIT_COUNTER' || strategy === 'HYBRID') ? parseInt(visitsTarget) : 0,
        baseCashback: isTiered ? baseCashbackValue : 0,
        awardOnMixedPayment
      };

      await api.post('/partners/points', payload);

      setOpen(false);
      setName('');
      setAddress('');
      setLatitude('');
      setLongitude('');
      setAwardOnMixedPayment(false);
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

      <Paper elevation={2}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>{t('dashboard.table_name')}</TableCell>
              <TableCell>{t('dashboard.table_type')}</TableCell>
              <TableCell>{t('dashboard.table_invite')}</TableCell>
              <TableCell>{t('common.status')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {points.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center">{t('dashboard.empty')}</TableCell>
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
                    value={visitsTarget}
                    onChange={e => setVisitsTarget(e.target.value)}
                    helperText={t('dashboard.hint_target')}
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
                key={mapSeed}
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
