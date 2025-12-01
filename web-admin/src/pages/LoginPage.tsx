import { useState } from 'react';
import { api } from '../api/axiosConfig';
import { Button, TextField, Typography, Paper, Box, CircularProgress, InputAdornment } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import PhoneIcon from '@mui/icons-material/Phone';
import LockIcon from '@mui/icons-material/Lock';
import LoyaltyIcon from '@mui/icons-material/Loyalty';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useTranslation } from 'react-i18next'; 
import { LanguageSwitcher } from '../components/LanguageSwitcher'; 
import { useUser } from '../context/UserContext';

export const LoginPage = () => {
  const { t } = useTranslation(); 
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { refreshUser } = useUser(); // <-- Get refreshUser

  const [phone, setPhone] = useState('+996');
  const [code, setCode] = useState('');
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);

  const handleSendCode = async () => {
    setLoading(true);
    try {
      await api.post('/auth/send-code', { phone });
      showSuccess(t('auth.code_sent'));
      setStep(2);
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async () => {
    setLoading(true);
    try {
      const res = await api.post('/auth/login', { phone, code });
      const { accessToken, refreshToken, workspaces } = res.data;
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('workspaces', JSON.stringify(workspaces));

      showSuccess(t('auth.success'));
      
      // Умный редирект
      if (workspaces.length > 1) {
          await refreshUser(); // Update context before redirect!
          navigate('/select-role');
      } else if (workspaces.length === 1) {
          // Автовыбор единственной роли
          localStorage.setItem('currentWorkspace', JSON.stringify(workspaces[0]));
          
          await refreshUser(); // Update context!
          
          const role = workspaces[0].role;
          if (role === 'PLATFORM_SUPER_ADMIN') {
              navigate('/admin/partners');
          } else if (role === 'PARTNER_ADMIN') {
              navigate('/partner/dashboard');
          } else {
              navigate('/dashboard');
          }
      } else {
          // Нет ролей (Новый юзер)
          await refreshUser();
          navigate('/partner/onboarding');
      }
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f0f2f5' }}>

      {/* ПЕРЕКЛЮЧАТЕЛЬ ЯЗЫКА (В правом верхнем углу) */}
      <Box sx={{ position: 'absolute', top: 16, right: 16 }}>
        <LanguageSwitcher />
      </Box>

      <Paper elevation={4} sx={{ p: 4, width: '100%', maxWidth: 400, borderRadius: 3, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Box sx={{ bgcolor: 'primary.light', p: 2, borderRadius: '50%', mb: 2, color: 'primary.contrastText' }}>
          <LoyaltyIcon fontSize="large" />
        </Box>

        <Typography component="h1" variant="h5" fontWeight="bold" gutterBottom>
          {t('auth.title')}
        </Typography>

        {step === 1 ? (
          <Box width="100%">
            <TextField
              fullWidth label={t('auth.phone_label')} variant="outlined"
              value={phone} onChange={(e) => setPhone(e.target.value)}
              InputProps={{ startAdornment: (<InputAdornment position="start"><PhoneIcon color="action" /></InputAdornment>) }}
            />
            <Button
              fullWidth variant="contained" size="large" sx={{ mt: 3, borderRadius: 2 }}
              onClick={handleSendCode} disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : t('auth.get_code')}
            </Button>
          </Box>
        ) : (
          <Box width="100%">
            <TextField
              fullWidth label={t('auth.code_label')} variant="outlined"
              value={code} onChange={(e) => setCode(e.target.value)}
              InputProps={{ startAdornment: (<InputAdornment position="start"><LockIcon color="action" /></InputAdornment>) }}
            />
            <Button
              fullWidth variant="contained" size="large" sx={{ mt: 3, borderRadius: 2 }}
              onClick={handleLogin} disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : t('auth.login_btn')}
            </Button>
            <Button fullWidth sx={{ mt: 1 }} onClick={() => setStep(1)} disabled={loading}>
              {t('auth.change_number')}
            </Button>
          </Box>
        )}
      </Paper>
    </Box>
  );
};
