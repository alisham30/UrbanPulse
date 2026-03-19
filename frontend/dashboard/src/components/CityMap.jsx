import React, { useMemo } from 'react';
import { MapContainer, TileLayer, CircleMarker, Popup, Marker, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { CITY_COORDINATES } from '../hooks/useUserLocation';
import styles from './CityMap.module.css';

const normalizeCityKey = (v) => String(v || '').trim().toLowerCase();
const toDisplayCity = (v) => {
  const raw = String(v || '').trim();
  if (!raw) return 'Unknown';
  return raw.split(' ').filter(Boolean).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
};

const INDIA_CENTER = [22.5, 79.5];
const DEFAULT_ZOOM = 5;

const getRiskColor = (riskLevel) => {
  switch (riskLevel) {
    case 'SEVERE': return '#ff3355';
    case 'HIGH_RISK': return '#ff8a4c';
    case 'ELEVATED': return '#f5bd3f';
    case 'NORMAL': return '#3ddc97';
    default: return '#7eb8d4';
  }
};

const getAqiColor = (aqi) => {
  if (aqi == null) return '#7eb8d4';
  if (aqi <= 50) return '#3ddc97';
  if (aqi <= 100) return '#a0e755';
  if (aqi <= 150) return '#f5bd3f';
  if (aqi <= 200) return '#ff8a4c';
  if (aqi <= 300) return '#ff5c7a';
  return '#9b4dff';
};

const getMarkerRadius = (aqi) => {
  if (aqi == null) return 12;
  if (aqi <= 50) return 12;
  if (aqi <= 100) return 15;
  if (aqi <= 150) return 18;
  if (aqi <= 200) return 21;
  return 25;
};

// User location icon (blue pulsing dot)
const userIcon = L.divIcon({
  className: '',
  html: `<div style="
    width: 18px; height: 18px; border-radius: 50%;
    background: #4a90d9; border: 3px solid #fff;
    box-shadow: 0 0 12px rgba(74,144,217,0.7), 0 0 24px rgba(74,144,217,0.3);
    animation: userPulse 2s ease-in-out infinite;
  "></div>
  <style>@keyframes userPulse { 0%,100% { box-shadow: 0 0 12px rgba(74,144,217,0.7); } 50% { box-shadow: 0 0 24px rgba(74,144,217,0.9), 0 0 48px rgba(74,144,217,0.4); } }</style>`,
  iconSize: [18, 18],
  iconAnchor: [9, 9],
});

const CityMap = ({ allMetrics, cities, userLocation, onCityClick }) => {
  const cityMarkers = useMemo(() => {
    return cities.map((city) => {
      const key = normalizeCityKey(city);
      const coords = CITY_COORDINATES[key];
      const metrics = allMetrics[key];
      if (!coords) return null;

      return {
        city,
        key,
        lat: coords.lat,
        lon: coords.lon,
        metrics,
        aqi: metrics?.aqi,
        riskLevel: metrics?.riskLevel,
        healthScore: metrics?.cityHealthScore,
        pm25: metrics?.pm25,
        temperature: metrics?.temperature,
        humidity: metrics?.humidity,
        confidence: metrics?.dataConfidenceScore,
        trend: metrics?.aqiTrend,
        alertMessage: metrics?.alertMessage,
      };
    }).filter(Boolean);
  }, [cities, allMetrics]);

  return (
    <div className={styles.mapWrapper}>
      <div className={styles.mapLegend}>
        <h4 className={styles.legendTitle}>Air Quality Index</h4>
        <div className={styles.legendItems}>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#3ddc97' }}></span> Good (0-50)</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#a0e755' }}></span> Moderate (51-100)</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#f5bd3f' }}></span> Unhealthy-S (101-150)</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#ff8a4c' }}></span> Unhealthy (151-200)</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#ff5c7a' }}></span> Very Unhealthy (201-300)</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: '#9b4dff' }}></span> Hazardous (300+)</span>
        </div>
      </div>

      <MapContainer
        center={INDIA_CENTER}
        zoom={DEFAULT_ZOOM}
        className={styles.mapContainer}
        scrollWheelZoom={true}
        zoomControl={true}
      >
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
        />

        {cityMarkers.map((marker) => (
          <CircleMarker
            key={marker.key}
            center={[marker.lat, marker.lon]}
            radius={getMarkerRadius(marker.aqi)}
            pathOptions={{
              color: getAqiColor(marker.aqi),
              fillColor: getAqiColor(marker.aqi),
              fillOpacity: 0.55,
              weight: 2,
            }}
          >
            <Popup className={styles.markerPopup}>
              <div className={styles.popupContent}>
                <div className={styles.popupHeader}>
                  <h3 className={styles.popupCity}>{toDisplayCity(marker.city)}</h3>
                  <span
                    className={styles.popupRisk}
                    style={{ color: getRiskColor(marker.riskLevel), borderColor: getRiskColor(marker.riskLevel) }}
                  >
                    {marker.riskLevel || 'UNKNOWN'}
                  </span>
                </div>

                <div className={styles.popupStats}>
                  <div className={styles.popupStat}>
                    <span className={styles.popupStatLabel}>AQI</span>
                    <span className={styles.popupStatValue} style={{ color: getAqiColor(marker.aqi) }}>
                      {marker.aqi ?? '--'}
                    </span>
                  </div>
                  <div className={styles.popupStat}>
                    <span className={styles.popupStatLabel}>Health</span>
                    <span className={styles.popupStatValue}>
                      {marker.healthScore != null ? marker.healthScore.toFixed(0) : '--'}
                    </span>
                  </div>
                  <div className={styles.popupStat}>
                    <span className={styles.popupStatLabel}>PM2.5</span>
                    <span className={styles.popupStatValue}>
                      {marker.pm25 != null ? marker.pm25.toFixed(1) : '--'}
                    </span>
                  </div>
                  <div className={styles.popupStat}>
                    <span className={styles.popupStatLabel}>Temp</span>
                    <span className={styles.popupStatValue}>
                      {marker.temperature != null ? `${marker.temperature.toFixed(1)}°` : '--'}
                    </span>
                  </div>
                </div>

                <div className={styles.popupMeta}>
                  {marker.confidence != null && (
                    <span>Confidence: {(marker.confidence * 100).toFixed(0)}%</span>
                  )}
                  {marker.trend && <span>Trend: {marker.trend}</span>}
                </div>

                {marker.alertMessage && (
                  <p className={styles.popupAlert}>{marker.alertMessage}</p>
                )}

                <button
                  className={styles.popupButton}
                  onClick={() => onCityClick(toDisplayCity(marker.city))}
                >
                  View Dashboard →
                </button>
              </div>
            </Popup>
          </CircleMarker>
        ))}

        {/* User location marker */}
        {userLocation.status === 'granted' && userLocation.lat && userLocation.lon && (
          <Marker position={[userLocation.lat, userLocation.lon]} icon={userIcon}>
            <Popup>
              <div className={styles.popupContent}>
                <h3 className={styles.popupCity}>📍 Your Location</h3>
                {userLocation.nearestCity && (
                  <p style={{ margin: '0.3rem 0', fontSize: '0.85rem', color: '#8ca6be' }}>
                    Nearest city: <strong style={{ color: '#e5f5ff' }}>{toDisplayCity(userLocation.nearestCity)}</strong>
                    {userLocation.distance != null && ` (${userLocation.distance} km)`}
                  </p>
                )}
              </div>
            </Popup>
          </Marker>
        )}
      </MapContainer>
    </div>
  );
};

export default CityMap;
