import { useCallback } from 'react';
import { Container, Typography, Paper, Avatar, Box } from '@mui/material';
import { Store as StoreIcon, AdminPanelSettings as AdminIcon, SupervisorAccount as ManagerIcon, SupportAgent as SupportIcon } from '@mui/icons-material';
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
      } else if (ws.role === 'PLATFORM_SUPER_MANAGER' || ws.role === 'PLATFORM_MANAGER') {
          navigate('/platform/requests');
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

  const getRoleIcon = (role: string) => {
      if (role === 'PLATFORM_SUPER_ADMIN') return <AdminIcon fontSize="large" />;
      if (role === 'PLATFORM_SUPER_MANAGER') return <ManagerIcon fontSize="large" />;
      if (role === 'PLATFORM_MANAGER') return <SupportIcon fontSize="large" />;
      return <StoreIcon fontSize="large" />;
  };

  const getRoleColor = (role: string) => {
      if (role === 'PLATFORM_SUPER_ADMIN') return 'error';
      if (role === 'PLATFORM_SUPER_MANAGER') return 'warning';
      if (role === 'PLATFORM_MANAGER') return 'info';
      return 'primary';
  };

  return (
    <>
    <Container maxWidth="md" sx={{ mt: 8 }}>
      <Box textAlign="center" mb={6}>
          <Typography variant="h3" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
        {t('select_role.title')}
      </Typography>
          <Typography variant="h6" color="text.secondary">
        {t('select_role.subtitle')}
      </Typography>
      </Box>

      <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={3}>
          {workspaces.map((ws) => {
             const color = getRoleColor(ws.role);
             return (
            <Paper
                key={`${ws.id}-${ws.role}`}
                elevation={0}
                onClick={() => handleSelect(ws)}
                sx={{
                    p: 3,
                    borderRadius: 4,
                    border: '1px solid',
                    borderColor: 'divider',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    '&:hover': {
                        transform: 'translateY(-4px)',
                        boxShadow: '0 12px 24px -8px rgba(0, 0, 0, 0.15)',
                        borderColor: `${color}.main`
                    },
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2
                }}
            >
                <Avatar 
                    sx={{ 
                        width: 56, 
                        height: 56, 
                        bgcolor: `${color}.light`,
                        color: `${color}.main`
                    }}
                >
                    {getRoleIcon(ws.role)}
                  </Avatar>
                <Box>
                    <Typography variant="h6" fontWeight="bold">
                        {ws.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ textTransform: 'capitalize' }}>
                        {ws.role.replace(/_/g, ' ').toLowerCase()}
                    </Typography>
                </Box>
      </Paper>
          )})}
      </Box>
    </Container>
    {PinDialog}
    </>
  );
};
