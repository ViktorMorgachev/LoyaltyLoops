import { Box, Container, Typography, Button, Stack, useTheme, Paper, alpha, Chip, Link } from '@mui/material';
import AndroidIcon from '@mui/icons-material/Android';
import AppleIcon from '@mui/icons-material/Apple';
import ShopIcon from '@mui/icons-material/Shop'; // Google Play approximation
import { useTranslation } from 'react-i18next';
import { BrandLogo } from '../components/BrandLogo';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import GetAppIcon from '@mui/icons-material/GetApp'; // Fallback for RuStore if custom icon not used
import { useEffect, useState } from 'react';

export const DownloadPage = () => {
    const { t } = useTranslation();
    const theme = useTheme();
    
    // TODO: Replace with actual APK link when hosted
    const apkUrl = "/LoyaltyLoop.apk"; 
    const [apkSize, setApkSize] = useState<string | null>(null);
    const appVersion = __APP_VERSION__; // Injected by Vite from build.gradle.kts

    useEffect(() => {
        const fetchSize = async () => {
            try {
                const response = await fetch(apkUrl, { method: 'HEAD' });
                if (response.ok) {
                    const length = response.headers.get('Content-Length');
                    if (length) {
                        const bytes = parseInt(length, 10);
                        const mb = (bytes / (1024 * 1024)).toFixed(1);
                        setApkSize(`~${mb} MB`);
                    }
                }
            } catch (e) {
                console.error("Failed to fetch APK size", e);
            }
        };
        fetchSize();
    }, []);

    const steps = [
        {
            icon: <SmartphoneIcon fontSize="large" color="primary" />,
            title: t('client_onboarding.step_1'),
            desc: t('client_onboarding.download_title')
        },
        {
            icon: <QrCodeScannerIcon fontSize="large" color="primary" />,
            title: t('client_onboarding.step_2'),
            desc: t('auth.phone_label')
        },
        {
            icon: <CardGiftcardIcon fontSize="large" color="primary" />,
            title: t('client_onboarding.step_3'),
            desc: t('landing.benefits_title')
        }
    ];

    return (
        <Box sx={{ 
            minHeight: '100vh', 
            bgcolor: 'background.default',
            display: 'flex',
            flexDirection: 'column'
        }}>
            {/* Header */}
            <Box sx={{ 
                p: 2, 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                bgcolor: 'background.paper',
                boxShadow: 1
            }}>
                <Box display="flex" alignItems="center" gap={1}>
                    <BrandLogo size={40} />
                    <Typography variant="h6" fontWeight="bold" sx={{ display: { xs: 'none', sm: 'block' } }}>
                        LoyaltyLoop
                    </Typography>
                </Box>
                <LanguageSwitcher />
            </Box>

            {/* Hero Section */}
            <Box sx={{ 
                flex: 1,
                background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.1)} 0%, ${alpha(theme.palette.background.default, 1)} 100%)`,
                pt: 6,
                pb: 8
            }}>
                <Container maxWidth="sm">
                    <Stack spacing={4} alignItems="center" textAlign="center">
                        
                        {/* App Icon / Logo Large */}
                        <Paper 
                            elevation={6}
                            sx={{ 
                                width: 120, 
                                height: 120, 
                                borderRadius: 4, 
                                display: 'flex', 
                                alignItems: 'center', 
                                justifyContent: 'center',
                                bgcolor: 'white',
                                mb: 2
                            }}
                        >
                            <BrandLogo size={80} />
                        </Paper>

                        <Box>
                            <Typography variant="h4" component="h1" fontWeight="900" gutterBottom>
                                {t('client_onboarding.welcome_title')}
                            </Typography>
                            <Typography variant="body1" color="text.secondary" sx={{ fontSize: '1.1rem' }}>
                                {t('client_onboarding.welcome_subtitle')}
                            </Typography>
                        </Box>

                        {/* Download Buttons */}
                        <Stack spacing={2} width="100%">
                            <Button 
                                variant="contained" 
                                size="large" 
                                startIcon={<AndroidIcon />}
                                href={apkUrl}
                                download
                                sx={{ 
                                    py: 1.8, 
                                    borderRadius: 3, 
                                    fontSize: '1.1rem', 
                                    fontWeight: 'bold',
                                    boxShadow: theme.shadows[4]
                                }}
                            >
                                {t('client_onboarding.download_apk')}
                            </Button>
                            
                            <Stack direction="row" spacing={2} width="100%" justifyContent="center">
                                <Box flex={1}>
                                    <Button 
                                        variant="outlined" 
                                        fullWidth 
                                        size="large"
                                        startIcon={<ShopIcon />}
                                        disabled
                                        sx={{ borderRadius: 3, py: 1.5, position: 'relative', height: '100%' }}
                                    >
                                        <Stack alignItems="center">
                                            <Typography variant="caption" lineHeight={1} sx={{fontSize: '0.65rem'}}>Google Play</Typography>
                                            <Typography variant="body2" fontWeight="bold" sx={{fontSize: '0.75rem'}}>{t('client_onboarding.soon')}</Typography>
                                        </Stack>
                                    </Button>
                                </Box>
                                <Box flex={1}>
                                    <Button 
                                        variant="outlined" 
                                        fullWidth 
                                        size="large"
                                        startIcon={<AppleIcon />}
                                        disabled
                                        sx={{ borderRadius: 3, py: 1.5, height: '100%' }}
                                    >
                                        <Stack alignItems="center">
                                            <Typography variant="caption" lineHeight={1} sx={{fontSize: '0.65rem'}}>App Store</Typography>
                                            <Typography variant="body2" fontWeight="bold" sx={{fontSize: '0.75rem'}}>{t('client_onboarding.soon')}</Typography>
                                        </Stack>
                                    </Button>
                                </Box>
                                <Box flex={1}>
                                    <Button 
                                        variant="outlined" 
                                        fullWidth 
                                        size="large"
                                        startIcon={<GetAppIcon />}
                                        disabled
                                        sx={{ borderRadius: 3, py: 1.5, height: '100%' }}
                                    >
                                        <Stack alignItems="center">
                                            <Typography variant="caption" lineHeight={1} sx={{fontSize: '0.65rem'}}>RuStore</Typography>
                                            <Typography variant="body2" fontWeight="bold" sx={{fontSize: '0.75rem'}}>{t('client_onboarding.soon')}</Typography>
                                        </Stack>
                                    </Button>
                                </Box>
                            </Stack>
                        </Stack>

                        <Box sx={{ mt: 2 }}>
                            <Chip 
                                label={`${t('client_onboarding.version')} ${appVersion} • ${t('client_onboarding.size')} ${apkSize || '...'}`} 
                                size="small" 
                            />
                        </Box>

                    </Stack>
                </Container>
            </Box>

            {/* How it works */}
            <Box sx={{ bgcolor: 'white', py: 6 }}>
                <Container maxWidth="sm">
                    <Typography variant="h5" fontWeight="bold" textAlign="center" mb={4}>
                        {t('client_onboarding.steps_title')}
                    </Typography>
                    
                    <Stack spacing={3}>
                        {steps.map((step, index) => (
                            <Paper 
                                key={index} 
                                elevation={0} 
                                sx={{ 
                                    p: 2, 
                                    border: '1px solid', 
                                    borderColor: 'divider',
                                    borderRadius: 3,
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 2
                                }}
                            >
                                <Box sx={{ 
                                    width: 56, 
                                    height: 56, 
                                    borderRadius: '50%', 
                                    bgcolor: alpha(theme.palette.primary.main, 0.1),
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    flexShrink: 0
                                }}>
                                    {step.icon}
                                </Box>
                                <Box>
                                    <Typography variant="subtitle1" fontWeight="bold">
                                        {step.title}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {step.desc}
                                    </Typography>
                                </Box>
                            </Paper>
                        ))}
                    </Stack>
                </Container>
            </Box>

            {/* Footer */}
            <Box sx={{ p: 3, textAlign: 'center', bgcolor: 'background.default' }}>
                <Link href="/privacy" color="text.secondary" underline="hover" variant="body2">
                    {t('landing.privacy')}
                </Link>
                <Typography variant="caption" display="block" color="text.disabled" mt={1}>
                    © 2025 LoyaltyLoop. All rights reserved.
                </Typography>
            </Box>
        </Box>
    );
};