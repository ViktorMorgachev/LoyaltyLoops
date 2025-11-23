import React from 'react';
import { Container, Typography, Paper, List, ListItem, ListItemButton, ListItemText, ListItemAvatar, Avatar } from '@mui/material';
import { Store as StoreIcon, AdminPanelSettings as AdminIcon, Person as PersonIcon } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../context/UserContext';

export const SelectRolePage = () => {
  const navigate = useNavigate();
  const { workspaces, selectWorkspace } = useUser(); 

  const handleSelect = (ws: any) => {
      selectWorkspace(ws);
      
      // Роутинг в зависимости от роли
      if (ws.role === 'PLATFORM_SUPER_ADMIN') {
          navigate('/admin/partners');
      } else if (ws.role === 'PARTNER_ADMIN') {
          navigate('/partner/dashboard');
      } else {
          navigate('/dashboard'); // Кассир или что-то еще
      }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 8 }}>
      <Typography variant="h4" gutterBottom align="center">Select Workspace</Typography>
      <Paper elevation={3}>
        <List>
          {workspaces.map((ws: any) => (
            <ListItem key={ws.id} disablePadding>
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
  );
};

