import { useState, useEffect } from 'react';
import { Container, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip, Box } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';

export const TransactionsPage = () => {
  const { t } = useTranslation();
  const { showError } = useNotification();
  const [history, setHistory] = useState<any[]>([]);

  useEffect(() => { loadHistory(); }, []);

  const loadHistory = async () => {
    try {
      const res = await api.get('/partners/history');
      setHistory(res.data);
    } catch (e: any) {
      if (e.response && e.response.status === 404) {
          setHistory([]);
      } else {
          showError(getErrorMessage(e));
      }
    }
  };

  const formatDate = (ts: number) => {
      return new Date(ts).toLocaleString();
  };

  const getTypeLabel = (type: string) => {
      if (type === 'VISIT') return t('history.type_visit');
      if (type === 'EARN') return t('history.type_earn');
      if (type === 'SPEND') return t('history.type_spend');
      return type;
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Typography variant="h4" mb={3} fontWeight="bold">{t('history.title')}</Typography>

      <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
        <Box sx={{ overflowX: 'auto' }}>
            <Table sx={{ minWidth: 650 }}>
              <TableHead sx={{ bgcolor: 'action.hover' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>{t('history.table_date')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('history.table_point')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('history.table_type')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('history.table_amount')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('history.table_bonus')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 6, color: 'text.secondary' }}>
                        {t('history.empty')}
                    </TableCell>
                  </TableRow>
                )}
                {history.map((h) => (
                  <TableRow key={h.id} hover>
                    <TableCell sx={{ color: 'text.secondary' }}>{formatDate(h.timestamp)}</TableCell>
                    <TableCell sx={{ fontWeight: 500 }}>{h.pointName}</TableCell>
                    <TableCell>
                        <Chip 
                            label={getTypeLabel(h.type)} 
                            color={h.type === 'VISIT' ? 'info' : (h.type === 'EARN' ? 'success' : 'warning')} 
                            size="small" 
                            variant="outlined"
                            sx={{ fontWeight: 500, borderRadius: 2 }}
                        />
                    </TableCell>
                    <TableCell>{h.amount > 0 ? h.amount.toFixed(2) : '—'}</TableCell>
                    <TableCell sx={{ color: h.pointsDelta > 0 || h.visitsDelta > 0 ? 'success.main' : 'text.primary', fontWeight: h.pointsDelta > 0 || h.visitsDelta > 0 ? 600 : 400 }}>
                        {h.pointsDelta !== 0 ? (h.pointsDelta > 0 ? `+${h.pointsDelta.toFixed(2)}` : h.pointsDelta.toFixed(2)) : (h.visitsDelta > 0 ? `+${h.visitsDelta} visit` : '—')}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
        </Box>
      </Paper>
    </Container>
  );
};
