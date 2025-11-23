import axios from 'axios';
import i18n from '../i18n';

// Адрес твоего Ktor сервера
// Важно: Сервер должен быть запущен на порту 8080
const BASE_URL = 'http://localhost:8080';

export const api = axios.create({
    baseURL: BASE_URL,
    headers: {
        'Content-Type': 'application/json',
        'Accept-Language': 'ru' // Язык админки по умолчанию
    }
});

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
        
        // 1. Локализация ошибок (Error Codes Strategy)
        if (error.response?.data?.code) {
            const code = error.response.data.code;
            const key = `errors.${code}`;
            const translated = i18n.t(key);
            
            // Подменяем сообщение об ошибке на локализованное
            // Axios по умолчанию пишет что-то типа "Request failed with status code 400"
            // Мы делаем его понятным для пользователя
            if (translated && translated !== key) {
                error.message = translated;
            }
        }

        const authCodes = ['UNAUTHORIZED', 'TOKEN_EXPIRED', 'TOKEN_INVALID', 'INVALID_CODE', 'CODE_EXPIRED', 'USER_NOT_FOUND'];

        const shouldForceLogout =
            (error.response?.data?.code && authCodes.includes(error.response.data.code)) ||
            (error.response && error.response.status === 401);

        if (shouldForceLogout) {
            // Если это не сам запрос логина (чтобы не зациклить)
            if (!error.config.url.includes('/auth/login')) {
                console.warn('Session expired, logging out...');
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                // Жесткая перезагрузка на страницу входа
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);
