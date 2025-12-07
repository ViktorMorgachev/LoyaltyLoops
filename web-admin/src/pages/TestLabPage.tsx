import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
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
    sendEvent: false,
    createSub: false,
    checkSub: false
  });

  const [subForm, setSubForm] = useState({
    partnerId: '',
    pointId: '',
    duration: 'DAY_3'
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

  const handleCreateRequest = async () => {
    // Only Point ID is now exposed
    if (!subForm.pointId.trim()) {
        showError(t('test_lab.validation_point_id'));
        return;
    }
    setLoading(prev => ({ ...prev, createSub: true }));
    try {
        const payload = {
            type: 'ACTIVATE_POINT',
            targetPointId: subForm.pointId.trim(),
            amount: 0,
            duration: subForm.duration,
            isTrial: false
        };
        const { data } = await api.post('/platform/requests', payload);
        showSuccess(t('test_lab.success_generic'));
        appendLog(`CREATE REQUEST: ${JSON.stringify(data)}`);
    } catch (error: any) {
        const message = error?.response?.data?.message || error.message || 'Error';
        showError(message);
        appendLog(`CREATE REQUEST ERROR: ${message}`);
    } finally {
        setLoading(prev => ({ ...prev, createSub: false }));
    }
  };

  const handleCheckSubscription = async () => {
    setLoading(prev => ({ ...prev, checkSub: true }));
    try {
        const { data } = await api.post('/test-support/subscription-check');
        showSuccess(t('test_lab.check_triggered'));
        appendLog(`SUBSCRIPTION CHECK: ${data?.message}`);
    } catch (error: any) {
        const message = error?.response?.data?.message || error.message || 'Error';
        showError(message);
        appendLog(`SUBSCRIPTION CHECK ERROR: ${message}`);
    } finally {
        setLoading(prev => ({ ...prev, checkSub: false }));
    }
  };

  const upcomingFeature = () => {
    showInfo(t('test_lab.soon_push'));
  };

  return (
    <Box maxWidth="xl" mx="auto" sx={{ mt: 4, mb: 8 }}>
      <Box mb={4}>
          <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #10b981 30%, #3b82f6 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {t('test_lab.title')}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {t('test_lab.subtitle')}
          </Typography>
      </Box>

      <Box
        display="grid"
        gridTemplateColumns={{ xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }}
        gap={3}
      >
        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.delete_user_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.delete_user_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.user_id')}
                value={deleteUserId}
                onChange={(e) => setDeleteUserId(e.target.value)}
                fullWidth
                size="small"
              />
              <Button
                variant="contained"
                color="error"
                onClick={handleDeleteUser}
                disabled={loading.deleteUser}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.delete_user_btn')}
              </Button>
            </Stack>
        </Paper>

        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.delete_card_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.delete_card_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={deleteCardId}
                onChange={(e) => setDeleteCardId(e.target.value)}
                fullWidth
                size="small"
              />
              <Button
                variant="contained"
                color="error"
                onClick={handleDeleteCard}
                disabled={loading.deleteCard}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.delete_card_btn')}
              </Button>
            </Stack>
        </Paper>

        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider', bgcolor: 'grey.50' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.push_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.push_desc')}
            </Typography>
            <Button variant="outlined" onClick={upcomingFeature} fullWidth sx={{ borderRadius: 2 }}>
              {t('test_lab.push_btn')}
            </Button>
          </Paper>

        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.card_mutation_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.card_mutation_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={mutationForm.cardId}
                onChange={(e) => setMutationForm((prev) => ({ ...prev, cardId: e.target.value }))}
                fullWidth
                size="small"
              />
              <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
                  <TextField
                    label={t('test_lab.balance')}
                    type="number"
                    size="small"
                    value={mutationForm.balance}
                    onChange={(e) => setMutationForm((prev) => ({ ...prev, balance: e.target.value }))}
                  />
                  <TextField
                    label={t('test_lab.visits')}
                    type="number"
                    size="small"
                    value={mutationForm.visits}
                    onChange={(e) => setMutationForm((prev) => ({ ...prev, visits: e.target.value }))}
                  />
                  <TextField
                    label={t('test_lab.tier')}
                    type="number"
                    size="small"
                    value={mutationForm.tierLevel}
                    onChange={(e) => setMutationForm((prev) => ({ ...prev, tierLevel: e.target.value }))}
                  />
                  <TextField
                    label={t('test_lab.total_spent')}
                    type="number"
                    size="small"
                    value={mutationForm.totalSpent}
                    onChange={(e) => setMutationForm((prev) => ({ ...prev, totalSpent: e.target.value }))}
                  />
              </Box>
              
              <Divider />
              
              <TextField
                label={t('test_lab.card_blocked_until_label')}
                select
                size="small"
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
                size="small"
                disabled={mutationForm.blockPreset === 'keep' || mutationForm.blockPreset === 'none'}
              />
              
              <Button
                variant="contained"
                onClick={handleMutateCard}
                disabled={loading.mutateCard}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.card_mutation_btn')}
              </Button>
            </Stack>
          </Paper>

        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.event_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.event_desc')}
            </Typography>
            <Stack spacing={2}>
              <TextField
                label={t('test_lab.card_id')}
                value={eventForm.cardId}
                size="small"
                onChange={(e) => setEventForm((prev) => ({ ...prev, cardId: e.target.value }))}
              />
              <TextField
                label={t('test_lab.event_type')}
                select
                size="small"
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
                size="small"
                onChange={(e) => setEventForm((prev) => ({ ...prev, args: e.target.value }))}
              />
              <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
                  <TextField
                    label={t('test_lab.new_balance')}
                    type="number"
                    size="small"
                    value={eventForm.newBalance}
                    onChange={(e) => setEventForm((prev) => ({ ...prev, newBalance: e.target.value }))}
                  />
                  <TextField
                    label={t('test_lab.new_visits')}
                    type="number"
                    size="small"
                    value={eventForm.newVisits}
                    onChange={(e) => setEventForm((prev) => ({ ...prev, newVisits: e.target.value }))}
                  />
              </Box>
              <Button
                variant="contained"
                color="secondary"
                onClick={handleSendEvent}
                disabled={loading.sendEvent}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.event_btn')}
              </Button>
            </Stack>
        </Paper>

        <Paper elevation={0} sx={{ p: 3, height: '100%', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight="bold" gutterBottom>{t('test_lab.subscription_title')}</Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              {t('test_lab.subscription_desc')}
            </Typography>
            <Stack spacing={2}>
              {/* REMOVED PARTNER ID FIELD AS REQUESTED */}
              <TextField
                label={t('test_lab.sub_point_id')}
                placeholder="Required"
                value={subForm.pointId}
                onChange={(e) => setSubForm(prev => ({ ...prev, pointId: e.target.value }))}
                fullWidth
                size="small"
              />
              <TextField
                label={t('test_lab.sub_duration')}
                select
                size="small"
                value={subForm.duration}
                onChange={(e) => setSubForm(prev => ({ ...prev, duration: e.target.value }))}
              >
                <MenuItem value="DAY_1">{t('test_lab.sub_day_1')}</MenuItem>
                <MenuItem value="DAY_3">{t('test_lab.sub_day_3')}</MenuItem>
              </TextField>
              <Button
                variant="contained"
                onClick={handleCreateRequest}
                disabled={loading.createSub}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.create_request_btn')}
              </Button>
              <Divider sx={{ my: 2 }} />
              <Typography variant="body2" color="text.secondary">
                  {t('test_lab.check_subscription_desc')}
              </Typography>
              <Button
                variant="outlined"
                color="warning"
                onClick={handleCheckSubscription}
                disabled={loading.checkSub}
                sx={{ borderRadius: 2 }}
              >
                {t('test_lab.check_subscription_btn')}
              </Button>
            </Stack>
        </Paper>

        <Paper
          elevation={0}
          sx={{
            p: 0,
            gridColumn: { md: 'span 2' },
            borderRadius: 4, 
            border: '1px solid', 
            borderColor: 'divider',
            overflow: 'hidden',
            bgcolor: '#1e293b',
            color: '#fff'
          }}
        >
          <Box p={2} borderBottom="1px solid" borderColor="rgba(255,255,255,0.1)">
            <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ m: 0, fontSize: '1rem', fontFamily: 'monospace' }}>
                TERMINAL_OUTPUT: {t('test_lab.log_title')}
            </Typography>
          </Box>
          <Box component="pre" sx={{ maxHeight: 300, overflowY: 'auto', m: 0, p: 2, fontFamily: 'monospace', fontSize: '0.85rem', color: '#4ade80' }}>
            {logs.length === 0 ? <span style={{ opacity: 0.5 }}>Waiting for events...</span> : logs.join('\n')}
          </Box>
        </Paper>
      </Box>
    </Box>
  );
};

