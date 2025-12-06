import { Container, Typography, Box, Paper, Button, Stack, useTheme } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useEffect } from 'react';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import PeopleIcon from '@mui/icons-material/People';
import SecurityIcon from '@mui/icons-material/Security';
import StorefrontIcon from '@mui/icons-material/Storefront';
import HandshakeIcon from '@mui/icons-material/Handshake';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MapIcon from '@mui/icons-material/Map';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

export const AboutPage = () => {
    const navigate = useNavigate();
    const { t } = useTranslation();
    const theme = useTheme();

    // Anti-copy & Anti-screenshot (best effort)
    useEffect(() => {
        const preventDefault = (e: Event) => e.preventDefault();

        // Disable context menu (Right click)
        document.addEventListener('contextmenu', preventDefault);
        
        // Disable copy/cut/paste
        document.addEventListener('copy', preventDefault);
        document.addEventListener('cut', preventDefault);
        document.addEventListener('paste', preventDefault);

        // Disable specific shortcuts
        const handleKeyDown = (e: KeyboardEvent) => {
            if (
                e.key === 'PrintScreen' ||
                ((e.ctrlKey || e.metaKey) && (e.key === 'c' || e.key === 'p' || e.key === 's'))
            ) {
                e.preventDefault();
            }
        };
        document.addEventListener('keydown', handleKeyDown);

        return () => {
            document.removeEventListener('contextmenu', preventDefault);
            document.removeEventListener('copy', preventDefault);
            document.removeEventListener('cut', preventDefault);
            document.removeEventListener('paste', preventDefault);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, []);

    const features = [
    {
            icon: <TrendingUpIcon fontSize="large" color="primary" />,
            title: t('landing.features.flexible_title'),
            description: t('landing.features.flexible_desc')
    },
    {
            icon: <PeopleIcon fontSize="large" color="primary" />,
            title: t('landing.features.crm_title'),
            description: t('landing.features.crm_desc')
    },
    {
            icon: <MapIcon fontSize="large" color="primary" />,
            title: t('landing.features.geo_title'),
            description: t('landing.features.geo_desc')
        },
        {
            icon: <SecurityIcon fontSize="large" color="primary" />,
            title: t('landing.features.security_title'),
            description: t('landing.features.security_desc')
        },
        {
            icon: <StorefrontIcon fontSize="large" color="primary" />,
            title: t('landing.features.network_title'),
            description: t('landing.features.network_desc')
    },
    {
            icon: <HandshakeIcon fontSize="large" color="primary" />,
            title: t('landing.features.partner_title'),
            description: t('landing.features.partner_desc')
    }
  ];

  return (
        <Box sx={{ 
            bgcolor: '#f8fafc', 
            minHeight: '100vh', 
            py: 6, 
            position: 'relative',
            userSelect: 'none',
            WebkitUserSelect: 'none',
            MozUserSelect: 'none',
            msUserSelect: 'none',
        }}>
            <Box sx={{ position: 'absolute', top: 16, right: 16 }}>
                <LanguageSwitcher />
            </Box>
            <Container maxWidth="lg">
                <Button 
                    startIcon={<ArrowBackIcon />} 
                    onClick={() => navigate('/', { replace: true })} 
                    sx={{ mb: 4 }}
                >
                    {t('common.details')}
                </Button>

                {/* HERO SECTION */}
                <Box textAlign="center" mb={8}>
                    <Typography variant="h2" component="h1" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                        {t('landing.hero_title')}
                    </Typography>
                    <Typography variant="h5" color="text.secondary" sx={{ mb: 4, maxWidth: 800, mx: 'auto', lineHeight: 1.6 }}>
                        {t('landing.hero_subtitle')}
                    </Typography>
                    <Stack direction="row" spacing={2} justifyContent="center">
                        <Button variant="contained" size="large" onClick={() => navigate('/login', { replace: true })} sx={{ px: 4, py: 1.5, borderRadius: 4, fontSize: '1.1rem' }}>
                            {t('landing.start_free')}
                        </Button>
                        <Button variant="outlined" size="large" onClick={() => navigate('/roadmap')} sx={{ px: 4, py: 1.5, borderRadius: 4, fontSize: '1.1rem' }}>
                            {t('landing.roadmap_btn')}
                        </Button>
                    </Stack>
                </Box>

                {/* FEATURES STACK */}
                <Stack spacing={4}>
                    {features.map((feature, index) => (
                        <Paper 
                            key={index}
                            elevation={0} 
                            sx={{ 
                                p: 4, 
                                width: '100%', // Гарантируем полную ширину
                                borderRadius: 4, 
                                border: '1px solid', 
                                borderColor: 'divider',
                                transition: 'all 0.2s',
                                '&:hover': {
                                    boxShadow: theme.shadows[4],
                                    borderColor: 'primary.main'
                                    // Removed translateY to fix visual shift/width issue
                                }
                            }}
                        >
                            <Box sx={{ mb: 2, p: 1.5, bgcolor: 'primary.50', borderRadius: '12px', display: 'inline-flex' }}>
                                {feature.icon}
                            </Box>
                            <Typography variant="h6" fontWeight="bold" gutterBottom>
                                {feature.title}
                            </Typography>
                            <Typography 
                                variant="body1" 
                                color="text.secondary" 
                                sx={{ 
                                    lineHeight: 1.6,
                                    display: '-webkit-box',
                                    WebkitLineClamp: 3,
                                    WebkitBoxOrient: 'vertical',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                }}
                            >
                                {feature.description}
        </Typography>
      </Paper>
                    ))}
                </Stack>

                {/* CTA SECTION */}
                <Paper sx={{ mt: 8, p: 6, borderRadius: 6, background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', color: 'white', textAlign: 'center' }}>
                    <Typography variant="h4" fontWeight="bold" gutterBottom>
                        {t('landing.cta.title')}
              </Typography>
                    <Typography variant="h6" sx={{ mb: 4, opacity: 0.8, fontWeight: 'normal' }}>
                        {t('landing.cta.subtitle')}
        </Typography>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} justifyContent="center">
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>{t('landing.cta.quick_start')}</Typography>
                         </Box>
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>{t('landing.cta.no_hardware')}</Typography>
                         </Box>
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>{t('landing.cta.trial')}</Typography>
                         </Box>
                    </Stack>
      </Paper>
            </Container>
    </Box>
  );
};
