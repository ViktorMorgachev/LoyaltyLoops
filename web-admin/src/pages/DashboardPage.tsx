import React, { useEffect, useState } from 'react';
import { api } from '../api/axiosConfig';
import {
  Container, Typography, Button, Box, Paper, TextField,
  Table, TableBody, TableCell, TableHead, TableRow, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, FormControl, InputLabel, Select, MenuItem
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';

export const DashboardPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();

  const [loading, setLoading] = useState(true);
  const [isPartner, setIsPartner] = useState(false);
  const [points, setPoints] = useState<any[]>([]);

  // Модалки
  const [openBusinessDialog, setOpenBusinessDialog] = useState(false);
  const [openPointDialog, setOpenPointDialog] = useState(false);

  // Формы
  const [bizName, setBizName] = useState('');
  const [pointName, setPointName] = useState('');
  const [pointType, setPointType] = useState('COFFEE_SHOP');

  // Стратегия
  const [strategy, setStrategy] = useState('TIERED_LTV');
  const [visitsTarget, setVisitsTarget] = useState('6');
  const [cashback, setCashback] = useState('5');

  useEffect(() => {
    checkStatus();
  }, []);

  const checkStatus = async () => {
    try {
      const res = await api.get('/client/me');
      const workspaces = res.data.workspaces || [];
      const partnerWorkspace = workspaces.find((w: any) => w.role === 'PARTNER_ADMIN');

      if (partnerWorkspace) {
        setIsPartner(true);
        loadPoints();
      } else {
        setIsPartner(false);
      }
    } catch (e: any) {
        // Игнорируем ошибку тут, либо логируем
    } finally {
      setLoading(false);
    }
  };

  const loadPoints = async () => {
    try {
      const res = await api.get('/partner/points');
      setPoints(res.data);
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  const handleCreateBusiness = async () => {
    try {
      await api.post('/partner/create', {
        businessName: bizName,
        countryCode: "KG"
      });
      setOpenBusinessDialog(false);
      showSuccess(t('common.create') + " OK");
      window.location.reload();
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  const handleCreatePoint = async () => {
    try {
      const payload = {
        name: pointName,
        type: pointType,
        programType: strategy,
        visitsTarget: strategy === 'VISIT_COUNTER' ? parseInt(visitsTarget) : null,
        baseCashback: strategy === 'TIERED_LTV' ? parseInt(cashback) / 100.0 : null
      };

      await api.post('/partner/points', payload);
      setOpenPointDialog(false);
      setPointName('');
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

  if (loading) return <Typography sx={{p: 4}}>{t('common.loading')}</Typography>;

  // --- СЦЕНАРИЙ: НЕТ БИЗНЕСА ---
  if (!isPartner) {
    return (
      <Container maxWidth="sm">
        <Box mt={10}>
          <Paper style={{ padding: 20, textAlign: 'center' }}>
            <Typography variant="h5">{t('dashboard.create_business_title')}</Typography>
            <Typography color="textSecondary" paragraph>{t('dashboard.create_business_subtitle')}</Typography>
            <Button variant="contained" size="large" onClick={() => setOpenBusinessDialog(true)}>
               {t('dashboard.create_business_btn')}
            </Button>
          </Paper>

          {/* DIALOG CREATE BUSINESS */}
          <Dialog open={openBusinessDialog} onClose={() => setOpenBusinessDialog(false)}>
            <DialogTitle>{t('dashboard.modal_biz_title')}</DialogTitle>
            <DialogContent sx={{ width: 400, pt: 1 }}>
                <TextField
                    autoFocus margin="dense" fullWidth
                    label={t('dashboard.modal_biz_name')}
                    value={bizName} onChange={(e) => setBizName(e.target.value)}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={() => setOpenBusinessDialog(false)}>{t('common.cancel')}</Button>
                <Button onClick={handleCreateBusiness} variant="contained">{t('common.create')}</Button>
            </DialogActions>
        </Dialog>
        </Box>
      </Container>
    );
  }

  // --- СЦЕНАРИЙ: ЕСТЬ БИЗНЕС ---
  return (
    <Container maxWidth="lg">
      <Box mt={2} display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">{t('dashboard.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpenPointDialog(true)}>
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
                <TableCell>{p.type}</TableCell>
                <TableCell>
                  {p.inviteCode ? (
                    <Chip
                      label={p.inviteCode}
                      onClick={() => copyInvite(p.inviteCode)}
                      icon={<ContentCopyIcon />}
                      color="primary"
                      clickable
                    />
                  ) : "—"}
                </TableCell>
                <TableCell>
                  <Chip
                    label={p.active ? t('common.active') : t('common.blocked')}
                    color={p.active ? "success" : "error"}
                    size="small"
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {/* DIALOG CREATE POINT */}
      <Dialog open={openPointDialog} onClose={() => setOpenPointDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('dashboard.create_title')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
                label={t('dashboard.table_name')}
                fullWidth value={pointName}
                onChange={(e) => setPointName(e.target.value)}
            />
            <FormControl fullWidth>
                <InputLabel>{t('dashboard.table_type')}</InputLabel>
                <Select value={type} label="Type" onChange={e => setPointType(e.target.value)}>
                    <MenuItem value="COFFEE_SHOP">Coffee Shop</MenuItem>
                    <MenuItem value="RESTAURANT">Restaurant</MenuItem>
                    <MenuItem value="RETAIL">Retail</MenuItem>
                </Select>
            </FormControl>
             <FormControl fullWidth>
                <InputLabel>Strategy</InputLabel>
                <Select value={strategy} label="Strategy" onChange={e => setStrategy(e.target.value)}>
                    <MenuItem value="TIERED_LTV">Cashback (Levels)</MenuItem>
                    <MenuItem value="VISIT_COUNTER">Visits (N+1)</MenuItem>
                </Select>
            </FormControl>
            {strategy === 'VISIT_COUNTER' ? (
                <TextField label="Target Visits" type="number" value={visitsTarget} onChange={e => setVisitsTarget(e.target.value)} />
            ) : (
                <TextField label="Base Cashback %" type="number" value={cashback} onChange={e => setCashback(e.target.value)} />
            )}
        </DialogContent>
        <DialogActions>
            <Button onClick={() => setOpenPointDialog(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreatePoint} variant="contained">{t('common.add')}</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};