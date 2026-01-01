import React from 'react';
import { TextField, MenuItem } from '@mui/material';
import { useTranslation } from 'react-i18next';

const TIMEZONES = [
    // Russia (all regions)
    { value: 'Europe/Kaliningrad', label: '🇷🇺 Kaliningrad (UTC+2)' },
    { value: 'Europe/Moscow', label: '🇷🇺 Moscow (UTC+3)' },
    { value: 'Europe/Samara', label: '🇷🇺 Samara (UTC+4)' },
    { value: 'Asia/Yekaterinburg', label: '🇷🇺 Yekaterinburg (UTC+5)' },
    { value: 'Asia/Omsk', label: '🇷🇺 Omsk (UTC+6)' },
    { value: 'Asia/Krasnoyarsk', label: '🇷🇺 Krasnoyarsk (UTC+7)' },
    { value: 'Asia/Irkutsk', label: '🇷🇺 Irkutsk (UTC+8)' },
    { value: 'Asia/Yakutsk', label: '🇷🇺 Yakutsk (UTC+9)' },
    { value: 'Asia/Vladivostok', label: '🇷🇺 Vladivostok (UTC+10)' },
    { value: 'Asia/Magadan', label: '🇷🇺 Magadan (UTC+11)' },
    { value: 'Asia/Kamchatka', label: '🇷🇺 Kamchatka (UTC+12)' },

    // Existing
    { value: 'Asia/Bishkek', label: '🇰🇬 Bishkek (UTC+6)' },
    { value: 'Asia/Almaty', label: '🇰🇿 Almaty (UTC+5)' },
    { value: 'Asia/Tashkent', label: '🇺🇿 Tashkent (UTC+5)' },
    { value: 'Europe/Minsk', label: '🇧🇾 Minsk (UTC+3)' },
    { value: 'UTC', label: '🌍 UTC' },
];

interface TimezoneSelectProps {
    value: string;
    onChange: (value: string) => void;
    label?: string;
    fullWidth?: boolean;
    disabled?: boolean;
}

export const TimezoneSelect: React.FC<TimezoneSelectProps> = ({ 
    value, 
    onChange, 
    label, 
    fullWidth = true,
    disabled = false
}) => {
    const { t } = useTranslation();

    return (
        <TextField
            select
            label={label || t('settings.timezone_label', 'Timezone')}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            fullWidth={fullWidth}
            variant="outlined"
            disabled={disabled}
        >
            {TIMEZONES.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                    {option.label}
                </MenuItem>
            ))}
        </TextField>
    );
};

