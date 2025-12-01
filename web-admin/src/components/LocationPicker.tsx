import React, { useCallback, useMemo, useState } from 'react';
import { Alert } from '@mui/material';
import { YandexMap } from './map/YandexMap';
import type { MapPoint } from './map/YandexMap';
import { useAppConfig } from '../context/ConfigContext';

interface LocationPickerProps {
    initialLat?: number;
    initialLng?: number;
    onLocationChange?: (lat: number, lng: number) => void;
    height?: number;
    interactive?: boolean;
    radiusMeters?: number;
    markerLabel?: string;
}

const DEFAULT_CENTER: [number, number] = [42.8746, 74.5698];

export const LocationPicker: React.FC<LocationPickerProps> = ({
    initialLat,
    initialLng,
    onLocationChange,
    height = 320,
    interactive = true,
    radiusMeters,
    markerLabel,
}) => {
    const { config } = useAppConfig();
    const fallbackKey = config?.map?.yandexWebKey ?? (import.meta.env.VITE_YMAPS_API_KEY as string | undefined);
    const [overrideCoords, setOverrideCoords] = useState<[number, number] | null>(() => {
        if (typeof initialLat === 'number' && typeof initialLng === 'number') {
            return [initialLat, initialLng];
        }
        return null;
    });

    const initialCoords = useMemo<[number, number] | null>(() => {
        if (
            typeof initialLat === 'number' &&
            !Number.isNaN(initialLat) &&
            typeof initialLng === 'number' &&
            !Number.isNaN(initialLng)
        ) {
            return [initialLat, initialLng];
        }
        return null;
    }, [initialLat, initialLng]);

    const displayCoords = overrideCoords ?? initialCoords;

    const center = useMemo<[number, number]>(() => {
        if (displayCoords) return displayCoords;
        return DEFAULT_CENTER;
    }, [displayCoords]);

    const markers: MapPoint[] = useMemo(() => {
        if (!displayCoords) return [];
        return [
            {
                id: 'selected-point',
                coordinates: displayCoords,
                active: true,
                label: markerLabel ?? 'Торговая точка',
            },
        ];
    }, [displayCoords, markerLabel]);

    const handleMapClick = useCallback(
        (lat: number, lng: number) => {
            if (!interactive) return;
            setOverrideCoords([lat, lng]);
            onLocationChange?.(lat, lng);
        },
        [interactive, onLocationChange]
    );

    if (!fallbackKey) {
        return (
            <Alert severity="warning">
                Ключ Yandex Maps не задан. Добавьте его в конфиг сервера или установите <code>VITE_YMAPS_API_KEY</code>.
            </Alert>
        );
    }

    return (
        <YandexMap
            apiKey={fallbackKey}
            center={center}
            markers={markers}
            onMapClick={handleMapClick}
            interactive={interactive}
            height={height}
            radiusMeters={radiusMeters}
        />
    );
};
