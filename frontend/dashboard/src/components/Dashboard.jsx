import React, { useEffect, useMemo, useState } from 'react';
import AlertsPanel from './AlertsPanel';
import Loading from './Loading';
import MetricCard from './MetricCard';
import RiskBadge from './RiskBadge';
import ScoreCard from './ScoreCard';
import TrendChart from './TrendChart';
import { apiClient } from '../services/api';
import styles from './Dashboard.module.css';

const normalizeCityKey = (value) => String(value || '').trim().toLowerCase();

const toDisplayCity = (value) => {
  const raw = String(value || '').trim();
  if (!raw) return 'Unknown';
  return raw.split(' ').filter(Boolean).map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
};

const TIME_RANGES = [
  { key: 'live', label: 'Live' },
  { key: '1h', label: '1H' },
  { key: '6h', label: '6H' },
  { key: '24h', label: '24H' },
  { key: '7d', label: '7D' },
];

function generateInsights(metrics, allMetrics, cities) {
  if (!metrics || !metrics.city) return [];
  const currentCity = toDisplayCity(metrics.city);
  const insights = [];

  if (metrics.baselineAqi != null && metrics.aqiDeviationPercent != null) {
    const deviation = Number(metrics.aqiDeviationPercent);
    const comparative = deviation >= 0 ? 'worse than' : 'better than';
    insights.push(`${currentCity} air quality is ${Math.abs(deviation).toFixed(1)}% ${comparative} its historical normal.`);
  }

  if (metrics.rollingAqiAverage != null && metrics.aqi != null) {
    const delta = ((metrics.aqi - metrics.rollingAqiAverage) / (metrics.rollingAqiAverage || 1)) * 100;
    if (Math.abs(delta) > 2) {
      const comparative = delta >= 0 ? 'worse than' : 'better than';
      insights.push(`Right now it is ${Math.abs(delta).toFixed(1)}% ${comparative} the recent trend.`);
    }
  }

  if (metrics.primaryDriver) {
    const driverMap = { AQI: 'Air Quality Index', 'PM2.5': 'Fine particulate matter (PM2.5)', 'PM10': 'Coarse dust (PM10)', temperature: 'Temperature', humidity: 'Humidity' };
    const driverName = driverMap[metrics.primaryDriver] || metrics.primaryDriver;
    insights.push(`${driverName} is the main factor dragging the environment score down.`);
  }

  if (metrics.validationStatus && metrics.validationStatus !== 'UNAVAILABLE') {
    if (metrics.validationStatus === 'MATCH') {
      insights.push('Independent sensors confirm these readings — high confidence.');
    } else if (metrics.validationStatus === 'MINOR_DEVIATION') {
      insights.push('Cross-source sensors show a minor difference — readings are still reliable.');
    } else {
      insights.push('Cross-source sensors diverge significantly — treat this data with caution.');
    }
  }

  if (metrics.aqiTimeChangePct != null && Math.abs(metrics.aqiTimeChangePct) > 10) {
    const dir = metrics.aqiTimeChangePct > 0 ? 'worsened by' : 'improved by';
    insights.push(`AQI has ${dir} ${Math.abs(metrics.aqiTimeChangePct).toFixed(1)}% over the last 10 readings.`);
  }

  if (cities.length > 1) {
    const ranked = cities
      .map((cityName) => allMetrics[normalizeCityKey(cityName)])
      .filter(Boolean)
      .sort((a, b) => (a.cityHealthScore ?? 100) - (b.cityHealthScore ?? 100));
    if (ranked.length > 0 && normalizeCityKey(ranked[0].city) !== normalizeCityKey(metrics.city)) {
      insights.push(`${toDisplayCity(ranked[0].city)} is under the most environmental stress of all monitored cities right now.`);
    }
  }

  return insights.slice(0, 5);
}

