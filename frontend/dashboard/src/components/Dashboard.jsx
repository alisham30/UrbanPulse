import React, { useEffect, useMemo, useRef, useState } from 'react';
import AlertsPanel from './AlertsPanel';
import Loading from './Loading';
import MetricCard from './MetricCard';
import RiskBadge from './RiskBadge';
import ScoreCard from './ScoreCard';
import TrendChart from './TrendChart';
import { apiClient, wsClient } from '../services/api';
import styles from './Dashboard.module.css';

const DEFAULT_CITY = '';

const normalizeCityKey = (value) => String(value || '').trim().toLowerCase();

const toDisplayCity = (value) => {
  const raw = String(value || '').trim();
  if (!raw) return 'Unknown';
  return raw
    .split(' ')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
};

function generateInsights(metrics, allMetrics, cities) {
  if (!metrics || !metrics.city) return [];

  const currentCity = toDisplayCity(metrics.city);
  const insights = [];

  if (metrics.baselineAqi != null && metrics.aqiDeviationPercent != null) {
    const deviation = Number(metrics.aqiDeviationPercent);
    const direction = deviation >= 0 ? 'above' : 'below';
    insights.push(`${currentCity} AQI is ${Math.abs(deviation).toFixed(1)}% ${direction} historical baseline.`);
  }

  if (metrics.rollingAqiAverage != null && metrics.aqi != null) {
    const delta = ((metrics.aqi - metrics.rollingAqiAverage) / (metrics.rollingAqiAverage || 1)) * 100;
    const direction = delta >= 0 ? 'above' : 'below';
    insights.push(`Current AQI is ${Math.abs(delta).toFixed(1)}% ${direction} recent rolling trend.`);
  }

  if (metrics.primaryDriver) {
    insights.push(`${metrics.primaryDriver} is the strongest contributor to current score drop.`);
  }

  if (metrics.validationStatus && metrics.validationStatus !== 'UNAVAILABLE') {
    if (metrics.validationStatus === 'MATCH') {
      insights.push('OpenAQ confirms strong agreement with live source readings.');
    } else if (metrics.validationStatus === 'MINOR_DEVIATION') {
      insights.push('OpenAQ indicates minor deviation from live source readings.');
    } else {
      insights.push('OpenAQ indicates major deviation from live source readings.');
    }
  }

  if (cities.length > 1) {
    const ranked = cities
      .map((cityName) => allMetrics[normalizeCityKey(cityName)])
      .filter(Boolean)
      .sort((a, b) => (a.cityHealthScore ?? 100) - (b.cityHealthScore ?? 100));

    if (ranked.length > 0) {
      const worst = ranked[0];
      if (normalizeCityKey(worst.city) !== normalizeCityKey(metrics.city)) {
        insights.push(`${toDisplayCity(worst.city)} is currently the most at-risk monitored city.`);
      }
    }
  }

  return insights.slice(0, 5);
}

