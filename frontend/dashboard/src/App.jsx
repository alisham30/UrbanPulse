import React, { useEffect, useRef, useState, useCallback } from 'react';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import Dashboard from './components/Dashboard';
import CityMap from './components/CityMap';
import CompareView from './components/CompareView';
import Header from './components/Header';
import { apiClient, wsClient } from './services/api';
import { useUserLocation } from './hooks/useUserLocation';
import './styles/common.css';

const VIEWS = { DASHBOARD: 'dashboard', MAP: 'map', COMPARE: 'compare' };

const normalizeCityKey = (v) => String(v || '').trim().toLowerCase();
const toDisplayCity = (v) => {
  const raw = String(v || '').trim();
  if (!raw) return 'Unknown';
  return raw.split(' ').filter(Boolean).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
};

function App() {
  const [activeView, setActiveView] = useState(VIEWS.DASHBOARD);
  const [selectedCity, setSelectedCity] = useState('');
  const [allMetrics, setAllMetrics] = useState({});
  const [alerts, setAlerts] = useState([]);
  const [cities, setCities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [topRiskCity, setTopRiskCity] = useState(null);
  const [mostImprovedCity, setMostImprovedCity] = useState(null);

  const wsReconnectTimer = useRef(null);
  const selectedCityRef = useRef('');
  const userLocation = useUserLocation();

  useEffect(() => {
    selectedCityRef.current = selectedCity;
  }, [selectedCity]);

  // Load initial data
  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [dashboardData, citiesData, topRisk, mostImproved] = await Promise.all([
          apiClient.getDashboard(),
          apiClient.getCities(),
          apiClient.getTopRiskCity().catch(() => null),
          apiClient.getMostImprovedCity().catch(() => null),
        ]);

        setTopRiskCity(topRisk);
        setMostImprovedCity(mostImproved);

        const metricsMap = dashboardData.cities || {};
        const availableCities = citiesData.cities || [];

        setAllMetrics(metricsMap);
        setAlerts(dashboardData.recentAlerts || []);
        setCities(availableCities);

        // Pick initial city: nearest to user, or first available
        let initialCity = availableCities[0] || '';
        if (userLocation.nearestCity) {
          const nearestDisplay = toDisplayCity(userLocation.nearestCity);
          if (availableCities.some((c) => normalizeCityKey(c) === normalizeCityKey(nearestDisplay))) {
            initialCity = nearestDisplay;
          }
        }
        setSelectedCity(initialCity);
        selectedCityRef.current = initialCity;
      } catch (err) {
        console.error('Error loading dashboard:', err);
        setError('Failed to load data. Confirm backend services are running.');
      } finally {
        setLoading(false);
      }
    };

    loadInitialData();
  }, [userLocation.nearestCity]);

  // WebSocket connection
  const handleWebSocketMessage = useCallback((message) => {
    if (!message || !message.data) return;
    const { type, data } = message;

    if (type === 'update') {
      const updatedCity = normalizeCityKey(data.city);
      if (!updatedCity) return;
      setAllMetrics((prev) => ({ ...prev, [updatedCity]: data }));
      setCities((prev) => {
        const display = toDisplayCity(data.city);
        return prev.includes(display) ? prev : [...prev, display];
      });
    }

    if (type === 'alert') {
      setAlerts((prev) => [data, ...prev].slice(0, 30));
      // Toast for HIGH_RISK / SEVERE alerts
      const risk = data.riskLevel || data.data?.riskLevel;
      if (risk === 'HIGH_RISK' || risk === 'SEVERE') {
        const cityName = data.city || data.data?.city || 'Unknown';
        const aqi = data.aqi || data.data?.aqi || '?';
        toast.error(
          `⚠️ ${toDisplayCity(cityName)} — AQI ${aqi} (${risk.replace('_', ' ')})`,
          { autoClose: 8000, position: 'top-right' }
        );
      }
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    const connectWebSocket = async () => {
      try {
        await wsClient.connect((msg) => {
          if (!cancelled) handleWebSocketMessage(msg);
        });
        if (!cancelled) {
          setWsConnected(true);
          if (wsReconnectTimer.current) {
            clearInterval(wsReconnectTimer.current);
            wsReconnectTimer.current = null;
          }
        }
      } catch {
        if (!cancelled) {
          setWsConnected(false);
          if (!wsReconnectTimer.current) {
            wsReconnectTimer.current = setInterval(() => {
              if (!wsClient.getIsConnected()) {
                wsClient.disconnect();
                connectWebSocket();
              } else {
                setWsConnected(true);
                clearInterval(wsReconnectTimer.current);
                wsReconnectTimer.current = null;
              }
            }, 15000);
          }
        }
      }
    };

    connectWebSocket();
    return () => {
      cancelled = true;
      if (wsReconnectTimer.current) clearInterval(wsReconnectTimer.current);
      wsClient.disconnect();
    };
  }, [handleWebSocketMessage]);

  const handleCitySelect = (city) => {
    setSelectedCity(city);
    selectedCityRef.current = city;
    setActiveView(VIEWS.DASHBOARD);
  };

  const handleMapCityClick = (city) => {
    setSelectedCity(city);
    selectedCityRef.current = city;
    setActiveView(VIEWS.DASHBOARD);
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Header
        activeView={activeView}
        onViewChange={setActiveView}
        wsConnected={wsConnected}
        userLocation={userLocation}
        allMetrics={allMetrics}
        cities={cities}
        onCitySelect={handleCitySelect}
      />

      {error && (
        <div style={{
          background: 'rgba(255,92,122,0.15)',
          border: '1px solid rgba(255,92,122,0.4)',
          color: '#ff5c7a',
          padding: '0.8rem 1.5rem',
          textAlign: 'center',
          fontSize: '0.9rem',
        }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {activeView === VIEWS.DASHBOARD && (
        <Dashboard
          selectedCity={selectedCity}
          onCityChange={handleCitySelect}
          allMetrics={allMetrics}
          alerts={alerts}
          cities={cities}
          loading={loading}
          wsConnected={wsConnected}
          topRiskCity={topRiskCity}
          mostImprovedCity={mostImprovedCity}
          userLocation={userLocation}
        />
      )}

      {activeView === VIEWS.MAP && (
        <CityMap
          allMetrics={allMetrics}
          cities={cities}
          userLocation={userLocation}
          onCityClick={handleMapCityClick}
        />
      )}

      {activeView === VIEWS.COMPARE && (
        <CompareView
          allMetrics={allMetrics}
          cities={cities}
        />
      )}

      <ToastContainer
        theme="dark"
        position="top-right"
        autoClose={8000}
        newestOnTop
        closeOnClick
        pauseOnHover
        draggable={false}
        limit={5}
      />
    </div>
  );
}

export default App;
