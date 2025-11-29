import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { useUser } from '../context/UserContext';

type TransactionEventType =
  | 'BALANCE_INFO'
  | 'POINTS_EARNED'
  | 'POINTS_SPENT'
  | 'POINTS_SPENT_EARNED'
  | 'VISIT_PROGRESS'
  | 'VISIT_REWARD';

const EVENT_OPTIONS: TransactionEventType[] = [
  'BALANCE_INFO',
  'POINTS_EARNED',
  'POINTS_SPENT',
  'POINTS_SPENT_EARNED',
  'VISIT_PROGRESS',
  'VISIT_REWARD'
];

type BlockPreset = 'keep' | 'none' | 'week' | 'month' | 'quarter' | 'half';
type PauseState = 'keep' | 'active' | 'paused';

const BLOCK_PRESET_DAYS: Record<Exclude<BlockPreset, 'keep' | 'none'>, number> = {
  week: 7,
  month: 30,
  quarter: 90,
  half: 180
};
const DAY_MS = 24 * 60 * 60 * 1000;

export const TestLabPage: React.FC = () => {
  const { t } = useTranslation();
  const { showError, showSuccess, showInfo } = useNotification();
  const { isSuperAdmin } = useUser();

  const [deleteUserId, setDeleteUserId] = useState('');
  const [deleteCardId, setDeleteCardId] = useState('');
  const [mutationForm, setMutationForm] = useState({
    cardId: '',
    balance: '',
    visits: '',
    tierLevel: '',
    totalSpent: '',
    blockPreset: 'keep' as BlockPreset,
    blockReason: '',
    pauseState: 'keep' as PauseState,
    pauseReason: ''
  });
  const [eventForm, setEventForm] = useState({
    cardId: '',
    successType: 'BALANCE_INFO' as TransactionEventType,
    args: '',
    newBalance: '',
    newVisits: ''
  });
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState({
    deleteUser: false,
    deleteCard: false,
    mutateCard: false,
    sendEvent: false
  });

  const appendLog = (message: string) => {
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ${message}`, ...prev].slice(0, 20));
  };

  if (!isSuperAdmin) {
    return (
      <Alert severity="warning">
        {t('test_lab.access_denied')}
      </Alert>
    );
  }

  const handleDeleteUser = async () => {
    const id = deleteUserId.trim();
    if (!id) {
      showError(t('test_lab.validation_user_id'));
      return;
    }
    setLoading((prev) => ({ ...prev, deleteUser: true }));
    try {
      const { data } = await api.delete(`/test-support/users/${id}`);
      showSuccess(data?.message || t('test_lab.success_generic'));
      appendLog(`DELETE user ${id}: ${data?.message ?? 'OK'}`);
      setDeleteUserId('');
    } catch (error: any) {
      const message = error?.response?.data?.message || error.message || 'Error';
      showError(message);
      appendLog(`DELETE user ${id}: ${message}`);
    } finally {
      setLoading((prev) => ({ ...prev, deleteUser: false }));
    }
  };

  const handleMutateCard = async () => {
    if (!mutationForm.cardId.trim()) {
      showError(t('test_lab.validation_card_id'));
      return;
    }
    const payload: any = {
      cardId: mutationForm.cardId.trim(),
      balance: mutationForm.balance ? Number(mutationForm.balance) : undefined,
      visits: mutationForm.visits ? Number(mutationForm.visits) : undefined,
      tierLevel: mutationForm.tierLevel ? Number(mutationForm.tierLevel) : undefined,
      totalSpent: mutationForm.totalSpent ? Number(mutationForm.totalSpent) : undefined
    };

    if (mutationForm.blockPreset !== 'keep') {
      payload.blockUpdate = true;
      if (mutationForm.blockPreset === 'none') {
        payload.block = null;
      } else {
        const days = BLOCK_PRESET_DAYS[mutationForm.blockPreset] ?? 0;
        const until = Date.now() + days * DAY_MS;
        payload.block = {
          until,
          reason: mutationForm.blockReason.trim() ? mutationForm.blockReason.trim().slice(0, 15) : null
        };
      }
    }

    if (mutationForm.pauseState !== 'keep') {
      payload.pauseUpdate = true;
      if (mutationForm.pauseState === 'paused') {
        payload.pause = {
          reason: mutationForm.pauseReason.trim() ? mutationForm.pauseReason.trim().slice(0, 15) : null
        };
      } else {
        payload.pause = null;
      }
    }
    setLoading((prev) => ({ ...prev, mutateCard: true }));
    try {
      const { data } = await api.post('/test-support/cards/mutate', payload);
      showSuccess(t('test_lab.card_mutated'));
      appendLog(`MUTATE card ${payload.cardId}: ${JSON.stringify(data)}`);
    } catch (error: any) {
      const message = error?.response?.data?.message || error.message || 'Error';
      showError(message);
      appendLog(`MUTATE card ${payload.cardId}: ${message}`);
    } finally {
      setLoading((prev) => ({ ...prev, mutateCard: false }));
    }
  };

  const handleDeleteCard = async () => {
    const id = deleteCardId.trim();
    if (!id) {
      showError(t('test_lab.validation_card_id'));
      return;
    }
    setLoading((prev) => ({ ...prev, deleteCard: true }));
    try {
      const { data } = await api.delete(`/test-support/cards/${id}`);
      showSuccess(data?.message || t('test_lab.success_generic'));
      appendLog(`DELETE card ${id}: ${data?.message ?? 'OK'}`);
      setDeleteCardId('');
    } catch (error: any) {
      const message = error?.response?.data?.message || error.message || 'Error';
      showError(message);
      appendLog(`DELETE card ${id}: ${message}`);
    } finally {
      setLoading((prev) => ({ ...prev, deleteCard: false }));
    }
  };

  const handleSendEvent = async () => {
    if (!eventForm.cardId.trim()) {
      showError(t('test_lab.validation_card_id'));
      return;
    }
    const payload = {
      cardId: eventForm.cardId.trim(),
      successType: eventForm.successType,
      args: eventForm.args
        .split(',')
        .map((token) => token.trim())
        .filter(Boolean),
      newBalance: eventForm.newBalance ? Number(eventForm.newBalance) : undefined,
      newVisits: eventForm.newVisits ? Number(eventForm.newVisits) : undefined
    };
    setLoading((prev) => ({ ...prev, sendEvent: true }));
    try {
      const { data } = await api.post('/test-support/cards/animation', payload);
      showSuccess(data?.message || t('test_lab.success_generic'));
      appendLog(`EVENT card ${payload.cardId}: ${payload.successType}`);
    } catch (error: any) {
      const message = error?.response?.data?.message || error.message || 'Error';
      showError(message);
      appendLog(`EVENT card ${payload.cardId}: ${message}`);
    } finally {
      setLoading((prev) => ({ ...prev, sendEvent: false }));
    }
  };

  const upcomingFeature = () => {
    showInfo(t('test_lab.soon_push'));
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>{t('test_lab.title')}</Typography>
      <Typography variant="body1" color="text.secondary" mb={3}>
        {t('test_lab.subtitle')}
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6">{t('test_lab.delete_user_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('test_lab.delete_user_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.user_id')}
                value={deleteUserId}
                onChange={(e) => setDeleteUserId(e.target.value)}
                fullWidth
              />
              <Button
                variant="contained"
                color="error"
                onClick={handleDeleteUser}
                disabled={loading.deleteUser}
              >
                {t('test_lab.delete_user_btn')}
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6">{t('test_lab.delete_card_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('test_lab.delete_card_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={deleteCardId}
                onChange={(e) => setDeleteCardId(e.target.value)}
                fullWidth
              />
              <Button
                variant="contained"
                color="error"
                onClick={handleDeleteCard}
                disabled={loading.deleteCard}
              >
                {t('test_lab.delete_card_btn')}
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6">{t('test_lab.push_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('test_lab.push_desc')}
            </Typography>
            <Button variant="outlined" onClick={upcomingFeature}>
              {t('test_lab.push_btn')}
            </Button>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6">{t('test_lab.card_mutation_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('test_lab.card_mutation_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={mutationForm.cardId}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, cardId: e.target.value }))}
                fullWidth
              />
              <TextField
                label={t('test_lab.balance')}
                type="number"
                value={mutationForm.balance}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, balance: e.target.value }))}
              />
              <TextField
                label={t('test_lab.visits')}
                type="number"
                value={mutationForm.visits}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, visits: e.target.value }))}
              />
              <TextField
                label={t('test_lab.tier')}
                type="number"
                value={mutationForm.tierLevel}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, tierLevel: e.target.value }))}
              />
              <TextField
                label={t('test_lab.total_spent')}
                type="number"
                value={mutationForm.totalSpent}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, totalSpent: e.target.value }))}
              />
              <TextField
                label={t('test_lab.card_blocked_until_label')}
                select
                value={mutationForm.blockPreset}
                onChange={(e) =>
                  setMutationForm((prev) => ({ ...prev, blockPreset: e.target.value as BlockPreset }))
                }
              >
                <MenuItem value="keep">{t('test_lab.card_status_no_change')}</MenuItem>
                <MenuItem value="none">{t('test_lab.card_blocked_until_none')}</MenuItem>
                <MenuItem value="week">{t('test_lab.card_blocked_until_week')}</MenuItem>
                <MenuItem value="month">{t('test_lab.card_blocked_until_month')}</MenuItem>
                <MenuItem value="quarter">{t('test_lab.card_blocked_until_3months')}</MenuItem>
                <MenuItem value="half">{t('test_lab.card_blocked_until_6months')}</MenuItem>
              </TextField>
              <TextField
                label={t('test_lab.card_blocked_reason_label')}
                value={mutationForm.blockReason}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, blockReason: e.target.value }))}
                inputProps={{ maxLength: 15 }}
                disabled={mutationForm.blockPreset === 'keep' || mutationForm.blockPreset === 'none'}
              />
              <Box display="flex" justifyContent="flex-end">
                <Button
                  variant="text"
                  size="small"
                  onClick={() =>
                    setMutationForm((prev) => ({
                      ...prev,
                      blockPreset: 'none',
                      blockReason: ''
                    }))
                  }
                >
                  {t('test_lab.card_blocked_clear_btn')}
                </Button>
              </Box>
              <TextField
                label={t('test_lab.card_closed_label')}
                select
                value={mutationForm.pauseState}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, pauseState: e.target.value as PauseState }))}
              >
                <MenuItem value="keep">{t('test_lab.card_status_no_change')}</MenuItem>
                <MenuItem value="active">{t('test_lab.card_closed_open')}</MenuItem>
                <MenuItem value="paused">{t('test_lab.card_closed_closed')}</MenuItem>
              </TextField>
              <TextField
                label={t('test_lab.card_closed_reason_label')}
                value={mutationForm.pauseReason}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, pauseReason: e.target.value }))}
                inputProps={{ maxLength: 15 }}
                disabled={mutationForm.pauseState !== 'paused'}
              />
              <Button
                variant="contained"
                onClick={handleMutateCard}
                disabled={loading.mutateCard}
              >
                {t('test_lab.card_mutation_btn')}
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6">{t('test_lab.event_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('test_lab.event_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={eventForm.cardId}
                onChange={(e) => setEventForm((prev) => ({ ...prev, cardId: e.target.value }))}
              />
              <TextField
                label={t('test_lab.event_type')}
                select
                value={eventForm.successType}
                onChange={(e) => setEventForm((prev) => ({ ...prev, successType: e.target.value as TransactionEventType }))}
              >
                {EVENT_OPTIONS.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label={t('test_lab.args')}
                helperText={t('test_lab.args_hint')}
                value={eventForm.args}
                onChange={(e) => setEventForm((prev) => ({ ...prev, args: e.target.value }))}
              />
              <TextField
                label={t('test_lab.new_balance')}
                type="number"
                value={eventForm.newBalance}
                onChange={(e) => setEventForm((prev) => ({ ...prev, newBalance: e.target.value }))}
              />
              <TextField
                label={t('test_lab.new_visits')}
                type="number"
                value={eventForm.newVisits}
                onChange={(e) => setEventForm((prev) => ({ ...prev, newVisits: e.target.value }))}
              />
              <Button
                variant="contained"
                color="secondary"
                onClick={handleSendEvent}
                disabled={loading.sendEvent}
              >
                {t('test_lab.event_btn')}
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>{t('test_lab.log_title')}</Typography>
            <Divider sx={{ mb: 2 }} />
            <Box component="pre" sx={{ maxHeight: 240, overflowY: 'auto', m: 0 }}>
              {logs.length === 0 ? t('test_lab.log_empty') : logs.join('\n')}
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

