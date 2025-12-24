# E2E Test Cases (Web + Mobile, все роли)

## Общие предпосылки
- UTC везде: `SHOW timezone` = UTC, логи в UTC, контейнеры с `TZ=UTC`, JVM `-Duser.timezone=UTC`.
- Всегда указывать `X-Workspace-Id` (PartnerId для владельца/менеджера; PointId для кассира). Для терминала/аналитики — ещё `X-Timezone-Id`.
- Валидные access/refresh токены; WS подключать с `?token=<accessToken>`.

## 1. Аутентификация/сессии
1. `POST /auth/send-code` (валидный/невалидный) → 200 / 400.
2. `POST /auth/verify-code` → access/refresh/workspaces; неверный код → 401.
3. `POST /auth/refresh` → новый access/refresh; невалидный → 401.
4. `GET /client/me` → профиль, локаль, workspaces; без токена → 401.

## 2. Рабочие области
1. Выбор workspace (Web: localStorage.currentWorkspaceId; Mobile: TokenStorage.saveCurrentWorkspaceId).
2. Все запросы несут `X-Workspace-Id`; отсутствие → 400/403/404 по контексту.

## 3. Бизнес (Partner Owner/Admin)
1. Создать бизнес `POST /partners/create` (ownerPin валидный) → 201, PENDING, managerInviteCode сгенерирован.
2. `GET/PUT /partners/me` (название, цвет, валюта, таймзона, visitsTarget≥1) → 200; пустые обязательные поля → 400.
3. PIN:
   - Проверка `POST /partners/verify-pin` (верный/неверный) → 200/401.
   - Обновление `PUT /partners/pin` (старый+новый) → 200; неверный старый → 401; freeze 24h.
   - Сброс `POST /partners/pin/reset` (confirm=true) → PIN очищен, freeze 24h; `POST /partners/pin/reset/confirm` по токену.
4. Partner BLOCKED → все терминальные операции 403.

## 4. Торговые точки
1. Создать `POST /partners/points` → 201, inviteCode кассира.
2. `GET/PUT /partners/points/{id}` (часы, адрес, контакт, валюта, pause) → 200; пустые поля/некорректные значения → 400.
3. `DELETE /partners/points/{id}` → 200.
4. Пауза `temporarilyPaused=true` → терминал scan/calc/process → 403.
5. Статус точки (Platform Admin) `PUT /admin/points/{id}/status` → актив/деактив.

## 5. Персонал
### Менеджеры
1. `GET /partners/managers/invite` → inviteCode.
2. `POST /partners/managers/join` (код) → 200; неверный → 404; повторный → 409.
3. `GET /partners/managers`, `DELETE /partners/managers/{id}` → 200.

### Кассиры
1. `POST /partners/join` (invite точки) → 200.
2. `GET /partners/cashiers` (агрегировано), `GET /partners/points/{id}/cashiers` (по точке) → 200.
3. `DELETE /partners/cashiers/{pointId}/{cashierId}` → 200; чужой/нет связи → 403/404.

## 6. Терминал (кассир, Mobile)
Предпосылки: кассир привязан к точке; карта клиента существует.
1. Scan `POST /terminal/scan` (X-Workspace-Id=pointId, X-Timezone-Id) → карта/баланс/settings; неверный QR/TTL/подпись, paused point, blocked partner → 400/403.
2. Calculate `POST /terminal/calculate` → расчёт без записи; совместимость стратегии; maxBurnPercentage и awardOnMixedPayment учитываются; локальная валюта = валюта точки.
3. Process `POST /terminal/process`:
   - Кассир видит локальную валюту точки.
   - Клиент (WS) получает base + approx (если rate≠1).
   - История: currency=точка, pointsDelta=локально, pointsBaseValue=base.
   - maxBurnPercentage: списание ≤ процент от суммы чека (локальная валюта точки); попытка больше → ошибка/ограничение.
   - awardOnMixedPayment=true: начисление на moneyPaid даже при spend; false — без начисления.
   - Смешанная оплата: корректные successType/args для кассира и клиента.
   - Tier upgrade при достижении threshold.
   - VISIT: учёт visitsTarget, reward, dropVisits.
   - Ошибки доступа/статусов → 403.
4. Stats `GET /terminal/stats` — сумма/кол-во за сутки (UTC).
5. Rate Client `POST /terminal/rate-client` — рейтинг клиента сохранён.

## 7. Мультивалюта и процент оплаты чеком
Сценарий: партнёр base=USD, точка currency=KGS, rate=0.0116.
- Списание ≤ maxBurnPercentage * сумма чека (KGS). Попытка выше → ошибка/ограничение.
- awardOnMixedPayment=true: начисление на moneyPaid даже при spend; false — нет.
- Кассир: всё в KGS; Клиент WS: base (USD) + approx KGS при rate≠1.
- История: currency=KGS, pointsBaseValue=USD.
- Проверить rate=1, rate≈0, чистое earn, чистое spend, смешанный платёж.

