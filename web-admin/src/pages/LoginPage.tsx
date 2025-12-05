import { useState, useEffect } from 'react';
import { api } from '../api/axiosConfig';
import { Button, TextField, Typography, Paper, Box, CircularProgress, InputAdornment } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import LockIcon from '@mui/icons-material/Lock';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useTranslation } from 'react-i18next'; 
import { LanguageSwitcher } from '../components/LanguageSwitcher'; 
import { useUser } from '../context/UserContext';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { BrandLogo } from '../components/BrandLogo';
import { PhoneInput } from '../components/inputs/PhoneInput';
import { parsePhoneNumber, isValidPhone } from '../utils/phone';

export const LoginPage = () => {
  const { t } = useTranslation(); 
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { refreshUser } = useUser();

  // Stores full phone in E.164 format (e.g. +996555123456)
  const [fullPhone, setFullPhone] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [timer, setTimer] = useState(0);

  // TIMER LOGIC
  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;
    if (timer > 0) {
      interval = setInterval(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [timer]);

  // AUTO-REDIRECT if already logged in
  useEffect(() => {
      const token = localStorage.getItem('accessToken');
      if (token) {
          refreshUser().then(() => {
             navigate('/select-role');
          }).catch(() => {
             // Token invalid, stay here
             localStorage.removeItem('accessToken');
          });
      }
  }, []);

  const handleSendCode = async () => {
    const { rawDigits, country } = parsePhoneNumber(fullPhone);
    if (!isValidPhone(rawDigits, country)) {
        showError(t('auth.phone_invalid', 'Invalid phone number format'));
        return;
    }
    setLoading(true);
    try {
      await api.post('/auth/send-code', { phone: fullPhone });
      showSuccess(t('auth.code_sent'));
      setStep(2);
      setTimer(60); // Start 60s cooldown
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async () => {
    setLoading(true);
    try {
      const res = await api.post('/auth/login', { phone: fullPhone, code });
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
      <Box sx={{ position: 'absolute', top: 16, right: 16, display: 'flex', gap: 2, alignItems: 'center' }}>
        <Button 
            startIcon={<InfoOutlinedIcon />} 
            onClick={() => navigate('/about')} 
            sx={{ color: 'text.secondary', textTransform: 'none' }}
        >
            О проекте
        </Button>
        <LanguageSwitcher />
      </Box>

      <Paper elevation={4} sx={{ p: 4, width: '100%', maxWidth: 400, borderRadius: 3, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Box mb={3}>
          <BrandLogo size={80} />
        </Box>

        <Typography component="h1" variant="h5" fontWeight="bold" gutterBottom>
          {t('auth.title')}
        </Typography>

        {step === 1 ? (
          <Box width="100%">
            <Box mb={2}>
                <PhoneInput 
                    value={fullPhone}
                    onChange={setFullPhone}
                    label={t('auth.phone_label')}
                    size="medium" // Larger for login page
                />
            </Box>
            
            <Button
              fullWidth variant="contained" size="large" sx={{ mt: 1, borderRadius: 2, height: 48 }}
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

            <Button
                fullWidth sx={{ mt: 1 }}
                onClick={handleSendCode}
                disabled={loading || timer > 0}
            >
                {timer > 0 
                    ? `${t('auth.resend_code', { defaultValue: 'Отправить повторно' })} (${timer})` 
                    : t('auth.resend_code', { defaultValue: 'Отправить повторно' })}
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
