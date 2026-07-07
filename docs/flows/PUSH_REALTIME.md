# Push & Realtime Flow

## Overview
Цель feature — чтобы клиентское приложение мгновенно узнавало о начислениях/списаниях и получало push-уведомления, даже когда оно в фоне. Решение состоит из двух частей:

- **Realtime (WebSocket)** – используется, когда приложение активно. После `processTransaction` сервер пушит событие в WebSocket (`/ws/cards`). Клиент сразу обновляет карточку и играет анимацию.
- **Push Notifications (FCM)** – для фонового режима. Токен устройства хранится на сервере с привязкой `(user, platform, role, workspaceId)`. После транзакции сервер (позже) будет отправлять FCM push. На Android 13+ уведомления требуют runtime‑разрешения.

## Backend
### Таблица `device_tokens`
```kotlin
val platform = varchar("platform", 16)
val role = varchar("role", 32)
val workspaceId = varchar("workspace_id", 64).default("")
val token = varchar("token", 512)
```
Уникальный индекс по `(user_id, platform, role, workspaceId)`. Это гарантирует, что при переключении роли или выходе из воркспейса токен перезаписывается (upsert).

### Endpoints
- `POST /client/device-token` — принимает `RegisterDeviceTokenRequest { token, platform, role, workspaceId }`, сохраняет токен (idempotent).
- `DELETE /client/device-token` — удаляет токен для текущего контекста.
Оба эндпоинта защищены `auth-jwt`.

### WebSocket `/ws/cards`
- Клиент подключается с query `token=<access token>`.
- Сервер валидирует токен и регистрирует сессию в `CardRealtimeService`.
- После `TransactionService.processTransaction` вызываем `realtimeService.notifyUser(card.userId, CardRealtimePayload)`; всем активным сессиям отправляется JSON:
```json
{
  "cardId": "...",
  "successType": "POINTS_EARNED",
  "args": ["55.0"],
  "newBalance": 420.5,
  "newVisits": 7
}
```

## Android/KMP
### Push токен
1. Любой экран, который подтягивает профиль (`SplashScreenModel`, `LoginScreenModel`, `OnboardingScreenModel`, `ProfileScreenModel`), после успешного `getProfile()` вызывает `pushService.register()`. Сервис сам следит, чтобы регистрация была идемпотентной.
2. `AndroidPushService`:
   - Получает FCM токен, создаёт `NotificationChannels`.
   - Отправляет `POST /client/device-token`.
   - Подписывается на `SessionManager.currentWorkspace` и при изменении роли/воркспейса повторно синхронизирует токен.
   - При logout → `unregister()` вызывает `DELETE /client/device-token` и чистит токен в FCM.
3. `MainActivity` на Android 13+ запрашивает `POST_NOTIFICATIONS`; сервис проверяет разрешение перед показом уведомления.

### Realtime клиент
1. `DefaultCardRealtimeService` использует `HttpClient.webSocketSession` для `/ws/cards`.
2. `WalletScreenModel`:
   - После `getMyCards()` вызывает `connect(token, cards.map { it.id }) { reconnect() }`, чтобы в случае разрыва автоматически переподключиться.
   - Подписывается на `realtimeService.events` и прокидывает их в `_cardEvents` для анимаций.
   - В `onDispose()` закрывает WebSocket.

### Deeplink из пуша
`LoyaltyFirebaseMessagingService` добавляет `cardId` в интент. `MainActivity` перехватывает intent и пушит новый экран `LoyaltyCardDetailsScreen(cardId)` через `NavigatorHolder`.

## Проверка
1. **Регистрация токена**: войти клиентом → в логах backend увидеть `device_tokens` upsert.
2. **Runtime permission**: на устройстве с Android 13 появится системный диалог. Отказать → уведомление не показывается, в логах `Skipping notification`.
3. **Realtime**: выпустить транзакцию через терминал → UI клиента покажет анимацию без перезапуска.
4. **Push deeplink**: отправить моковое FCM сообщение с `cardId` → при тапе открывается `LoyaltyCardDetailsScreen`.

## TODO / Next Steps
- Отправка реальных FCM сообщений из backend (через Firebase HTTP v1).
- Наполнение `LoyaltyCardDetailsScreen` (история операций, баланс, визиты).
- Реализация iOS-ветки (APNs + FirebaseMessaging iOS).

