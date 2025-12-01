import { useEffect, useMemo, useState } from 'react';

declare global {
    interface Window {
        ymaps?: any;
    }
}

const SCRIPT_ID = 'ymaps-sdk';
const loaders = new Map<string, Promise<any>>();

const buildScriptUrl = (apiKey: string, lang: string) =>
    `https://api-maps.yandex.ru/2.1/?apikey=${apiKey}&lang=${lang}`;

export const loadYandexMaps = (apiKey: string, lang = 'ru_RU'): Promise<any> => {
    if (!apiKey) {
        return Promise.reject(new Error('Yandex Maps API key is missing'));
    }

    const cacheKey = `${apiKey}-${lang}`;
    if (window.ymaps) {
        return new Promise((resolve, reject) => {
            window.ymaps.ready(() => resolve(window.ymaps));
            window.ymaps?.ready?.() ?? reject(new Error('Failed to initialize ymaps'));
        });
    }

    if (loaders.has(cacheKey)) {
        return loaders.get(cacheKey)!;
    }

    const promise = new Promise<any>((resolve, reject) => {
        const existingScript = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
        if (existingScript) {
            existingScript.parentElement?.removeChild(existingScript);
        }

        const script = document.createElement('script');
        script.id = SCRIPT_ID;
        script.src = buildScriptUrl(apiKey, lang);
        script.async = true;
        script.onload = () => {
            if (window.ymaps) {
                window.ymaps.ready(() => resolve(window.ymaps));
            } else {
                reject(new Error('Yandex Maps SDK did not expose ymaps'));
            }
        };
        script.onerror = () => reject(new Error('Failed to load Yandex Maps SDK'));
        document.body.appendChild(script);
    });

    loaders.set(cacheKey, promise);
    return promise;
};

export const useYandexMaps = (apiKey?: string, lang: string = 'ru_RU') => {
    const [state, setState] = useState<{
        ready: boolean;
        error: string | null;
        ymaps: any | null;
    }>({ ready: false, error: null, ymaps: null });

    const normalizedKey = apiKey?.trim();

    useEffect(() => {
        let cancelled = false;

        if (!normalizedKey) {
            setState({
                ready: false,
                error: 'Yandex Maps API key is not configured',
                ymaps: null,
            });
            return;
        }

        loadYandexMaps(normalizedKey, lang)
            .then((ymapsInstance) => {
                if (!cancelled) {
                    setState({ ready: true, error: null, ymaps: ymapsInstance });
                }
            })
            .catch((err) => {
                if (!cancelled) {
                    setState({
                        ready: false,
                        error: err instanceof Error ? err.message : String(err),
                        ymaps: null,
                    });
                }
            });

        return () => {
            cancelled = true;
        };
    }, [normalizedKey, lang]);

    return useMemo(
        () => ({
            ready: state.ready,
            error: state.error,
            ymaps: state.ymaps,
        }),
        [state]
    );
};

