import { mkdirSync, writeFileSync } from 'fs';
import path from 'path';
import { resources } from '../web-admin/src/i18n/resources.ts';

type FlatMap = Record<string, string | number | boolean>;

const isPlainObject = (value: unknown): value is Record<string, unknown> => {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
};

const flatten = (input: Record<string, unknown>, parentKey = '', acc: FlatMap = {}): FlatMap => {
  Object.entries(input).forEach(([key, value]) => {
    const nextKey = parentKey ? `${parentKey}.${key}` : key;
    if (isPlainObject(value)) {
      flatten(value, nextKey, acc);
    } else {
      acc[nextKey] = (value ?? '') as string | number | boolean;
    }
  });
  return acc;
};

const ensureDir = (dir: string) => mkdirSync(dir, { recursive: true });

const ruMap = flatten(resources.ru.translation);
const enMap = flatten(resources.en.translation);
const allKeys = Array.from(new Set([...Object.keys(ruMap), ...Object.keys(enMap)])).sort();

const rows = allKeys.map((key) => ({
  key,
  ru: ruMap[key] ?? '',
  en: enMap[key] ?? '',
}));

const exportDir = path.resolve(process.cwd(), 'web-admin', 'translations');
ensureDir(exportDir);

const jsonPayload = {
  generatedAt: new Date().toISOString(),
  totalKeys: rows.length,
  entries: rows,
};

writeFileSync(
  path.join(exportDir, 'translations.json'),
  JSON.stringify(jsonPayload, null, 2),
  'utf8'
);

const csvHeader = ['key', 'ru', 'en'];
const escapeCsv = (value: string | number | boolean) => {
  const str = value === undefined || value === null ? '' : String(value);
  return `"${str.replace(/"/g, '""')}"`;
};

const csvLines = [
  csvHeader.join(','),
  ...rows.map((row) => csvHeader.map((col) => escapeCsv((row as any)[col])).join(',')),
];

writeFileSync(path.join(exportDir, 'translations.csv'), csvLines.join('\n'), 'utf8');

console.log(`Exported ${rows.length} translation keys to ${exportDir}`);

