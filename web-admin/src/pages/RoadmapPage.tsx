import { Container, Typography, Box, Paper, Stepper, Step, StepLabel, StepContent, Button, Chip, Link } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import ConstructionIcon from '@mui/icons-material/Construction';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

export const RoadmapPage = () => {
    const navigate = useNavigate();
    const { t } = useTranslation();

    const steps = [
        {
            label: t('landing.roadmap.steps.mvp_label'),
            description: t('landing.roadmap.steps.mvp_desc'),
            status: 'DONE',
            date: 'Q4 2025'
        },
        {
            label: t('landing.roadmap.steps.analytics_label'),
            description: t('landing.roadmap.steps.analytics_desc'),
            status: 'DONE',
            date: 'Q4 2025'
        },
        {
            label: t('landing.roadmap.steps.b2b_label'),
            description: t('landing.roadmap.steps.b2b_desc'),
            status: 'DONE',
            date: 'Q4 2025'
        },
        {
            label: t('landing.roadmap.steps.push_label'),
            description: t('landing.roadmap.steps.push_desc'),
            status: 'PLANNED',
            date: 'Q1 2026'
        },
        {
            label: t('landing.roadmap.steps.gamification_label'),
            description: t('landing.roadmap.steps.gamification_desc'),
            status: 'PLANNED',
            date: 'Q1 2026'
        },
        {
            label: t('landing.roadmap.steps.referral_label'),
            description: t('landing.roadmap.steps.referral_desc'),
            status: 'PLANNED',
            date: 'Q2 2026'
        },
        
    ];

    const getStepIcon = (status: string) => {
        switch (status) {
            case 'DONE': return <RocketLaunchIcon color="success" />;
            case 'IN_PROGRESS': return <ConstructionIcon color="warning" />;
            default: return <HourglassEmptyIcon color="disabled" />;
        }
    };

    const getStatusChip = (status: string) => {
        switch (status) {
            case 'DONE': return <Chip label={t('landing.roadmap.status.done')} color="success" size="small" />;
            case 'IN_PROGRESS': return <Chip label={t('landing.roadmap.status.in_progress')} color="warning" size="small" />;
            default: return <Chip label={t('landing.roadmap.status.planned')} variant="outlined" size="small" />;
        }
    };

    return (
        <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', py: 6, position: 'relative' }}>
            <Box sx={{ position: 'absolute', top: 16, right: 16 }}>
                <LanguageSwitcher />
            </Box>
            <Container maxWidth="md">
                <Button 
                    startIcon={<ArrowBackIcon />} 
                    onClick={() => navigate('/about')} 
                    sx={{ mb: 4 }}
                >
                    {t('landing.roadmap.back')}
                </Button>

                <Box textAlign="center" mb={6}>
                    <Typography variant="h3" fontWeight="bold" gutterBottom>
                        {t('landing.roadmap.title')}
                    </Typography>
                    <Typography variant="subtitle1" color="text.secondary">
                        {t('landing.roadmap.subtitle')}
                    </Typography>
                </Box>

                <Paper elevation={0} sx={{ p: 4, borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
                    <Stepper orientation="vertical">
                        {steps.map((step, index) => (
                            <Step key={index} active={true}>
                                <StepLabel 
                                    icon={getStepIcon(step.status)}
                                >
                                    <Box display="flex" alignItems="center" gap={2}>
                                        <Typography variant="h6" fontWeight="600">
                                            {step.label}
                                        </Typography>
                                        {getStatusChip(step.status)}
                                        <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                                            {step.date}
                                        </Typography>
                                    </Box>
                                </StepLabel>
                                <StepContent>
                                    <Typography color="text.secondary" sx={{ mb: 2, maxWidth: 600 }}>
                                        {step.description}
                                    </Typography>
                                </StepContent>
                            </Step>
                        ))}
                    </Stepper>
                </Paper>

                <Box mt={6} textAlign="center">
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t('landing.roadmap.contact_support')}
                    </Typography>
                    <Link 
                        href="mailto:morgachev.v.s@gmail.com" 
                        underline="hover" 
                        color="primary"
                        sx={{ fontWeight: 600, fontSize: '1rem' }}
                    >
                        morgachev.v.s@gmail.com
                    </Link>
                </Box>
            </Container>
        </Box>
    );
};

