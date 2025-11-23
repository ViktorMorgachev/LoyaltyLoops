import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import { LatLngExpression } from 'leaflet';
import 'leaflet/dist/leaflet.css';
// Fix for Leaflet icons in React
import L from 'leaflet';
// @ts-ignore
import icon from 'leaflet/dist/images/marker-icon.png';
// @ts-ignore
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

let DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});
L.Marker.prototype.options.icon = DefaultIcon;

interface LocationPickerProps {
    initialLat?: number;
    initialLng?: number;
    onLocationChange: (lat: number, lng: number) => void;
    height?: number;
}

const LocationMarker = ({ position, setPosition, onChange }: any) => {
    useMapEvents({
        click(e) {
            setPosition(e.latlng);
            onChange(e.latlng.lat, e.latlng.lng);
        },
    });

    return position === null ? null : (
        <Marker position={position}></Marker>
    );
}

export const LocationPicker: React.FC<LocationPickerProps> = ({ initialLat, initialLng, onLocationChange, height = 300 }) => {
    // Default to Bishkek center if none provided
    const defaultCenter: LatLngExpression = [42.8746, 74.5698]; 
    const center = (initialLat && initialLng) ? [initialLat, initialLng] as LatLngExpression : defaultCenter;
    
    const [position, setPosition] = useState<LatLngExpression | null>(
        (initialLat && initialLng) ? [initialLat, initialLng] as LatLngExpression : null
    );

    useEffect(() => {
         if (initialLat && initialLng) {
             setPosition([initialLat, initialLng]);
         }
    }, [initialLat, initialLng]);

    return (
        <MapContainer center={center} zoom={13} style={{ height: `${height}px`, width: '100%', borderRadius: '8px', marginTop: '10px' }}>
            <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution='&copy; OpenStreetMap contributors'
            />
            <LocationMarker position={position} setPosition={setPosition} onChange={onLocationChange} />
        </MapContainer>
    );
};

