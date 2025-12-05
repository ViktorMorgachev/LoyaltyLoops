export const SystemEventType = {
    LOGIN: 'LOGIN',
    REGISTER: 'REGISTER',
    SMS_REQUEST: 'SMS_REQUEST',
    ACCRUAL: 'ACCRUAL',
    REDEMPTION: 'REDEMPTION',
    TIER_CHANGE: 'TIER_CHANGE',
    VISIT: 'VISIT',
    ERROR: 'ERROR',
    INFO: 'INFO',
    OTP_VERIFICATION_FAILED: 'OTP_VERIFICATION_FAILED',
    PIN_CHANGE_SUCCESS: 'PIN_CHANGE_SUCCESS',
    PIN_RESET_REQUEST: 'PIN_RESET_REQUEST',
    PIN_RESET_SUCCESS: 'PIN_RESET_SUCCESS',
    PIN_VERIFICATION_FAILED: 'PIN_VERIFICATION_FAILED'
} as const;

export type SystemEventType = typeof SystemEventType[keyof typeof SystemEventType];

export interface SystemEvent {
    id: string;
    type: SystemEventType;
    userId?: string;
    userPhone?: string;
    partnerId?: string;
    payload?: string;
    timestamp: number;
}

export interface SystemEventFilter {
    type?: SystemEventType | null;
    userId?: string;
    userPhone?: string;
    partnerId?: string;
    from?: number;
    to?: number;
    limit: number;
    offset: number;
}
