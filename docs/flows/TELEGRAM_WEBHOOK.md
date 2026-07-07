# Telegram Webhook (login) — как настроить

## Что делает
- Авторизация через Telegram теперь работает **только через webhook** (long polling отключён, чтобы не ловить 409/дубли).
- Эндпоинт вебхука: `POST /auth/telegram/webhook`.
- Опциональный `secret` в query: `/auth/telegram/webhook?secret=...` — если задан, запросы без него отклоняются (403).
- Обрабатываются только `message`-обновления (кнопка /start login_xxx и отправка контакта).

## Что нужно
1. Публичный HTTPS-домен, доступный Telegram (например `https://api.yourdomain.com`).
2. Отдельный токен бота для окружения (prod/stage/dev) — задаётся через переменную/конфиг:
   - `TELEGRAM_BOT_TOKEN` (`telegram.botToken`).
3. URL вебхука и (желательно) секрет:
   - `TELEGRAM_WEBHOOK_URL` (`telegram.webhookUrl`), например `https://api.yourdomain.com/auth/telegram/webhook`.
   - `TELEGRAM_WEBHOOK_SECRET` (`telegram.webhookSecret`), любая строка. Если задан, Telegram должен добавлять `?secret=<...>` в URL.
4. Один инстанс на один токен: нельзя использовать один и тот же токен одновременно в другом сервисе или в long polling.

## Как это работает при старте
- Сервис при старте вызывает `setWebhook` с `telegram.webhookUrl` (+ `secret`, если указан).
- Long polling не используется.
- Health-check: сервис периодически делает `getMe` для диагностики.

## Проверка
1) В логах при старте: `setWebhook OK: <url>`.
2) Отправьте `/start login_<uuid>` боту (uuid выдаёт `POST /auth/telegram/start`), должно прийти приглашение отправить контакт.
3) Отправьте контакт — сессия должна перейти в `CONFIRMED`.

## Переменные окружения (Railway/ENV)
- `TELEGRAM_BOT_TOKEN` — токен бота.
- `TELEGRAM_WEBHOOK_URL` — полный публичный URL вебхука.
- `TELEGRAM_WEBHOOK_SECRET` — секрет для query (?secret=...).

## Замечания
- Если вебхук не настроен (URL пуст), логгер выдаст ошибку, авторизация через Telegram работать не будет.
- При смене домена/URL обновите переменную и перезапустите сервис (вызов `setWebhook` произойдёт автоматически).

