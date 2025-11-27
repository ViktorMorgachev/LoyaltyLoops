# Translation export workflow

## Generate fresh catalog

```bash
cd /Users/viktormorgachev/LoyaltyLoop
npx ts-node --esm scripts/exportTranslations.ts
```

The script reads `web-admin/src/i18n/resources.ts`, flattens every translation key and produces two synchronized files under `web-admin/translations/`:

- `translations.json` – structured payload with `generatedAt`, `totalKeys` and an `entries` array (`{ key, ru, en }`).
- `translations.csv` – comma-separated table with the same data, compatible with Google Sheets and other CAT tools.

## Translating

1. Share either file with linguists. They can filter on the `ru` column and provide `ky/kk/uz/be` equivalents.
2. When translations are ready, copy the values back into `web-admin/src/i18n/resources.ts` (or automate the import if desired).
3. Re-run the export to verify there are no missing keys before shipping.

> The export currently captures the Russian (`ru`) source strings and the English (`en`) reference. If another source language is needed, adjust `scripts/exportTranslations.ts` to include the desired locale map before running the command.

## Auto-translate (no linguist needed)

For a quick first pass you can machine-translate every missing key straight from Russian:

```bash
TARGET_LANGS=ky,kk,uz,be npx ts-node --esm scripts/autoTranslate.ts
```

- By default the script hits the public Google Translate endpoint (no API key required) and writes `web-admin/src/i18n/autoLanguages.ts`.
- Placeholders like `{{count}}` are preserved automatically.
- Existing manual edits are kept; only empty/duplicate strings get overwritten.

### Dry run / cloning only

If you just need to refresh the file without calling the API (e.g. CI or offline dev), run:

```bash
DRY_RUN=1 npx ts-node --esm scripts/autoTranslate.ts
```

This copies the Russian strings into every target language so the UI still builds.

> Скрипт вычисляет пути относительно файла `scripts/autoTranslate.ts`, поэтому можно запускать команду из любого каталога. Главное — чтобы репозиторий оставался по пути `../web-admin`. Для простоты всё равно рекомендую `cd /Users/viktormorgachev/LoyaltyLoop && npx ...`.

### Tweaking targets

- Use `TARGET_LANGS=ky` to translate a single locale.
- Re-run the command whenever you change Russian source strings; the script will only translate keys that still match Russian, so your manual fixes remain intact.

