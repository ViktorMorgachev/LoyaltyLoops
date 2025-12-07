# Процесс работы с переводами

## Веб-админка (Web Admin)



Скрипт читает `web-admin/src/i18n/resources.ts`, "выпрямляет" ключи и создает два файла в `web-admin/translations/`:
- `translations.json` – структурированный файл с метаданными.
- `translations.csv` – таблица (CSV) для импорта в Google Sheets.

### Авто-перевод (без участия лингвиста)
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
