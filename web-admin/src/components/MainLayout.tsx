import React, { useState, useEffect } from 'react';
import {
  AppBar, Box, CssBaseline, Divider, Drawer, IconButton, List, ListItem,
  ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography, Avatar, Menu, MenuItem
} from '@mui/material';
import {
  Menu as MenuIcon,
  Store as StoreIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  People as PeopleIcon,
  AdminPanelSettings as AdminIcon,
  Dashboard as DashboardIcon
} from '@mui/icons-material';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';

const drawerWidth = 240;

export const MainLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  // Определяем роль (из localStorage)
  const workspacesStr = localStorage.getItem('workspaces');
  const workspaces = workspacesStr ? JSON.parse(workspacesStr) : [];

  const isSuperAdmin = workspaces.some((w: any) => w.role === 'PLATFORM_SUPER_ADMIN');
  const isPartner = workspaces.some((w: any) => w.role === 'PARTNER_ADMIN');

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    localStorage.clear();
    navigate('/login');
  };

  const menuItems = [
    // Общее
    { text: 'Профиль', icon: <PersonIcon />, path: '/profile', show: true },

    // Для Партнера
    { text: 'Мои Точки', icon: <StoreIcon />, path: '/dashboard', show: isPartner },
    { text: 'Настройки Лояльности', icon: <SettingsIcon />, path: '/loyalty-settings', show: isPartner },
    { text: 'Персонал', icon: <PeopleIcon />, path: '/staff', show: isPartner },

    // Для Супер-Админа
    { text: 'Все Партнеры', icon: <AdminIcon />, path: '/admin/partners', show: isSuperAdmin },
  ];

  const drawer = (
    <div>
      <Toolbar sx={{ bgcolor: 'primary.main', color: 'white' }}>
        <Typography variant="h6" noWrap component="div">
          LoyaltyLoop
        </Typography>
      </Toolbar>
      <Divider />
      <List>
        {menuItems.filter(item => item.show).map((item) => (
          <ListItem key={item.text} disablePadding>
            <ListItemButton
              selected={location.pathname === item.path}
              onClick={() => navigate(item.path)}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.text} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </div>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      <CssBaseline />

      {/* Верхняя панель */}
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
          bgcolor: 'white',
          color: 'text.primary',
          boxShadow: 1
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { sm: 'none' } }}
          >
            <MenuIcon />
          </IconButton>

          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            Console
          </Typography>

          {/* Аватарка и Меню */}
          <IconButton onClick={handleMenuOpen} sx={{ p: 0 }}>
            <Avatar sx={{ bgcolor: 'primary.main' }}>U</Avatar>
          </IconButton>
          <Menu
            sx={{ mt: '45px' }}
            id="menu-appbar"
            anchorEl={anchorEl}
            anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
            keepMounted
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            <MenuItem onClick={() => { navigate('/profile'); handleMenuClose(); }}>Профиль</MenuItem>
            <MenuItem onClick={handleLogout}>Выйти</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      {/* Боковое меню (Responsive) */}
      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
      >
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      {/* Контент страницы */}
      <Box
        component="main"
        sx={{ flexGrow: 1, p: 3, width: { sm: `calc(100% - ${drawerWidth}px)` }, minHeight: '100vh', bgcolor: '#f5f5f5' }}
      >
        <Toolbar /> {/* Отступ под AppBar */}
        <Outlet /> {/* Сюда будут подставляться страницы */}
      </Box>
    </Box>
  );
};