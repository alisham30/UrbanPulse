import React, { useState, useMemo, useEffect } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, BarChart, Bar, RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis } from 'recharts';
import { apiClient } from '../services/api';
import styles from './CompareView.module.css';

const normalizeCityKey = (v) => String(v || '').trim().toLowerCase();
const toDisplayCity = (v) => {
  const raw = String(v || '').trim();
  if (!raw) return 'Unknown';
  return raw.split(' ').filter(Boolean).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
};

const CHART_COLORS = ['#30c4ff', '#3ddc97', '#f5bd3f', '#ff5c7a', '#9b4dff', '#ff8a4c', '#e056fd', '#7ed6df'];

const COMPARE_METRICS = [
  { key: 'aqi', label: 'AQI', unit: '', higherIsWorse: true },
  { key: 'cityHealthScore', label: 'Health Score', unit: '/100', higherIsWorse: false },
  { key: 'pm25', label: 'PM2.5', unit: ' µg/m³', higherIsWorse: true },
  { key: 'pm10', label: 'PM10', unit: ' µg/m³', higherIsWorse: true },
  { key: 'temperature', label: 'Temperature', unit: '°C', higherIsWorse: null },
  { key: 'humidity', label: 'Humidity', unit: '%', higherIsWorse: null },
  { key: 'windSpeed', label: 'Wind Speed', unit: ' m/s', higherIsWorse: null },
  { key: 'dataConfidenceScore', label: 'Confidence', unit: '', higherIsWorse: false, isPercent: true },
];

