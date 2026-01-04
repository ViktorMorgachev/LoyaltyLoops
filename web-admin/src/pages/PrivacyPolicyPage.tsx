import { Box, Container, Typography, Paper, Stack, Button, Divider } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export const PrivacyPolicyPage = () => {
    const navigate = useNavigate();
    const { t } = useTranslation();

    return (
        <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', py: 6 }}>
            <Container maxWidth="md">
                <Stack direction="row" alignItems="center" justifyContent="space-between" mb={4}>
                    <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} color="inherit">
                        {t('common.back', 'Back')}
                    </Button>
                </Stack>
                <Paper sx={{ p: { xs: 3, md: 5 }, borderRadius: 4 }}>
                    <Typography variant="h4" fontWeight="bold" gutterBottom>
                        Privacy Policy
                    </Typography>
                    <Typography variant="subtitle1" color="text.secondary" gutterBottom>
                        Last updated: December 12, 2025
                    </Typography>
                    
                    <Divider sx={{ my: 3 }} />

                    <Typography variant="h6" fontWeight="bold" gutterBottom>
                        1. Introduction
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        Welcome to LoyaltyLoops. We are committed to protecting your personal information and your right to privacy. If you have any questions or concerns about this privacy notice, or our practices with regards to your personal information, please contact us.
                    </Typography>

                    <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ mt: 3 }}>
                        2. Information We Collect
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        We collect personal information that you voluntarily provide to us when you register on the Services, express an interest in obtaining information about us or our products and Services, when you participate in activities on the Services, or otherwise when you contact us.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        The personal information that we collect depends on the context of your interactions with us and the Services, the choices you make and the products and features you use. The personal information we collect may include the following:
                    </Typography>
                    <ul style={{ color: '#666' }}>
                        <li>Phone numbers (for authentication)</li>
                        <li>Names (optional)</li>
                        <li>Transaction data (points, rewards)</li>
                        <li>Device information (for security and notifications)</li>
                    </ul>

                    <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ mt: 3 }}>
                        3. How We Use Your Information
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        We use personal information collected via our Services for a variety of business purposes described below. We process your personal information for these purposes in reliance on our legitimate business interests, in order to enter into or perform a contract with you, with your consent, and/or for compliance with our legal obligations.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        - To facilitate account creation and logon process.
                        <br />
                        - To send administrative information to you.
                        <br />
                        - To fulfill and manage your orders/rewards.
                        <br />
                        - To protect our Services.
                    </Typography>

                    <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ mt: 3 }}>
                        4. Sharing Your Information
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        We do not sell, rent, or share your personal data with third parties for marketing purposes. We only share information with your consent, to comply with laws, to provide you with services, to protect your rights, or to fulfill business obligations.
                    </Typography>

                     <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ mt: 3 }}>
                        5. Data Security
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        We have implemented appropriate technical and organizational security measures designed to protect the security of any personal information we process. However, despite our safeguards and efforts to secure your information, no electronic transmission over the Internet or information storage technology can be guaranteed to be 100% secure.
                    </Typography>

                    <Typography variant="h6" fontWeight="bold" gutterBottom sx={{ mt: 3 }}>
                        6. Contact Us
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        If you have questions or comments about this notice, you may contact our Data Protection Officer (DPO) via the in-app support chat or by email at morgachev.v.s@gmail.com.
                    </Typography>
                </Paper>
            </Container>
        </Box>
    );
};
