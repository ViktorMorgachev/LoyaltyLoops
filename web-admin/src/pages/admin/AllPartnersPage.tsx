import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/axiosConfig';
import {
  Container, Typography, Paper, Table, TableBody, TableCell, TableHead, TableRow, Chip, Button, Box
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import { useUser } from '../../context/UserContext';
import { maskPhone } from '../../utils/maskPhone';
import { TableSkeleton } from '../../components/common/TableSkeleton';

export const AllPartnersPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { isSuperAdmin, isSuperManager,isPlatformManager, currentWorkspace } = useUser();
  const [partners, setPartners] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // Guard: only super admin/manager with selected workspace
  if (!currentWorkspace || (!isSuperAdmin && !isSuperManager && !isPlatformManager,)) {
    return null;
  }

  useEffect(() => { loadPartners(); }, []);

  const loadPartners = async () => {
    try {
      setLoading(true);
      const res = await api.get('/admin/partners');
      setPartners(res.data);
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  const changeStatus = async (id: string, newStatus: string) => {
    try {
      await api.post(`/admin/partners/${id}/status`, { status: newStatus });
      showSuccess(t('common.status_changed'));
      loadPartners();
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 8 }}>
      <Box mb={4}>
        <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {t('admin.title')}
        </Typography>
      </Box>

      <Paper elevation={0} sx={{ borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
        <Box sx={{ overflowX: 'auto' }}>
            <Table sx={{ minWidth: 650 }}>
              <TableHead sx={{ bgcolor: 'action.hover' }}>
            <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>{t('dashboard.table_name')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('admin.table_country')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('admin.table_owner')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t('common.status')}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>{t('common.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
                <TableSkeleton cols={5} />
            ) : partners.length === 0 ? (
                <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 8, color: 'text.secondary' }}>
                        {t('admin.no_partners', 'No partners found')}
                    </TableCell>
                </TableRow>
            ) : (
                partners.map((p) => (
                  <TableRow key={p.id} hover>
                    <TableCell sx={{ fontWeight: 500 }}>{p.businessName || p.name || t('dashboard.table_name')}</TableCell>
                <TableCell>{p.countryCode}</TableCell>
                <TableCell>{isSuperAdmin ? p.ownerPhone : (maskPhone(p.ownerPhone) || "N/A")}</TableCell>
                <TableCell>
                  <Chip
                    label={p.status === 'ACTIVE' ? t('common.active') : p.status === 'BLOCKED' ? t('common.blocked') : t('common.pending')}
                    color={p.status === 'ACTIVE' ? 'success' : p.status === 'BLOCKED' ? 'error' : 'warning'}
                        size="small"
                        variant="outlined"
                        sx={{ fontWeight: 600 }}
                  />
                </TableCell>
                    <TableCell align="right">
                      <Box display="flex" gap={1} justifyContent="flex-end">
                        <Button size="small" variant="outlined" onClick={() => navigate(`/admin/partners/${p.id}`)} sx={{ borderRadius: 2 }}>
                        {t('common.details')}
                    </Button>
                    {p.status === 'PENDING' && (
                          <Button size="small" variant="contained" color="success" onClick={() => changeStatus(p.id, 'ACTIVE')} sx={{ borderRadius: 2 }}>
                        {t('common.approve')}
                      </Button>
                    )}
                    {p.status !== 'BLOCKED' ? (
                          <Button size="small" variant="text" color="error" onClick={() => changeStatus(p.id, 'BLOCKED')}>
                        {t('common.block')}
                      </Button>
                    ) : (
                           <Button size="small" variant="outlined" onClick={() => changeStatus(p.id, 'ACTIVE')} sx={{ borderRadius: 2 }}>
                        {t('common.unblock')}
                      </Button>
                    )}
                  </Box>
                </TableCell>
              </TableRow>
            )))}
          </TableBody>
        </Table>
        </Box>
      </Paper>
    </Container>
  );
};
