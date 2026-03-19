import React, { useState, useMemo, useRef, useEffect } from 'react';
import styles from './Header.module.css';

const VIEWS = { DASHBOARD: 'dashboard', MAP: 'map', COMPARE: 'compare' };

const normalizeCityKey = (v) => String(v || '').trim().toLowerCase();
const toDisplayCity = (v) => {
  const raw = String(v || '').trim();
  if (!raw) return 'Unknown';
  return raw.split(' ').filter(Boolean).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
};

const NAV_TABS = [
  { key: VIEWS.DASHBOARD, label: 'Dashboard', icon: '📊' },
  { key: VIEWS.MAP, label: 'Map', icon: '🗺️' },
  { key: VIEWS.COMPARE, label: 'Compare', icon: '⚖️' },
];

const Header = ({ activeView, onViewChange, wsConnected, userLocation, allMetrics, cities, onCitySelect }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const searchRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const filteredCities = useMemo(() => {
    if (!searchQuery.trim()) return cities;
    const q = searchQuery.toLowerCase();
    return cities.filter((c) => normalizeCityKey(c).includes(q));
  }, [searchQuery, cities]);

  const networkStats = useMemo(() => {
    const entries = Object.values(allMetrics || {}).filter(Boolean);
    if (entries.length === 0) return null;
    const avgAqi = entries.reduce((s, e) => s + (e.aqi || 0), 0) / entries.length;
    const avgScore = entries.reduce((s, e) => s + (e.cityHealthScore || 0), 0) / entries.length;
    const highRisk = entries.filter((e) => e.riskLevel === 'HIGH_RISK' || e.riskLevel === 'SEVERE').length;
    return { avgAqi: avgAqi.toFixed(0), avgScore: avgScore.toFixed(0), highRisk, totalCities: entries.length };
  }, [allMetrics]);

  const handleCityClick = (city) => {
    setSearchQuery('');
    setSearchOpen(false);
    onCitySelect(city);
  };

  return (
    <header className={styles.header}>
      <div className={styles.headerContent}>
        {/* Brand */}
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

        {/* Nav tabs */}
        <nav className={styles.navTabs}>
          {NAV_TABS.map((tab) => (
            <button
              key={tab.key}
              className={`${styles.navTab} ${activeView === tab.key ? styles.navTabActive : ''}`}
              onClick={() => onViewChange(tab.key)}
            >
              <span className={styles.navIcon}>{tab.icon}</span>
              <span className={styles.navLabel}>{tab.label}</span>
            </button>
          ))}
        </nav>

        {/* Right section */}
        <div className={styles.headerRight}>
          {/* City search */}
          <div className={styles.searchContainer} ref={searchRef}>
            <div className={styles.searchInputWrap}>
              <span className={styles.searchIcon}>🔍</span>
              <input
                type="text"
                className={styles.searchInput}
                placeholder="Search city..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setSearchOpen(true);
                }}
                onFocus={() => setSearchOpen(true)}
              />
            </div>
            {searchOpen && filteredCities.length > 0 && (
              <div className={styles.searchDropdown}>
                {filteredCities.map((city) => {
                  const m = allMetrics[normalizeCityKey(city)];
                  return (
                    <button
                      key={city}
                      className={styles.searchResultItem}
                      onClick={() => handleCityClick(city)}
                    >
                      <span className={styles.searchCityName}>{toDisplayCity(city)}</span>
                      <span className={styles.searchCityMeta}>
                        {m ? `AQI ${m.aqi} · ${m.riskLevel || 'UNKNOWN'}` : 'No data'}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Network stats mini */}
          {networkStats && (
            <div className={styles.networkMini}>
              <span className={styles.networkStat}>
                <span className={styles.networkLabel}>Avg AQI</span>
                <span className={styles.networkValue}>{networkStats.avgAqi}</span>
              </span>
              <span className={styles.networkDivider}>|</span>
              <span className={styles.networkStat}>
                <span className={styles.networkLabel}>Cities</span>
                <span className={styles.networkValue}>{networkStats.totalCities}</span>
              </span>
              {networkStats.highRisk > 0 && (
                <>
                  <span className={styles.networkDivider}>|</span>
                  <span className={styles.networkStat}>
                    <span className={styles.networkLabel}>At Risk</span>
                    <span className={styles.networkValueDanger}>{networkStats.highRisk}</span>
                  </span>
                </>
              )}
            </div>
          )}

          {/* Location indicator */}
          {userLocation.status === 'granted' && userLocation.nearestCity && (
            <div className={styles.locationBadge} onClick={() => handleCityClick(toDisplayCity(userLocation.nearestCity))}>
              <span className={styles.locationIcon}>📍</span>
              <span className={styles.locationText}>
                Near {toDisplayCity(userLocation.nearestCity)}
                <small className={styles.locationDist}>
                  {userLocation.distance < 1 ? '<1' : userLocation.distance.toFixed(0)} km
                </small>
              </span>
            </div>
          )}

          {/* Data source badges */}
          <div className={styles.dataSourceBadges}>
            <span className={styles.sourceBadge}>OpenWeather</span>
            <span className={styles.sourceBadge}>Open-Meteo AQ</span>
            <span className={styles.sourceBadge}>Kafka + Spark</span>
          </div>

          {/* Live indicator */}
          <div className={styles.liveIndicator}>
            <span className={wsConnected ? styles.liveDot : styles.offlineDot}></span>
            <span className={wsConnected ? styles.liveText : styles.offlineText}>
              {wsConnected ? 'LIVE' : 'OFFLINE'}
            </span>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
