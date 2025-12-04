import { Container, Typography, Box, Paper, Stepper, Step, StepLabel, StepContent, Button, Chip } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import ConstructionIcon from '@mui/icons-material/Construction';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';

export const RoadmapPage = () => {
    const navigate = useNavigate();

    const steps = [
        {
            label: 'Запуск Платформы (MVP)',
            description: 'Базовый функционал: регистрация партнеров, создание точек, QR-транзакции, начисление/списание баллов, мобильное приложение для клиентов и кассиров.',
            status: 'DONE',
            date: 'Q1 2025'
        },
        {
            label: 'Аналитика и Отчеты',
            description: 'Детальная статистика по выручке, среднему чеку и активности клиентов. Дашборд партнера.',
            status: 'IN_PROGRESS',
            date: 'Q2 2025'
        },
        {
            label: 'Push-уведомления',
            description: 'Возможность отправлять маркетинговые рассылки (акции, поздравления) своим клиентам через приложение.',
            status: 'PLANNED',
            date: 'Q3 2025'
        },
        {
            label: 'Бан-система и Модерация',
            description: 'Инструменты защиты бизнеса: блокировка недобросовестных клиентов, система заявок от кассиров и арбитраж.',
            status: 'PLANNED',
            date: 'Q3 2025'
        },
        {
            label: 'Реферальная программа (B2C)',
            description: 'Механика "Пригласи друга": клиенты получают бонусы за то, что приводят друзей в ваше заведение.',
            status: 'PLANNED',
            date: 'Q4 2025'
        },
        {
            label: 'Расширение B2B Партнерства',
            description: 'Личный кабинет Менеджера Платформы. Прозрачная статистика по привлеченным бизнесам и начисление 50% комиссии.',
            status: 'PLANNED',
            date: '2026'
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
            case 'DONE': return <Chip label="Готово" color="success" size="small" />;
            case 'IN_PROGRESS': return <Chip label="В работе" color="warning" size="small" />;
            default: return <Chip label="В планах" variant="outlined" size="small" />;
        }
    };

    return (
        <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', py: 6 }}>
            <Container maxWidth="md">
                <Button 
                    startIcon={<ArrowBackIcon />} 
                    onClick={() => navigate('/about')} 
                    sx={{ mb: 4 }}
                >
                    Назад к описанию
                </Button>

                <Box textAlign="center" mb={6}>
                    <Typography variant="h3" fontWeight="bold" gutterBottom>
                        Roadmap проекта 🚀
                    </Typography>
                    <Typography variant="subtitle1" color="text.secondary">
                        Мы не стоим на месте. Вот что ждет LoyaltyLoop в ближайшем будущем.
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
                    <Typography variant="body2" color="text.secondary">
                        Есть идеи или предложения? Напишите нам в поддержку!
                    </Typography>
                </Box>
            </Container>
        </Box>
    );
};