## 8. Карты/гео
1. `GET /map/points/search` (lat/lon обязательны; radius/limit/query/openNow/includeInactive/minRating/type[]) → total + items; openNow учитывает расписание/pauses.
2. `GET /map/partners/points` для менеджера → точки его партнёра.

## 9. Отзывы/рейтинги
1. Сводка `GET /partners/reviews/summary` (с timezone) → avgRating, total, stars[].
2. Список отзывов `GET /partners/reviews` (limit/offset) → rating/comment/timestamp.
3. Клиентские рейтинги `GET /partners/client-ratings` → score/reason/timestamp.

## 10. История и аналитика
1. `GET /partners/history` → currency точки, pointsDelta (локально), pointsBaseValue (base), cashierId/pointId корректны.
2. `GET /partners/analytics` (period, timezone) → totalRevenue/totalTransactions/averageCheck + chartData без дыр.

## 11. Поддержка/чат
Партнёр:
- `GET /partners/support/thread` → тред или мок (id="").
- `POST /partners/support/messages` (content not blank) → 201; последующий GET содержит сообщение.
- WS `/ws/support/partner?token=...` → приходят ответы админа.

Админ:
- `GET /admin/support/threads`, `GET /admin/support/threads/{id}` → тред + сообщения.
- `POST /admin/support/threads/{id}/messages` → видно партнёру.
- WS `/ws/support/admin?token=...` → приходят новые сообщения партнёров.

## 12. Realtime карты
- `/ws/cards?token=...` подключается; после `terminal/process` клиент получает `TRANSACTION` payload (base + approx при нужде).

## 13. Админ платформы (Platform Admin/Super Manager/Manager)
1. Партнёры/точки:
   - `GET /admin/partners`, `GET /admin/partners/{id}` — доступ только platform staff; прочие → 403/401.
   - `POST /admin/partners/{id}/status` — статус меняется (BLOCKED/ACTIVE/PENDING).
   - `GET /admin/partners/{id}/points` — список точек.
2. Заявки (platform requests):
   - `GET /platform/requests` (filters) — SUPER_ADMIN/SUPER_MANAGER видят все; MANAGER — свои.
   - `POST /platform/requests/{id}/approve`: ACTIVATE_POINT → подписка, точка активна; BLOCK/UNBLOCK → статус партнёра.
   - `POST /platform/requests/{id}/reject` → REJECTED с reason; попытка для не-PENDING → 400/409.
3. Platform staff:
   - `POST /platform/invite?role=...` по разрешённым ролям.
   - `POST /platform/join` по коду → роль назначена.
   - `GET /platform/staff` (filter role).
   - `DELETE /platform/staff/{id}` — проверки ранга (нельзя удалить Super Admin и т.п.).

## 14. Доступы/ограничения
- Без токена → 401; неверный формат UserId → 401.
- Нет `X-Workspace-Id` там, где нужно → 400/403/404.
- Кассир чужой точки → 403 на scan/calc/process.
- Менеджер без прав owner/admin — не меняет настройки партнёра/точек.
- Партнёр BLOCKED → терминал 403.

## 15. Временные зоны / UTC
- `SHOW timezone` = UTC; timestamps без смещения.
- INSERT `CURRENT_TIMESTAMP` → UTC.
- Аналитика/статистика — сутки по UTC (если бизнес-логика не иная).
# Test Cases (Stage Regression, Web + Mobile)

## Общие предпосылки
- Все сервисы в UTC (`SHOW timezone` = `UTC`, логи в UTC).
- Заголовок `X-Workspace-Id` отправляется при работе с бизнесом/точкой; `X-Timezone-Id` — для терминала/аналитики.
- У пользователя есть валидные access/refresh токены. WS подключаются с `?token=...`.

## 1. Аутентификация
1. Отправка кода: `POST /auth/send-code` с валидным телефоном → 200, код отправлен.
2. Логин: `POST /auth/verify-code` → вернулся `accessToken`, `refreshToken`, `workspaces`.
3. Обновление токена: `POST /auth/refresh` с refresh → новый access, refresh.
4. `/client/me`: возвращает профиль, локаль, workspaces.

## 2. Переключение рабочей области
1. Выбрать workspace в Web/Mobile → `localStorage.currentWorkspaceId` (web) / `TokenStorage.saveCurrentWorkspaceId` (mobile).
2. Любой авторизованный запрос содержит `X-Workspace-Id`; сервер не ругается.

## 3. Партнёр: бизнес и PIN
1. Создать бизнес: `POST /partners/create` (ownerPin валиден) → 201, статус PENDING.
2. Проверка PIN: `POST /partners/verify-pin` с верным/неверным PIN → 200 / 401.
3. Обновить настройки: `PUT /partners/me` (название, цвет, валюта, таймзона) → 200, сохранены.
4. Сброс PIN: `POST /partners/pin/reset` (confirm=true) → PIN очищен, freeze 24h. Подтверждение по токену `/partners/pin/reset/confirm`.
5. Обновить PIN: `PUT /partners/pin` (старый/новый) → 200, freeze 24h.

