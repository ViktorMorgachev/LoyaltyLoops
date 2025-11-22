import React, { useState, useEffect } from 'react';
import {
  Container, Button, Box, Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api } from '../../api/axiosConfig';

export const PartnerDashboardPage = () => {
  const [points, setPoints] = useState<any[]>([]);
  const [open, setOpen] = useState(false);

  // Поля формы создания
  const [name, setName] = useState('');
  const [type, setType] = useState('COFFEE_SHOP');

  // Настройки лояльности
  const [strategy, setStrategy] = useState('TIERED_LTV'); // или VISIT_COUNTER
  const [visitsTarget, setVisitsTarget] = useState('6'); // Цель визитов
  const [cashback, setCashback] = useState('5'); // % кешбэка

  useEffect(() => {
    loadPoints();
  }, []);

  const loadPoints = async () => {
    try {
      const res = await api.get('/partners/points');
      setPoints(res.data);
    } catch (e) {
      console.error(e);
    }
  };

  const handleCreate = async () => {
    try {
      // Формируем запрос согласно нашему DTO на сервере
      const payload = {
        name,
        type,
        programType: strategy,
        // Если выбраны визиты - шлем цель, иначе null
        visitsTarget: strategy === 'VISIT_COUNTER' ? parseInt(visitsTarget) : null,
        // Если выбран кешбэк - шлем процент (делим на 100, т.к. сервер ждет 0.05)
        baseCashback: strategy === 'TIERED_LTV' ? parseInt(cashback) / 100.0 : null
      };

      await api.post('/partners/points', payload);

      setOpen(false);
      setName('');
      loadPoints(); // Обновляем список
    } catch (e) {
      alert('Ошибка создания точки');
    }
  };

  const copyInvite = (code: string) => {
    navigator.clipboard.writeText(code);
    alert('Код скопирован: ' + code);
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Мои Торговые Точки</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          Добавить филиал
        </Button>
      </Box>

      <Paper elevation={2}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Название</TableCell>
              <TableCell>Тип</TableCell>
              <TableCell>Стратегия</TableCell>
              <TableCell>Инвайт (для кассира)</TableCell>
              <TableCell>Статус</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {points.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">Нет точек. Добавьте первую!</TableCell>
              </TableRow>
            )}
            {points.map((p) => (
              <TableRow key={p.id}>
                <TableCell>{p.name}</TableCell>
                <TableCell>{p.type}</TableCell>
                <TableCell>
                   <Chip
                     label={p.programType === 'VISIT_COUNTER' ? 'Штампы' : 'Кешбэк'}
                     color="info"
                     size="small"
                     variant="outlined"
                   />
                </TableCell>
                <TableCell>
                  {p.inviteCode ? (
                    <Chip
                      label={p.inviteCode}
                      onClick={() => copyInvite(p.inviteCode)}
                      icon={<ContentCopyIcon />}
                      color="primary"
                    />
                  ) : "—"}
                </TableCell>
                <TableCell>
                  <Chip
                    label={p.active ? "Работает" : "Не оплачена"}
                    color={p.active ? "success" : "error"}
                    size="small"
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {/* МОДАЛКА СОЗДАНИЯ */}
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Новый филиал</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              label="Название"
              value={name}
              onChange={e => setName(e.target.value)}
              fullWidth
              placeholder="Например: Филиал Центр"
            />

            <FormControl fullWidth>
                <InputLabel>Тип заведения</InputLabel>
                <Select value={type} label="Тип заведения" onChange={e => setType(e.target.value)}>
                    <MenuItem value="COFFEE_SHOP">Кофейня</MenuItem>
                    <MenuItem value="RESTAURANT">Ресторан</MenuItem>
                    <MenuItem value="RETAIL">Магазин</MenuItem>
                    <MenuItem value="SERVICE">Услуги</MenuItem>
                    <MenuItem value="OTHER">Другое</MenuItem>
                </Select>
            </FormControl>

            <FormControl fullWidth>
                <InputLabel>Стратегия Лояльности</InputLabel>
                <Select value={strategy} label="Стратегия Лояльности" onChange={e => setStrategy(e.target.value)}>
                    <MenuItem value="TIERED_LTV">Накопительная (Кешбэк)</MenuItem>
                    <MenuItem value="VISIT_COUNTER">Счетчик (N-й в подарок)</MenuItem>
                </Select>
            </FormControl>

            {/* АДАПТИВНЫЕ ПОЛЯ */}
            {strategy === 'VISIT_COUNTER' ? (
                <TextField
                    label="Цель (сколько собрать?)"
                    type="number"
                    value={visitsTarget}
                    onChange={e => setVisitsTarget(e.target.value)}
                    helperText="Например: 6 (клиент покупает 5, 6-й бесплатно)"
                />
            ) : (
                <TextField
                    label="Начальный Кешбэк (%)"
                    type="number"
                    value={cashback}
                    onChange={e => setCashback(e.target.value)}
                    helperText="Например: 5 (клиент получает 5% баллами)"
                />
            )}

        </DialogContent>
        <DialogActions>
            <Button onClick={() => setOpen(false)}>Отмена</Button>
            <Button variant="contained" onClick={handleCreate}>Создать</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};