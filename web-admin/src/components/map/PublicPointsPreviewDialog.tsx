import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Dialog, Divider, Fab, IconButton, InputBase,
    List, ListItem, ListItemButton, Menu, MenuItem, Paper, Popover, Slider, Stack,
    Typography
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import CloseIcon from '@mui/icons-material/Close';
import FilterListIcon from '@mui/icons-material/FilterList';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import CheckIcon from '@mui/icons-material/Check';
import StoreIcon from '@mui/icons-material/Store';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { YandexMap } from './YandexMap';
import type { MapPoint } from './YandexMap';
import { useAppConfig } from '../../context/ConfigContext';
import { api } from '../../api/axiosConfig';
import { formatIntervals, normalizeSchedule } from '../../utils/scheduleUtils';
import type { TradingPointDto, TradingPointSearchResponse, TradingPointType } from '../../types/points';

interface PublicPointsPreviewDialogProps {
    open: boolean;
    onClose: () => void;
    initialCenter?: [number, number];
    initialPoint?: TradingPointDto | null;
}

const DEFAULT_CENTER: [number, number] = [42.8746, 74.5698];

// --- ЦВЕТА ---
const BG_INACTIVE = '#FFFFFF';
const ICON_INACTIVE = '#1F2937';
const BG_ACTIVE = '#111827';
const TEXT_ACTIVE = '#FFFFFF';

const TYPE_OPTIONS: { value: TradingPointType | 'ALL'; labelKey: string }[] = [
    { value: 'ALL', labelKey: 'common.all' },
    { value: 'COFFEE_SHOP', labelKey: 'dashboard.types.COFFEE_SHOP' },
    { value: 'RESTAURANT', labelKey: 'dashboard.types.RESTAURANT' },
    { value: 'RETAIL', labelKey: 'dashboard.types.RETAIL' },
    { value: 'SERVICE', labelKey: 'dashboard.types.SERVICE' },
    { value: 'FLOWERS', labelKey: 'dashboard.types.FLOWERS' },
    { value: 'GIFTS', labelKey: 'dashboard.types.GIFTS' },
    { value: 'CAKES', labelKey: 'dashboard.types.CAKES' },
    { value: 'BARBERSHOP', labelKey: 'dashboard.types.BARBERSHOP' },
    { value: 'CLOTHING', labelKey: 'dashboard.types.CLOTHING' },
    { value: 'TOYS', labelKey: 'dashboard.types.TOYS' },
    { value: 'CAR_RENTAL', labelKey: 'dashboard.types.CAR_RENTAL' },
    { value: 'SCOOTER_RENTAL', labelKey: 'dashboard.types.SCOOTER_RENTAL' },
    { value: 'AUTO_SERVICE', labelKey: 'dashboard.types.AUTO_SERVICE' },
    { value: 'TIRE_SERVICE', labelKey: 'dashboard.types.TIRE_SERVICE' },
    { value: 'AUTO_PARTS', labelKey: 'dashboard.types.AUTO_PARTS' },
    { value: 'BANK', labelKey: 'dashboard.types.BANK' },
    { value: 'OTHER', labelKey: 'dashboard.types.OTHER' },
];

type TypeIconMeta = { emoji: string };

const TYPE_ICON_META: Partial<Record<TradingPointType, TypeIconMeta>> = {
    COFFEE_SHOP: { emoji: '☕️' },
    RESTAURANT: { emoji: '🍽️' },
    RETAIL: { emoji: '🛍️' },
    SERVICE: { emoji: '🛠️' },
    FLOWERS: { emoji: '🌸' },
    GIFTS: { emoji: '🎁' },
    CAKES: { emoji: '🍰' },
    BARBERSHOP: { emoji: '💈' },
    CLOTHING: { emoji: '👗' },
    TOYS: { emoji: '🧸' },
    CAR_RENTAL: { emoji: '🚗' },
    SCOOTER_RENTAL: { emoji: '🛵' },
    AUTO_SERVICE: { emoji: '🔧' },
    TIRE_SERVICE: { emoji: '⚙️' },
    AUTO_PARTS: { emoji: '🚙' },
    BANK: { emoji: '🏦' },
    OTHER: { emoji: '📍' },
};

