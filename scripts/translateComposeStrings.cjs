const { mkdirSync, readFileSync, writeFileSync } = require('fs');
const path = require('path');

const TARGET_LANGS = (process.env.TARGET_LANGS ?? 'en,ky,kk,uz,be')
  .split(',')
  .map((lang) => lang.trim())
  .filter(Boolean);

const SKIP_TRANSLATION = process.env.DRY_RUN === '1';
const BASE_LANG = 'ru';

const PLACEHOLDER_REGEX = /{{\s*[^}]+\s*}}|%(\d+\$)?[0-9]*[sd]/g;

const escapeXml = (text) =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');

const decodeXml = (text) =>
  text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');

const parseStringsXml = (content) => {
  const result = {};
  const stringRegex = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  let match;
  while ((match = stringRegex.exec(content)) !== null) {
    const [, key, rawValue] = match;
    result[key] = decodeXml(rawValue.trim());
  }
  return result;
};

const buildStringsXml = (map) => {
  const entries = Object.entries(map).sort(([a], [b]) => a.localeCompare(b));
  const body = entries
    .map(([key, value]) => `    <string name="${key}">${escapeXml(value)}</string>`)
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${body}\n</resources>\n`;
};

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const protectPlaceholders = (text) => {
  const matches = text.match(PLACEHOLDER_REGEX) ?? [];
  let protectedText = text;
  matches.forEach((placeholder, index) => {
    const token = `__LL_VAR_${index}__`;
    protectedText = protectedText.replace(placeholder, token);
  });
  return { protectedText, matches };
};

const restorePlaceholders = (text, placeholders) => {
  let restored = text;
  placeholders.forEach((placeholder, index) => {
    const token = new RegExp(`__LL_VAR_${index}__`, 'g');
    restored = restored.replace(token, placeholder);
  });
  return restored;
};

const translateText = async (text, target) => {
  if (SKIP_TRANSLATION || !text.trim()) {
    return text;
  }

  const { protectedText, matches } = protectPlaceholders(text);

  const params = new URLSearchParams({
    client: 'gtx',
    sl: BASE_LANG,
    tl: target,
    dt: 't',
    q: protectedText,
  });

  const response = await fetch(`https://translate.googleapis.com/translate_a/single?${params.toString()}`);
  if (!response.ok) {
    throw new Error(`Failed to translate text. HTTP ${response.status}`);
  }
  const data = await response.json();
  const translated = (data?.[0] ?? [])
    .map((segment) => segment?.[0] ?? '')
    .join('');

  await delay(250);

  return restorePlaceholders(translated, matches);
};

const run = async () => {
  const scriptDir = __dirname;
  const repoRoot = path.resolve(scriptDir, '..');
  const resourcesRoot = path.join(
    repoRoot,
    'composeApp',
    'src',
    'commonMain',
    'composeResources'
  );
  const baseFile = path.join(resourcesRoot, 'values', 'strings.xml');

  const baseContent = readFileSync(baseFile, 'utf8');
  const baseStrings = parseStringsXml(baseContent);

  for (const lang of TARGET_LANGS) {
    console.log(`Translating compose strings to "${lang}" (${Object.keys(baseStrings).length} keys)...`);
    const translated = {};
    for (const [key, value] of Object.entries(baseStrings)) {
      try {
        translated[key] = await translateText(value, lang);
      } catch (error) {
        console.error(`  ✖ Failed to translate key "${key}" for ${lang}: ${error.message}`);
        translated[key] = value;
      }
    }

    const dirName = lang === 'en' ? 'values-en' : `values-${lang}`;
    const targetDir = path.join(resourcesRoot, dirName);
    mkdirSync(targetDir, { recursive: true });
    const targetFile = path.join(targetDir, 'strings.xml');
    writeFileSync(targetFile, buildStringsXml(translated), 'utf8');
    console.log(`  ✔ Saved ${targetFile}`);
  }
};

run().catch((error) => {
  console.error('translateComposeStrings failed:', error);
  process.exit(1);
});

