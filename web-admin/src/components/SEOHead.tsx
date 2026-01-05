import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';

const keywordsMap: Record<string, string> = {
  ru: 'система лояльности, электронные карты, wallet, программа лояльности для кофейни, crm для бизнеса, кэшбэк сервис, удержание клиентов, b2c лояльность, loyaltyloops',
  en: 'loyalty program, digital loyalty cards, wallet passes, cashback system, customer retention, small business crm, loyalty app, loyaltyloops',
  ky: 'берилгендик программасы, электрондук карталар, арзандатуу, кардарларды тартуу, лоялдуулук тиркемеси, loyaltyloops',
  uz: 'sodiqlik dasturi, bonus kartalari, keshbek tizimi, mijozlarni saqlash, savdoni oshirish, loyaltyloops',
  kk: 'адалдық бағдарламасы, бонустық карталар, кэшбэк жүйесі, электронды карталар, бизнеске адалдық, loyaltyloops',
  be: 'праграма лаяльнасці, электронныя карты, кешбэк сэрвіс, утрыманне кліентаў, loyaltyloops'
};

export const SEOHead = () => {
  const { i18n } = useTranslation();
  const currentKeywords = keywordsMap[i18n.language] || keywordsMap.ru;

  return (
    <Helmet>
      <meta name="keywords" content={currentKeywords} />
    </Helmet>
  );
};

