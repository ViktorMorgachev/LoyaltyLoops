import { useState } from 'react';
import { Container, Paper, Typography, TextField, Button, Box, Avatar, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { api } from '../../api/axiosConfig';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../../context/UserContext';
import { useTranslation } from 'react-i18next';
import { useNotification } from '../../context/NotificationContext';
import { getErrorMessage } from '../../utils/errorHandler';
import StorefrontTwoToneIcon from '@mui/icons-material/StorefrontTwoTone';

export const CreateBusinessPage = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { refreshUser } = useUser();

  const [name, setName] = useState('');
  const [baseCurrency, setBaseCurrency] = useState('USD');
  const [color, setColor] = useState('#4F46E5');
  const [logo, setLogo] = useState('');
  const [pin, setPin] = useState('');
  const [pinConfirm, setPinConfirm] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCreate = async () => {
    if (!name) return showError(t('dashboard.modal_biz_name_required'));
    if (pin.length < 4 || !/^\d+$/.test(pin)) {
        return showError(t('dashboard.pin_length_error'));
    }
    if (pin !== pinConfirm) {
        return showError(t('dashboard.pin_confirm_error'));
    }

    setLoading(true);
    try {
      await api.post('/partners/create', {
        businessName: name,
        countryCode: "KG",
        ownerPin: pin,
        color: color,
        logoUrl: logo || null,
        baseCurrency: baseCurrency
      });

      showSuccess(t('common.create') + " OK");
      await refreshUser();
      navigate('/partner/dashboard');
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 8, mb: 8 }}>
      <Paper elevation={0} sx={{ p: 5, borderRadius: 5, border: '1px solid', borderColor: 'divider', textAlign: 'center' }}>
        <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main', width: 64, height: 64, mx: 'auto', mb: 3 }}>
            <StorefrontTwoToneIcon fontSize="large" />
        </Avatar>

        <Typography variant="h4" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {t('dashboard.create_business_title')}
        </Typography>
        <Typography color="text.secondary" paragraph sx={{ mb: 4, maxWidth: 400, mx: 'auto' }}>
          {t('dashboard.create_business_subtitle')}
        </Typography>

        <Box component="form" noValidate autoComplete="off">
        <TextField
          label={t('dashboard.modal_biz_name')}
              fullWidth 
              variant="outlined"
          value={name} onChange={(e) => setName(e.target.value)}
              sx={{ mb: 3 }}
        />

        <FormControl fullWidth sx={{ mb: 3 }}>
            <InputLabel>{t('dashboard.label_base_currency', 'Base Currency')}</InputLabel>
            <Select
                value={baseCurrency}
                label={t('dashboard.label_base_currency', 'Base Currency')}
                onChange={(e) => setBaseCurrency(e.target.value)}
            >
                <MenuItem value="USD">USD (Доллар)</MenuItem>
                <MenuItem value="KGS">KGS (Сом)</MenuItem>
                <MenuItem value="KZT">KZT (Тенге)</MenuItem>
                <MenuItem value="UZS">UZS (Сум)</MenuItem>
                <MenuItem value="BYN">BYN (Бел. рубль)</MenuItem>
            </Select>
        </FormControl>

            <Box mb={3} textAlign="left">
                 <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>{t('settings.color_label')}</Typography>
                 <Box 
                    sx={{ 
                        display: 'flex', 
                        alignItems: 'center', 
                        gap: 2, 
                        p: 1, 
                        border: '1px solid', 
                        borderColor: 'rgba(0, 0, 0, 0.23)', 
                        borderRadius: 1,
                        mt: 0.5
                    }}
                 >
             <input
                  type="color"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                          style={{ width: '40px', height: '40px', cursor: 'pointer', border: 'none', padding: 0, backgroundColor: 'transparent' }}
             />
                     <Typography variant="body2" color="text.primary">{color}</Typography>
                 </Box>
        </Box>

        <TextField
          label={t('dashboard.modal_biz_logo')}
              fullWidth 
          value={logo} onChange={(e) => setLogo(e.target.value)}
          placeholder="https://..."
              sx={{ mb: 3 }}
        />

            <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
        <TextField
          label={t('dashboard.pin_label')}
          type="password"
          fullWidth
          value={pin}
          onChange={(e) => setPin(e.target.value.replaceAll(" ", ""))}
          required
          autoComplete="new-password"
          inputProps={{
            maxLength: 12,
            inputMode: 'numeric',
            pattern: '[0-9]*'
          }}
        />
        <TextField
          label={t('dashboard.pin_confirm_label')}
          type="password"
          fullWidth
          value={pinConfirm}
          onChange={(e) => setPinConfirm(e.target.value.replaceAll(" ", ""))}
          required
          autoComplete="new-password"
          inputProps={{
            maxLength: 12,
            inputMode: 'numeric',
            pattern: '[0-9]*'
          }}
        />
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, textAlign: 'left' }}>
                {t('dashboard.pin_hint')}
            </Typography>

        <Button
              variant="contained" 
              fullWidth 
              size="large" 
              sx={{ mt: 4, borderRadius: 3, py: 1.5, fontSize: '1.1rem', fontWeight: 'bold' }}
          onClick={handleCreate} disabled={loading}
        >
          {loading ? t('common.loading') : t('dashboard.create_business_btn')}
        </Button>
        </Box>
      </Paper>
    </Container>
  );
};