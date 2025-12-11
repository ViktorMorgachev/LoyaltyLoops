# Логика рейтингов

## Отзывы о точке (клиент → сервис)
- **Вход**: `rating` (1–5 звёзд), теги (`ServiceReviewTag`), опциональный комментарий.  
- **Формула (на отзыв)**: `score = звёзды + сумма(весов тегов)` → ограничиваем 0..5 при усреднении.  
- **Веса тегов** (по умолчанию; можно переопределить через `rating.tags.service.*` или env `RATING_TAGS_SERVICE_*`):
  - Негатив: `RUDE_STAFF -2.0`, `DIRTY -1.0`, `SLOW -1.0`, `WAIT_TIME -0.5`, `PRICEY -0.5`
  - Позитив: `FRIENDLY +1.5`, `TASTY +1.0`, `ATTENTIVE +1.0`, `FAST +0.5`, `CLEAN +0.5`, `COMFORT +0.5`
- **Рейтинг точки**: среднее итоговых оценок.  
- **NPS**: считаем по звёздам (1–5 шкала: промоутер = 5, нейтрал = 4, детрактор = 1–3).  
- **Heatmap/графики**: используют те же итоговые оценки и веса, агрегированные по датам/точкам.

## Оценка клиента (кассир → клиент)
- **Вход**: `rating` (1–5 звёзд), теги (`ClientRatingTag`), опциональный комментарий.  
- **Формула (на отзыв)**: `score = звёзды + сумма(весов тегов, кроме FRAUD)`; усредняем по последним ~20 отзывам, clamp 0..5.  
- **Веса тегов** (по умолчанию; можно переопределить через `rating.tags.client.*` или env `RATING_TAGS_CLIENT_*`):
  - Негатив: `ABUSE -2.0`, `AGGRESSION -1.0`, `NO_PAYMENT -1.0`
  - Нейтраль/флаги: `FRAUD 0.0` (ставит `fraudFlag=true`, в математику не идёт), `NONE 0.0`
  - Позитив: `TIP +1.5`, `FRIENDLY +1.5`, `POLITE +1.0`
- **Trust Score**: среднее (звёзды + веса) по последним отзывам, clamp 0..5.  
  - **RiskLevel**: `BLACK`, если `fraudFlag=true`; иначе `GREEN ≥4.5`, `YELLOW ≥3.5`, `ORANGE ≥2.0`, `RED <2.0`.
- **Анти-абьюз**:  
  - Кулдаун (1 отзыв/день на кассира→клиента) через `features.rating.enableCooldown` (env `FEATURE_RATING_ENABLE_COOLDOWN`, по умолчанию true).  
  - Защита от “мстительной 1★”: если текущий `trustScore ≥ 4.5` и прилетела 1★ без `FRAUD`, игнорируем и логируем.
- **Хранение/использование**: сохраняется в `LoyaltyCard` (`trustScore`, `riskLevel`, `fraudFlag`), возвращается в `TrustScoreDto`.

## Конфигурация
- **Файл**: `application.conf` → секции `rating.tags.client`, `rating.tags.service`, `features.rating.enableCooldown`.  
- **Env‑override**: `RATING_TAGS_CLIENT_*`, `RATING_TAGS_SERVICE_*`, `FEATURE_RATING_ENABLE_COOLDOWN`.  
- **Публичный роут**: `/config` отдаёт клиентам актуальные веса тегов.

