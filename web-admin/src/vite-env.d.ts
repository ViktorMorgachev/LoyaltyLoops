/// <reference types="vite/client" />

declare const __APP_VERSION__: string;

interface ImportMetaEnv {
    readonly VITE_API_URL?: string;
    readonly VITE_YMAPS_API_KEY?: string;
    readonly VITE_ENV?: string;
    readonly VITE_SHOW_PLAY_LINKS?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
