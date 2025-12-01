import { loadYandexMaps } from '../hooks/useYandexMaps';

export interface GeocodeResult {
    lat: number;
    lng: number;
    address?: string;
}

export const geocodeAddress = async (apiKey: string, query: string): Promise<GeocodeResult | null> => {
    if (!apiKey || !query.trim()) {
        return null;
    }
    const ymaps = await loadYandexMaps(apiKey);
    const response = await ymaps.geocode(query, { results: 1 });
    const geoObject = response?.geoObjects?.get(0);
    if (!geoObject) {
        return null;
    }
    const coords = geoObject.geometry?.getCoordinates?.();
    if (!coords) {
        return null;
    }
    return {
        lat: coords[0],
        lng: coords[1],
        address: geoObject.getAddressLine?.(),
    };
};

export const reverseGeocode = async (apiKey: string, lat: number, lng: number): Promise<string | null> => {
    if (!apiKey) {
        return null;
    }
    const ymaps = await loadYandexMaps(apiKey);
    const response = await ymaps.geocode([lat, lng], { kind: 'house', results: 1 });
    const geoObject = response?.geoObjects?.get(0);
    return geoObject?.getAddressLine?.() ?? null;
};


