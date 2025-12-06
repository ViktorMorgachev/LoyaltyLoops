import React, { useCallback, useMemo, useState } from 'react';
import { Alert } from '@mui/material';
import { YandexMap } from './map/YandexMap';
import type { MapPoint } from './map/YandexMap';

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
    const fallbackKey = (import.meta.env.VITE_YMAPS_API_KEY as string | undefined);
    const [zoom, setZoom] = useState(13);
    
    // Инициализируем viewCenter
    const [viewCenter, setViewCenter] = useState<[number, number]>(() => {
        if (typeof initialLat === 'number' && typeof initialLng === 'number' && !Number.isNaN(initialLat) && !Number.isNaN(initialLng)) {
            return [initialLat, initialLng];
        }
        return DEFAULT_CENTER;
    });

    const [overrideCoords, setOverrideCoords] = useState<[number, number] | null>(() => {
        if (typeof initialLat === 'number' && typeof initialLng === 'number') {
            return [initialLat, initialLng];
        }
        return null;
    });

    // Если initialLat/Lng меняются снаружи (например, поиск), обновляем центр и маркер
    React.useEffect(() => {
        if (
            typeof initialLat === 'number' &&
            !Number.isNaN(initialLat) &&
            typeof initialLng === 'number' &&
            !Number.isNaN(initialLng)
        ) {
            const newCoords: [number, number] = [initialLat, initialLng];
            // Проверяем, изменились ли они реально, чтобы не сбрасывать зум/центр лишний раз
            // Но если это пришло сверху, мы должны перейти туда
            setOverrideCoords(newCoords);
            setViewCenter(newCoords);
        }
    }, [initialLat, initialLng]);

    const displayCoords = overrideCoords;

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
            // Не меняем viewCenter при клике, чтобы карта не прыгала
        },
        [interactive, onLocationChange]
    );

    const handleStateChange = useCallback((state: { zoom: number; center: [number, number] }) => {
        setZoom(state.zoom);
        setViewCenter(state.center);
    }, []);

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
            center={viewCenter}
            zoom={zoom}
            markers={markers}
            onMapClick={handleMapClick}
            onStateChange={handleStateChange}
            interactive={interactive}
            height={height}
            radiusMeters={radiusMeters}
        />
    );
};
