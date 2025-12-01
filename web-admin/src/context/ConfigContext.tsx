import React, { createContext, useContext, useEffect, useMemo, useState, useCallback } from 'react';
import { API_BASE_URL } from '../api/axiosConfig';

export type MapProvider = 'YANDEX';

export interface FeatureToggleConfig {
    realtimeEnabled: boolean;
    pushEnabled: boolean;
    mapEnabled: boolean;
    testLabEnabled: boolean;
}

export interface MapSettingsConfig {
    provider: MapProvider;
    minRadiusMeters: number;
    defaultRadiusMeters: number;
    maxRadiusMeters: number;
    clusterRadiusMeters: number;
    searchDebounceMs: number;
    showFilters: boolean;
    showRatings: boolean;
    showWorkingHours: boolean;
    yandexAndroidKey: string | null;
    yandexIosKey: string | null;
    yandexWebKey: string | null;
    defaultLat: double;
    defaultLon: double;
}

export interface PublicConfig {
    features: FeatureToggleConfig;
    map: MapSettingsConfig;
}

interface ConfigContextValue {
    loading: boolean;
    error: string | null;
    config: PublicConfig;
    reload: () => Promise<void>;
}

const DEFAULT_CONFIG: PublicConfig = {
    features: {
        realtimeEnabled: true,
        pushEnabled: true,
        mapEnabled: true,
        testLabEnabled: false,
    },
    map: {
        provider: 'YANDEX',
        minRadiusMeters: 50,
        defaultRadiusMeters: 2000,
        maxRadiusMeters: 15000,
        clusterRadiusMeters: 80,
        searchDebounceMs: 350,
        showFilters: true,
        showRatings: true,
        showWorkingHours: true,
        yandexAndroidKey: null,
        yandexIosKey: null,
        yandexWebKey: null,
    },
};

const ConfigContext = createContext<ConfigContextValue>({
    loading: true,
    error: null,
    config: DEFAULT_CONFIG,
    reload: async () => {},
});

export const ConfigProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const [config, setConfig] = useState<PublicConfig>(DEFAULT_CONFIG);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchConfig = useCallback(async () => {
        setLoading(true);
        try {
            const response = await fetch(`${API_BASE_URL}/public/config`);
            if (!response.ok) {
                throw new Error(`Config request failed with status ${response.status}`);
            }
            const data = (await response.json()) as PublicConfig;
            setConfig({
                features: { ...DEFAULT_CONFIG.features, ...data.features },
                map: { ...DEFAULT_CONFIG.map, ...data.map },
            });
            setError(null);
        } catch (err) {
            console.error('[Config] Failed to load config', err);
            setError(err instanceof Error ? err.message : String(err));
            setConfig(DEFAULT_CONFIG);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchConfig();
    }, [fetchConfig]);

    const value = useMemo<ConfigContextValue>(
        () => ({
            loading,
            error,
            config,
            reload: fetchConfig,
        }),
        [loading, error, config, fetchConfig]
    );

    return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>;
};

export const useAppConfig = () => useContext(ConfigContext);


