import { useState, useEffect, useCallback } from 'react';

/**
 * City coordinates — sourced from ingestion-service config.
 * These are used for nearest-city matching when browser geolocation is available.
 */
const CITY_COORDINATES = {
  mumbai: { lat: 19.076, lon: 72.8777 },
  delhi: { lat: 28.6139, lon: 77.209 },
  pune: { lat: 18.5204, lon: 73.8567 },
  bengaluru: { lat: 12.9716, lon: 77.5946 },
  chennai: { lat: 13.0827, lon: 80.2707 },
  hyderabad: { lat: 17.385, lon: 78.4867 },
  kolkata: { lat: 22.5726, lon: 88.3639 },
  ahmedabad: { lat: 23.0225, lon: 72.5714 },
};

function haversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function findNearestCity(userLat, userLon) {
  let nearest = null;
  let minDistance = Infinity;

  for (const [city, coords] of Object.entries(CITY_COORDINATES)) {
    const dist = haversineDistance(userLat, userLon, coords.lat, coords.lon);
    if (dist < minDistance) {
      minDistance = dist;
      nearest = city;
    }
  }

  return { city: nearest, distance: Math.round(minDistance) };
}

export function useUserLocation() {
  const [location, setLocation] = useState({
    lat: null,
    lon: null,
    nearestCity: null,
    distance: null,
    status: 'pending', // pending | granted | denied | unavailable
    error: null,
  });

  const requestLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setLocation((prev) => ({ ...prev, status: 'unavailable', error: 'Geolocation not supported' }));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        const { city, distance } = findNearestCity(latitude, longitude);
        setLocation({
          lat: latitude,
          lon: longitude,
          nearestCity: city,
          distance,
          status: 'granted',
          error: null,
        });
      },
      (err) => {
        setLocation((prev) => ({
          ...prev,
          status: 'denied',
          error: err.message,
        }));
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 }
    );
  }, []);

  useEffect(() => {
    requestLocation();
  }, [requestLocation]);

  return location;
}

export { CITY_COORDINATES, haversineDistance, findNearestCity };
