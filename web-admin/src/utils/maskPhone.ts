export const maskPhone = (phone: string | null | undefined): string => {
    if (!phone) return '';
    // Keep first 6 and last 2, mask the rest.
    // Example: +996 554 190 030 -> +996 554 *** *30
    // Simple implementation:
    if (phone.length < 8) return phone;
    
    // Assuming format is mostly clean or with spaces
    // Let's just keep first 7 chars and last 2 chars visible
    // +996554...
    
    const visibleStart = 7;
    const visibleEnd = 2;
    
    if (phone.length <= visibleStart + visibleEnd) return phone;
    
    const start = phone.substring(0, visibleStart);
    const end = phone.substring(phone.length - visibleEnd);
    const middle = '*'.repeat(phone.length - visibleStart - visibleEnd);
    
    return `${start}${middle}${end}`;
};

