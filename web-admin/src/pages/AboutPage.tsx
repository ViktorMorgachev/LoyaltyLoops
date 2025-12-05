import { Container, Typography, Box, Paper, Button, Stack, useTheme } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import PeopleIcon from '@mui/icons-material/People';
import SecurityIcon from '@mui/icons-material/Security';
import StorefrontIcon from '@mui/icons-material/Storefront';
import HandshakeIcon from '@mui/icons-material/Handshake';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MapIcon from '@mui/icons-material/Map';

export const AboutPage = () => {
    const navigate = useNavigate();
    const theme = useTheme();

    const features = [
    {
            icon: <TrendingUpIcon fontSize="large" color="primary" />,
            title: 'Гибкая Лояльность',
            description: 'Конструктор стратегий: Кэшбэк, Штамп-карты ("6-й кофе в подарок") или Гибридная система. Адаптируется под Ритейл, HoReCa и Услуги.'
    },
    {
            icon: <PeopleIcon fontSize="large" color="primary" />,
            title: 'CRM и Сегментация',
            description: 'Оцифровка базы клиентов без пластиковых карт. Автоматическое определение "Постоянных" и "Потерянных" клиентов для возврата.'
    },
    {
            icon: <MapIcon fontSize="large" color="primary" />,
            title: 'Гео-маркетинг',
            description: 'Ваши филиалы на интерактивной карте города. Клиенты видят вас, когда ищут услуги рядом. Бесплатный трафик из приложения.'
        },
        {
            icon: <SecurityIcon fontSize="large" color="primary" />,
            title: 'Контроль и Безопасность',
            description: 'Прозрачная история операций. Защита от фрода со стороны персонала. Разграничение прав доступа (Владелец, Менеджер, Кассир).'
        },
        {
            icon: <StorefrontIcon fontSize="large" color="primary" />,
            title: 'Филиальная Сеть',
            description: 'Единая экосистема для всех ваших точек. Клиент копит баллы в одной кофейне, а тратит в другой (по вашему желанию).'
    },
    {
            icon: <HandshakeIcon fontSize="large" color="primary" />,
            title: 'Партнерство 50/50',
            description: 'Станьте Менеджером Платформы: подключайте другие бизнесы и получайте 50% от их абонентской платы пожизненно.'
    }
  ];

  return (
        <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', py: 6 }}>
            <Container maxWidth="lg">
                <Button 
                    startIcon={<ArrowBackIcon />} 
                    onClick={() => navigate('/', { replace: true })} 
                    sx={{ mb: 4 }}
                >
                    Назад
                </Button>

                {/* HERO SECTION */}
                <Box textAlign="center" mb={8}>
                    <Typography variant="h2" component="h1" fontWeight="800" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                        LoyaltyLoop
                    </Typography>
                    <Typography variant="h5" color="text.secondary" sx={{ mb: 4, maxWidth: 800, mx: 'auto', lineHeight: 1.6 }}>
                        Современная система лояльности для малого и среднего бизнеса. 
                        Превращаем случайных посетителей в постоянных клиентов.
      </Typography>
                    <Stack direction="row" spacing={2} justifyContent="center">
                        <Button variant="contained" size="large" onClick={() => navigate('/login', { replace: true })} sx={{ px: 4, py: 1.5, borderRadius: 4, fontSize: '1.1rem' }}>
                            Начать бесплатно
                        </Button>
                        <Button variant="outlined" size="large" onClick={() => navigate('/roadmap')} sx={{ px: 4, py: 1.5, borderRadius: 4, fontSize: '1.1rem' }}>
                            Планы развития
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
                        Готовы масштабировать бизнес?
              </Typography>
                    <Typography variant="h6" sx={{ mb: 4, opacity: 0.8, fontWeight: 'normal' }}>
                        Присоединяйтесь к экосистеме LoyaltyLoop уже сегодня.
        </Typography>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} justifyContent="center">
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>Быстрый старт за 15 минут</Typography>
                         </Box>
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>Никакого сложного оборудования</Typography>
                         </Box>
                         <Box display="flex" alignItems="center" gap={1}>
                            <CheckCircleIcon color="success" />
                            <Typography>14 дней пробный период</Typography>
                         </Box>
                    </Stack>
      </Paper>
            </Container>
    </Box>
  );
};
