import { Box, Typography, Button, Paper, Avatar } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';

export const NotFoundPage = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <Box minHeight="100vh" display="flex" alignItems="center" justifyContent="center" bgcolor="#f8fafc" px={2}>
      <Paper elevation={0} sx={{ p: 5, textAlign: 'center', maxWidth: 480, borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
        <Avatar sx={{ bgcolor: 'grey.100', color: 'grey.500', width: 80, height: 80, mx: 'auto', mb: 3 }}>
            <QuestionMarkIcon fontSize="large" />
        </Avatar>
        
        <Typography variant="h2" fontWeight="900" sx={{ background: 'linear-gradient(45deg, #64748b 30%, #94a3b8 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', mb: 1 }}>
          404
        </Typography>
        <Typography variant="h5" gutterBottom fontWeight="bold">
          {t('not_found.title')}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
          {t('not_found.subtitle')}
        </Typography>
        <Box display="flex" gap={2} justifyContent="center">
          <Button variant="contained" onClick={() => navigate('/profile')} sx={{ borderRadius: 2, px: 3 }}>
            {t('not_found.to_profile')}
          </Button>
          <Button variant="outlined" onClick={() => navigate('/login')} sx={{ borderRadius: 2, px: 3 }}>
            {t('not_found.to_login')}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

