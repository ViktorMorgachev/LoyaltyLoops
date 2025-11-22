import React, { useEffect, useState } from 'react';
import { api } from '../api/axiosConfig';
import { useNavigate } from 'react-router-dom';
import {
  Container, Typography, Button, Box, Paper, TextField,
  Table, TableBody, TableCell, TableHead, TableRow, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions,
  AppBar, Toolbar, IconButton, FormControl, InputLabel, Select, MenuItem
} from '@mui/material';
import LogoutIcon from '@mui/icons-material/Logout';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';

export const DashboardPage = () => {
  const navigate = useNavigate();

  // Состояние
  const [loading, setLoading] = useState(true);
  const [isPartner, setIsPartner] = useState(false);
  const [points, setPoints] = useState<any[]>([]);
  const [userName, setUserName] = useState('');

  // Формы
  const [openBusinessDialog, setOpenBusinessDialog] = useState(false);
  const [openPointDialog, setOpenPointDialog] = useState(false);

  const [bizName, setBizName] = useState('');
  const [pointName, setPointName] = useState('');
  const [pointType, setPointType] = useState('COFFEE_SHOP');

  // 1. Инициализация: Узнаем кто мы
  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      const res = await api.get('/client/me');
      const profile = res.data;
      setUserName(profile.firstName || 'Пользователь');

      // Проверяем, есть ли роль Владельца
      const partnerWorkspace = profile.workspaces.find((w: any) => w.role === 'PARTNER_ADMIN');

      if (partnerWorkspace) {
        setIsPartner(true);
        loadPoints(); // Если партнер - грузим точки
      } else {
        setIsPartner(false);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const loadPoints = async () => {
    try {
      const res = await api.get('/partner/points');
      setPoints(res.data);
    } catch (e) {
      console.error("Error loading points", e);
    }
  };

  // --- ДЕЙСТВИЯ ---

  const handleLogout = () => {
    localStorage.clear();
    navigate('/login');
  };

  const handleCreateBusiness = async () => {
    try {
      await api.post('/partner/create', {
        businessName: bizName,
        countryCode: "KG" // Пока хардкод
      });
      setOpenBusinessDialog(false);
      alert('Бизнес успешно создан!');
      // Перезагружаем профиль, чтобы увидеть новые права
      loadProfile();
    } catch (e) {
      alert('Ошибка создания бизнеса');
    }
  };

  const handleCreatePoint = async () => {
    try {
      await api.post('/partner/points', {
        name: pointName,
        type: pointType
      });
      setOpenPointDialog(false);
      setPointName('');
      loadPoints(); // Обновляем таблицу
    } catch (e) {
      alert('Ошибка создания точки');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    alert('Скопировано: ' + text);
  };

  if (loading) return <Typography sx={{p: 4}}>Загрузка...</Typography>;

  return (
    <Box sx={{ flexGrow: 1, bgcolor: '#f5f5f5', minHeight: '100vh' }}>
      {/* ВЕРХНЯЯ ПАНЕЛЬ */}
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1, fontWeight: 'bold' }}>
            LoyaltyLoop Admin
          </Typography>
          <Typography sx={{ mr: 2 }}>{userName}</Typography>
          <IconButton onClick={handleLogout} color="inherit">
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ mt: 4 }}>

        {/* СЦЕНАРИЙ 1: ЕЩЕ НЕ ПАРТНЕР */}
        {!isPartner ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Typography variant="h4" gutterBottom>Запустите свою систему лояльности</Typography>
            <Typography color="textSecondary" paragraph>
              У вас пока нет зарегистрированного бизнеса. Создайте его за пару секунд.
            </Typography>
            <Button variant="contained" size="large" onClick={() => setOpenBusinessDialog(true)}>
              Создать Бизнес
            </Button>
          </Paper>
        ) : (
          // СЦЕНАРИЙ 2: ДАШБОРД ВЛАДЕЛЬЦА
          <>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
              <Typography variant="h5">Мои торговые точки</Typography>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setOpenPointDialog(true)}
              >
                Добавить филиал
              </Button>
            </Box>

            <Paper elevation={2}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Название</TableCell>
                    <TableCell>Тип</TableCell>
                    <TableCell>Инвайт для Кассира</TableCell>
                    <TableCell>Статус</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {points.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} align="center">Нет точек. Создайте первую!</TableCell>
                    </TableRow>
                  )}
                  {points.map((point) => (
                    <TableRow key={point.id}>
                      <TableCell>{point.name}</TableCell>
                      <TableCell>{point.type}</TableCell>
                      <TableCell>
                        {point.inviteCode ? (
                          <Chip
                            label={point.inviteCode}
                            color="primary"
                            variant="outlined"
                            onClick={() => copyToClipboard(point.inviteCode!!)}
                            icon={<ContentCopyIcon />}
                            sx={{ cursor: 'pointer' }}
                          />
                        ) : "—"}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={point.active ? "Активна" : "Не оплачена"}
                          color={point.active ? "success" : "error"}
                          size="small"
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </>
        )}

        {/* МОДАЛКА: СОЗДАНИЕ БИЗНЕСА */}
        <Dialog open={openBusinessDialog} onClose={() => setOpenBusinessDialog(false)}>
          <DialogTitle>Регистрация Компании</DialogTitle>
          <DialogContent sx={{ width: 400 }}>
            <TextField
              autoFocus
              margin="dense"
              label="Название бизнеса"
              fullWidth
              value={bizName}
              onChange={(e) => setBizName(e.target.value)}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpenBusinessDialog(false)}>Отмена</Button>
            <Button onClick={handleCreateBusiness} variant="contained">Создать</Button>
          </DialogActions>
        </Dialog>

        {/* МОДАЛКА: СОЗДАНИЕ ТОЧКИ */}
        <Dialog open={openPointDialog} onClose={() => setOpenPointDialog(false)}>
          <DialogTitle>Новая Торговая Точка</DialogTitle>
          <DialogContent sx={{ width: 400, display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              autoFocus
              label="Название филиала"
              fullWidth
              value={pointName}
              onChange={(e) => setPointName(e.target.value)}
              placeholder="Например: Центр, ЦУМ..."
            />
            <FormControl fullWidth>
              <InputLabel>Тип заведения</InputLabel>
              <Select
                value={pointType}
                label="Тип заведения"
                onChange={(e) => setPointType(e.target.value)}
              >
                <MenuItem value="COFFEE_SHOP">Кофейня</MenuItem>
                <MenuItem value="RESTAURANT">Ресторан</MenuItem>
                <MenuItem value="RETAIL">Магазин</MenuItem>
                <MenuItem value="SERVICE">Услуги</MenuItem>
                <MenuItem value="OTHER">Другое</MenuItem>
              </Select>
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpenPointDialog(false)}>Отмена</Button>
            <Button onClick={handleCreatePoint} variant="contained">Добавить</Button>
          </DialogActions>
        </Dialog>

      </Container>
    </Box>
  );
};