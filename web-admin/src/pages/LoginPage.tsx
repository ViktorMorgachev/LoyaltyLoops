import { useState, useEffect } from 'react';
import { api } from '../api/axiosConfig';
import { Button, TextField, Typography, Paper, Box, CircularProgress, InputAdornment } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import PhoneIcon from '@mui/icons-material/Phone';
import LockIcon from '@mui/icons-material/Lock';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useTranslation } from 'react-i18next'; 
import { LanguageSwitcher } from '../components/LanguageSwitcher'; 
import { useUser } from '../context/UserContext';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { MenuItem, Select, FormControl } from '@mui/material';
import { BrandLogo } from '../components/BrandLogo';

const COUNTRY_CODES = [
    { code: 'KG', dial: '+996', mask: '999 99 99 99' },
    { code: 'KZ', dial: '+7', mask: '777 777 77 77' }, // Kazakhstan uses 77...
    { code: 'UZ', dial: '+998', mask: '99 999 99 99' },
    { code: 'RU', dial: '+7', mask: '999 999 99 99' },
    { code: 'BY', dial: '+375', mask: '99 999 99 99' },
];

export const LoginPage = () => {
  const { t } = useTranslation(); 
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { refreshUser } = useUser();

  const [countryIndex, setCountryIndex] = useState(0); // Default KG
  const [rawPhone, setRawPhone] = useState(''); // Only body
  const [code, setCode] = useState('');
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);

  const currentCountry = COUNTRY_CODES[countryIndex];

  const handleCountryChange = (e: any) => {
      setCountryIndex(Number(e.target.value));
      setRawPhone('');
  };

  const handlePhoneInput = (e: React.ChangeEvent<HTMLInputElement>) => {
      // Allow only digits
      const val = e.target.value.replace(/\D/g, '');
      setRawPhone(val);
  };

  const getFullPhone = () => {
      return currentCountry.dial + rawPhone;
  };

  const validatePhone = () => {
      // Simple length validation based on mask (count of '9's)
      // For KZ +7 it can be tricky because RU also +7, but we separate by country select
      const requiredLength = currentCountry.mask.replace(/[^97]/g, '').length;
      if (rawPhone.length !== requiredLength) {
          return false;
      }
      return true;
  };

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
    if (!validatePhone()) {
        showError(t('auth.phone_invalid', 'Invalid phone number format'));
        return;
    }
    setLoading(true);
    try {
      await api.post('/auth/send-code', { phone: getFullPhone() });
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
      const res = await api.post('/auth/login', { phone: getFullPhone(), code });
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
            <Box display="flex" gap={1} mb={2}>
                <FormControl variant="outlined" sx={{ minWidth: 100 }}>
                    <Select
                        value={countryIndex}
                        onChange={handleCountryChange}
                        displayEmpty
                        inputProps={{ 'aria-label': 'Country Code' }}
                    >
                        {COUNTRY_CODES.map((c, idx) => (
                            <MenuItem key={c.code} value={idx}>
                                <Box display="flex" alignItems="center" gap={1}>
                                    <Typography fontWeight="bold">{c.code}</Typography>
                                    <Typography color="text.secondary">{c.dial}</Typography>
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
                <TextField
                    fullWidth 
                    label={t('auth.phone_label')} 
                    variant="outlined"
                    value={rawPhone} 
                    onChange={handlePhoneInput}
                    placeholder={currentCountry.mask.replace(/9|7/g, '_')}
                    InputProps={{ 
                        startAdornment: (<InputAdornment position="start"><PhoneIcon color="action" /></InputAdornment>),
                        inputMode: 'numeric'
                    }}
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
            <Button fullWidth sx={{ mt: 1 }} onClick={() => setStep(1)} disabled={loading}>
              {t('auth.change_number')}
            </Button>
          </Box>
        )}
      </Paper>
    </Box>
  );
};
