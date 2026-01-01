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
  Science as ScienceIcon,
  ListAlt as ListAltIcon,
  RateReview as ReviewsIcon // Added icon
} from '@mui/icons-material';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from './LanguageSwitcher';
import { useUser } from '../context/UserContext';
import { useAppConfig } from '../context/ConfigContext';

const drawerWidth = 240;

export const MainLayout = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { config } = useAppConfig();

  // БЕРЕМ ДАННЫЕ ИЗ КОНТЕКСТА (Они реактивные!)
  const { isPartner, isPartnerAdmin, isSuperAdmin, isSuperManager, isPlatformManager, isNewUser, workspaces, currentWorkspace, logout } = useUser();
  const isPlatformStaff = isSuperAdmin || isPlatformManager || isSuperManager;

  React.useEffect(() => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    if (!token) {
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  const relaxedRoutes = ['/select-role', '/profile', '/about', '/login', '/join/partner', '/join/platform-manager'];
  const isRelaxed = relaxedRoutes.some((route) => location.pathname.startsWith(route));

  React.useEffect(() => {
      if (workspaces.length > 0 && !currentWorkspace && !isRelaxed) {
          navigate('/select-role');
      }
  }, [workspaces, currentWorkspace, isRelaxed, navigate]);

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
        menuItems.push({ text: t('analytics.title'), icon: <ReviewsIcon />, path: '/partner/reviews' }); // Added
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
          { text: t('menu.support_inbox'), icon: <ChatIcon />, path: '/admin/support' },
          { text: t('platform.requests_title'), icon: <ListAltIcon />, path: '/platform/requests' }
      );

      if (isSuperAdmin || isSuperManager) {
          menuItems.push({ text: t('menu.platform_staff', { defaultValue: 'Staff' }), icon: <GroupIcon />, path: '/platform/staff' });
      }

      if (isSuperAdmin || isSuperManager) {
        if (isSuperAdmin && config.features.enableTestSupport) {
            menuItems.push({
              text: t('menu.test_lab'),
              icon: <ScienceIcon />,
              path: '/test-lab'
            });
        }
        menuItems.push({
            text: t('menu.system_events'),
            icon: <ListAltIcon />,
            path: '/admin/events'
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
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Toolbar sx={{ display: 'flex', alignItems: 'center', px: 3, py: 1 }}>
        <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 800, background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
          LoyaltyLoop
        </Typography>
      </Toolbar>
      <Divider sx={{ mx: 2 }} />
      <List sx={{ px: 2, py: 2, flexGrow: 1 }}>
        {menuItems.map((item) => (
          <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
            <ListItemButton
              selected={location.pathname === item.path}
              onClick={() => {
                  navigate(item.path);
                  setMobileOpen(false);
              }}
              sx={{
                borderRadius: 2,
                '&.Mui-selected': {
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  '&:hover': {
                    bgcolor: 'primary.dark',
                  },
                  '& .MuiListItemIcon-root': {
                    color: 'inherit',
                  },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40, color: location.pathname === item.path ? 'inherit' : 'text.secondary' }}>
                  {item.icon}
              </ListItemIcon>
              <ListItemText 
                primary={item.text} 
                primaryTypographyProps={{ fontWeight: location.pathname === item.path ? 600 : 400 }} 
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );

  // Если воркспейс не выбран (и это не relaxed-роут), ничего не рендерим, чтобы не мигали данные.
  if (workspaces.length > 0 && !currentWorkspace && !isRelaxed) {
      return null;
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#f8fafc' }}>
      <CssBaseline />
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
          bgcolor: 'rgba(255, 255, 255, 0.8)',
          backdropFilter: 'blur(8px)',
          color: 'text.primary',
          borderBottom: '1px solid',
          borderColor: 'divider'
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

          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1, fontWeight: 600 }}>
             {t('menu.title')}
          </Typography>

          <LanguageSwitcher />

          <IconButton onClick={handleMenuOpen} sx={{ p: 0, ml: 2 }}>
            <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36, fontSize: '0.9rem' }}>U</Avatar>
          </IconButton>

          <Menu
            sx={{ mt: '45px', '& .MuiPaper-root': { borderRadius: 3, boxShadow: '0px 4px 20px rgba(0,0,0,0.08)' } }}
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
            <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
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
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth, borderRight: 'none', bgcolor: '#f8fafc' },
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth, borderRight: '1px solid', borderColor: 'divider', bgcolor: '#fff' },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{ flexGrow: 1, p: 3, width: { sm: `calc(100% - ${drawerWidth}px)` }, minHeight: '100vh' }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
};
