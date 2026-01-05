import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

const keywordsMap: Record<string, string> = {
  ru: 'цифровые карты лояльности, электронные карты лояльности, замена пластиковых карт, цифровые штамп-карты, программа лояльности CRM, внедрение системы лояльности, мобильные приложения лояльности, виртуальные карты лояльности, интеграция с CRM, персонализация предложений, сегментация клиентов, замена бумажных штамп-карт, карты Wallet лояльности, автоматизированные награды, персонализированные вознаграждения, повышение лояльности клиентов, системы лояльности для бизнеса, CRM для удержания клиентов, цифровая трансформация лояльности, расширение бизнеса через лояльность',
  en: 'digital loyalty cards, electronic loyalty cards, replacement of plastic cards, digital stamp cards, loyalty program CRM, implementation of loyalty system, mobile loyalty apps, virtual loyalty cards, integration with CRM, personalization of offers, customer segmentation, replacement of paper stamp cards, Wallet loyalty cards, automated rewards, personalized rewards, increasing customer loyalty, loyalty systems for business, CRM for customer retention, digital transformation of loyalty, business expansion through loyalty',
  ky: 'санариптик лоялдук карталары, электрондук лоялдук карталары, пластикалык карталарды алмаштыруу, санариптик штамп-карталары, лоялдук программасы CRM, лоялдук системасын киргизүү, мобилдик лоялдук тиркемелери, виртуалдык лоялдук карталары, CRM менен интеграция, сунуштарды персоналдаштыруу, кардарларды сегментациялоо, кагаз штамп-карталарды алмаштыруу, Wallet лоялдук карталары, автоматташтырылган сыйлыктар, персоналдаштырылган сыйлыктар, кардарлардын лоялдугун жогорулатуу, бизнес үчүн лоялдук системалары, кардарларды сактоо үчүн CRM, лоялдуктун санариптик трансформациясы, лоялдук аркылуу бизнесин кеңейтүү',
  uz: 'raqamli sadoqat kartalari, elektron sadoqat kartalari, plastik kartalarni almashtirish, raqamli shtamp-kartalari, sadoqat dasturi CRM, sadoqat tizimini joriy etish, mobil sadoqat ilovalari, virtual sadoqat kartalari, CRM bilan integratsiya, takliflarni shaxsiylashtirish, mijozlarni segmentatsiya qilish, qog\'oz shtamp-kartalarni almashtirish, Wallet sadoqat kartalari, avtomatlashtirilgan mukofotlar, shaxsiylashtirilgan mukofotlar, mijozlar sadoqatini oshirish, biznes uchun sadoqat tizimlari, mijozlarni saqlash uchun CRM, sadoqatni raqamli transformatsiya, sadoqat orqali biznesni kengaytirish',
  kk: 'цифрлық адалдық карталары, электрондық адалдық карталары, пластикалық карталарды ауыстыру, цифрлық мөр карталары, адалдық бағдарламасы CRM, адалдық жүйесін енгізу, мобильді адалдық қосымшалары, виртуалдық адалдық карталары, CRM-мен интеграция, ұсыныстарды жекелеу, тұтынушыларды сегментациялау, қағаз мөр карталарын ауыстыру, Wallet адалдық карталары, автоматтандырылған сыйақылар, жеке сыйақылар, тұтынушылар адалдығын арттыру, бизнес үшін адалдық жүйелері, тұтынушыларды ұстау үшін CRM, адалдықтың цифрлық трансформациясы, адалдық арқылы бизнесті кеңейту',
  be: 'лічбавыя карты лаяльнасці, электронныя карты лаяльнасці, замена пластыкавых карт, лічбавыя штамп-карты, праграма лаяльнасці CRM, ўвядзенне сістэмы лаяльнасці, мабільныя прыкладанні лаяльнасці, віртуальныя карты лаяльнасці, інтэграцыя з CRM, персаналізацыя прапаноў, сегментацыя кліентаў, замена папяровых штамп-карт, карты Wallet лаяльнасці, аўтаматызаваныя ўзнагароды, персаналізаваныя ўзнагароды, павышэнне лаяльнасці кліентаў, сістэмы лаяльнасці для бізнесу, CRM для ўтрымання кліентаў, лічбавая трансфармацыя лаяльнасці, пашырэнне бізнесу праз лаяльнасць'
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

