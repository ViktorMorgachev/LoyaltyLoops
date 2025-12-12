import { Box, Container, Typography, Paper, Button, Stack, useTheme } from '@mui/material';
import AndroidIcon from '@mui/icons-material/Android';
import { useTranslation } from 'react-i18next';
import { BrandLogo } from '../components/BrandLogo';

export const DownloadPage = () => {
    const { t } = useTranslation();
    const theme = useTheme();
    // TODO: Replace with actual APK link
    const apkUrl = "/LoyaltyLoop.apk"; 

    return (
        <Box sx={{ 
            bgcolor: '#f0f2f5', 
            minHeight: '100vh', 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            p: 2
        }}>
            <Container maxWidth="xs">
                <Paper 
                    elevation={0} 
                    sx={{ 
                        p: 4, 
                        borderRadius: 6, 
                        textAlign: 'center',
                        border: '1px solid', 
                        borderColor: 'divider',
                        boxShadow: theme.shadows[4]
                    }}
                >
                    <Box mb={4} display="flex" justifyContent="center">
                        <BrandLogo size={80} />
                    </Box>

                    <Typography variant="h5" fontWeight="bold" gutterBottom>
                        {t('download.title', 'Download LoyaltyLoop')}
                    </Typography>
                    <Typography color="text.secondary" paragraph sx={{ mb: 4 }}>
                        {t('download.desc', 'Get the app to collect points, get rewards, and track your progress.')}
                    </Typography>

                    <Stack spacing={2}>
                        <Button 
                            variant="contained" 
                            size="large" 
                            startIcon={<AndroidIcon />}
                            component="a" 
                            href={apkUrl} 
                            download
                            sx={{ 
                                py: 1.5, 
                                borderRadius: 3, 
                                fontSize: '1.1rem',
                                fontWeight: 'bold' 
                            }}
                        >
                            {t('download.btn_apk', 'Download APK')}
                        </Button>
                        
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
                            {t('download.requirements', 'Requires Android 8.0 or later')}
                        </Typography>
                    </Stack>
                </Paper>
            </Container>
        </Box>
    );
};

