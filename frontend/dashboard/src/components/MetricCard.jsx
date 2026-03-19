import React from 'react';
import PropTypes from 'prop-types';
import styles from './Dashboard.module.css';

const MetricCard = ({ label, value, unit, trend = null }) => {
  const hasTrend = trend !== null && trend !== undefined;
  const trendClass = hasTrend ? (trend > 0 ? styles.trendUp : styles.trendDown) : '';

  return (
    <div className={styles.metricCard}>
      <div className={styles.metricHeader}>
        <span className={styles.metricLabel}>{label}</span>
      </div>

      <div className={styles.metricValue}>
        {value}
        {unit && <span className={styles.metricUnit}>{unit}</span>}
      </div>

      {hasTrend && (
        <div className={`${styles.metricTrend} ${trendClass}`}>
          {trend > 0 ? 'UP' : 'DOWN'} {Math.abs(trend).toFixed(1)}%
        </div>
      )}
    </div>
  );
};

MetricCard.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  unit: PropTypes.string,
  trend: PropTypes.number,
};

export default MetricCard;
