import React, { useEffect, useState } from 'react';
import { 
    Box, 
    Typography,
    Select,
    MenuItem,
    FormControl,
    Paper,
    InputBase,
    Divider
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { 
    COUNTRY_CODES, 
    DEFAULT_COUNTRY, 
    cleanPhone, 
    formatPhoneWithMask, 
    parsePhoneNumber 
} from '../../utils/phone';
import type { CountryConfig } from '../../utils/phone';

interface PhoneInputProps {
    value: string; // Expects full phone number e.g. +996555123456 or empty
    onChange: (newValue: string) => void; // Returns full phone number
    label?: string;
    error?: boolean;
    helperText?: string;
    fullWidth?: boolean;
    disabled?: boolean;
    required?: boolean;
    size?: 'small' | 'medium';
}

export const PhoneInput: React.FC<PhoneInputProps> = ({
    value,
    onChange,
    label,
    error,
    helperText,
    fullWidth = true,
    disabled = false,
    required = false,
    size = 'medium'
}) => {
    const [country, setCountry] = useState<CountryConfig>(DEFAULT_COUNTRY);
    const [displayValue, setDisplayValue] = useState('');

    // Sync internal state with external value prop
    useEffect(() => {
        if (value) {
            const parsed = parsePhoneNumber(value);
            // Only update country if strictly detected, otherwise keep user selection
            if (value.startsWith(parsed.country.dial)) {
                 setCountry(parsed.country);
            }
            
            // Format the body
            const formatted = formatPhoneWithMask(parsed.rawDigits, parsed.country.mask);
            setDisplayValue(formatted);
        } else {
            setDisplayValue('');
        }
    }, [value]);

    const handleCountryChange = (event: SelectChangeEvent<string>) => {
        const newCode = event.target.value;
        const newCountry = COUNTRY_CODES.find(c => c.code === newCode) || DEFAULT_COUNTRY;
        setCountry(newCountry);
        
        // Recalculate full value with new code but same digits (if possible)
        const rawDigits = cleanPhone(displayValue);
        if (rawDigits) {
            onChange(newCountry.dial + rawDigits);
        } else if (value) {
             // If we had a value but cleared digits? No, if displayValue is empty rawDigits is empty.
             // If there was a value, we try to keep it with new code.
             onChange(newCountry.dial + rawDigits);
        }
    };

    const handlePhoneChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const input = event.target.value;
        // We extract digits.
        const rawDigits = cleanPhone(input);
        
        // Limit length to mask length (count of digits in mask)
        const maxDigits = country.mask.replace(/[^97]/g, '').length;
        const truncatedDigits = rawDigits.slice(0, maxDigits);

        // Format for display
        const formatted = formatPhoneWithMask(truncatedDigits, country.mask);
        setDisplayValue(formatted);

        // Return full E.164 to parent
        if (truncatedDigits) {
            onChange(country.dial + truncatedDigits);
        } else {
            onChange('');
        }
    };

    return (
        <Box width={fullWidth ? "100%" : "auto"}>
            {label && (
                <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block', ml: 0.5 }}>
                    {label}
                </Typography>
            )}
            <Paper
                elevation={0}
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    border: '1px solid',
                    borderColor: error ? 'error.main' : 'divider',
                    borderRadius: 3,
                    px: 1.5,
                    py: size === 'small' ? 0.5 : 1,
                    bgcolor: '#f8fafc',
                    transition: 'all 0.2s',
                    '&:focus-within': {
                        borderColor: 'primary.main',
                        bgcolor: '#fff',
                        boxShadow: '0 0 0 4px rgba(37, 99, 235, 0.1)'
                    }
                }}
            >
                <FormControl variant="standard" size="small" sx={{ minWidth: 'auto' }}>
                    <Select
                        value={country.code}
                        onChange={handleCountryChange}
                        disableUnderline
                        displayEmpty
                        inputProps={{ 'aria-label': 'Country Code' }}
                        renderValue={(selected) => {
                             const c = COUNTRY_CODES.find(cc => cc.code === selected) || DEFAULT_COUNTRY;
                             return (
                                 <Box display="flex" alignItems="center" gap={1}>
                                     <Typography fontSize="1.5rem" lineHeight={1}>{c.emoji}</Typography>
                                     <Typography variant="body1" fontWeight="500" color="text.primary">
                                         {c.dial}
                                     </Typography>
                                 </Box>
                             );
                        }}
                        MenuProps={{
                            PaperProps: {
                                sx: {
                                    maxHeight: 300,
                                    borderRadius: 2,
                                    mt: 1,
                                    boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
                                    '& .MuiMenuItem-root': {
                                        px: 1.5,
                                        py: 1
                                    }
                                }
                            }
                        }}
                        sx={{
                            '& .MuiSelect-select': {
                                display: 'flex',
                                alignItems: 'center',
                                py: 0.5,
                                pr: '24px !important', // space for arrow
                                pl: 0,
                                '&:focus': { backgroundColor: 'transparent' }
                            },
                            '& .MuiSelect-icon': {
                                right: 0
                            }
                        }}
                    >
                        {COUNTRY_CODES.map((c) => (
                            <MenuItem key={c.code} value={c.code}>
                                <Box display="flex" alignItems="center" gap={1.5}>
                                    <Typography fontSize="1.4rem">{c.emoji}</Typography>
                                    <Typography fontWeight="500">{c.name}</Typography>
                                    <Typography color="text.secondary" variant="caption">({c.dial})</Typography>
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                <Divider orientation="vertical" flexItem sx={{ mx: 1.5, my: 0.5, bgcolor: 'divider' }} />

                <InputBase
                    value={displayValue}
                    onChange={handlePhoneChange}
                    placeholder={country.mask.replace(/[97]/g, '0')}
                    fullWidth
                    disabled={disabled}
                    required={required}
                    sx={{ 
                        fontSize: '1.1rem', 
                        fontWeight: 500,
                        color: 'text.primary',
                        '& input::placeholder': {
                            color: 'text.disabled',
                            opacity: 0.5
                        }
                    }}
                />
            </Paper>
            {helperText && (
                <Typography variant="caption" color={error ? "error" : "text.secondary"} sx={{ mt: 0.5, ml: 1 }}>
                    {helperText}
                </Typography>
            )}
        </Box>
    );
};
