import { Container, Typography, Box, Paper, Button, Stack, useTheme, Avatar, TextField, CircularProgress } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useEffect, useMemo, useState } from 'react';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SecurityIcon from '@mui/icons-material/Security';
import HandshakeIcon from '@mui/icons-material/Handshake';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MapIcon from '@mui/icons-material/Map';
import TuneIcon from '@mui/icons-material/Tune';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import SavingsIcon from '@mui/icons-material/Savings';
import CoffeeIcon from '@mui/icons-material/Coffee';
import ShuffleIcon from '@mui/icons-material/Shuffle';
import TranslateIcon from '@mui/icons-material/Translate';
import BarChartIcon from '@mui/icons-material/BarChart';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import SwitchAccountIcon from '@mui/icons-material/SwitchAccount';
import ShieldIcon from '@mui/icons-material/Shield';
import PublicIcon from '@mui/icons-material/Public';
import AndroidIcon from '@mui/icons-material/Android';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { api } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';

import { Analytics } from '../utils/analytics';

export const AboutPage = () => {
    const navigate = useNavigate();
    const { t } = useTranslation();
    const theme = useTheme();
    const playStoreUrl = 'https://play.google.com/store/apps/details?id=io.loyaltyloop.app';
    const showPlayLinks = (import.meta as any)?.env?.VITE_SHOW_PLAY_LINKS === 'true';
    const showStartFree = (import.meta as any)?.env?.VITE_SHOW_PLAY_LINKS === 'true';

    const { showSuccess, showError } = useNotification();
    const [email, setEmail] = useState('');
    const [loading, setLoading] = useState(false);
    const [joined, setJoined] = useState(false);

    const handleStoreClick = (store: string) => {
        Analytics.track('download_click', { store });
    };

    const handleJoinWaitlist = async () => {
        Analytics.track('waitlist_click_join');
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!email || !emailRegex.test(email)) {
             Analytics.track('waitlist_validation_error', { email_length: email.length });
            showError(t('landing.waitlist.error_email', 'Please enter a valid email'));
            return;
        }
        setLoading(true);
        try {
            await api.post('/public/waitlist', { email });
            setJoined(true);
             Analytics.track('waitlist_join_success', {
                            email_domain: email.split('@')[1] // Полезно знать, corporate это или gmail
                        });
        } catch (e: any) {
            // Check if it is a "Conflict" (409) -> Already joined
            if (e.response && e.response.status === 409) {
                 Analytics.track('waitlist_join_duplicate');
                showSuccess(t('landing.waitlist.success_exists', 'You are already in the waitlist. A manager will contact you soon.'));
                setEmail('');
                return;
            }
        Analytics.track('waitlist_join_server_error', { status: e.response?.status });
            showError(getErrorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    // Anti-copy & Anti-screenshot (best effort)
    useEffect(() => {

          Analytics.track('page_view', { page: 'landing' });
           const startTime = Date.now();
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
              const timeSpent = (Date.now() - startTime) / 1000;
              Analytics.track('page_leave', { page: 'landing', duration_seconds: timeSpent });
        };
    }, []);

    const mechanics = [
        {
            icon: <SavingsIcon fontSize="large" sx={{ color: '#fff' }} />,
            title: t('landing.mechanics.accumulative.title'),
            desc: t('landing.mechanics.accumulative.desc'),
            color: '#3b82f6'
        },
        {
            icon: <CoffeeIcon fontSize="large" sx={{ color: '#fff' }} />,
            title: t('landing.mechanics.visits.title'),
            desc: t('landing.mechanics.visits.desc'),
            color: '#10b981'
        },
        {
            icon: <ShuffleIcon fontSize="large" sx={{ color: '#fff' }} />,
            title: t('landing.mechanics.hybrid.title'),
            desc: t('landing.mechanics.hybrid.desc'),
            color: '#8b5cf6'
        }
    ];

    const benefits = [
        { icon: <SecurityIcon color="primary" />, title: t('landing.security.title'), desc: t('landing.security.desc') },
        { icon: <TranslateIcon color="primary" />, title: t('landing.localization.title'), desc: t('landing.localization.desc') },
        { icon: <BarChartIcon color="primary" />, title: t('landing.analytics.title'), desc: t('landing.analytics.item_1') },
        { icon: <AdminPanelSettingsIcon color="primary" />, title: t('landing.admin.title'), desc: t('landing.admin.desc') },
        { icon: <MapIcon color="primary" />, title: t('landing.multiregion.title'), desc: t('landing.multiregion.desc') },
        { icon: <SwitchAccountIcon color="primary" />, title: t('landing.multirole.title'), desc: t('landing.multirole.desc') },
        { icon: <ShieldIcon color="primary" />, title: t('landing.protection.title'), desc: t('landing.protection.desc') },
        { icon: <HandshakeIcon color="primary" />, title: t('landing.partnership.title'), desc: t('landing.partnership.desc') },
    ];

    const roadmapSteps = useMemo(() => ([
        {
            status: 'done',
            label: t('landing.roadmap.steps.mvp_label'),
            desc: t('landing.roadmap.steps.mvp_desc'),
            date: 'Q4 2025'
        },
        {
            status: 'done', // Changed from 'done'
            label: t('landing.roadmap.steps.analytics_label'),
            desc: t('landing.roadmap.steps.analytics_desc'),
            date: 'Q4 2025'
        },
        {
            status: 'done',
            label: t('landing.roadmap.steps.b2b_label'),
            desc: t('landing.roadmap.steps.b2b_desc'),
            date: 'Q4 2025'
        },
        {
            status: 'planned',
            label: t('landing.roadmap.steps.push_label'),
            desc: t('landing.roadmap.steps.push_desc'),
            date: 'Q1 2026'
        },
        {
            status: 'planned',
            label: t('landing.roadmap.steps.gamification_label'),
            desc: t('landing.roadmap.steps.gamification_desc'),
            date: 'Q1 2026'
        },
        {
            status: 'planned',
            label: t('landing.roadmap.steps.referral_label'),
            desc: t('landing.roadmap.steps.referral_desc'),
            date: 'Q2 2026'
        }
    ]), [t]);

    const statusColor = (status: string) => {
        if (status === 'done') return theme.palette.success.main;
        if (status === 'in_progress') return theme.palette.warning.main;
        return theme.palette.info.main;
    };

    const hoverCard = {
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': { transform: 'translateY(-6px)', boxShadow: theme.shadows[6] }
    } as const;

    return (
        <Box sx={{ 
            bgcolor: '#f8fafc', 
            minHeight: '100vh', 
            position: 'relative',
            userSelect: 'none',
            WebkitUserSelect: 'none',
            overflowX: 'hidden',
            pb: { xs: 12, md: 0 } // запас под липкий CTA на мобайле
        }}>
            {/* Header */}
            <Box sx={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100, backdropFilter: 'blur(10px)', bgcolor: 'rgba(255,255,255,0.8)', borderBottom: '1px solid', borderColor: 'divider', px: 2, py: 1 }}>
                <Container maxWidth="lg" sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Button 
                        startIcon={<ArrowBackIcon />} 
                        onClick={() => navigate(-1)} 
                        color="inherit"
                    >
                        {t('common.back')}
                    </Button>
                    <LanguageSwitcher />
                </Container>
            </Box>

            <Box sx={{ pt: 10, pb: { xs: 14, md: 8 } }}>
                <Container maxWidth="lg">
                    {/* HERO SECTION */}
                    <Box textAlign="center" mb={12} mt={4}>
                        <Paper
                            elevation={0}
                            sx={{
                                p: { xs: 3.5, md: 6 },
                                borderRadius: 6,
                                background: 'radial-gradient(circle at 10% 20%, rgba(59,130,246,0.15), transparent 35%), radial-gradient(circle at 90% 10%, rgba(236,72,153,0.18), transparent 30%), linear-gradient(135deg, #0f172a 0%, #111827 100%)',
                                color: 'white',
                            }}
                        >
                            <Typography variant="h2" component="h1" fontWeight="900" gutterBottom sx={{ fontSize: { xs: '2.4rem', md: '3.6rem' } }}>
                                {t('landing.hero_title')}
                            </Typography>
                            <Typography variant="h5" sx={{ mb: 4, maxWidth: 900, mx: 'auto', lineHeight: 1.6, opacity: 0.9 }}>
                                {t('landing.hero_subtitle')}
                            </Typography>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="center">
                                {showStartFree && (
                                    <Button variant="contained" size="large" onClick={() => {
                                         Analytics.track('start_free_click');
                                          navigate('/login', { replace: true })
                                        }} sx={{ px: 6, py: 1.6, borderRadius: 8, fontSize: '1.15rem', fontWeight: 'bold', boxShadow: theme.shadows[10] }}>
                                        {t('landing.start_free')}
                                    </Button>
                                )}
                                {showPlayLinks && (
                                    <Button
                                        variant="outlined"
                                        size="large"
                                        component="a"
                                        href={playStoreUrl}
                                        onClick={() => handleStoreClick('google_play_store')}
                                        target="_blank"
                                        rel="noreferrer"
                                        startIcon={<AndroidIcon />}
                                        sx={{ px: 5, py: 1.6, borderRadius: 8, fontSize: '1.05rem', fontWeight: 'bold', color: 'white', borderColor: 'rgba(255,255,255,0.6)', '&:hover': { borderColor: 'white' } }}
                                    >
                                        {t('landing.download_play')}
                                    </Button>
                                )}
                            </Stack>
                        </Paper>
                    </Box>

                    {/* ROADMAP SECTION */}
                    <Box id="roadmap-section" mb={10}>
                        <Typography
                            variant="h3"
                            fontWeight="bold"
                            textAlign="center"
                            mb={2}
                            sx={{ fontSize: { xs: '2rem', sm: '2.3rem', md: '2.6rem' }, lineHeight: 1.15 }}
                        >
                            {t('landing.roadmap.title')}
                        </Typography>
                        <Typography variant="h6" color="text.secondary" textAlign="center" mb={4}>
                            {t('landing.roadmap.subtitle')}
                        </Typography>
                        <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: 'repeat(3, 1fr)' }} gap={3}>
                            {roadmapSteps.map((step, idx) => (
                                <Paper
                                    key={idx}
                                    elevation={0}
                                    sx={{
                                        p: 3,
                                        borderRadius: 6,
                                        border: '1px solid',
                                        borderColor: 'divider',
                                        height: '100%',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 1.5,
                                        ...hoverCard
                                    }}
                                >
                                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                                        <Typography variant="subtitle2" sx={{ color: statusColor(step.status), fontWeight: 700 }}>
                                            {t(`landing.roadmap.status.${step.status}`)}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            {step.date}
                                        </Typography>
                                    </Stack>
                                    <Typography variant="h6" fontWeight="bold">
                                        {step.label}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5 }}>
                                        {step.desc}
                                    </Typography>
                                </Paper>
                            ))}
                        </Box>
                    </Box>

                    {/* PROBLEM SECTION */}
                    <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: '1fr 1fr' }} gap={6} mb={12}>
                        <Box sx={{ p: 4, bgcolor: '#fee2e2', borderRadius: 8 }}>
                            <TrendingDownIcon sx={{ fontSize: 80, color: '#ef4444', mb: 2 }} />
                            <Typography variant="h4" fontWeight="bold" gutterBottom color="#991b1b">
                                {t('landing.problem.title')}
                            </Typography>
                            <Typography variant="h6" color="#7f1d1d" sx={{ opacity: 0.9, lineHeight: 1.6 }}>
                                {t('landing.problem.desc')}
                            </Typography>
                        </Box>
                        <Box sx={{ p: 4, bgcolor: '#eff6ff', borderRadius: 8 }}>
                            <Typography variant="h4" fontWeight="bold" gutterBottom color="#1e40af">
                                {t('landing.solution.title')}
                            </Typography>
                            <Typography variant="h6" color="#1e3a8a" sx={{ mb: 4, opacity: 0.9, lineHeight: 1.6 }}>
                                {t('landing.solution.desc')}
                            </Typography>
                            <Stack spacing={2}>
                                <Stack direction="row" alignItems="center" spacing={2}>
                                    <Avatar sx={{ bgcolor: '#3b82f6' }}><TuneIcon /></Avatar>
                                    <Typography variant="subtitle1" fontWeight="bold">{t('landing.solution.item_1')}</Typography>
                                </Stack>
                                <Stack direction="row" alignItems="center" spacing={2}>
                                    <Avatar sx={{ bgcolor: '#3b82f6' }}><PublicIcon /></Avatar>
                                    <Typography variant="subtitle1" fontWeight="bold">{t('landing.solution.item_2')}</Typography>
                                </Stack>
                                <Stack direction="row" alignItems="center" spacing={2}>
                                    <Avatar sx={{ bgcolor: '#3b82f6' }}><FlashOnIcon /></Avatar>
                                    <Typography variant="subtitle1" fontWeight="bold">{t('landing.solution.item_3')}</Typography>
                                </Stack>
                            </Stack>
                        </Box>
                    </Box>

                    {/* MECHANICS */}
                    <Box mb={12}>
                        <Typography
                            variant="h3"
                            fontWeight="bold"
                            textAlign="center"
                            mb={6}
                            sx={{ fontSize: { xs: '1.9rem', sm: '2.2rem', md: '2.5rem' }, lineHeight: 1.15 }}
                        >
                            {t('landing.mechanics_title')}
                        </Typography>
                        <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: 'repeat(3, 1fr)' }} gap={4}>
                            {mechanics.map((item, index) => (
                                <Paper
                                    key={index}
                                    elevation={0}
                                    sx={{
                                        p: 4,
                                        height: '100%',
                                        borderRadius: 6,
                                        border: '1px solid',
                                        borderColor: 'divider',
                                        ...hoverCard
                                    }}
                                >
                                    <Box sx={{
                                        width: 64,
                                        height: 64,
                                        borderRadius: 4,
                                        bgcolor: item.color,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        mb: 3
                                    }}>
                                        {item.icon}
                                    </Box>
                                    <Typography variant="h5" fontWeight="bold" gutterBottom>
                                        {item.title}
                                    </Typography>
                                    <Typography color="text.secondary" sx={{ lineHeight: 1.6 }}>
                                        {item.desc}
                                    </Typography>
                                </Paper>
                            ))}
                        </Box>
                    </Box>

                    {/* BENEFITS GRID */}
                    <Box mb={12}>
                        <Typography
                            variant="h3"
                            fontWeight="bold"
                            textAlign="center"
                            mb={6}
                            sx={{ fontSize: { xs: '1.9rem', sm: '2.2rem', md: '2.5rem' }, lineHeight: 1.15 }}
                        >
                            {t('landing.benefits_title')}
                        </Typography>
                        <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }} gap={3}>
                            {benefits.map((benefit, index) => (
                                <Paper
                                    key={index}
                                    elevation={0}
                                    sx={{
                                        p: 3,
                                        height: '100%',
                                        borderRadius: 4,
                                        bgcolor: '#fff',
                                        border: '1px solid',
                                        borderColor: 'divider',
                                        ...hoverCard
                                    }}
                                >
                                    <Box sx={{ mb: 2 }}>{benefit.icon}</Box>
                                    <Typography variant="h6" fontWeight="bold" gutterBottom>
                                        {benefit.title}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {benefit.desc}
                                    </Typography>
                                </Paper>
                            ))}
                        </Box>
                    </Box>

                    {/* RESULTS */}
                    <Box mb={8}>
                        <Paper sx={{ 
                            p: 6, 
                            borderRadius: 8, 
                            background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)', 
                            color: 'white',
                            position: 'relative',
                            overflow: 'hidden'
                        }}>
                            <Box sx={{ position: 'relative', zIndex: 2 }}>
                                <Typography
                                    variant="h3"
                                    fontWeight="bold"
                                    textAlign="center"
                                    mb={6}
                                    sx={{ fontSize: { xs: '1.9rem', sm: '2.2rem', md: '2.5rem' }, lineHeight: 1.15 }}
                                >
                                    {t('landing.result.title')}
                                </Typography>
                                <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: 'repeat(3, 1fr)' }} gap={4}>
                                    <Box textAlign="center">
                                        <Typography variant="h2" fontWeight="900" color="#4ade80" gutterBottom>
                                            +40%
                                        </Typography>
                                        <Typography variant="h6" sx={{ opacity: 0.9 }}>
                                            {t('landing.result.stat_1')}
                                        </Typography>
                                    </Box>
                                    <Box textAlign="center">
                                        <Typography variant="h2" fontWeight="900" color="#fbbf24" gutterBottom>
                                            +25%
                                        </Typography>
                                        <Typography variant="h6" sx={{ opacity: 0.9 }}>
                                            {t('landing.result.stat_2')}
                                        </Typography>
                                    </Box>
                                    <Box textAlign="center">
                                        <Typography variant="h2" fontWeight="900" color="#60a5fa" gutterBottom>
                                            100%
                                        </Typography>
                                        <Typography variant="h6" sx={{ opacity: 0.9 }}>
                                            {t('landing.result.stat_3')}
                                        </Typography>
                                    </Box>
                                </Box>
                            </Box>
                        </Paper>
                    </Box>

                    {/* WAITLIST */}
                    {false && (
                    <Box mb={16} textAlign="center" maxWidth="800px" mx="auto">
                        <Paper 
                            elevation={4} 
                            sx={{ 
                                p: { xs: 4, md: 6 }, 
                                borderRadius: 8, 
                                border: '1px solid', 
                                borderColor: 'divider', 
                                bgcolor: 'background.paper',
                                position: 'relative',
                                overflow: 'hidden'
                            }}
                        >
                             <Box 
                                sx={{ 
                                    position: 'absolute', 
                                    top: 0, left: 0, right: 0, height: 6, 
                                    background: 'linear-gradient(90deg, #3b82f6, #8b5cf6, #ec4899)' 
                                }} 
                             />
                             <Typography variant="h3" fontWeight="800" gutterBottom sx={{ mb: 2 }}>
                                {t('landing.waitlist.title', 'Join the Waitlist')}
                             </Typography>
                             <Typography variant="h6" color="text.secondary" mb={5} sx={{ lineHeight: 1.6, maxWidth: 600, mx: 'auto' }}>
                                {t('landing.waitlist.desc', 'Join the waitlist to get exclusive early access and a free trial period when we launch.')}
                             </Typography>
                             {joined ? (
                                <Box textAlign="center" py={2} sx={{ animation: 'fadeIn 0.5s ease-out', '@keyframes fadeIn': { from: { opacity: 0, transform: 'translateY(10px)' }, to: { opacity: 1, transform: 'translateY(0)' } } }}>
                                    <CheckCircleIcon sx={{ fontSize: 64, color: '#10b981', mb: 2 }} />
                                    <Typography variant="h5" fontWeight="bold" gutterBottom>
                                        {t('landing.waitlist.success_title')}
                                    </Typography>
                                    <Typography color="text.secondary" sx={{ maxWidth: 500, mx: 'auto' }}>
                                        {t('landing.waitlist.success_joined_body', 'Thank you! A manager will contact you soon.')}
                                    </Typography>
                                </Box>
                             ) : (
                                <Box 
                                    component="form"
                                    display="flex" 
                                    gap={2} 
                                    flexDirection={{ xs: 'column', sm: 'row' }}
                                    alignItems="stretch"
                                    justifyContent="center"
                                    maxWidth="500px"
                                    mx="auto"
                                >
                                    <TextField 
                                        fullWidth 
                                        placeholder={t('landing.waitlist.email_placeholder', 'Enter your email')}
                                        variant="outlined"
                                        value={email}
                                        onFocus={() => Analytics.track('waitlist_input_focus')}
                                        onChange={(e) => setEmail(e.target.value)}
                                        disabled={loading}
                                        sx={{ 
                                            '& .MuiOutlinedInput-root': {
                                                borderRadius: 3,
                                                bgcolor: '#f8fafc'
                                            }
                                        }}
                                    />
                                    <Button 
                                        variant="contained" 
                                        size="large" 
                                        onClick={handleJoinWaitlist}
                                        disabled={loading}
                                        sx={{ 
                                            px: 5, 
                                            borderRadius: 3, 
                                            minWidth: 160,
                                            fontSize: '1.1rem',
                                            fontWeight: 'bold',
                                            boxShadow: theme.shadows[4]
                                        }}
                                    >
                                        {loading ? <CircularProgress size={24} color="inherit" /> : t('landing.waitlist.join', 'Join')}
                                    </Button>
                                </Box>
                             )}
                        </Paper>
                    </Box>
                    )}

                    {/* CTA FINAL */}
                    <Box textAlign="center">
                        <Typography variant="h4" fontWeight="bold" gutterBottom>
                            {t('landing.cta.title')}
                        </Typography>
                        <Typography variant="h6" color="text.secondary" mb={4}>
                            {t('landing.cta.subtitle')}
                        </Typography>
                        {showStartFree && (
                            <Button 
                                variant="contained" 
                                size="large" 
                                onClick={() => {
                                    Analytics.track('start_free_click');
                                    navigate('/login', { replace: true })
                                   }}
                                sx={{ 
                                    px: 8, 
                                    py: 2, 
                                    borderRadius: 4, 
                                    fontSize: '1.2rem', 
                                    fontWeight: 'bold',
                                    boxShadow: theme.shadows[10]
                                }}
                            >
                                {t('landing.start_free')}
                            </Button>
                        )}
                        <Stack direction="row" spacing={2} justifyContent="center" mt={2}>
                            {showPlayLinks && (
                                <Button
                                    variant="outlined"
                                    startIcon={<AndroidIcon />}
                                    component="a"
                                    href={playStoreUrl}
                                    target="_blank"
                                    rel="noreferrer"
                                    sx={{ borderRadius: 3 }}
                                >
                                    {t('landing.download_play')}
                                </Button>
                            )}
                            <Button variant="text" onClick={() => navigate('/privacy')} sx={{ textTransform: 'none' }}>
                                {t('landing.privacy')}
                            </Button>
                        </Stack>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} justifyContent="center" mt={4}>
                             <Box display="flex" alignItems="center" gap={1}>
                                <CheckCircleIcon color="success" />
                                <Typography>{t('landing.cta.quick_start')}</Typography>
                             </Box>
                             <Box display="flex" alignItems="center" gap={1}>
                                <CheckCircleIcon color="success" />
                                <Typography>{t('landing.cta.no_hardware')}</Typography>
                             </Box>
                             {/* Removed 14 days trial text as per request */}
                        </Stack>
                    </Box>
                </Container>
            </Box>

            {/* Sticky CTA for mobile */}
            {showStartFree && (
                <Box
                    sx={{
                        position: 'fixed',
                        bottom: 0,
                        left: 0,
                        right: 0,
                        zIndex: 120,
                        display: { xs: 'block', md: 'none' },
                        bgcolor: 'rgba(15,23,42,0.95)',
                        boxShadow: theme.shadows[10],
                        borderTop: '1px solid rgba(255,255,255,0.08)',
                        backdropFilter: 'blur(8px)',
                        py: 1.5,
                        px: 2
                    }}
                >
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="center">
                        <Button
                            fullWidth
                            variant="contained"
                            size="large"
                            onClick={() => navigate('/login', { replace: true })}
                            sx={{ borderRadius: 8, fontWeight: 'bold' }}
                        >
                            {t('landing.start_free')}
                        </Button>
                        {showPlayLinks && (
                            <Button
                                fullWidth
                                variant="outlined"
                                size="large"
                                component="a"
                                href={playStoreUrl}
                                target="_blank"
                                rel="noreferrer"
                                startIcon={<AndroidIcon />}
                                sx={{ borderRadius: 8, color: 'white', borderColor: 'rgba(255,255,255,0.7)', '&:hover': { borderColor: 'white' } }}
                            >
                                {t('landing.download_play')}
                            </Button>
                        )}
                    </Stack>
                </Box>
            )}
        </Box>
    );
};
