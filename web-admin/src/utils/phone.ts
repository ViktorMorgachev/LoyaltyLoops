export interface CountryConfig {
    code: string;
    dial: string;
    mask: string; // e.g. '999 99 99 99' where 9 is digit
    name: string;
    emoji: string;
}

export const COUNTRY_CODES: CountryConfig[] = [
    { code: 'KG', dial: '+996', mask: '999 99-99-99', name: 'Kyrgyzstan', emoji: '🇰🇬' },
    { code: 'KZ', dial: '+7', mask: '777 777-77-77', name: 'Kazakhstan', emoji: '🇰🇿' },
    { code: 'UZ', dial: '+998', mask: '99 999-99-99', name: 'Uzbekistan', emoji: '🇺🇿' },
    { code: 'BY', dial: '+375', mask: '99 999-99-99', name: 'Belarus', emoji: '🇧🇾' },
];

export const DEFAULT_COUNTRY = COUNTRY_CODES[0]; // KG

/**
 * Очищает строку от всего, кроме цифр
 */
export const cleanPhone = (input: string): string => {
    return input.replace(/\D/g, '');
};

/**
 * Форматирует "сырые" цифры согласно маске
 * @param rawDigits только цифры номера (без кода страны, если он отделен)
 * @param mask маска (например "999 99 99 99")
 */
export const formatPhoneWithMask = (rawDigits: string, mask: string): string => {
    let result = '';
    let digitIndex = 0;

    for (let i = 0; i < mask.length && digitIndex < rawDigits.length; i++) {
        const maskChar = mask[i];
        if (maskChar === '9' || maskChar === '7') { // 9 and 7 are placeholders in our masks
            result += rawDigits[digitIndex];
            digitIndex++;
        } else {
            result += maskChar;
            // Если пользователь ввел достаточно цифр, чтобы "перешагнуть" через разделитель, добавляем его.
            // Но если цифры кончились, разделитель в конце не нужен (обычно).
            // Хотя для UX лучше добавлять разделитель, если мы до него дошли.
        }
    }
    return result;
};

/**
 * Пытается определить страну по полному номеру телефона (e.g. +996555...)
 */
export const detectCountry = (fullPhone: string): CountryConfig | undefined => {
    if (!fullPhone) return undefined;
    // Сортируем по длине кода (от длинного к короткому), чтобы +996 не перепутать с +9 (если такой будет)
    // В нашем случае +7 для KZ.

    // Сначала ищем точное совпадение начала
    const candidates = COUNTRY_CODES.filter(c => fullPhone.startsWith(c.dial));

    if (candidates.length === 1) return candidates[0];
    if (candidates.length > 1) {
        // Конфликт (например KZ и еще кто-то с +7).
        // Сейчас только KZ с +7.
        // KZ: +7 7...
        // Если вдруг появится еще кто-то, логику усложним.
        return candidates[0];
    }

    return undefined;
};

/**
 * Валидирует длину номера по маске страны
 */
export const isValidPhone = (rawDigits: string, country: CountryConfig): boolean => {
    const requiredLength = country.mask.replace(/[^97]/g, '').length;
    return rawDigits.length === requiredLength;
};

/**
 * Преобразует полный номер (+996...) в объект { country, rawDigits }
 */
export const parsePhoneNumber = (fullPhone: string) => {
    if (!fullPhone) return { country: DEFAULT_COUNTRY, rawDigits: '' };
    
    const detected = detectCountry(fullPhone);
    
    if (detected) {
        const rawDigits = fullPhone.slice(detected.dial.length);
        return { country: detected, rawDigits };
    }
    
    // Fallback: If no country detected (e.g. local format like 0550...),
    // return default country and the FULL string as raw digits.
    return { country: DEFAULT_COUNTRY, rawDigits: fullPhone };
};
