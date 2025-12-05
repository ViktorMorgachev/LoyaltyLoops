import React, { useEffect, useMemo, useRef } from 'react';
import { Alert, Box, CircularProgress } from '@mui/material';
import { useYandexMaps } from '../../hooks/useYandexMaps';

export type MapPoint = {
    id: string;
    coordinates: [number, number];
    // Опции для стандартных пинов
    color?: string;
    iconColor?: string;
    label?: string;
    preset?: string;
    active?: boolean;
    // Опции для кастомных картинок
    iconLayout?: string;
    iconImageHref?: string;
    iconImageSize?: [number, number];
    iconImageOffset?: [number, number];
    iconImageClipRect?: [[number, number], [number, number]];
    // Балуны
    balloonContent?: string;
    hasBalloon?: boolean;
    // Любые доп данные
    payload?: unknown;
};

interface YandexMapProps {
    apiKey?: string;
    center?: [number, number];
    zoom?: number;
    markers?: MapPoint[];
    onMarkerClick?: (marker: MapPoint) => void;
    onMapClick?: (lat: number, lng: number) => void;
    onStateChange?: (info: { zoom: number; center: [number, number] }) => void;
    interactive?: boolean;
    height?: number | string;
    radiusMeters?: number;
    className?: string;
    lang?: string;
}

const DEFAULT_CENTER: [number, number] = [42.8746, 74.5698];