const ICON_CACHE = new Map<string, string>();

const CHIP_BASE_STYLES = {
    borderRadius: '6px',
    backgroundColor: '#fff',
    borderColor: 'rgba(15,23,42,0.12)',
    height: 24,
    border: '1px solid rgba(15,23,42,0.12)',
    '& .MuiChip-label': { paddingLeft: '6px', paddingRight: '8px', fontSize: '0.75rem', fontWeight: 600, lineHeight: 1 },
    '& .MuiChip-icon': { marginLeft: '6px', marginRight: '-2px', fontSize: '14px' },
};

// --- HELPERS ---
// Функция для глубокого сравнения массивов точек, чтобы избежать перерисовок
const arePointsEqual = (prev: TradingPointDto[], next: TradingPointDto[]) => {
    if (prev.length !== next.length) return false;
    // Сравниваем ID и координаты (самое важное)
    // Для надежности можно использовать JSON.stringify, если объекты не огромные
    return JSON.stringify(prev) === JSON.stringify(next);
};

const createCirclePin = (emoji: string, logoUrl?: string) => {
    const cacheKey = `circle-v4-${emoji}-${logoUrl || 'no'}`;
    if (ICON_CACHE.has(cacheKey)) return ICON_CACHE.get(cacheKey)!;
    const size = 42; const iconSize = 24;
    const content = logoUrl
        ? `<image x="${(size-iconSize)/2}" y="${(size-iconSize)/2}" width="${iconSize}" height="${iconSize}" href="${logoUrl}" style="clip-path: circle(50%);" />`
        : `<text x="50%" y="50%" fill="${ICON_INACTIVE}" font-size="22" text-anchor="middle" dominant-baseline="central" font-family="Apple Color Emoji, Segoe UI Emoji, Noto Color Emoji, sans-serif">${emoji}</text>`;
    const svg = `<svg width="${size}" height="${size+4}" viewBox="0 0 ${size} ${size+4}" fill="none" xmlns="http://www.w3.org/2000/svg"><g filter="url(#shadow)"><circle cx="${size/2}" cy="${size/2}" r="${size/2}" fill="${BG_INACTIVE}" /></g>${content}<defs><filter id="shadow" x="-4" y="-2" width="${size+8}" height="${size+8}" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB"><feFlood flood-opacity="0" result="BackgroundImageFix"/><feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha"/><feOffset dy="2"/><feGaussianBlur stdDeviation="2"/><feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.15 0"/><feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow"/><feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow" result="shape"/></filter></defs></svg>`;
    const dataUrl = `data:image/svg+xml,${encodeURIComponent(svg)}`; ICON_CACHE.set(cacheKey, dataUrl); return dataUrl;
};

const createPillPin = (emoji: string, label: string, logoUrl?: string) => {
    const cacheKey = `pill-v4-${emoji}-${label}-${logoUrl || 'no'}`;
    if (ICON_CACHE.has(cacheKey)) return ICON_CACHE.get(cacheKey)!;
    const height = 40; const charWidth = 8; const textWidth = Math.max(label.length * charWidth, 40); const iconSize = 22; const paddingX = 14; const gap = 8;
    const totalWidth = paddingX + iconSize + gap + textWidth + paddingX; const borderRadius = height / 2;
    const content = logoUrl
        ? `<image x="${paddingX}" y="${(height - iconSize)/2}" width="${iconSize}" height="${iconSize}" href="${logoUrl}" style="clip-path: circle(50%);" />`
        : `<text x="${paddingX + iconSize/2}" y="50%" fill="${TEXT_ACTIVE}" font-size="20" text-anchor="middle" dominant-baseline="central" font-family="Apple Color Emoji, Segoe UI Emoji, Noto Color Emoji, sans-serif">${emoji}</text>`;
    const svg = `<svg width="${totalWidth}" height="${height + 6}" viewBox="0 0 ${totalWidth} ${height + 6}" fill="none" xmlns="http://www.w3.org/2000/svg"><g filter="url(#shadow)"><rect x="2" y="2" width="${totalWidth - 4}" height="${height}" rx="${borderRadius}" fill="${BG_ACTIVE}" /></g>${content}<text x="${paddingX + iconSize + gap}" y="50%" dy="1" fill="${TEXT_ACTIVE}" font-size="13" font-family="system-ui, sans-serif" font-weight="600" dominant-baseline="central">${label}</text><defs><filter id="shadow" x="0" y="0" width="${totalWidth}" height="${height + 6}" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB"><feFlood flood-opacity="0" result="BackgroundImageFix"/><feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha"/><feOffset dy="4"/><feGaussianBlur stdDeviation="2.5"/><feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.3 0"/><feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow"/><feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow" result="shape"/></filter></defs></svg>`;
    const dataUrl = `data:image/svg+xml,${encodeURIComponent(svg)}`; ICON_CACHE.set(cacheKey, dataUrl); return dataUrl;
};

