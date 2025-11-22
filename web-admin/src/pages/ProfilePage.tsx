import React, { useEffect, useState } from 'react';
import { Paper, Typography, Box, TextField, Button } from '@mui/material';
import { api } from '../api/axiosConfig';

export const ProfilePage = () => {
    const [profile, setProfile] = useState<any>({});

    useEffect(() => {
        api.get('/client/me').then(res => setProfile(res.data));
    }, []);

    return (
        <Box>
            <Typography variant="h4" gutterBottom>Мой Профиль</Typography>
            <Paper sx={{ p: 3, maxWidth: 600 }}>
                <TextField label="ID" value={profile.userId || ''} fullWidth margin="normal" disabled />
                <TextField label="Телефон" value={profile.phone || ''} fullWidth margin="normal" disabled />
                <TextField label="Имя" value={profile.firstName || ''} fullWidth margin="normal" />

                <Button variant="contained" sx={{ mt: 2 }}>Сохранить</Button>
            </Paper>
        </Box>
    );
};