const fs = require('fs');
const path = require('path');
const vm = require('vm');

const TARGET_LANGS = ['en', 'ky', 'kk', 'uz', 'be'];

const rootDir = path.resolve(__dirname, '..');
const composeResourcesDir = path.join(
  rootDir,
  'composeApp',
  'src',
  'commonMain',
  'composeResources'
);

const readFile = (filePath) => fs.readFileSync(filePath, 'utf8');

const loadAutoLanguages = () => {
  const filePath = path.join(rootDir, 'web-admin', 'src', 'i18n', 'autoLanguages.ts');
  const raw = readFile(filePath)
    .replace(/^\/\/.*$/gm, '')
    .replace(/export const autoLanguages =/, 'module.exports =')
    .replace(/as const;?/, '');
  const sandbox = { module: { exports: {} } };
  vm.runInNewContext(raw, sandbox, { filename: 'autoLanguages.ts' });
  return sandbox.module.exports;
};

const loadResources = (autoLanguages) => {
  const filePath = path.join(rootDir, 'web-admin', 'src', 'i18n', 'resources.ts');
  const raw = readFile(filePath)
    .replace(/^import .*$/m, '')
    .replace(/export const resources:[^=]+=/, 'module.exports =');
  const sandbox = { module: { exports: {} }, autoLanguages };
  vm.runInNewContext(raw, sandbox, { filename: 'resources.ts' });
  return sandbox.module.exports;
};

const parseStringsXml = (content) => {
  const result = {};
  const regex = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  let match;
  const decode = (value) =>
    value
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&apos;/g, "'")
      .replace(/&amp;/g, '&')
      .trim();
  while ((match = regex.exec(content)) !== null) {
    const [, key, rawValue] = match;
    result[key] = decode(rawValue);
  }
  return result;
};

const buildStringsXml = (map) => {
  const encode = (value) =>
    value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  const entries = Object.entries(map)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `    <string name="${key}">${encode(value)}</string>`)
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${entries}\n</resources>\n`;
};

const flatten = (input, prefix = '', acc = {}) => {
  Object.entries(input || {}).forEach(([key, value]) => {
    const nextKey = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      flatten(value, nextKey, acc);
    } else {
      acc[nextKey] = value;
    }
  });
  return acc;
};

const main = () => {
  const autoLanguages = loadAutoLanguages();
  const resources = loadResources(autoLanguages);

  const baseXmlPath = path.join(composeResourcesDir, 'values', 'strings.xml');
  const baseStrings = parseStringsXml(readFile(baseXmlPath));

  const ruFlat = flatten(resources?.ru?.translation ?? {});

  const translationMaps = {};
  TARGET_LANGS.forEach((lang) => {
    const langTranslation = resources?.[lang]?.translation;
    if (!langTranslation) {
      translationMaps[lang] = {};
      return;
    }
    const flat = flatten(langTranslation);
    const map = {};
    Object.entries(ruFlat).forEach(([key, ruValue]) => {
      const translated = flat[key];
      if (ruValue && translated) {
        map[ruValue] = translated;
      }
    });
    translationMaps[lang] = map;
  });

  TARGET_LANGS.forEach((lang) => {
    const langDir = path.join(
      composeResourcesDir,
      lang === 'en' ? 'values-en' : `values-${lang}`
    );
    if (!fs.existsSync(langDir)) {
      fs.mkdirSync(langDir, { recursive: true });
    }
    const translatedMap = {};
    const dictionary = translationMaps[lang] ?? {};
    Object.entries(baseStrings).forEach(([key, ruValue]) => {
      translatedMap[key] = dictionary[ruValue] ?? ruValue;
    });
    const xmlContent = buildStringsXml(translatedMap);
    fs.writeFileSync(path.join(langDir, 'strings.xml'), xmlContent, 'utf8');
    console.log(`Generated ${langDir}/strings.xml`);
  });
};

main();

