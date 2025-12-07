
export const formatCurrency = (amount: number | string | undefined | null, currencyCode: string | undefined | null): string => {
    if (amount === undefined || amount === null) return '';
    
    const value = amount.toString();
    const code = currencyCode?.toUpperCase() || '';

    switch (code) {
        case 'KGS':
            return `${value}c`;
        case 'RUB':
            return `${value}₽`;
        case 'USD':
            return `$${value}`;
        case 'EUR':
            return `€${value}`;
        case 'KZT':
            return `${value} ₸`;
        case 'UZS':
            return `${value} sum`;
        case 'BYN':
            return `${value} Br`;
        default:
            // Если валюта не распознана или пустая, просто возвращаем значение + код (если есть)
            return code ? `${value} ${code}` : value;
    }
};

