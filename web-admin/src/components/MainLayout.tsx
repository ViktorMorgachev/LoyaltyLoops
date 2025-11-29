import React, { useState } from 'react';
import {
  AppBar, Box, CssBaseline, Divider, Drawer, IconButton, List, ListItem,
  ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography, Avatar, Menu, MenuItem
} from '@mui/material';
import {
  Menu as MenuIcon,
  Store as StoreIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  AdminPanelSettings as AdminIcon,
  AddBusiness as AddBusinessIcon,
  History as HistoryIcon,
  Dashboard as DashboardIcon,
  Group as GroupIcon,
  Info as InfoIcon,
  ChatBubbleOutline as ChatIcon,
  Science as ScienceIcon
} from '@mui/icons-material';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from './LanguageSwitcher';
import { useUser } from '../context/UserContext'; // <-- ИСПОЛЬЗУЕМ КОНТЕКСТ

const drawerWidth = 240;

export const MainLayout = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  // БЕРЕМ ДАННЫЕ ИЗ КОНТЕКСТА (Они реактивные!)
  const { isPartner, isPartnerAdmin, isSuperAdmin, isPlatformManager, isNewUser, workspaces, currentWorkspace, logout } = useUser();
  const isPlatformStaff = isSuperAdmin || isPlatformManager;

  React.useEffect(() => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    if (!token) {
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  React.useEffect(() => {
      const relaxedRoutes = ['/select-role', '/profile', '/about', '/login', '/join/partner', '/join/platform-manager'];
      const isRelaxed = relaxedRoutes.some((route) => location.pathname.startsWith(route));
      if (workspaces.length > 0 && !currentWorkspace && !isRelaxed) {
          navigate('/select-role');
      }
  }, [workspaces, currentWorkspace, location.pathname, navigate]);

  const [mobileOpen, setMobileOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

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
    logout();
  };

  const menuItems = [
    {
      text: t('menu.profile'),
      icon: <PersonIcon />,
      path: '/profile'
    },
    {
      text: t('menu.about_project'),
      icon: <InfoIcon />,
      path: '/about'
    }
  ];

  if (isNewUser) {
      menuItems.push({
          text: t('menu.create_business'),
          icon: <AddBusinessIcon />,
          path: '/partner/onboarding'
      });
  }

  if (isPartner) {
      menuItems.push(
          { text: t('menu.dashboard'), icon: <DashboardIcon />, path: '/partner/dashboard' },
          { text: t('menu.my_points'), icon: <StoreIcon />, path: '/partner/points' }
      );

      if (isPartnerAdmin) {
        menuItems.push({ text: t('menu.staff'), icon: <GroupIcon />, path: '/partner/staff' });
        menuItems.push({ text: t('menu.support_chat'), icon: <ChatIcon />, path: '/partner/support' });
      }

      menuItems.push({ text: t('menu.transactions'), icon: <HistoryIcon />, path: '/partner/transactions' });

      if (isPartnerAdmin) {
        menuItems.push({ text: t('menu.settings'), icon: <SettingsIcon />, path: '/partner/settings' });
      }
  }

  if (isPlatformStaff) {
      menuItems.push(
          { text: t('menu.admin_partners'), icon: <AdminIcon />, path: '/admin/partners' },
          { text: t('menu.support_inbox'), icon: <ChatIcon />, path: '/admin/support' }
      );

      if (isSuperAdmin) {
        menuItems.push({
          text: t('menu.test_lab'),
          icon: <ScienceIcon />,
          path: '/test-lab'
        });
      }
  }

  if (workspaces.length > 1) {
      menuItems.push({
          text: t('menu.switch_role'),
          icon: <StoreIcon />, // TODO: Better icon
          path: '/select-role'
      });
  }

  const drawer = (
    <div>
      <Toolbar sx={{ bgcolor: 'primary.main', color: 'white' }}>
        <Typography variant="h6" noWrap component="div">
          LoyaltyLoop
        </Typography>
      </Toolbar>
      <Divider />
      <List>
        {menuItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <ListItemButton
              selected={location.pathname === item.path}
              onClick={() => {
                  navigate(item.path);
                  setMobileOpen(false);
              }}
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
             {t('menu.title')}
          </Typography>

          <LanguageSwitcher />

          <IconButton onClick={handleMenuOpen} sx={{ p: 0, ml: 2 }}>
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
            <MenuItem onClick={() => { navigate('/profile'); handleMenuClose(); }}>
                {t('menu.profile')}
            </MenuItem>
            <MenuItem onClick={handleLogout}>
                {t('menu.logout')}
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

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

      <Box
        component="main"
        sx={{ flexGrow: 1, p: 3, width: { sm: `calc(100% - ${drawerWidth}px)` }, minHeight: '100vh', bgcolor: '#f5f5f5' }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
};