const getTypeIconConfig = (type: TradingPointType | undefined, labelText: string, isActive: boolean = false, logoUrl?: string) => {
    let meta = type ? TYPE_ICON_META[type] : undefined;
    if (!meta) meta = TYPE_ICON_META['OTHER'];
    if (!meta) meta = { emoji: '📍' };

    let iconUrl; let size: [number, number]; let offset: [number, number];
    if (isActive) {
        iconUrl = createPillPin(meta.emoji, labelText, logoUrl);
        const charWidth = 8; const textWidth = Math.max(labelText.length * charWidth, 40); const totalWidth = 14 + 22 + 8 + textWidth + 14; const height = 40;
        size = [totalWidth, height + 6]; offset = [-totalWidth / 2, -height / 2];
    } else {
        iconUrl = createCirclePin(meta.emoji, logoUrl);
        const s = 42; size = [s, s + 4]; offset = [-s / 2, -s / 2];
    }
    return { iconLayout: 'default#image' as const, iconImageHref: iconUrl, iconImageSize: size, iconImageOffset: offset, emoji: meta.emoji };
};

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char] || char));
const getTodayScheduleText = (point: TradingPointDto, t: TFunction<'translation'>) => {
    const scheduleRows = normalizeSchedule(point.schedule);
    if (!scheduleRows.length) return t('point_details.schedule_day_off');
    const jsDay = new Date().getDay(); const normalizedIndex = jsDay === 0 ? 6 : jsDay - 1; const row = scheduleRows[normalizedIndex];
    if (!row || row.isDayOff || row.intervals.length === 0) return t('point_details.schedule_day_off');
    return formatIntervals(row.intervals);
};
const buildBalloonContent = (point: TradingPointDto, t: TFunction<'translation'>) => {
    const scheduleText = getTodayScheduleText(point, t);
    let statusText = point.temporarilyPaused ? t('point_details.status_paused') : point.isOpenNow ? t('point_details.status_open_now') : t('point_details.status_closed_now');
    const addressLine = point.address ? `<div style="margin-top:2px;">${escapeHtml(point.address)}</div>` : '';

    let contacts = '';
    if (point.contactPhone) {
        contacts += `<div style="margin-top:6px;"><a href="tel:${escapeHtml(point.contactPhone)}" style="text-decoration:none;color:#2563EB;font-weight:500;">📞 ${escapeHtml(point.contactPhone)}</a></div>`;
    }
    if (point.contactLink) {
        const linkLabel = point.contactLink.includes('t.me') ? 'Telegram' : point.contactLink.includes('wa.me') ? 'WhatsApp' : t('point_details.contact_link_label');
        contacts += `<div style="margin-top:2px;"><a href="${escapeHtml(point.contactLink)}" target="_blank" style="text-decoration:none;color:#2563EB;font-weight:500;">🔗 ${escapeHtml(linkLabel)}</a></div>`;
    }
    if (point.additionalInfo) {
        contacts += `<div style="margin-top:4px;color:#4b5563;font-style:italic;">${escapeHtml(point.additionalInfo)}</div>`;
    }

    return `<div style="font-size:13px;line-height:1.35;font-family:'Inter',system-ui;"><strong style="font-size:14px;display:block;margin-bottom:2px;">${escapeHtml(point.name)}</strong>${addressLine}<div style="margin-top:6px;font-weight:600;">${escapeHtml(statusText)}</div><div style="color:#6b7280;">${t('point_details.schedule_title')}: ${escapeHtml(scheduleText)}</div>${contacts}</div>`;
};
const formatMeters = (meters: number, t: TFunction<'translation'>) => {
    if (meters >= 1000) return `${(meters / 1000).toFixed(1)} ${t('point_details.unit_km')}`;
    return `${Math.round(meters)} ${t('point_details.unit_m')}`;
};

