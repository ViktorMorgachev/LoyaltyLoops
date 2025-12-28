Вот финальная, собранная воедино техническая документация (RFC/Tech Spec). Она включает архитектуру, схему БД, кеширование, логику обновлений и клиентские контракты.

---

# RFC: Мультивалютность и Глобальная Лояльность (Multi-Currency Support)

## 1. Обзор и Концепция
Система внедряет поддержку работы партнеров в разных странах (KG, KZ, UZ, BY) с разными валютами филиалов.

### Принцип «Base vs Terminal»
1.  **Base Currency (Валюта Баланса):** Валюта, в которой физически хранятся баллы клиента в БД. Привязана к **Партнеру**.
2.  **Terminal Currency (Валюта Точки):** Валюта, в которой происходит оплата на кассе. Привязана к **Торговой Точке**.
3.  **Конвертация:** Происходит динамически в момент расчета транзакции по актуальному курсу.

---

## 2. Инфраструктура (Redis & API)

Для обеспечения мгновенного отклика кассы (< 100мс) мы не ходим во внешние API при каждой транзакции, а используем систему кеширования.

### A. Внешний провайдер
*   **Сервис:** [ExchangeRate-API](https://www.exchangerate-api.com/)
*   **Auth:** API Key в `ENV`.
*   **Частота:** Cron Job раз в 4 часа.

### B. Redis (Hot Cache)
*   **Роль:** Хранение актуальных курсов для чтения `O(1)`.
*   **Docker:** Стандартный образ `redis:alpine`.
*   **Key Pattern:** `rate:{BASE_CURRENCY}:{TARGET_CURRENCY}` (например, `rate:USD:KGS`).
*   **TTL (Time To Live):** **25 часов**.
    *   *Зачем:* Механизм самоочистки. Если партнер перестает работать с валютой, джоб перестает её обновлять, и старый ("зомби") курс автоматически удаляется через сутки (если у нас больше нет партнеров с этим курсом).

---

## 3. Схема Базы Данных (Migrations)

### `PartnersTable`
```kotlin
object PartnersTable : Table("partners") {
    // ...
    // [NEW] Валюта хранения баллов. Default: "USD" (или валюта страны регистрации)
    val baseCurrency = varchar("base_currency", 3).default("USD")
}
```

### `ExchangeRatesTable` (New)
Персистентное хранилище курсов (Backup на случай падения Redis).
```kotlin
object ExchangeRatesTable : Table("exchange_rates") {
    val fromCurrency = varchar("from_currency", 3) // Base (USD)
    val toCurrency = varchar("to_currency", 3)     // Target (KGS)
    val rate = double("rate")                      // Multiplier (89.5)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(fromCurrency, toCurrency)
}
```

### `TransactionsTable`
```kotlin
object TransactionsTable : Table("transactions") {
    // ...
    // [NEW] Валюта оплаты (KGS)
    val currency = varchar("currency", 3) 
    // [NEW] Курс на момент сделки (для фин. отчетности)
    val exchangeRateSnapshot = double("exchange_rate_snapshot").default(1.0)
    // [NEW] Реальное изменение баланса в базовой валюте (USD)
    val pointsBaseValue = double("points_base_value").default(0.0)
}
```

---

## 4. Фоновый процесс (Rates Updater Job)

Этот процесс отвечает за наполнение Redis и БД свежими данными.

**Алгоритм (Агрегация):**
1.  **Select Bases:** Получить список уникальных `base_currency` у всех **активных** (`PartnerStatus.ACTIVE`) партнеров. *(Пример: `["USD", "KGS"]`)*.
2.  **Select Targets:** Получить список уникальных `currency` у всех **активных** торговых точек. *(Пример: `["RUB", "KGS", "UZS"]`)*.
3.  **Fetch & Filter:**
    *   Для каждой Base валюты делаем запрос к API.
    *   Из ответа (160+ валют) берем только те, что есть в списке Targets.
4.  **Save:**
    *   Пишем в Postgres (`ExchangeRatesTable`).
    *   Пишем в Redis с TTL 25 часов.

---

## 5. Флоу Транзакции (Business Logic)

### Запрос Калькуляции (`POST /calculate`)
Вход: `billAmount: 5000` (KGS), `pointsToBurn: 425` (KGS).

**Логика Бэкенда:**
1.  **Identify:**
    *   Точка = KGS.
    *   Партнер = USD.
    *   Политика `allowEarnOnBurn` = true.
2.  **Get Rate:**
    *   Читаем Redis `rate:USD:KGS`. Если пусто -> Читаем БД -> Если пусто -> Ошибка конфига.
    *   Rate = `85.0`.
3.  **Burn Logic:**
    *   Клиент списывает 425 сом.
    *   В базу: `425 / 85.0 = 5.0 USD`.
    *   Проверка: `Balance (10$) >= 5.0$`.
4.  **Earn Logic:**
    *   Остаток деньгами: `5000 - 425 = 4575c`.
    *   Кэшбэк 10%: `457.5с`.
    *   В базу: `457.5 / 85.0 = 5.38 USD`.
5.  **Response Construction:**
    *   Возвращаем цифры в **KGS**.

### Ответ API (DTO)
```kotlin
@Serializable
data class TransactionCalculationDto(
    val purchaseAmount: Double, // 5000.0
    val pointsToSpend: Double,  // 425.0
    val pointsToAward: Double,  // 457.5
    val moneyPaid: Double,      // 4575.0
    
    // UI Metadata
    val currency: String,       // "KGS" (для отображения символа)
    val exchangeRate: Double,   // 85.0 (для прозрачности)
    val newBalance: Double,     // 882.3 (Прогноз баланса в KGS: (10-5+5.38)*85)
    
    val message: LoyaltyMessage
)
```

---

## 6. UX / UI Поведение

### Кассир (POS Terminal)
*   **Всегда** видит интерфейс в валюте своей торговой точки.
*   Никаких конвертаций или долларов он не видит. Система для него работает "локально".

### Клиент (Mobile App)
1.  **Главная карта:** Показывает баланс в Базовой валюте + примерную оценку в валюте текущей геолокации.
    *   *Пример:* "10.38 Б (≈ 882с)".
2.  **Чеки (История):** Показывают сумму и валюту, в которой была совершена покупка.
    *   *Пример:* "Кофейня Бишкек: -425с (Списание)".
3.  **Таймзоны:** Влияют только на отображение времени работы филиалов и timestamp в истории транзакций. Расчет валют от времени не зависит.