const CompareView = ({ allMetrics, cities }) => {
  const [selectedCities, setSelectedCities] = useState([]);
  const [rankings, setRankings] = useState(null);
  const [compareData, setCompareData] = useState(null);
  const [activeChart, setActiveChart] = useState('bar');

  // Auto-select first 3 cities on load
  useEffect(() => {
    if (cities.length > 0 && selectedCities.length === 0) {
      setSelectedCities(cities.slice(0, Math.min(3, cities.length)));
    }
  }, [cities]);

  // Fetch rankings on mount
  useEffect(() => {
    apiClient.getRankings().then(setRankings).catch(() => null);
  }, []);

  // Fetch compare data when selection changes
  useEffect(() => {
    if (selectedCities.length >= 2) {
      apiClient.getCompareData(selectedCities).then(setCompareData).catch(() => null);
    }
  }, [selectedCities]);

  const toggleCity = (city) => {
    setSelectedCities((prev) => {
      if (prev.includes(city)) return prev.filter((c) => c !== city);
      if (prev.length >= 6) return prev; // Max 6
      return [...prev, city];
    });
  };

  // Comparison table data
  const tableData = useMemo(() => {
    return COMPARE_METRICS.map((metric) => {
      const values = selectedCities.map((city) => {
        const m = allMetrics[normalizeCityKey(city)];
        const raw = m?.[metric.key];
        return { city, raw, formatted: raw != null ? (metric.isPercent ? `${(raw * 100).toFixed(0)}%` : typeof raw === 'number' ? raw.toFixed(1) : raw) : '--' };
      });

      let best = null;
      let worst = null;
      const numericValues = values.filter((v) => v.raw != null);
      if (numericValues.length >= 2 && metric.higherIsWorse !== null) {
        const sorted = [...numericValues].sort((a, b) => a.raw - b.raw);
        best = metric.higherIsWorse ? sorted[0].city : sorted[sorted.length - 1].city;
        worst = metric.higherIsWorse ? sorted[sorted.length - 1].city : sorted[0].city;
      }

      return { ...metric, values, best, worst };
    });
  }, [selectedCities, allMetrics]);

  // Bar chart data
  const barData = useMemo(() => {
    return selectedCities.map((city) => {
      const m = allMetrics[normalizeCityKey(city)];
      return {
        city: toDisplayCity(city),
        AQI: m?.aqi || 0,
        'Health Score': m?.cityHealthScore || 0,
        'PM2.5': m?.pm25 || 0,
      };
    });
  }, [selectedCities, allMetrics]);

  // Radar chart data
  const radarData = useMemo(() => {
    const metrics = ['aqi', 'cityHealthScore', 'pm25', 'humidity', 'windSpeed'];
    const labels = ['AQI', 'Health', 'PM2.5', 'Humidity', 'Wind'];
    const maxVals = { aqi: 500, cityHealthScore: 100, pm25: 300, humidity: 100, windSpeed: 20 };

    return metrics.map((key, i) => {
      const point = { metric: labels[i] };
      selectedCities.forEach((city) => {
        const m = allMetrics[normalizeCityKey(city)];
        const raw = m?.[key] || 0;
        point[toDisplayCity(city)] = Math.min(100, (raw / (maxVals[key] || 100)) * 100);
      });
      return point;
    });
  }, [selectedCities, allMetrics]);

  return (
    <div className={styles.compareWrapper}>
      {/* City selector */}
      <div className={styles.selectorSection}>
        <h2 className={styles.sectionTitle}>Select Cities to Compare</h2>
        <p className={styles.selectorHint}>Choose 2-6 cities for side-by-side analysis</p>
        <div className={styles.cityChips}>
          {cities.map((city) => {
            const isSelected = selectedCities.includes(city);
            const m = allMetrics[normalizeCityKey(city)];
            return (
              <button
                key={city}
                className={`${styles.cityChip} ${isSelected ? styles.cityChipSelected : ''}`}
                onClick={() => toggleCity(city)}
              >
                <span className={styles.chipName}>{toDisplayCity(city)}</span>
                {m && <span className={styles.chipAqi}>AQI {m.aqi}</span>}
                {isSelected && <span className={styles.chipCheck}>✓</span>}
              </button>
            );
          })}
        </div>
      </div>

      {selectedCities.length < 2 ? (
        <div className={styles.emptyState}>
          <p>Select at least 2 cities to start comparing</p>
        </div>
      ) : (
        <>
          {/* Overview cards */}
          <div className={styles.overviewCards}>
            {selectedCities.map((city, idx) => {
              const m = allMetrics[normalizeCityKey(city)];
              if (!m) return null;
              return (
                <div key={city} className={styles.overviewCard} style={{ borderTopColor: CHART_COLORS[idx % CHART_COLORS.length] }}>
                  <div className={styles.overviewColorBar} style={{ background: CHART_COLORS[idx % CHART_COLORS.length] }}></div>
                  <h3 className={styles.overviewCity}>{toDisplayCity(city)}</h3>
                  <div className={styles.overviewScore}>{m.cityHealthScore?.toFixed(0) ?? '--'}</div>
                  <div className={styles.overviewMeta}>
                    <span>AQI {m.aqi ?? '--'}</span>
                    <span className={styles.overviewRisk} style={{ color: m.riskLevel === 'SEVERE' ? '#ff3355' : m.riskLevel === 'HIGH_RISK' ? '#ff8a4c' : m.riskLevel === 'ELEVATED' ? '#f5bd3f' : '#3ddc97' }}>
                      {m.riskLevel || 'UNKNOWN'}
                    </span>
                  </div>
                  <div className={styles.overviewTrend}>
                    {m.aqiTrend === 'RISING' ? '📈' : m.aqiTrend === 'FALLING' ? '📉' : '➡️'} {m.aqiTrend || 'STABLE'}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Chart selection */}
          <div className={styles.chartSection}>
            <div className={styles.chartTabs}>
              <button className={`${styles.chartTab} ${activeChart === 'bar' ? styles.chartTabActive : ''}`} onClick={() => setActiveChart('bar')}>
                Bar Chart
              </button>
              <button className={`${styles.chartTab} ${activeChart === 'radar' ? styles.chartTabActive : ''}`} onClick={() => setActiveChart('radar')}>
                Radar Chart
              </button>
            </div>

            <div className={styles.chartContainer}>
              {activeChart === 'bar' && (
                <ResponsiveContainer width="100%" height={350}>
                  <BarChart data={barData} margin={{ top: 20, right: 30, left: 10, bottom: 30 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(52,78,113,0.3)" />
                    <XAxis dataKey="city" stroke="#8ca6be" fontSize={12} />
                    <YAxis stroke="#8ca6be" fontSize={11} />
                    <Tooltip
                      contentStyle={{ background: 'rgba(10,18,32,0.95)', border: '1px solid rgba(52,78,113,0.6)', borderRadius: '8px', color: '#d0e4f3' }}
                    />
                    <Legend />
                    <Bar dataKey="AQI" fill="#30c4ff" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="Health Score" fill="#3ddc97" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="PM2.5" fill="#f5bd3f" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}

              {activeChart === 'radar' && (
                <ResponsiveContainer width="100%" height={350}>
                  <RadarChart data={radarData}>
                    <PolarGrid stroke="rgba(52,78,113,0.4)" />
                    <PolarAngleAxis dataKey="metric" stroke="#8ca6be" fontSize={11} />
                    <PolarRadiusAxis stroke="rgba(52,78,113,0.3)" fontSize={10} />
                    {selectedCities.map((city, idx) => (
                      <Radar
                        key={city}
                        name={toDisplayCity(city)}
                        dataKey={toDisplayCity(city)}
                        stroke={CHART_COLORS[idx % CHART_COLORS.length]}
                        fill={CHART_COLORS[idx % CHART_COLORS.length]}
                        fillOpacity={0.15}
                        strokeWidth={2}
                      />
                    ))}
                    <Legend />
                    <Tooltip
                      contentStyle={{ background: 'rgba(10,18,32,0.95)', border: '1px solid rgba(52,78,113,0.6)', borderRadius: '8px', color: '#d0e4f3' }}
                    />
                  </RadarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* Comparison Table */}
          <div className={styles.tableSection}>
            <h3 className={styles.sectionTitle}>Detailed Comparison</h3>
            <div className={styles.tableWrap}>
              <table className={styles.compTable}>
                <thead>
                  <tr>
                    <th className={styles.metricCol}>Metric</th>
                    {selectedCities.map((city, idx) => (
                      <th key={city} style={{ borderTop: `3px solid ${CHART_COLORS[idx % CHART_COLORS.length]}` }}>
                        {toDisplayCity(city)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tableData.map((row) => (
                    <tr key={row.key}>
                      <td className={styles.metricCol}>
                        <span className={styles.metricName}>{row.label}</span>
                        {row.unit && <span className={styles.metricUnit}>{row.unit}</span>}
                      </td>
                      {row.values.map((v) => (
                        <td
                          key={v.city}
                          className={`${styles.valueCell} ${v.city === row.best ? styles.bestCell : ''} ${v.city === row.worst ? styles.worstCell : ''}`}
                        >
                          {v.formatted}
                          {v.city === row.best && <span className={styles.bestBadge}>Best</span>}
                          {v.city === row.worst && <span className={styles.worstBadge}>Worst</span>}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Rankings */}
          {rankings && rankings.rankings && rankings.rankings.length > 0 && (
            <div className={styles.rankingsSection}>
              <h3 className={styles.sectionTitle}>City Rankings (by Health Score)</h3>
              <div className={styles.rankingCards}>
                {rankings.rankings.map((r, idx) => {
                  const m = allMetrics[normalizeCityKey(r.city)];
                  return (
                    <div key={r.city} className={styles.rankCard}>
                      <div className={styles.rankNumber}>#{idx + 1}</div>
                      <div className={styles.rankInfo}>
                        <span className={styles.rankCity}>{toDisplayCity(r.city)}</span>
                        <span className={styles.rankScore}>Score: {r.cityHealthScore?.toFixed(0) ?? '--'}</span>
                      </div>
                      <div className={styles.rankAqi}>AQI {r.aqi ?? '--'}</div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Compare API result */}
          {compareData && compareData.bestCity && (
            <div className={styles.compareInsights}>
              <div className={styles.insightCard}>
                <span className={styles.insightIcon}>🏆</span>
                <div>
                  <h4 className={styles.insightLabel}>Best City</h4>
                  <p className={styles.insightValue}>{toDisplayCity(compareData.bestCity)}</p>
                </div>
              </div>
              <div className={styles.insightCard}>
                <span className={styles.insightIcon}>⚠️</span>
                <div>
                  <h4 className={styles.insightLabel}>Needs Attention</h4>
                  <p className={styles.insightValue}>{toDisplayCity(compareData.worstCity)}</p>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default CompareView;
