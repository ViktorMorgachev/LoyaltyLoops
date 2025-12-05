import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useUser } from '../context/UserContext';
import { api } from '../api/axiosConfig';

export const AuthSync = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const { refreshUser } = useUser();

    useEffect(() => {
        // 1. Проверяем, есть ли токены в URL
        const accessToken = searchParams.get('accessToken');
        const refreshToken = searchParams.get('refreshToken');

        if (accessToken && refreshToken) {
            console.log('🔄 Mobile Auth Sync detected');

            // 2. Сохраняем в LocalStorage
            localStorage.setItem('accessToken', accessToken);
            localStorage.setItem('refreshToken', refreshToken);

            // 3. Обновляем дефолтные заголовки axios (на всякий случай)
            api.defaults.headers.common.Authorization = `Bearer ${accessToken}`;

            // 4. Очищаем URL от токенов (визуальная безопасность)
            // Удаляем параметры, но оставляем остальные (если есть)
            searchParams.delete('accessToken');
            searchParams.delete('refreshToken');
            setSearchParams(searchParams);

            // 5. Заставляем UserContext обновить данные юзера
            refreshUser().then(() => {
                console.log('✅ Auth Sync complete');
            }).catch((err) => {
                console.error('❌ Auth Sync failed (token might be expired)', err);
                // Если токен протух, Axios interceptor сам попробует его обновить
                // через refreshToken, который мы только что сохранили.
            });
        }
    }, [searchParams, setSearchParams, refreshUser]);

    return null; // Этот компонент ничего не рисует
};