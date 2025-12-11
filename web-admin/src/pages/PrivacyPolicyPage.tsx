import { Box, Container, Typography, Paper, Stack, Button } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';

export const PrivacyPolicyPage = () => {
    const navigate = useNavigate();

    return (
        <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', py: 6 }}>
            <Container maxWidth="md">
                <Stack direction="row" alignItems="center" justifyContent="space-between" mb={4}>
                    <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} color="inherit">
                        Back
                    </Button>
                </Stack>
                <Paper sx={{ p: { xs: 3, md: 5 }, borderRadius: 4 }}>
                    <Typography variant="h4" fontWeight="bold" gutterBottom>
                        Privacy Policy
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        We respect your privacy. We do not sell, rent, or share your personal data with third parties for marketing purposes.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        Data is collected and processed solely to provide and improve LoyaltyLoop services, enable authentication, issue rewards, prevent fraud, and ensure platform security.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        Access to data is restricted to authorized personnel only. All data exchanges are protected with industry-standard encryption in transit and at rest.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        You can request data deletion or export at any time by contacting support. We retain data only for as long as required for legal, accounting, or security needs.
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        If you have any questions, please reach out to us via the in-app support chat or by email.
                    </Typography>
                </Paper>
            </Container>
        </Box>
    );
};

