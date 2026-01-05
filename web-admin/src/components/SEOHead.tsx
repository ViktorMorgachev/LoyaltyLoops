import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

const keywordsMap: Record<string, string> = {
  ru: 'система лояльности, электронные карты wallet, программа лояльности для кофейни, crm для бизнеса, кэшбэк сервис, удержание клиентов, loyaltyloops',
  en: 'loyalty program, digital loyalty cards, wallet passes, cashback system, customer retention, small business crm, loyaltyloops',
  ky: 'берилгендик программасы, электрондук карталар, арзандатуу, кардарларды тартуу, лоялдуулук тиркемеси, loyaltyloops',
  uz: 'sodiqlik dasturi, bonus kartalari, keshbek tizimi, mijozlarni saqlash, savdoni oshirish, loyaltyloops',
  kk: 'адалдық бағдарламасы, бонустық карталар, кэшбэк жүйесі, электронды карталар, бизнеске адалдық, loyaltyloops',
  be: 'праграма лаяльнасці, электронныя карты, кешбэк сэрвіс, утрыманне кліентаў, loyaltyloops'
};

export const SEOHead = () => {
  const { i18n } = useTranslation();

  useEffect(() => {
    const currentKeywords = keywordsMap[i18n.language] || keywordsMap.ru;
    let tag = document.querySelector('meta[name="keywords"]') as HTMLMetaElement | null;
    if (!tag) {
      tag = document.createElement('meta');
      tag.name = 'keywords';
      document.head.appendChild(tag);
    }
    tag.content = currentKeywords;
  }, [i18n.language]);

  return null;
};

