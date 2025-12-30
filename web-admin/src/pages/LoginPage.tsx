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
import { Analytics } from '../utils/analytics';
import TelegramIcon from '@mui/icons-material/Telegram';
import DownloadIcon from '@mui/icons-material/Download';
import { QRCodeSVG } from 'qrcode.react';

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

  // Telegram Auth
  const [telegramAuthMode, setTelegramAuthMode] = useState(false);
  const [telegramUuid, setTelegramUuid] = useState('');
  const [telegramBot, setTelegramBot] = useState('');
  const [telegramStatus, setTelegramStatus] = useState<'PENDING' | 'CONFIRMED' | 'EXPIRED'>('PENDING');

  const [telegramTimer, setTelegramTimer] = useState(0);

  const formatTime = (seconds: number) => {
      const m = Math.floor(seconds / 60);
      const s = seconds % 60;
      return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  // TIMER LOGIC (SMS)
  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;
    if (timer > 0) {
      interval = setInterval(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [timer]);

  // TELEGRAM TIMER LOGIC
  useEffect(() => {
      let interval: any;
      if (telegramAuthMode && telegramTimer > 0) {
          interval = setInterval(() => {
              setTelegramTimer((prev) => prev - 1);
          }, 1000);
      } else if (telegramAuthMode && telegramTimer === 0 && telegramStatus !== 'CONFIRMED') {
          handleStartTelegram();
      }
      return () => clearInterval(interval);
  }, [telegramAuthMode, telegramTimer, telegramStatus]);

  // AUTO-REDIRECT if already logged in
  useEffect(() => {
      // Check first launch
      const hasVisited = localStorage.getItem('hasVisited');
      if (!hasVisited) {
          localStorage.setItem('hasVisited', 'true');
          navigate('/about');
          return;
      }

      const token = localStorage.getItem('accessToken');
      if (token) {
          refreshUser().then(() => {
             navigate('/select-role');
          }).catch(() => {
             // Token invalid, stay here
             localStorage.removeItem('accessToken');
             localStorage.removeItem('currentWorkspaceId');
          });
      } else {
        localStorage.removeItem('currentWorkspaceId');
      }
  }, []);

  // Telegram Polling
  useEffect(() => {
    let interval: any;
    if (telegramAuthMode && telegramUuid && telegramStatus === 'PENDING') {
      interval = setInterval(async () => {
         try {
             const res = await api.get(`/auth/telegram/status/${telegramUuid}`);
             if (res.data.status === 'CONFIRMED' && res.data.auth) {
                 setTelegramStatus('CONFIRMED');
                 await onLoginSuccess(res.data.auth);
             }
         } catch(e: any) {
             if (e.response && e.response.status === 404) {
                 // Session expired - refresh
                 handleStartTelegram();
             } else {
                 console.error(e); // Log other errors but don't stop polling
             }
         }
      }, 2000);
    }
    return () => clearInterval(interval);
  }, [telegramAuthMode, telegramUuid, telegramStatus]);

  const handleSendCode = async () => {
    const { rawDigits, country } = parsePhoneNumber(fullPhone);
    if (!isValidPhone(rawDigits, country)) {
        showError(t('errors.INVALID_PHONE_NUMBER', 'Invalid phone number format'));
        return;
    }
    setLoading(true);
    try {
        Analytics.track('login_send_code');
      await api.post('/auth/send-code', { phone: fullPhone });
      showSuccess(t('auth.code_sent'));
      setStep(2);
      setTimer(60); // Start 60s cooldown
    } catch (e: any) {
        Analytics.track('login_send_code_error', { status: getErrorMessage(e)});
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  const handleStartTelegram = async () => {
      // Don't show full screen loader for background refresh, maybe local loader?
      // But setTelegramUuid will trigger re-render anyway.
      try {
          const res = await api.post('/auth/telegram/start');
          setTelegramUuid(res.data.uuid);
          setTelegramBot(res.data.bot);
          setTelegramAuthMode(true);
          setTelegramStatus('PENDING');
          setTelegramTimer(res.data.expiresIn || 120);
          Analytics.track('login_telegram_start');
      } catch (e: any) {
          showError(getErrorMessage(e));
      }
  };

  async function onLoginSuccess(data: any) {
      const { accessToken, refreshToken, workspaces } = data;
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('workspaces', JSON.stringify(workspaces));

      showSuccess(t('auth.success'));
      Analytics.track('login_success');
      
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
  };

  const handleLogin = async () => {
    setLoading(true);
    try {
      const res = await api.post('/auth/login', { phone: fullPhone, code });
      await onLoginSuccess(res.data);
    } catch (e: any) {
      showError(getErrorMessage(e));
       Analytics.track('login_failed', { error: getErrorMessage(e) });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f0f2f5' }}>

      {/* ПЕРЕКЛЮЧАТЕЛЬ ЯЗЫКА (В правом верхнем углу) */}
      <Box sx={{ position: 'absolute', top: 16, right: 16, display: 'flex', gap: 2, alignItems: 'center' }}>
        <LanguageSwitcher />
      </Box>

      <Paper elevation={4} sx={{ p: 4, width: '100%', maxWidth: 400, borderRadius: 3, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Box mb={3}>
          <BrandLogo size={80} />
        </Box>

        <Typography component="h1" variant="h5" fontWeight="bold" gutterBottom>
          {t('auth.title')}
        </Typography>

        {telegramAuthMode ? (
            <Box width="100%" textAlign="center">
                <Typography variant="body1" gutterBottom sx={{ mb: 2 }}>
                    {t('auth.telegram_scan', 'Scan this code with your phone or click the button below')}
                </Typography>
                
                <Box mb={3} sx={{ p: 2, bgcolor: 'white', display: 'inline-block', borderRadius: 2, border: '1px solid #ddd' }}>
                    <QRCodeSVG value={`https://t.me/${telegramBot}?start=login_${telegramUuid}`} size={200} />
                </Box>
                
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    {t('auth.telegram_expires_in', 'Code expires in {{time}}', { time: formatTime(telegramTimer) })}
                </Typography>

                <Button 
                    href={`https://t.me/${telegramBot}?start=login_${telegramUuid}`} 
                    target="_blank"
                    startIcon={<TelegramIcon />}
                    variant="contained" 
                    fullWidth 
                    size="large"
                    sx={{ mb: 2, borderRadius: 2, bgcolor: '#0088cc', '&:hover': { bgcolor: '#0077b5' } }}
                >
                    {t('auth.telegram_open', 'Open Telegram')}
                </Button>
                
                <Button onClick={() => setTelegramAuthMode(false)} fullWidth sx={{ borderRadius: 2 }}>
                    {t('auth.back_phone', 'Back to Phone')}
                </Button>
            </Box>
        ) : step === 1 ? (
          <Box width="100%">
            <Box mb={2}>
                <PhoneInput 
                    value={fullPhone}
                    onChange={setFullPhone}
                    label={t('auth.phone_label')}
                    size="medium"
                />
            </Box>
            
            <Button
              fullWidth variant="contained" size="large" sx={{ mt: 1, borderRadius: 2, height: 48 }}
              onClick={handleSendCode} disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : t('auth.get_code')}
            </Button>

            <Button
                fullWidth
                variant="outlined"
                size="large"
                startIcon={<TelegramIcon />}
                onClick={handleStartTelegram}
                disabled={loading}
                sx={{ mt: 2, borderRadius: 2, height: 48, textTransform: 'none', fontWeight: 600, borderColor: '#0088cc', color: '#0088cc' }}
            >
                {t('auth.telegram_login', 'Log in with Telegram')}
            </Button>

            <Button 
                fullWidth
                variant="outlined"
                size="large"
                startIcon={<InfoOutlinedIcon />} 
                onClick={() => navigate('/about')} 
                sx={{ 
                    mt: 2, 
                    borderRadius: 2, 
                    textTransform: 'none', 
                    borderWidth: 2,
                    fontWeight: 'bold',
                    '&:hover': { borderWidth: 2 }
                }}
            >
                {t('menu.about_project', 'О проекте')}
            </Button>

            <Button
              fullWidth
              variant="text"
              size="small"
              startIcon={<DownloadIcon />}
              sx={{ mt: 1, textTransform: 'none', fontWeight: 600 }}
              onClick={() => navigate('/download')}
            >
              {t('auth.open_download', 'Перейти на страницу загрузки')}
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

            <Button
              fullWidth
              variant="text"
              size="small"
              startIcon={<DownloadIcon />}
              sx={{ mt: 1, textTransform: 'none', fontWeight: 600 }}
              onClick={() => navigate('/download')}
            >
              {t('auth.open_download', 'Перейти на страницу загрузки')}
            </Button>
          </Box>
        )}
      </Paper>
    </Box>
  );
};