## 4. Торговые точки
1. Создать точку: `POST /partners/points` → 201, inviteCode выдан.
2. Получить/обновить точку: `GET /partners/points/{id}`, `PUT /partners/points/{id}` (часы, адрес, валюту, паузу) → 200.
3. Удалить точку: `DELETE /partners/points/{id}` → 200, точка исчезает из списка.
4. Статус точки (Admin): `PUT /admin/points/{id}/status` → актив/деактив.

## 5. Персонал
### Менеджеры
1. Получить инвайт: `GET /partners/managers/invite` → inviteCode.
2. Join менеджером: `POST /partners/managers/join` (invite) → 200; повторный → 409.
3. Лист/удаление: `GET /partners/managers`, `DELETE /partners/managers/{id}` → 200, роль деактивирована.

### Кассиры
1. Join кассиром: `POST /partners/join` (invite точки) → 200.
2. Лист кассиров: `GET /partners/cashiers` (агрегировано) и `GET /partners/points/{id}/cashiers` (по точке) → 200.
3. Удаление кассира: `DELETE /partners/cashiers/{pointId}/{cashierId}` → 200; нет доступа — 403/404.

## 6. Терминал (Mobile)
Предусловия: Кассир привязан к точке; карта есть у клиента.
1. Scan QR: `POST /terminal/scan` (X-Workspace-Id=pointId, X-Timezone-Id) → карта, баланс, настройки.
2. Calculate: `POST /terminal/calculate` → расчёт по стратегии, без записи.
3. Process: `POST /terminal/process` → транзакция записана, баланс обновлён:
   - Кассир видит локальную валюту точки.
   - Клиент в WS payload видит base-валюту и approx в локальной (если курс ≠ 1).
4. Stats: `GET /terminal/stats` → дневная сумма/кол-во.
5. Rate Client: `POST /terminal/rate-client` → рейтинг сохранён.

## 7. Карты
1. Поиск публичных точек: `GET /map/points/search` (lat, lon обязательны; radius/limit/query/openNow/includeInactive/minRating/type) → список с total.
2. Точки менеджера: `GET /map/partners/points` → точки его партнёра.

## 8. Отзывы и рейтинги
1. Сводка: `GET /partners/reviews/summary` → avgRating, totalReviews, разбивка по звёздам.
2. Отзывы: `GET /partners/reviews` → список с rating/comment/timestamp.
3. Клиентские рейтинги: `GET /partners/client-ratings` → score/reason/timestamp.

## 9. История и аналитика
1. История: `GET /partners/history` → список транзакций (валюта, тип, cashierId, pointId).
2. Аналитика: `GET /partners/analytics` (period, X-Timezone-Id) → totalRevenue, totalTransactions, averageCheck, chartData (заполнены все buckets).

## 10. Поддержка/чат
### Партнёр
1. `GET /partners/support/thread` → тред или мок с пустым id.
2. `POST /partners/support/messages` (content не пустой) → 201, сообщение видно в последующем `GET`.
3. WS `/ws/support/partner?token=...` → новые admin сообщения приходят.

### Админ
1. `GET /admin/support/threads` → список тредов.
2. `GET /admin/support/threads/{id}` → тред + сообщения.
3. `POST /admin/support/threads/{id}/messages` → 201, сообщение видно партнёру.
4. WS `/ws/support/admin?token=...` → новые partner сообщения приходят.

## 11. Realtime карты
1. Подключение `/ws/cards?token=...` валидно.
2. При `terminal/process` клиентский payload приходит по WS (eventType=TRANSACTION).

## 12. Админ платформы
1. Партнёры: `GET /admin/partners`, `GET /admin/partners/{id}`.
2. Статус партнёра: `POST /admin/partners/{id}/status` (ACTIVE/BLOCKED/PENDING).
3. Точки партнёра: `GET /admin/partners/{id}/points`.
4. Заявки: `GET /platform/requests`, `POST /platform/requests/{id}/approve|reject` (BLOCK/UNBLOCK/ACTIVATE_POINT); проверка изменений статуса/подписок/точки.
5. Staff платформы: invites/join/list/delete.

## 13. Мультивалюта
1. Точка в валюте X, партнёр в base Y, курс ≠ 1:
   - Кассир: суммы/баланс в валюте точки.
   - Клиент WS: баланс/earned в base, approx в локальной при расхождении.
2. История хранит currency (точки) + pointsBaseValue (base).

## 14. Удаление/доступы
1. Нет токена → 401; неправильный формат UserId → 401.
2. Неверный/отсутствующий `X-Workspace-Id` → 400/403/404 по контексту.
3. Кассир пытается работать с чужой точкой → 403.

## 15. Временные зоны / UTC
1. `SHOW timezone` → UTC.
2. В ответах с timestamp — нет локального смещения (UTC millis/iso).
3. Тестовый insert `CURRENT_TIMESTAMP` в БД → UTC время.

## Примечания для прогонов
- Используйте реальные значения `X-Workspace-Id`: PartnerId для владельца/менеджера, PointId для кассира.
- Для WS всегда передавайте `?token=<accessToken>`.
- Для терминала обязательно `X-Timezone-Id` (иначе 400 в некоторых маршрутах).

