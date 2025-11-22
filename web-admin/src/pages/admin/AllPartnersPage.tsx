import React, { useEffect, useState } from 'react';
import { api } from '../../api/axiosConfig';
import CircleIcon from '@mui/icons-material/Circle';
import {
  Container, Typography, Paper, Table, TableBody, TableCell, TableHead, TableRow, Chip, Button, Box
} from '@mui/material';

export const AllPartnersPage = () => {
  const [partners, setPartners] = useState<any[]>([]);

  useEffect(() => {
    loadPartners();
  }, []);

  const loadPartners = async () => {
    try {
      const res = await api.get('/admin/partners');
      setPartners(res.data);
    } catch (e) {
      console.error(e);
      alert('Ошибка загрузки партнеров (Вы точно Админ?)');
    }
  };

  const changeStatus = async (id: string, newStatus: string) => {
    try {
      await api.post(`/admin/partners/${id}/status`, { status: newStatus });
      loadPartners(); // Обновляем таблицу
    } catch (e) {
      alert('Ошибка обновления статуса');
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Typography variant="h4" gutterBottom>Управление Партнерами</Typography>

      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Название</TableCell>
              <TableCell>Страна</TableCell>
              <TableCell>Владелец (ID)</TableCell>
              <TableCell>Статус</TableCell>
              <TableCell>Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {partners.map((p) => (
              <TableRow key={p.id}>
                <TableCell>
                                    <Box display="flex" alignItems="center" gap={1}>
                                        <CircleIcon sx={{ color: p.color || '#ccc', fontSize: 16 }} />
                                        {p.name}
                                    </Box>
                                </TableCell>
                <TableCell>{p.countryCode || 'KG'}</TableCell>
                <TableCell>{p.ownerId}</TableCell>
                <TableCell>
                  <Chip
                    label={p.status}
                    color={p.status === 'ACTIVE' ? 'success' : p.status === 'BLOCKED' ? 'error' : 'warning'}
                  />
                </TableCell>
                <TableCell>
                  <Box display="flex" gap={1}>
                    {p.status === 'PENDING' && (
                      <Button size="small" variant="contained" color="success" onClick={() => changeStatus(p.id, 'ACTIVE')}>
                        Одобрить
                      </Button>
                    )}
                    {p.status !== 'BLOCKED' ? (
                      <Button size="small" variant="outlined" color="error" onClick={() => changeStatus(p.id, 'BLOCKED')}>
                        Блок
                      </Button>
                    ) : (
                       <Button size="small" variant="outlined" onClick={() => changeStatus(p.id, 'ACTIVE')}>
                        Разблок
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