export const PublicPointsPreviewDialog: React.FC<PublicPointsPreviewDialogProps> = ({
    open,
    onClose,
    initialCenter,
    initialPoint,
}) => {
    const { t } = useTranslation();
    const { config } = useAppConfig();

    const defaultRadius = Math.min(config?.map?.defaultRadiusMeters ?? 2000, 3000);
    const minRadius = config?.map?.minRadiusMeters ?? 50;
    const maxRadius = config?.map?.maxRadiusMeters ?? 15000;

    const [viewCenter, setViewCenter] = useState<[number, number]>(initialCenter ?? DEFAULT_CENTER);
    const [searchCenter, setSearchCenter] = useState<[number, number]>(initialCenter ?? DEFAULT_CENTER);
    const [mapZoom, setMapZoom] = useState<number>(14);
    const [radius, setRadius] = useState<number>(defaultRadius);

    const [queryInput, setQueryInput] = useState('');
    const [query, setQuery] = useState('');
    const [typeFilter, setTypeFilter] = useState<TradingPointType | 'ALL'>('ALL');
    const [openNowOnly, setOpenNowOnly] = useState(false);

    const [showOnlyMine, setShowOnlyMine] = useState(true);
    const [ownPointIds, setOwnPointIds] = useState<string[]>([]);
    const [ownPoints, setOwnPoints] = useState<TradingPointDto[]>([]);
    const [ownFilterAvailable, setOwnFilterAvailable] = useState(true);
    const [loadingOwnPoints, setLoadingOwnPoints] = useState(false);

    const [radiusAnchor, setRadiusAnchor] = useState<HTMLElement | null>(null);
    const [typeAnchor, setTypeAnchor] = useState<HTMLElement | null>(null);

    const [points, setPoints] = useState<TradingPointDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedPointId, setSelectedPointId] = useState<string | null>(initialPoint?.id ?? null);
    const [error, setError] = useState<string | null>(null);

    const [geoLoading, setGeoLoading] = useState(false);
    const [autoLocateTried, setAutoLocateTried] = useState(false);
    const [anchorType, setAnchorType] = useState<'default' | 'point' | 'user'>(initialPoint ? 'point' : 'default');
    const [userLocation, setUserLocation] = useState<[number, number] | null>(null);

    const zoomAnimationRef = useRef<number | null>(null);
    const markersApiKey = (import.meta.env.VITE_YMAPS_API_KEY as string | undefined);

    useEffect(() => {
        if (initialPoint?.latitude && initialPoint.longitude) {
            const coords: [number, number] = [initialPoint.latitude, initialPoint.longitude];
            setViewCenter(coords);
            setSearchCenter(coords);
            setSelectedPointId(initialPoint.id);
            setMapZoom(17);
            setAnchorType('point');
        } else if (initialCenter) {
            setViewCenter(initialCenter);
            setSearchCenter(initialCenter);
        }
    }, [initialCenter, initialPoint]);

    useEffect(() => { return () => { if (zoomAnimationRef.current) window.clearTimeout(zoomAnimationRef.current); }; }, []);

    const fetchOwnPoints = useCallback(async () => {
        if (!ownFilterAvailable || ownPointIds.length > 0) return;
        setLoadingOwnPoints(true);
        try {
            const response = await api.get<TradingPointDto[]>('/map/partners/points');
            setOwnPoints(response.data);
            setOwnPointIds(response.data.map((p) => p.id));
            setOwnFilterAvailable(true);
        } catch (_err) {
            setOwnFilterAvailable(false);
            setShowOnlyMine(false);
        } finally {
            setLoadingOwnPoints(false);
        }
    }, [ownFilterAvailable, ownPointIds.length]);

    useEffect(() => {
        if (open && showOnlyMine) {
            fetchOwnPoints();
        }
    }, [open, showOnlyMine, fetchOwnPoints]);

    // --- MAIN SEARCH LOGIC (ИСПРАВЛЕНО) ---
    const loadPoints = useCallback(async (override?: { lat: number; lon: number }) => {
        if (!config?.features?.mapEnabled) return;
        setLoading(true);
        setError(null);
        try {
            const latValue = override?.lat ?? searchCenter[0];
            const lonValue = override?.lon ?? searchCenter[1];
            const params = new URLSearchParams({ lat: latValue.toString(), lon: lonValue.toString(), radius: radius.toString(), limit: '50' });
            if (query.trim()) params.append('query', query.trim());
            if (openNowOnly) params.append('openNow', 'true');
            if (typeFilter !== 'ALL') params.append('type', typeFilter);

            const response = await api.get<TradingPointSearchResponse>(`/map/points/search?${params.toString()}`);
            const data = response.data;

            // ВАЖНО: Проверка на изменение данных перед обновлением стейта
            setPoints(currentPoints => {
                if (arePointsEqual(currentPoints, data.points)) {
                    // Данные не изменились, не вызываем ре-рендер списка
                    return currentPoints;
                }
                return data.points;
            });

        } catch (err) {
            setError(t('point_details.map_load_error'));
        } finally {
            setLoading(false);
        }
    }, [searchCenter, radius, query, openNowOnly, typeFilter, config?.features?.mapEnabled, t]);

    useEffect(() => { if (open) loadPoints(); }, [open, loadPoints]);

    const visiblePoints = useMemo(() => {
        if (showOnlyMine && ownFilterAvailable) {
            if (ownPointIds.length > 0 || !loadingOwnPoints) {
                return ownPoints;
            }
        }
        return points;
    }, [points, showOnlyMine, ownFilterAvailable, ownPointIds.length, loadingOwnPoints, ownPoints]);

    const handleMapClick = useCallback(() => {
        setSelectedPointId(null);
    }, []);

    const markers = useMemo<MapPoint[]>(() => {
        const base = visiblePoints
            .filter((p) => typeof p.latitude === 'number' && typeof p.longitude === 'number')
            .map((p) => {
                const isActive = p.id === selectedPointId;
                const labelText = t(`dashboard.types.${p.type}`);
                const logoUrl = (p as any).logoUrl || (p as any).iconUrl;
                const typeConfig = getTypeIconConfig(p.type, labelText, isActive, logoUrl);

                const marker: MapPoint = {
                    id: p.id,
                    coordinates: [p.latitude as number, p.longitude as number],
                    label: p.name,
                    active: isActive,
                    payload: p,
                    hasBalloon: true,
                    balloonContent: buildBalloonContent(p, t),
                };
                Object.assign(marker, {
                    iconLayout: typeConfig.iconLayout,
                    iconImageHref: typeConfig.iconImageHref,
                    iconImageSize: typeConfig.iconImageSize,
                    iconImageOffset: typeConfig.iconImageOffset,
                });
                return marker;
            });

        const extras: MapPoint[] = [];
        if (anchorType !== 'point' && anchorType !== 'user') {
            extras.push({ id: 'search-anchor', coordinates: searchCenter, label: t('point_details.map_anchor_default'), preset: 'islands#darkBlueCircleDotIcon' });
        }
        if (userLocation) {
            extras.push({ id: 'user-location', coordinates: userLocation, label: t('point_details.map_anchor_user'), preset: 'islands#bluePersonIcon', iconColor: '#2563EB' });
        }
        return [...extras, ...base];
    }, [visiblePoints, selectedPointId, searchCenter, anchorType, userLocation, t]);

    const handlePointSelect = useCallback((point: TradingPointDto) => {
        setSelectedPointId(point.id);
        if (typeof point.latitude === 'number' && typeof point.longitude === 'number') {
            setViewCenter([point.latitude, point.longitude]);
            setAnchorType('point');
            setMapZoom((prev) => {
                if (prev < 15) return 15;
                return prev;
            });
        }
    }, []);

    const handleLocateMe = useCallback(() => {
        if (!navigator.geolocation) { setAutoLocateTried(true); return; }
        setGeoLoading(true);
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                setGeoLoading(false);
                setAutoLocateTried(true);
                const coords: [number, number] = [pos.coords.latitude, pos.coords.longitude];
                setUserLocation(coords);
                setViewCenter(coords);
                setSearchCenter(coords);
                setAnchorType('user');
                setSelectedPointId(null);
                setMapZoom(15);
                loadPoints({ lat: coords[0], lon: coords[1] });
            },
            () => { setGeoLoading(false); setAutoLocateTried(true); },
            { enableHighAccuracy: true, timeout: 8000 }
        );
    }, [loadPoints]);

    useEffect(() => { if (open && !autoLocateTried) handleLocateMe(); }, [open, autoLocateTried, handleLocateMe]);

    const renderTypeChip = useCallback((point: TradingPointDto) => {
        const typeConfig = getTypeIconConfig(point.type, '', false);
        return (<Chip icon={typeConfig?.emoji ? <Box component="span" sx={{ fontSize: 15, lineHeight: 1 }}>{typeConfig?.emoji}</Box> : undefined} label={t(`dashboard.types.${point.type}`)} size="small" sx={CHIP_BASE_STYLES} />);
    }, [t]);

    const renderStatusChip = useCallback((point: TradingPointDto) => {
        if (point.temporarilyPaused) return <Chip label={t('point_details.status_paused')} size="small" sx={{...CHIP_BASE_STYLES, borderColor: 'rgba(251, 191, 36, 0.4)', backgroundColor: 'rgba(251, 191, 36, 0.15)', color: '#92400E'}} />;
        if (!point.active) return <Chip label={t('point_details.status_inactive')} size="small" sx={{...CHIP_BASE_STYLES, borderColor: 'rgba(148, 163, 184, 0.5)', backgroundColor: 'rgba(148, 163, 184, 0.15)', color: '#475569'}} />;
        if (point.isOpenNow) return <Chip label={t('point_details.status_open_now')} size="small" sx={{...CHIP_BASE_STYLES, borderColor: 'rgba(34, 197, 94, 0.4)', backgroundColor: 'rgba(34, 197, 94, 0.15)', color: '#166534'}} />;
        return <Chip label={t('point_details.status_closed_now')} size="small" sx={{...CHIP_BASE_STYLES, borderColor: 'rgba(148, 163, 184, 0.5)', backgroundColor: 'rgba(148, 163, 184, 0.12)', color: '#475569'}} />;
    }, [t]);

    return (
        <Dialog open={open} onClose={onClose} fullScreen>
            <Box sx={{ position: 'relative', width: '100%', height: '100%', bgcolor: '#f3f4f6', overflow: 'hidden' }}>
                <Box sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 0 }}>
                    <YandexMap
                        apiKey={markersApiKey}
                        center={viewCenter}
                        markers={markers}
                        radiusMeters={anchorType !== 'point' ? radius : 0}
                        zoom={mapZoom}
                        height="100%"
                        className="fullscreen-map"
                        onMarkerClick={(marker) => { const p = marker.payload as TradingPointDto; if (p) handlePointSelect(p); }}
                        onMapClick={handleMapClick}
                    />
                </Box>

                <Paper
                    elevation={4}
                    sx={{
                        position: 'absolute',
                        top: { xs: 0, md: 16 },
                        left: { xs: 0, md: 16 },
                        bottom: { xs: 'auto', md: 16 },
                        width: { xs: '100%', md: 400 },
                        maxHeight: { xs: 'auto', md: 'calc(100% - 32px)' },
                        borderRadius: { xs: 0, md: 3 },
                        zIndex: 10,
                        display: 'flex', flexDirection: 'column', overflow: 'hidden',
                        bgcolor: 'rgba(255, 255, 255, 0.96)', backdropFilter: 'blur(8px)',
                    }}
                >
                    <Box sx={{ p: 2, pb: 1 }}>
                        <Paper variant="outlined" sx={{ p: '2px 4px', display: 'flex', alignItems: 'center', borderRadius: 2, borderColor: 'rgba(0,0,0,0.1)', boxShadow: '0 2px 4px rgba(0,0,0,0.02)' }}>
                            <IconButton sx={{ p: '10px' }} onClick={() => { setQuery(queryInput); }}><SearchIcon /></IconButton>
                            <InputBase sx={{ ml: 1, flex: 1 }} placeholder={t('point_details.search_points_placeholder')} value={queryInput} onChange={(e) => setQueryInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') setQuery(queryInput); }} />
                            {queryInput && <IconButton sx={{ p: '10px' }} onClick={() => { setQueryInput(''); setQuery(''); }}><CloseIcon /></IconButton>}
                        </Paper>
                    </Box>

                    <Box sx={{ px: 2, pb: 2, overflowX: 'auto', display: 'flex', gap: 1, '::-webkit-scrollbar': { display: 'none' } }}>
                        <Chip
                            label={t('point_details.filter_my_points')}
                            onClick={() => setShowOnlyMine(!showOnlyMine)}
                            icon={showOnlyMine ? <CheckIcon /> : <StoreIcon />}
                            color={showOnlyMine ? 'primary' : 'default'}
                            variant={showOnlyMine ? 'filled' : 'outlined'}
                            disabled={!ownFilterAvailable && !loadingOwnPoints}
                            sx={{ borderRadius: 2 }}
                        />
                        <Chip label={typeFilter === 'ALL' ? t('point_details.filter_type') : t(`dashboard.types.${typeFilter}`)} onClick={(e) => setTypeAnchor(e.currentTarget)} onDelete={typeFilter !== 'ALL' ? () => setTypeFilter('ALL') : undefined} deleteIcon={<CloseIcon />} icon={<FilterListIcon />} variant={typeFilter === 'ALL' ? 'outlined' : 'filled'} color={typeFilter === 'ALL' ? 'default' : 'primary'} sx={{ borderRadius: 2 }} />
                        <Menu anchorEl={typeAnchor} open={Boolean(typeAnchor)} onClose={() => setTypeAnchor(null)}>
                            {TYPE_OPTIONS.map((opt) => (<MenuItem key={opt.value} selected={typeFilter === opt.value} onClick={() => { setTypeFilter(opt.value); setTypeAnchor(null); }}>{t(opt.labelKey)}</MenuItem>))}
                        </Menu>
                        <Chip label={`${t('point_details.filter_radius_short', { defaultValue: 'Radius' })}: ${formatMeters(radius, t)}`} onClick={(e) => setRadiusAnchor(e.currentTarget)} variant="outlined" deleteIcon={<KeyboardArrowDownIcon />} onDelete={(e) => setRadiusAnchor(e.currentTarget)} sx={{ borderRadius: 2 }} />
                        <Popover open={Boolean(radiusAnchor)} anchorEl={radiusAnchor} onClose={() => setRadiusAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }} PaperProps={{ sx: { p: 2, width: 300 } }}>
                            <Typography gutterBottom>{t('point_details.filter_radius', { value: formatMeters(radius, t) })}</Typography>
                            <Slider value={radius} min={minRadius} max={maxRadius} step={100} onChange={(_, v) => setRadius(v as number)} />
                        </Popover>
                        <Chip label={t('point_details.filter_open_now')} onClick={() => setOpenNowOnly(!openNowOnly)} icon={openNowOnly ? <CheckIcon /> : undefined} color={openNowOnly ? 'success' : 'default'} variant={openNowOnly ? 'filled' : 'outlined'} sx={{ borderRadius: 2 }} />
                    </Box>

                    <Divider />

                    <Box sx={{ flex: 1, overflowY: 'auto', p: 0 }}>
                        {/* ИСПРАВЛЕННЫЙ РЕНДЕР: Меньше мерцания */}
                        {loading && points.length === 0 ? (
                            // Показываем спиннер ТОЛЬКО если совсем нет точек (первая загрузка)
                            <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
                        ) : visiblePoints.length === 0 ? (
                            // Если загрузка закончилась, но ничего не найдено
                            <Typography align="center" color="text.secondary" sx={{ p: 4 }}>{t('point_details.no_points_found')}</Typography>
                        ) : (
                            // Если есть точки - показываем список (даже если loading=true, просто можно добавить прозрачность)
                            <List disablePadding sx={{ px: 0.5, pb: 1, opacity: loading ? 0.6 : 1, transition: 'opacity 0.2s' }}>
                                {visiblePoints.map((point) => {
                                    const isSelected = point.id === selectedPointId;
                                    return (
                                        <ListItem key={point.id} disablePadding sx={{ mb: 1.5 }}>
                                            <ListItemButton
                                                selected={isSelected}
                                                onClick={() => handlePointSelect(point)}
                                                alignItems="flex-start"
                                                sx={{
                                                    borderRadius: 2, border: '1px solid', borderColor: isSelected ? 'primary.main' : 'divider',
                                                    bgcolor: isSelected ? 'rgba(37, 99, 235, 0.04)' : 'background.paper', transition: 'all 0.2s ease', p: 1.5, gap: 1, flexDirection: 'column',
                                                    '&.Mui-selected': { bgcolor: 'rgba(37, 99, 235, 0.08)', borderColor: 'primary.main', '&:hover': { bgcolor: 'rgba(37, 99, 235, 0.12)' } }
                                                }}
                                            >
                                                <Box display="flex" justifyContent="space-between" width="100%" alignItems="center" mb={0.5}>
                                                    <Typography variant="subtitle2" fontWeight={700} sx={{ fontSize: '0.95rem' }}>{point.name}</Typography>
                                                    {typeof point.distanceMeters === 'number' && <Typography variant="caption" color="text.secondary" fontWeight={500} sx={{ whiteSpace: 'nowrap', ml: 1 }}>{formatMeters(point.distanceMeters, t)}</Typography>}
                                                </Box>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, fontSize: '0.8rem', lineHeight: 1.3, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{point.address || t('point_details.address_none')}</Typography>
                                                <Box display="flex" justifyContent="space-between" alignItems="center" width="100%" mt="auto">
                                                    <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap>{renderTypeChip(point)}{renderStatusChip(point)}</Stack>
                                                    <Button size="small" onClick={(e) => { e.stopPropagation(); handlePointSelect(point); }} sx={{ minWidth: 'auto', fontSize: '0.75rem', fontWeight: 600, textTransform: 'none', py: 0.5, px: 1.5, ml: 1 }}>{t('common.details')}</Button>
                                                </Box>
                                            </ListItemButton>
                                        </ListItem>
                                    );
                                })}
                            </List>
                        )}
                    </Box>
                </Paper>

                <Stack spacing={2} sx={{ position: 'absolute', top: 16, right: 16, zIndex: 20 }}>
                    <Fab color="default" size="medium" onClick={onClose}><CloseIcon /></Fab>
                    <Fab color="primary" size="medium" onClick={handleLocateMe} disabled={geoLoading}>{geoLoading ? <CircularProgress size={20} color="inherit" /> : <MyLocationIcon />}</Fab>
                </Stack>
                {error && <Alert severity="error" sx={{ position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)', zIndex: 30 }}>{error}</Alert>}
            </Box>
        </Dialog>
    );
};