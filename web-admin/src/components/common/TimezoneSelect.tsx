import React from 'react';
import { TextField, MenuItem } from '@mui/material';
import { useTranslation } from 'react-i18next';

const TIMEZONES = [
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