const Dashboard = ({
  selectedCity,
  onCityChange,
  allMetrics,
  alerts,
  cities,
  loading,
  wsConnected,
  topRiskCity,
  mostImprovedCity,
  userLocation,
}) => {
  const [aqiTrend, setAqiTrend] = useState([]);
  const [timeRange, setTimeRange] = useState('live');
  const [cityFilter, setCityFilter] = useState('');
  const [lastUpdated, setLastUpdated] = useState(null);

  const city = selectedCity;
  const metrics = allMetrics[normalizeCityKey(city)] || null;

  // Update trend data when city changes or time range changes
  useEffect(() => {
    if (!city) return;
    let cancelled = false;

    const fetchTrend = async () => {
      try {
        if (timeRange === 'live') {
          const historyData = await apiClient.getAqiHistory(city);
          const history = (historyData.history || []).map((item) => ({
            time: item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : '',
            value: item.aqi,
          }));
          if (!cancelled) {
            setAqiTrend(history.length > 0 ? history : (metrics?.aqi != null ? [{ time: new Date().toLocaleTimeString(), value: metrics.aqi }] : []));
          }
        } else {
          const historyData = await apiClient.getHistoryWithRange(city, timeRange);
          const history = (historyData.history || []).map((item) => ({
            time: item.recordedAt ? new Date(item.recordedAt).toLocaleTimeString() : (item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ''),
            value: item.aqi,
          }));
          if (!cancelled) {
            setAqiTrend(history.length > 0 ? history : []);
          }
        }
      } catch {
        if (!cancelled && metrics?.aqi != null) {
          setAqiTrend([{ time: new Date().toLocaleTimeString(), value: metrics.aqi }]);
        }
      }
    };

    fetchTrend();
    return () => { cancelled = true; };
  }, [city, timeRange]);

  // Update last updated when metrics change
  useEffect(() => {
    if (metrics?.timestamp) {
      setLastUpdated(new Date(metrics.timestamp));
    }
  }, [metrics?.timestamp]);

  // Add live trend points from WebSocket
  useEffect(() => {
    if (timeRange !== 'live' || !metrics?.aqi) return;
    setAqiTrend((prev) => {
      const latest = prev.length > 0 ? prev[prev.length - 1] : null;
      if (latest && latest.value === metrics.aqi) return prev;
      return [...prev, { time: new Date().toLocaleTimeString(), value: metrics.aqi }].slice(-50);
    });
  }, [metrics?.aqi, timeRange]);

  const filteredCities = useMemo(() => {
    if (!cityFilter.trim()) return cities;
    const q = cityFilter.toLowerCase();
    return cities.filter((c) => normalizeCityKey(c).includes(q));
  }, [cities, cityFilter]);

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
      <main className={styles.mainContent}>
        {/* Location banner */}
        {userLocation?.status === 'granted' && userLocation.nearestCity && (
          <div className={styles.locationBanner}>
            <span className={styles.locationBannerIcon}>📍</span>
            <span>
              You're near <strong>{toDisplayCity(userLocation.nearestCity)}</strong>
              {userLocation.distance != null && ` (~${userLocation.distance < 1 ? '<1' : userLocation.distance.toFixed(0)} km)`}
              {(() => {
                const nearMetrics = allMetrics[normalizeCityKey(userLocation.nearestCity)];
                return nearMetrics ? ` — AQI ${nearMetrics.aqi}, ${nearMetrics.riskLevel || 'UNKNOWN'}` : '';
              })()}
            </span>
            {normalizeCityKey(userLocation.nearestCity) !== normalizeCityKey(city) && (
              <button
                className={styles.locationBannerBtn}
                onClick={() => onCityChange(toDisplayCity(userLocation.nearestCity))}
              >
                Switch to {toDisplayCity(userLocation.nearestCity)}
              </button>
            )}
          </div>
        )}

        {/* City strip with filter */}
        {cities.length > 0 && (
          <>
            <div className={styles.cityStripHeader}>
              <input
                type="text"
                className={styles.cityFilterInput}
                placeholder="Filter cities..."
                value={cityFilter}
                onChange={(e) => setCityFilter(e.target.value)}
              />
              <span className={styles.cityCount}>{filteredCities.length} cities</span>
            </div>
            <div className={styles.cityStrip}>
              {filteredCities.map((entry) => {
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
                    onClick={() => onCityChange(entry)}
                  >
                    <span className={styles.miniCityName}>{toDisplayCity(entry)}</span>
                    <span className={styles.miniAqi}>{cityMetrics ? `AQI ${cityMetrics.aqi}` : 'No data'}</span>
                    <span className={styles.miniRisk}>{cityMetrics?.riskLevel || 'UNKNOWN'}</span>
                    <div className={styles.miniBottom}>
                      <span className={styles.miniScore}>
                        {cityMetrics?.cityHealthScore != null ? cityMetrics.cityHealthScore.toFixed(0) : '--'}
                      </span>
                      <span className={styles.miniTrend}>
                        {cityMetrics?.aqiTrend === 'RISING' ? '↑' : cityMetrics?.aqiTrend === 'FALLING' ? '↓' : '→'} {cityMetrics?.aqiTrend || 'STABLE'}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </>
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
                      {(() => {
                        const arrow = metrics.aqiTrend === 'RISING' ? ' ↑' : metrics.aqiTrend === 'FALLING' ? ' ↓' : ' →';
                        const label = (metrics.aqiDeviationPercent || 0) > 0 ? ' worse' : ' better';
                        return metrics.aqiDeviationPercent != null
                          ? `${metrics.aqiDeviationPercent.toFixed(1)}%${label}${arrow}`
                          : '--';
                      })()}
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
              <MetricCard label="PM2.5" value={metrics.pm25 != null ? metrics.pm25.toFixed(1) : '--'} unit="µg/m³" />
              <MetricCard label="PM10" value={metrics.pm10 != null ? metrics.pm10.toFixed(1) : '--'} unit="µg/m³" />
              <MetricCard label="Temperature" value={metrics.temperature != null ? metrics.temperature.toFixed(1) : '--'} unit="°C" />
              <MetricCard label="Humidity" value={metrics.humidity ?? '--'} unit="%" />
              <MetricCard label="Wind Speed" value={metrics.windSpeed != null ? metrics.windSpeed.toFixed(1) : '--'} unit="m/s" />
            </section>

            <section className={styles.validationRow}>
              <h3 className={styles.validationTitle}>Cross-source validation</h3>
              <div className={styles.validationCards}>
                <div className={styles.validationCard}>
                  <label>Open-Meteo PM2.5</label>
                  <span>{metrics.openAqPm25 != null ? metrics.openAqPm25.toFixed(1) : '--'} µg/m³</span>
                  <small>OpenWeather PM2.5 {metrics.pm25 != null ? metrics.pm25.toFixed(1) : '--'} µg/m³</small>
                </div>
                <div className={styles.validationCard}>
                  <label>Open-Meteo PM10</label>
                  <span>{metrics.openAqPm10 != null ? metrics.openAqPm10.toFixed(1) : '--'} µg/m³</span>
                  <small>OpenWeather PM10 {metrics.pm10 != null ? metrics.pm10.toFixed(1) : '--'} µg/m³</small>
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

            {/* Analytics with time range selector */}
            <section className={styles.analyticsRow}>
              <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <div className={styles.timeRangeBar}>
                  {TIME_RANGES.map((tr) => (
                    <button
                      key={tr.key}
                      className={`${styles.timeRangeBtn} ${timeRange === tr.key ? styles.timeRangeBtnActive : ''}`}
                      onClick={() => setTimeRange(tr.key)}
                    >
                      {tr.label}
                    </button>
                  ))}
                </div>
                <TrendChart
                  data={aqiTrend}
                  title={`AQI Trend — ${toDisplayCity(city)}${timeRange !== 'live' ? ` (${timeRange})` : ''}`}
                  baselineAqi={metrics.baselineAqi}
                  rollingAvg={metrics.rollingAqiAverage}
                  trend={metrics.aqiTrend}
                />
              </div>

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

            <section className={styles.intelligencePanels}>
              <div className={styles.intelCard}>
                <h4 className={styles.intelCardTitle}>Top Risk City</h4>
                {topRiskCity ? (
                  <>
                    <p className={styles.intelCityName}>{toDisplayCity(topRiskCity.city)}</p>
                    <p className={styles.intelSubtext}>Health score: <strong>{topRiskCity.cityHealthScore?.toFixed(0) ?? '--'}</strong></p>
                    <p className={styles.intelSubtext}>AQI: <strong>{topRiskCity.aqi ?? '--'}</strong> &bull; {topRiskCity.riskLevel}</p>
                  </>
                ) : (
                  <p className={styles.intelEmpty}>Waiting for data…</p>
                )}
              </div>

              <div className={styles.intelCard}>
                <h4 className={styles.intelCardTitle}>Most Improved City</h4>
                {mostImprovedCity ? (
                  <>
                    <p className={styles.intelCityName}>{toDisplayCity(mostImprovedCity.city)}</p>
                    <p className={styles.intelSubtext}>Trend: <strong>{mostImprovedCity.aqiTrend ?? '--'}</strong></p>
                    <p className={styles.intelSubtext}>
                      {mostImprovedCity.aqiDeviationPercent != null
                        ? `${Math.abs(mostImprovedCity.aqiDeviationPercent).toFixed(1)}% closer to normal`
                        : 'Improving'}
                    </p>
                  </>
                ) : (
                  <p className={styles.intelEmpty}>No improvement detected yet…</p>
                )}
              </div>

              <div className={styles.intelCard}>
                <h4 className={styles.intelCardTitle}>Recommended Action</h4>
                <p className={styles.intelRecommendation}>
                  {metrics.recommendation || 'Waiting for intelligence stream…'}
                </p>
                <p className={styles.intelSubtext}>Risk: <strong>{metrics.riskLevel}</strong> &bull; Trend: <strong>{metrics.aqiTrend || '--'}</strong></p>
              </div>

              <div className={styles.intelCard}>
                <h4 className={styles.intelCardTitle}>Confidence Level</h4>
                <p className={styles.intelConfidenceValue}>
                  {metrics.dataConfidenceScore != null
                    ? `${(metrics.dataConfidenceScore * 100).toFixed(0)}%`
                    : '--'}
                </p>
                <p className={styles.intelSubtext}>
                  {metrics.validationStatus === 'MATCH'
                    ? 'Cross-source confirms readings'
                    : metrics.validationStatus === 'MINOR_DEVIATION'
                    ? 'Minor cross-source deviation'
                    : metrics.validationStatus === 'MAJOR_DEVIATION'
                    ? 'Major cross-source deviation'
                    : 'No cross-validation'}
                </p>
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
        <p>UrbanPulse v2.0 | Sources: OpenWeather + Open-Meteo AQ + Kaggle | Pipeline: Kafka → Spark → Spring → React</p>
      </footer>
    </div>
  );
};

export default Dashboard;
