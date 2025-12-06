# Процесс работы с переводами

## Веб-админка (Web Admin)

### Генерация каталога для переводчиков

Этот шаг создает файлы для ручного перевода (например, для передачи лингвистам).

```bash
cd /Users/viktormorgachev/LoyaltyLoop
npx ts-node --esm scripts/exportTranslations.ts
```

Скрипт читает `web-admin/src/i18n/resources.ts`, "выпрямляет" ключи и создает два файла в `web-admin/translations/`:
- `translations.json` – структурированный файл с метаданными.
- `translations.csv` – таблица (CSV) для импорта в Google Sheets.

### Авто-перевод (без участия лингвиста)

Для быстрого старта можно использовать авто-перевод через Google Translate (бесплатный API).

```bash
TARGET_LANGS=ky,kk,uz,be npx ts-node --esm scripts/autoTranslate.ts
```

- Скрипт читает русские строки из `resources.ts` и переводит их.
- Результат сохраняется в `web-admin/src/i18n/autoLanguages.ts`.
- Переменные типа `{{count}}` сохраняются.
- Ручные правки в `autoLanguages.ts` не перезаписываются (переводятся только новые или пустые ключи).

**Параметры:**
- `TARGET_LANGS`: Языки через запятую (по умолчанию `ky,kk,uz,be`).
- `DRY_RUN=1`: Прогон без запросов к API (просто копирует русские строки).

## Мобильное приложение (Android / Compose)

Для локализации ресурсов Compose Multiplatform используется отдельный скрипт.

### Запуск авто-перевода

```bash
npx ts-node --esm scripts/translateComposeStrings.ts
```

Скрипт берет строки из основного файла:
`composeApp/src/commonMain/composeResources/values/strings.xml`

И генерирует/обновляет переводы в папках:
- `values-en` (Английский)
- `values-ky` (Кыргызский)
- `values-kk` (Казахский)
- `values-uz` (Узбекский)
- `values-be` (Белорусский)

**Параметры:**
- `TARGET_LANGS`: Языки для перевода (по умолчанию `en,ky,kk,uz,be`).
- `DRY_RUN=1`: Тестовый прогон (копирование исходных строк).

### Пример: перевести только на английский

```bash
TARGET_LANGS=en npx ts-node --esm scripts/translateComposeStrings.ts
```
