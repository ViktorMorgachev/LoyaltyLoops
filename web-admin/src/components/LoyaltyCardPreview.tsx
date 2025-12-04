import React from 'react';
import { Box, Typography, Avatar } from '@mui/material';

interface LoyaltyCardPreviewProps {
    businessName: string;
    color: string;
    logoUrl?: string;
    levelName?: string; // "Gold", "Silver" etc.
    balance?: number;
    currencySymbol?: string;
}

export const LoyaltyCardPreview: React.FC<LoyaltyCardPreviewProps> = ({
    businessName,
    color,
    logoUrl,
    levelName = "Gold",
    balance = 1530,
    currencySymbol = "Б"
}) => {
    // Определяем цвет обводки и бейджа в зависимости от уровня (или дефолтный Gold для превью)
    // В реальном приложении логика может быть сложнее
    const levelColor = '#FCD34D'; // Gold-ish yellow
    const textColor = '#FFFFFF'; // White text usually looks best on brand colors

    return (
        <Box
            sx={{
                width: '100%',
                maxWidth: 340, // Примерная ширина мобильной карты
                aspectRatio: '1.586', // Стандартное соотношение сторон банковской карты
                borderRadius: 4,
                position: 'relative',
                bgcolor: color,
                color: textColor,
                p: 3,
                boxShadow: '0 10px 30px -5px rgba(0,0,0,0.3)',
                overflow: 'hidden',
                // Имитация золотой обводки (Border)
                border: `6px solid ${levelColor}80`, // Полупрозрачный золотой
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between'
            }}
        >
            {/* Верхняя часть: Лого и Название */}
            <Box display="flex" alignItems="center" gap={2}>
                <Avatar 
                    src={logoUrl} 
                    sx={{ 
                        width: 48, 
                        height: 48, 
                        bgcolor: 'rgba(255,255,255,0.2)',
                        fontSize: '1.2rem',
                        fontWeight: 'bold',
                        color: textColor
                    }}
                >
                    {businessName.slice(0, 2).toUpperCase()}
                </Avatar>
                <Box>
                    <Typography variant="h6" fontWeight="bold" sx={{ lineHeight: 1.2 }}>
                        {businessName || "Название Бизнеса"}
                    </Typography>
                    <Typography variant="caption" sx={{ opacity: 0.8 }}>
                        Бонусная карта
                    </Typography>
                </Box>
                
                {/* Бейдж уровня (справа сверху) */}
                <Box 
                    sx={{ 
                        ml: 'auto', 
                        bgcolor: 'rgba(255,255,255,0.2)', 
                        px: 1.5, 
                        py: 0.5, 
                        borderRadius: 4,
                        backdropFilter: 'blur(4px)'
                    }}
                >
                    <Typography variant="caption" fontWeight="bold">
                        {levelName}
                    </Typography>
                </Box>
            </Box>

            {/* Нижняя часть: Баланс */}
            <Box>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                    Баланс
                </Typography>
                <Typography variant="h3" fontWeight="800" sx={{ lineHeight: 1 }}>
                    {balance} {currencySymbol}
                </Typography>
            </Box>

            {/* Декоративные элементы (блики) */}
            <Box 
                sx={{
                    position: 'absolute',
                    top: -50,
                    right: -50,
                    width: 150,
                    height: 150,
                    borderRadius: '50%',
                    bgcolor: 'rgba(255,255,255,0.1)',
                    zIndex: 0
                }}
            />
            <Box 
                sx={{
                    position: 'absolute',
                    bottom: -30,
                    left: -30,
                    width: 100,
                    height: 100,
                    borderRadius: '50%',
                    bgcolor: 'rgba(255,255,255,0.05)',
                    zIndex: 0
                }}
            />
        </Box>
    );
};

