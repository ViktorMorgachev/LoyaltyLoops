import React, { useState, useEffect } from 'react';
import {
  Container, Button, Box, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem,
  Alert
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';

export const PartnerDashboardPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();

  const [points, setPoints] = useState<any[]>([]);
  const [open, setOpen] = useState(false);

  const [name, setName] = useState('');
  const [type, setPointType] = useState('COFFEE_SHOP');

  const [strategy, setStrategy] = useState('TIERED_LTV');
  const [visitsTarget, setVisitsTarget] = useState('6');
  const [cashback, setCashback] = useState('5');

  useEffect(() => { loadPoints(); }, []);

  const loadPoints = async () => {
    try {
      // ВАЖНО: Запрос на /partners/points (множественное число)
      const res = await api.get('/partners/points');
      setPoints(res.data);
    } catch (e: any) {
      // Если 404 (нет точек) или другая ошибка - не пугаем алертами при старте,
      // просто логируем, если это не критично. Но если это 500 - показываем.
      if (e.response && e.response.status !== 404) {
          showError(getErrorMessage(e));
      }
    }
  };

  const handleCreatePoint = async () => {
    try {
      const payload = {
        name,
        type,
        programType: strategy,
        visitsTarget: strategy === 'VISIT_COUNTER' ? parseInt(visitsTarget) : null,
        baseCashback: strategy === 'TIERED_LTV' ? parseInt(cashback) / 100.0 : null
      };

      // ВАЖНО: Запрос на /partners/points
      await api.post('/partners/points', payload);

      setOpen(false);
      setName('');
      showSuccess(t('common.create') + " OK");
      loadPoints();
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  const copyInvite = (code: string) => {
    navigator.clipboard.writeText(code);
    showSuccess(`${t('common.copied')}: ${code}`);
  };

  const getLocalizedType = (typeEnum: string) => {
      return t(`dashboard.types.${typeEnum}`, typeEnum);
  };

  const base = parseInt(cashback) || 0;
  const mid = base + 2;
  const max = base + 5;

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">{t('dashboard.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          {t('dashboard.add_point')}
        </Button>
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
              <TableRow key={p.id}>
                <TableCell>{p.name}</TableCell>
                <TableCell>{getLocalizedType(p.type)}</TableCell>
                <TableCell>
                  {p.inviteCode ? (
                    <Chip
                      label={p.inviteCode}
                      onClick={() => copyInvite(p.inviteCode)}
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

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('dashboard.create_title')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>

            <TextField
                label={t('dashboard.label_point_name')}
                fullWidth value={name} onChange={e => setName(e.target.value)} sx={{ mt: 1 }}
            />

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
                </Select>
            </FormControl>

            {strategy === 'VISIT_COUNTER' ? (
                <TextField
                    label={t('dashboard.label_target')}
                    type="number"
                    value={visitsTarget}
                    onChange={e => setVisitsTarget(e.target.value)}
                    helperText={t('dashboard.hint_target')}
                    sx={{ mt: 1 }}
                />
            ) : (
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
                </>
            )}

        </DialogContent>
        <DialogActions>
            <Button onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreatePoint} variant="contained">{t('common.add')}</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};