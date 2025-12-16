import axios from 'axios';
import i18n from '../i18n';

// --- CONFIG ---
// Адрес твоего Ktor сервера
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const IS_DEV = import.meta.env.DEV; // Флаг разработки от Vite

export const API_BASE_URL = BASE_URL;
export const WS_BASE_URL = BASE_URL.replace('http', 'ws');

// --- LOGGER HELPER ---
// Объявляем логгер, чтобы он работал только в режиме разработки
const logger = {
    log: (...args: any[]) => IS_DEV && console.log(...args),
    warn: (...args: any[]) => IS_DEV && console.warn(...args),
    error: (...args: any[]) => IS_DEV && console.error(...args),
};

const resolveInitialLanguage = (): string => {
    if (typeof window !== 'undefined' && window.localStorage) {
        return localStorage.getItem('lang') || i18n.language || 'ru';
    }
    return i18n.language || 'ru';
};
const initialLanguage = resolveInitialLanguage();

export const api = axios.create({
    baseURL: BASE_URL,
    headers: {
        'Content-Type': 'application/json',
        'Accept-Language': initialLanguage
    }
});
api.defaults.headers.common['Accept-Language'] = initialLanguage;

const performLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('workspaces'); // Не забываем чистить воркспейсы
    window.location.href = '/login';
};

const refreshableStatuses = [401];
const refreshableCodes = ['TOKEN_EXPIRED', 'TOKEN_INVALID', 'UNAUTHORIZED'];
let refreshPromise: Promise<string> | null = null;

const requestRefresh = async (): Promise<string> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
        throw new Error('Refresh token missing');
    }

    const response = await axios.post(
        `${BASE_URL}/auth/refresh`,
        { refreshToken },
        {
            headers: {
                'Content-Type': 'application/json',
                'Accept-Language': i18n.language
            }
        }
    );

    const { accessToken, refreshToken: newRefreshToken, workspaces } = response.data;
    localStorage.setItem('accessToken', accessToken);
    if (newRefreshToken) {
        localStorage.setItem('refreshToken', newRefreshToken);
    }
    if (workspaces) {
        localStorage.setItem('workspaces', JSON.stringify(workspaces));
    }
    api.defaults.headers.common.Authorization = `Bearer ${accessToken}`;

    return accessToken;
};

const refreshAccessToken = () => {
    if (!refreshPromise) {
        refreshPromise = requestRefresh()
            .catch((error) => {
                throw error;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }
    return refreshPromise;
};

// --- HELPER: Device Signals ---
const getDeviceId = (): string => {
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
        // Modern browsers
        if (typeof crypto !== 'undefined' && crypto.randomUUID) {
            deviceId = crypto.randomUUID();
        } else {
            // Fallback for older browsers
            deviceId = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
                const r = (Math.random() * 16) | 0,
                    v = c === 'x' ? r : (r & 0x3) | 0x8;
                return v.toString(16);
            });
        }
        localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
};

// --- ИНТЕРЦЕПТОР ЗАПРОСА ---
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    config.headers['Accept-Language'] = i18n.language;

    // Device Signals for Prelude
    config.headers['X-Device-Id'] = getDeviceId();
    config.headers['X-Device-Platform'] = 'web';
    config.headers['X-Device-Model'] = navigator.userAgent; // Browser User Agent
    config.headers['X-Os-Version'] = navigator.platform; // OS Platform
    config.headers['X-App-Version'] = '1.0.0'; // Web Admin Version
    
    // Timezone
    try {
        config.headers['X-Timezone-Id'] = Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch (e) {
        config.headers['X-Timezone-Id'] = 'UTC';
    }

    return config;
}, (error) => {
    return Promise.reject(error);
});

// --- ИНТЕРЦЕПТОР ОТВЕТА ---
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config || {};

        // 1. ЛОГИРОВАНИЕ (Теперь logger определен и ошибки не будет)
        logger.warn('[API] Error response', {
            status: error.response?.status,
            code: error.response?.data?.code,
            url: originalRequest?.url,
        });

        // 2. ЛОКАЛИЗАЦИЯ
        if (error.response?.data?.code) {
            const code = error.response.data.code;
            const message = error.response.data.message;

            if (code === 'SMS_PROVIDER_ERROR') {
                const preludeKey = `errors.prelude.${message || 'try_later'}`;
                error.message = i18n.exists(preludeKey)
                    ? i18n.t(preludeKey)
                    : i18n.t('errors.SMS_PROVIDER_ERROR');
            } else {
                const key = `errors.${code}`;
                if (i18n.exists(key)) {
                    error.message = i18n.t(key);
                }
            }
        }

        const code = error.response?.data?.code;
        const status = error.response?.status;
        const isLoginOrRefresh = originalRequest?.url?.includes('/auth/login') || originalRequest?.url?.includes('/auth/refresh');

        const canAttemptRefresh =
            !isLoginOrRefresh &&
            !originalRequest._retry &&
            (refreshableStatuses.includes(status) || (code && refreshableCodes.includes(code)));

        // 3. ПОПЫТКА ОБНОВИТЬ ТОКЕН
        if (canAttemptRefresh) {
            Object.defineProperty(originalRequest, '_retry', { value: true, writable: true, enumerable: true, configurable: true });
            try {
                logger.warn('[API] Trying to refresh access token…');
                const newToken = await refreshAccessToken();
                if (newToken) {
                    originalRequest.headers = originalRequest.headers || {};
                    originalRequest.headers.Authorization = `Bearer ${newToken}`;
                    logger.warn('[API] Token refreshed, repeating request', originalRequest?.url);
                    return api(originalRequest);
                }
            } catch (refreshError) {
                logger.warn('[API] Refresh failed, forcing logout');
                performLogout();
                return Promise.reject(refreshError);
            }
        }

        const authCodes = ['UNAUTHORIZED', 'TOKEN_INVALID', 'INVALID_CODE', 'CODE_EXPIRED', 'USER_NOT_FOUND'];
        const shouldForceLogout =
            (code && authCodes.includes(code)) ||
            status === 401;

        if (shouldForceLogout && !isLoginOrRefresh) {
            performLogout();
        }

        return Promise.reject(error);
    }
);