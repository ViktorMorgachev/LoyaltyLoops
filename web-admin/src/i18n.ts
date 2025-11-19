import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { resources } from './i18n/resources';

i18n
  .use(LanguageDetector) // Определяем язык браузера
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'ru', // Язык по умолчанию
    debug: true,
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'], // Сначала смотрим, что выбрал юзер ранее
      caches: ['localStorage'],
    },
  });

export default i18n;