import React from 'react';
import PropTypes from 'prop-types';
import RiskBadge from './RiskBadge';
import styles from './Dashboard.module.css';

const RISK_BORDER = {
  SEVERE: '3px solid #ff5c7a',
  HIGH_RISK: '3px solid #ff8a4c',
  ELEVATED: '3px solid #f5bd3f',
  NORMAL: '3px solid #3ddc97',
};

const AlertsPanel = ({ alerts, maxVisible = 12 }) => {
  if (!alerts || alerts.length === 0) {
    return (
      <div className={styles.alertsPanel}>
        <h3>Intelligence Alerts</h3>
        <p className={styles.noAlerts}>No active alerts in the latest stream window.</p>
      </div>
    );
  }

  const displayAlerts = alerts.slice(0, maxVisible);

  const formatTime = (timestamp) => {
    try {
      return new Date(timestamp).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return 'N/A';
    }
  };

  return (
    <div className={styles.alertsPanel}>
      <h3>Intelligence Alerts ({alerts.length})</h3>
      <div className={styles.alertsList}>
        {displayAlerts.map((alert, idx) => {
          const borderLeft = RISK_BORDER[alert.riskLevel] || RISK_BORDER.ELEVATED;
          const cityName = String(alert.city || 'Unknown');
          const alertType = String(alert.alertType || 'UPDATE');

          return (
            <div
              key={alert.id || idx}
              className={styles.alertItem}
              style={{ borderLeft }}
            >
              <div className={styles.alertHeader}>
                <RiskBadge level={alert.riskLevel} />
                <span className={styles.alertCity}>{cityName}</span>
                {alertType !== 'UPDATE' && (
                  <span className={styles.alertType}>{alertType}</span>
                )}
                <span className={styles.alertTime}>{formatTime(alert.timestamp)}</span>
              </div>

              <p className={styles.alertMessage}>{alert.message}</p>

              <div className={styles.alertMeta}>
                AQI <strong>{alert.aqi}</strong>
                {alert.cityHealthScore != null && (
                  <>
                    {' | '} Score <strong>{Number(alert.cityHealthScore).toFixed(1)}</strong>
                  </>
                )}
                {alert.primaryDriver && (
                  <>
                    {' | '} Driver <strong>{alert.primaryDriver}</strong>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

AlertsPanel.propTypes = {
  alerts: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.string,
    city: PropTypes.string,
    timestamp: PropTypes.string,
    riskLevel: PropTypes.string,
    alertType: PropTypes.string,
    message: PropTypes.string,
    aqi: PropTypes.number,
    cityHealthScore: PropTypes.number,
    primaryDriver: PropTypes.string,
  })),
  maxVisible: PropTypes.number,
};

export default AlertsPanel;
