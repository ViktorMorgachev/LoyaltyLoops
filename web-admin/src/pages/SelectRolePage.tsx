import { useCallback } from 'react';
import { Container, Typography, Paper, List, ListItem, ListItemButton, ListItemText, ListItemAvatar, Avatar } from '@mui/material';
import { Store as StoreIcon, AdminPanelSettings as AdminIcon } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../context/UserContext'; 
import type { Workspace } from '../context/UserContext';
import { useTranslation } from 'react-i18next';
import { usePinVerification } from '../hooks/usePinVerification';

export const SelectRolePage = () => {
  const navigate = useNavigate();
  const { workspaces, selectWorkspace } = useUser(); 
  const { t } = useTranslation();

  const goToWorkspace = useCallback((ws: Workspace) => {
      selectWorkspace(ws);
      
      if (ws.role === 'PLATFORM_SUPER_ADMIN') {
          navigate('/admin/partners');
      } else if (ws.role === 'PARTNER_ADMIN') {
          navigate('/partner/dashboard');
      } else {
          navigate('/dashboard');
      }
  }, [navigate, selectWorkspace]);

  const { requestPinVerification, PinDialog } = usePinVerification(goToWorkspace);

  const handleSelect = (ws: Workspace) => {
      requestPinVerification(ws);
  };

  return (
    <>
    <Container maxWidth="sm" sx={{ mt: 8 }}>
      <Typography variant="h4" gutterBottom align="center">
        {t('select_role.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}>
        {t('select_role.subtitle')}
      </Typography>
      <Paper elevation={3}>
        <List>
          {workspaces.map((ws) => (
            <ListItem key={`${ws.id}-${ws.role}`} disablePadding>
              <ListItemButton onClick={() => handleSelect(ws)}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: ws.role === 'PLATFORM_SUPER_ADMIN' ? 'error.main' : 'primary.main' }}>
                    {ws.role === 'PLATFORM_SUPER_ADMIN' ? <AdminIcon /> : <StoreIcon />}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText 
                    primary={ws.title} 
                    secondary={ws.role.replace('_', ' ')} 
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </Paper>
    </Container>
    {PinDialog}
    </>
  );
};