const Dashboard = () => {
  const [city, setCity] = useState(DEFAULT_CITY);
  const [metrics, setMetrics] = useState(null);
  const [allMetrics, setAllMetrics] = useState({});
  const [alerts, setAlerts] = useState([]);
  const [cities, setCities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [aqiTrend, setAqiTrend] = useState([]);
  const [lastUpdated, setLastUpdated] = useState(null);

  const wsReconnectTimer = useRef(null);
  const currentCityRef = useRef(DEFAULT_CITY);

  useEffect(() => {
    currentCityRef.current = city;
  }, [city]);

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [dashboardData, citiesData] = await Promise.all([
          apiClient.getDashboard(),
          apiClient.getCities(),
        ]);

        const metricsMap = dashboardData.cities || {};
        const availableCities = citiesData.cities || [];

        setAllMetrics(metricsMap);
        setAlerts(dashboardData.recentAlerts || []);
        setCities(availableCities);

        const initialCity = availableCities.includes(DEFAULT_CITY)
          ? DEFAULT_CITY
          : (availableCities[0] || DEFAULT_CITY);

        setCity(initialCity);
        currentCityRef.current = initialCity;

        const initialMetrics = metricsMap[normalizeCityKey(initialCity)];
        if (initialMetrics) {
          setMetrics(initialMetrics);
          setLastUpdated(new Date(initialMetrics.timestamp || Date.now()));
        }

        if (initialCity) {
          const historyData = await apiClient.getAqiHistory(initialCity);
          const history = (historyData.history || []).map((item) => ({
            time: item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : '',
            value: item.aqi,
          }));
          if (history.length > 0) {
            setAqiTrend(history);
          } else if (initialMetrics?.aqi != null) {
            setAqiTrend([{ time: new Date().toLocaleTimeString(), value: initialMetrics.aqi }]);
          }
        }
      } catch (err) {
        console.error('Error loading dashboard:', err);
        setError('Failed to load data. Confirm backend services are running.');
      } finally {
        setLoading(false);
      }
    };

    loadInitialData();
  }, []);

  useEffect(() => {
    let cancelled = false;

    const connectWebSocket = async () => {
      try {
        await wsClient.connect((message) => {
          if (!cancelled) {
            handleWebSocketMessage(message);
          }
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
      if (wsReconnectTimer.current) {
        clearInterval(wsReconnectTimer.current);
      }
      wsClient.disconnect();
    };
  }, []);

  const handleWebSocketMessage = (message) => {
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

      if (updatedCity === normalizeCityKey(currentCityRef.current)) {
        setMetrics(data);
        setLastUpdated(new Date(data.timestamp || Date.now()));
        addTrendData(data.aqi);
      }
    }

    if (type === 'alert') {
      setAlerts((prev) => [data, ...prev].slice(0, 20));
    }
  };

  const addTrendData = (aqiValue) => {
    if (aqiValue == null) return;
    const currentTime = new Date().toLocaleTimeString();
    setAqiTrend((prev) => [...prev, { time: currentTime, value: aqiValue }].slice(-20));
  };

  const handleCityChange = async (newCity) => {
    setCity(newCity);
    currentCityRef.current = newCity;

    const cityMetrics = allMetrics[normalizeCityKey(newCity)];
    if (cityMetrics) {
      setMetrics(cityMetrics);
      setLastUpdated(new Date(cityMetrics.timestamp || Date.now()));
    }

    setAqiTrend([]);

    try {
      const historyData = await apiClient.getAqiHistory(newCity);
      const history = (historyData.history || []).map((item) => ({
        time: item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : '',
        value: item.aqi,
      }));

      if (history.length > 0) {
        setAqiTrend(history);
      } else if (cityMetrics?.aqi != null) {
        setAqiTrend([{ time: new Date().toLocaleTimeString(), value: cityMetrics.aqi }]);
      }
    } catch {
      if (cityMetrics?.aqi != null) {
        setAqiTrend([{ time: new Date().toLocaleTimeString(), value: cityMetrics.aqi }]);
      }
    }
  };

  const insights = useMemo(() => generateInsights(metrics, allMetrics, cities), [metrics, allMetrics, cities]);

  const formatLastUpdated = () => {
    if (!lastUpdated) return 'N/A';
    return lastUpdated.toLocaleTimeString();
  };

  if (loading) {
    return <Loading message="Booting UrbanPulse command center..." />;
  }

  return (
    <div className={styles.dashboard}>
      <header className={styles.header}>
        <div className={styles.headerContent}>
          <div className={styles.brandSection}>
            <div className={styles.logoMark}>
              <span className={styles.logoPulse}></span>
              <span className={styles.logoIcon}></span>
            </div>
            <div className={styles.titleSection}>
              <h1 className={styles.title}>UrbanPulse</h1>
              <p className={styles.subtitle}>Environmental Intelligence Command Center</p>
            </div>
          </div>

          <div className={styles.headerRight}>
            <div className={styles.dataSourceBadges}>
              <span className={styles.sourceBadge}>OpenWeather</span>
              <span className={styles.sourceBadge}>OpenAQ</span>
              <span className={styles.sourceBadge}>Kaggle Baseline</span>
              <span className={styles.sourceBadge}>Kafka + Spark</span>
            </div>

            <div className={styles.liveIndicator}>
              <span className={wsConnected ? styles.liveDot : styles.offlineDot}></span>
              <span className={wsConnected ? styles.liveText : styles.offlineText}>
                {wsConnected ? 'LIVE FEED' : 'OFFLINE'}
              </span>
            </div>
          </div>
        </div>
      </header>

      {error && (
        <div className={styles.errorBanner}>
          <strong>Error:</strong> {error}
        </div>
      )}

      <main className={styles.mainContent}>
        {cities.length > 0 && (
          <div className={styles.cityStrip}>
            {cities.map((entry) => {
              const cityMetrics = allMetrics[normalizeCityKey(entry)];
              const active = normalizeCityKey(entry) === normalizeCityKey(city);
              const risk = cityMetrics?.riskLevel;
              const riskClass = risk === 'SEVERE'
                ? styles.stripSevere
                : risk === 'HIGH_RISK'
                ? styles.stripHigh
                : risk === 'ELEVATED'
                ? styles.stripElevated
                : styles.stripNormal;

              return (
                <button
                  key={entry}
                  className={`${styles.cityMiniCard} ${active ? styles.cityMiniActive : ''} ${riskClass}`}
                  onClick={() => handleCityChange(entry)}
                >
                  <span className={styles.miniCityName}>{toDisplayCity(entry)}</span>
                  <span className={styles.miniAqi}>{cityMetrics ? `AQI ${cityMetrics.aqi}` : 'No data'}</span>
                  <span className={styles.miniRisk}>{cityMetrics?.riskLevel || 'UNKNOWN'}</span>
                  <div className={styles.miniBottom}>
                    <span className={styles.miniScore}>
                      {cityMetrics?.cityHealthScore != null ? cityMetrics.cityHealthScore.toFixed(0) : '--'}
                    </span>
                    <span className={styles.miniTrend}>{cityMetrics?.aqiTrend || '-'}</span>
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {metrics ? (
          <>
            <section className={styles.heroSection}>
              <div className={styles.heroLeft}>
                <ScoreCard
                  city={toDisplayCity(city)}
                  score={metrics.cityHealthScore || 0}
                  lastUpdated={metrics.timestamp}
                  riskLevel={metrics.riskLevel}
                  trend={metrics.aqiTrend}
                />
              </div>

              <div className={styles.heroRight}>
                <div className={styles.heroBadgeRow}>
                  <RiskBadge level={metrics.riskLevel} />

                  {metrics.validationStatus && (
                    <span className={`${styles.validationBadge} ${
                      metrics.validationStatus === 'MATCH'
                        ? styles.valMatch
                        : metrics.validationStatus === 'MINOR_DEVIATION'
                        ? styles.valMinor
                        : metrics.validationStatus === 'MAJOR_DEVIATION'
                        ? styles.valMajor
                        : styles.valUnknown
                    }`}>
                      {metrics.validationStatus.replace('_', ' ')}
                    </span>
                  )}

                  {metrics.anomaly && <span className={styles.anomalyBadge}>ANOMALY DETECTED</span>}
                </div>

                <div className={styles.heroStats}>
                  <div className={styles.heroStatItem}>
                    <label>Live AQI</label>
                    <span className={styles.heroStatValue}>{metrics.aqi ?? '--'}</span>
                  </div>
                  <div className={styles.heroStatItem}>
                    <label>Baseline AQI</label>
                    <span>{metrics.baselineAqi != null ? metrics.baselineAqi.toFixed(0) : '--'}</span>
                  </div>
                  <div className={styles.heroStatItem}>
                    <label>Deviation</label>
                    <span className={(metrics.aqiDeviationPercent || 0) > 0 ? styles.devNeg : styles.devPos}>
                      {metrics.aqiDeviationPercent != null ? `${metrics.aqiDeviationPercent.toFixed(1)}%` : '--'}
                    </span>
                  </div>
                  <div className={styles.heroStatItem}>
                    <label>Rolling Avg</label>
                    <span>{metrics.rollingAqiAverage != null ? metrics.rollingAqiAverage.toFixed(0) : '--'}</span>
                  </div>
                  <div className={styles.heroStatItem}>
                    <label>Primary Driver</label>
                    <span className={styles.driverBadge}>{metrics.primaryDriver || '--'}</span>
                  </div>
                  <div className={styles.heroStatItem}>
                    <label>Confidence</label>
                    <span>
                      {metrics.dataConfidenceScore != null
                        ? `${(metrics.dataConfidenceScore * 100).toFixed(0)}%`
                        : '--'}
                    </span>
                  </div>
                </div>

                <div className={styles.currentAlert}>
                  <p className={styles.alertText}>{metrics.alertMessage || 'No alert context yet.'}</p>
                  <small className={styles.lastUpdated}>Last updated {formatLastUpdated()}</small>
                </div>
              </div>
            </section>

            <section className={styles.metricsGrid}>
              <MetricCard label="Air Quality Index" value={metrics.aqi ?? '--'} unit="AQI" trend={metrics.aqiDeviationPercent} />
              <MetricCard label="PM2.5" value={metrics.pm25 != null ? metrics.pm25.toFixed(1) : '--'} unit="ug/m3" />
              <MetricCard label="PM10" value={metrics.pm10 != null ? metrics.pm10.toFixed(1) : '--'} unit="ug/m3" />
              <MetricCard label="Temperature" value={metrics.temperature != null ? metrics.temperature.toFixed(1) : '--'} unit="C" />
              <MetricCard label="Humidity" value={metrics.humidity ?? '--'} unit="%" />
              <MetricCard label="Wind Speed" value={metrics.windSpeed != null ? metrics.windSpeed.toFixed(1) : '--'} unit="m/s" />
            </section>

            <section className={styles.validationRow}>
              <h3 className={styles.validationTitle}>Cross-source validation</h3>
              <div className={styles.validationCards}>
                <div className={styles.validationCard}>
                  <label>OpenAQ PM2.5</label>
                  <span>{metrics.openAqPm25 != null ? metrics.openAqPm25.toFixed(1) : '--'} ug/m3</span>
                  <small>OpenWeather PM2.5 {metrics.pm25 != null ? metrics.pm25.toFixed(1) : '--'} ug/m3</small>
                </div>
                <div className={styles.validationCard}>
                  <label>OpenAQ PM10</label>
                  <span>{metrics.openAqPm10 != null ? metrics.openAqPm10.toFixed(1) : '--'} ug/m3</span>
                  <small>OpenWeather PM10 {metrics.pm10 != null ? metrics.pm10.toFixed(1) : '--'} ug/m3</small>
                </div>
                <div className={styles.validationCard}>
                  <label>Validation status</label>
                  <span className={styles.valStatusText}>{metrics.validationStatus || 'UNAVAILABLE'}</span>
                  <small>
                    Confidence {metrics.dataConfidenceScore != null
                      ? `${(metrics.dataConfidenceScore * 100).toFixed(0)}%`
                      : '--'}
                  </small>
                </div>
              </div>
            </section>

            <section className={styles.analyticsRow}>
              <TrendChart
                data={aqiTrend}
                title={`AQI Trend - ${toDisplayCity(city)}`}
                baselineAqi={metrics.baselineAqi}
                rollingAvg={metrics.rollingAqiAverage}
                trend={metrics.aqiTrend}
              />

              <div className={styles.insightsPanel}>
                <h3 className={styles.insightsTitle}>Urban Insight Engine</h3>
                <ul className={styles.insightsList}>
                  {insights.map((insight, idx) => (
                    <li key={idx} className={styles.insightItem}>
                      <span>{insight}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          </>
        ) : (
          <Loading message="Waiting for live analytics stream..." />
        )}
      </main>

      <aside className={styles.sidebar}>
        <AlertsPanel alerts={alerts} maxVisible={12} />
      </aside>

      <footer className={styles.footer}>
        <p>UrbanPulse v2.0 | Sources: OpenWeather + OpenAQ + Kaggle | Flow: Kafka to Spark to Notification to Dashboard</p>
      </footer>
    </div>
  );
};

export default Dashboard;