export const YandexMap: React.FC<YandexMapProps> = ({
    apiKey,
    center,
    zoom = 13,
    markers = [],
    onMarkerClick,
    onMapClick,
    onStateChange,
    interactive = true,
    height = 320,
    radiusMeters,
    className,
    lang = 'ru_RU',
}) => {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const mapRef = useRef<any>(null);
    const markersRef = useRef<Map<string, any>>(new Map());
    const radiusRef = useRef<any>(null);

    const onMapClickRef = useRef(onMapClick);
    const onMarkerClickRef = useRef(onMarkerClick);
    const onStateChangeRef = useRef(onStateChange);

    onMapClickRef.current = onMapClick;
    onMarkerClickRef.current = onMarkerClick;
    onStateChangeRef.current = onStateChange;

    const fallbackKey = useMemo(() => {
        return apiKey ?? (import.meta.env.VITE_YMAPS_API_KEY as string | undefined);
    }, [apiKey]);

    const { ready, error, ymaps } = useYandexMaps(fallbackKey, lang);

    const effectiveCenter = useMemo<[number, number]>(() => {
        if (center && !Number.isNaN(center[0]) && !Number.isNaN(center[1])) {
            return center;
        }
        return DEFAULT_CENTER;
    }, [center]);

    // 1. Инициализация
    useEffect(() => {
        if (!ready || !ymaps || mapRef.current || !containerRef.current) {
            return;
        }

        const mapInstance = new ymaps.Map(
            containerRef.current,
            {
                center: effectiveCenter,
                zoom,
                controls: ['zoomControl'],
            },
            { suppressMapOpenBlock: true }
        );

        mapRef.current = mapInstance;

        try {
            mapInstance.options?.set?.('yandexMapDisablePoiInteractivity', true);
        } catch (_err) {
            console.warn('Yandex Maps API: Failed to disable POI interactivity');
        }

        // ОБРАБОТЧИК ИЗМЕНЕНИЯ СОСТОЯНИЯ (ЗУМ, ЦЕНТР)
        mapInstance.events.add('boundschange', (event: any) => {
            const newZoom = event.get('newZoom');
            const newCenter = event.get('newCenter');
            if (onStateChangeRef.current) {
                onStateChangeRef.current({ zoom: newZoom, center: newCenter });
            }
        });

        // ОБРАБОТЧИК КЛИКА ПО КАРТЕ
        mapInstance.events.add('click', (event: any) => {
            const coords = event.get('coords');

            // 1. Закрываем балун принудительно при клике в пустоту
            mapInstance.balloon.close();

            // 2. Передаем координаты наружу
            if (coords && onMapClickRef.current) {
                onMapClickRef.current(coords[0], coords[1]);
            }
        });

        if (!interactive) {
            mapInstance.behaviors.disable(['scrollZoom', 'multiTouch', 'drag']);
        }

        return () => {
            if (mapRef.current) {
                mapRef.current.destroy();
                mapRef.current = null;
                markersRef.current.clear();
                radiusRef.current = null;
            }
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, ymaps]);

    // 2. Интерактивность
    useEffect(() => {
        if (!mapRef.current) return;
        if (interactive) {
            mapRef.current.behaviors.enable(['scrollZoom', 'multiTouch', 'drag']);
        } else {
            mapRef.current.behaviors.disable(['scrollZoom', 'multiTouch', 'drag']);
        }
    }, [interactive]);

    // 3. Центр и зум
    useEffect(() => {
        if (!mapRef.current || !ymaps) return;

        const currentZoom = mapRef.current.getZoom();
        const currentCenter = mapRef.current.getCenter();

        const isCenterDiff = Math.abs(currentCenter[0] - effectiveCenter[0]) > 0.00001 ||
                             Math.abs(currentCenter[1] - effectiveCenter[1]) > 0.00001;
        const isZoomDiff = currentZoom !== zoom;

        if (isCenterDiff || isZoomDiff) {
             mapRef.current.setCenter(effectiveCenter, zoom, {
                 duration: 500,
                 checkZoomRange: true,
                 timingFunction: 'ease-in-out'
             });
        }
    }, [effectiveCenter, zoom, ymaps]);

    // 4. Синхронизация маркеров
    useEffect(() => {
        if (!mapRef.current || !ymaps) return;

        const existing = markersRef.current;
        const nextIds = new Set(markers.map((marker) => marker.id));

        existing.forEach((placemark, id) => {
            if (!nextIds.has(id)) {
                mapRef.current?.geoObjects.remove(placemark);
                existing.delete(id);
            }
        });

        markers.forEach((marker) => {
            let placemark = existing.get(marker.id);

            const applyOptions = (pm: any) => {
                pm.properties.set({
                    hintContent: marker.label,
                    balloonContent: marker.balloonContent ?? marker.label
                });

                const optionsToSet: Record<string, any> = {
                    hasBalloon: marker.hasBalloon ?? Boolean(marker.balloonContent),
                    zIndex: marker.active ? 1000 : 500,
                    // !!! ВАЖНОЕ ИСПРАВЛЕНИЕ !!!
                    // Запрещаем скрывать иконку при открытии балуна
                    hideIconOnBalloonOpen: false,
                    openBalloonOnClick: true,
                };

                if (marker.iconLayout) {
                    pm.options.unset('preset');
                    pm.options.unset('iconColor');

                    optionsToSet.iconLayout = marker.iconLayout;
                    optionsToSet.iconImageHref = marker.iconImageHref;
                    optionsToSet.iconImageSize = marker.iconImageSize;
                    optionsToSet.iconImageOffset = marker.iconImageOffset;
                    optionsToSet.iconImageClipRect = marker.iconImageClipRect;
                } else {
                    pm.options.unset('iconLayout');
                    pm.options.unset('iconImageHref');
                    pm.options.unset('iconImageSize');
                    pm.options.unset('iconImageOffset');
                    pm.options.unset('iconImageClipRect');

                    optionsToSet.preset = marker.preset ?? (marker.active ? 'islands#redDotIcon' : 'islands#blueDotIcon');
                    if (marker.iconColor || marker.color) {
                        optionsToSet.iconColor = marker.iconColor ?? marker.color;
                    }
                }

                pm.options.set(optionsToSet);
            };

            if (placemark) {
                const oldCoords = placemark.geometry.getCoordinates();
                if (oldCoords[0] !== marker.coordinates[0] || oldCoords[1] !== marker.coordinates[1]) {
                    placemark.geometry.setCoordinates(marker.coordinates);
                }
                applyOptions(placemark);
            } else {
                placemark = new ymaps.Placemark(marker.coordinates, {}, {});
                applyOptions(placemark);

                placemark.events.add('click', () => {
                    if (onMarkerClickRef.current) {
                        onMarkerClickRef.current(marker);
                    }
                });

                existing.set(marker.id, placemark);
                mapRef.current.geoObjects.add(placemark);
            }
        });
    }, [markers, ymaps]);

    // 5. Радиус
    useEffect(() => {
        if (!mapRef.current || !ymaps) return;

        if (radiusRef.current && (!radiusMeters || radiusMeters <= 0)) {
            mapRef.current.geoObjects.remove(radiusRef.current);
            radiusRef.current = null;
            return;
        }

        if (!radiusMeters || radiusMeters <= 0) return;

        if (!radiusRef.current) {
            radiusRef.current = new ymaps.Circle([effectiveCenter, radiusMeters], {}, {
                fillColor: 'rgba(45, 147, 255, 0.08)',
                strokeColor: '#2D93FF',
                strokeOpacity: 0.6,
                strokeWidth: 2,
                interactivityModel: 'default#transparent',
            });
            mapRef.current.geoObjects.add(radiusRef.current);
        } else {
            radiusRef.current.geometry.setCoordinates(effectiveCenter);
            radiusRef.current.geometry.setRadius(radiusMeters);
        }
    }, [radiusMeters, effectiveCenter, ymaps]);

    if (!fallbackKey) {
        return (
            <Alert severity="warning">
                Не задан ключ Yandex Maps.
            </Alert>
        );
    }

    return (
        <Box position="relative" className={className} sx={{ width: '100%', height }}>
            {!ready && !error && (
                <Box
                    position="absolute"
                    top={0} left={0} right={0} bottom={0}
                    display="flex" alignItems="center" justifyContent="center"
                    zIndex={1}
                    bgcolor="rgba(255,255,255,0.7)"
                >
                    <CircularProgress size={32} />
                </Box>
            )}
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>
            )}
            <Box
                ref={containerRef}
                sx={{
                    width: '100%',
                    height: '100%',
                    borderRadius: 0,
                    overflow: 'hidden',
                    filter: interactive ? 'none' : 'grayscale(0.2)',
                }}
            />
        </Box>
    );
};