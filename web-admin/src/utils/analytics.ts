import { clarity } from 'react-microsoft-clarity';
const CLARITY_ID = import.meta.env.VITE_CLARITY_ID;

/**
 * Единый объект для управления аналитикой
 */
export const Analytics = {
    /**
     * Инициализация всех систем аналитики.
     * Вызывать ОДИН РАЗ в main.tsx
     */
    init: () => {
        // A. Запускаем Clarity (Тепловые карты + Запись)
        if (CLARITY_ID) {
            clarity.init(CLARITY_ID);
        } else if (import.meta.env.PROD) {
            console.warn('⚠️ Analytics: VITE_CLARITY_ID не задан в .env файле!');
        }
    },

    /**
     * Ручная отправка событий (например, нажатие кнопки)
     */
    track: (eventName: string, params: Record<string, any> = {}) => {
        if (import.meta.env.DEV) {
            console.log(`📊 [Event]: ${eventName}`, params);
        }

        // Отправка в Clarity
        if (clarity.hasStarted()) {
            (clarity as any).event(eventName);
        }
    },

    /**
        * @param userId - Уникальный ID пользователя из твоей базы (строка)
        * @param properties - Доп. данные (email, роль, тариф и т.д.)
        */
       identify: (userId: string, properties: Record<string, any> = {}) => {
           // Логируем локально для проверки
           if (import.meta.env.DEV) {
               console.log(`👤 [Analytics Identity]: User ${userId}`, properties);
           }

           if (clarity.hasStarted()) {
               // 1. Привязываем ID
               clarity.identify(userId, properties);

               // 2. (Опционально) Если нужно фильтровать по роли или тарифу в Clarity,
               // можно задать это как сессионные теги:
               // for (const key in properties) {
               //    clarity.setTag(key, properties[key]);
               // }
           }
       }
};