import axios from 'axios';
import i18n from '../i18n';

// Адрес твоего Ktor сервера
// В продакшене он должен быть задан через переменную окружения VITE_API_URL
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const IS_DEV = import.meta.env.DEV ?? true;
export const API_BASE_URL = BASE_URL;
export const WS_BASE_URL = BASE_URL.replace('http', 'ws');
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

// --- ИНТЕРЦЕПТОР ЗАПРОСА ---
// Автоматически добавляет токен, если он есть в localStorage
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    // Обновляем заголовок языка при каждом запросе (если пользователь сменил язык)
    config.headers['Accept-Language'] = i18n.language;
    
    return config;
}, (error) => {
    return Promise.reject(error);
});

// --- ИНТЕРЦЕПТОР ОТВЕТА ---
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config || {};
        if (IS_DEV) {
            console.warn(
                '[API] Error response',
                {
                    status: error.response?.status,
                    code: error.response?.data?.code,
                    url: originalRequest?.url,
                }
            );
        }

        // Локализация ошибок
        if (error.response?.data?.code) {
            const code = error.response.data.code;
            const key = `errors.${code}`;
            const translated = i18n.t(key);
            if (translated && translated !== key) {
                error.message = translated;
            }
        }

        const code = error.response?.data?.code;
        const status = error.response?.status;
        const isLoginOrRefresh = originalRequest?.url?.includes('/auth/login') || originalRequest?.url?.includes('/auth/refresh');

        const canAttemptRefresh =
            !isLoginOrRefresh &&
            !originalRequest._retry &&
            (refreshableStatuses.includes(status) || (code && refreshableCodes.includes(code)));

        if (canAttemptRefresh) {
            originalRequest._retry = true;
            try {
                if (IS_DEV) {
                    console.warn('[API] Trying to refresh access token…');
                }
                const newToken = await refreshAccessToken();
                if (newToken) {
                    originalRequest.headers = originalRequest.headers || {};
                    originalRequest.headers.Authorization = `Bearer ${newToken}`;
                    if (IS_DEV) {
                        console.warn('[API] Token refreshed, repeating request', originalRequest?.url);
                    }
                    return api(originalRequest);
                }
            } catch (refreshError) {
                if (IS_DEV) {
                    console.warn('[API] Refresh failed, forcing logout');
                }
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
