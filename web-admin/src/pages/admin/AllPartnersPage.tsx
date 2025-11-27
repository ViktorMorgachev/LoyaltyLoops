import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/axiosConfig';
import {
  Container, Typography, Paper, Table, TableBody, TableCell, TableHead, TableRow, Chip, Button, Box
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';

export const AllPartnersPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const [partners, setPartners] = useState<any[]>([]);

  useEffect(() => { loadPartners(); }, []);

  const loadPartners = async () => {
    try {
      const res = await api.get('/admin/partners');
      setPartners(res.data);
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  const changeStatus = async (id: string, newStatus: string) => {
    try {
      await api.post(`/admin/partners/${id}/status`, { status: newStatus });
      showSuccess("Status changed");
      loadPartners();
    } catch (e: any) {
      showError(getErrorMessage(e));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Typography variant="h4" gutterBottom>{t('admin.title')}</Typography>
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>{t('dashboard.table_name')}</TableCell>
              <TableCell>{t('admin.table_country')}</TableCell>
              <TableCell>{t('admin.table_owner')}</TableCell>
              <TableCell>{t('common.status')}</TableCell>
              <TableCell>{t('common.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {partners.map((p) => (
              <TableRow key={p.id}>
                <TableCell>{p.businessName || p.name || t('dashboard.table_name')}</TableCell>
                <TableCell>{p.countryCode}</TableCell>
                <TableCell>{p.ownerPhone || "N/A"}</TableCell>
                <TableCell>
                  <Chip
                    label={p.status === 'ACTIVE' ? t('common.active') : p.status === 'BLOCKED' ? t('common.blocked') : t('common.pending')}
                    color={p.status === 'ACTIVE' ? 'success' : p.status === 'BLOCKED' ? 'error' : 'warning'}
                  />
                </TableCell>
                <TableCell>
                  <Box display="flex" gap={1}>
                    <Button size="small" variant="outlined" onClick={() => navigate(`/admin/partners/${p.id}`)}>
                        Details
                    </Button>
                    {p.status === 'PENDING' && (
                      <Button size="small" variant="contained" color="success" onClick={() => changeStatus(p.id, 'ACTIVE')}>
                        {t('common.approve')}
                      </Button>
                    )}
                    {p.status !== 'BLOCKED' ? (
                      <Button size="small" variant="outlined" color="error" onClick={() => changeStatus(p.id, 'BLOCKED')}>
                        {t('common.block')}
                      </Button>
                    ) : (
                       <Button size="small" variant="outlined" onClick={() => changeStatus(p.id, 'ACTIVE')}>
                        {t('common.unblock')}
                      </Button>
                    )}
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Container>
  );
};