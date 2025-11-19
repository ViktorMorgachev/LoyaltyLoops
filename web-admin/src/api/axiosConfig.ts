import axios from 'axios';

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
    return config;
}, (error) => {
    return Promise.reject(error);
});

// --- ИНТЕРЦЕПТОР ОТВЕТА ---
// Если токен протух (401), выкидываем на логин
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        if (error.response && error.response.status === 401) {
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