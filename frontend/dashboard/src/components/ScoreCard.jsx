import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import styles from './Dashboard.module.css';

const TREND_ARROW = { RISING: '^', FALLING: 'v', STABLE: '-' };
const TREND_COLOR = { RISING: '#ff5c7a', FALLING: '#3ddc97', STABLE: '#30c4ff' };

const ScoreCard = ({ city, score, lastUpdated, riskLevel, trend }) => {
  const safeScore = Number.isFinite(score) ? score : 0;
  const [displayScore, setDisplayScore] = useState(safeScore);

  useEffect(() => {
    let frameId;
    const start = displayScore;
    const target = safeScore;
    const duration = 450;
    const startTime = performance.now();

    const animate = (time) => {
      const elapsed = Math.min(1, (time - startTime) / duration);
      const eased = 1 - Math.pow(1 - elapsed, 3);
      setDisplayScore(start + (target - start) * eased);
      if (elapsed < 1) {
        frameId = requestAnimationFrame(animate);
      }
    };

    frameId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frameId);
  }, [safeScore]);

  let scoreClass = styles.scoreExcellent;
  if (safeScore >= 75) scoreClass = styles.scoreExcellent;
  else if (safeScore >= 50) scoreClass = styles.scoreGood;
  else if (safeScore >= 25) scoreClass = styles.scoreWarning;
  else scoreClass = styles.scoreCritical;

  const formatTime = (ts) => {
    if (!ts) return 'N/A';
    try {
      return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return 'N/A';
    }
  };

  const arrow = TREND_ARROW[trend];
  const arrowColor = TREND_COLOR[trend] || '#9aa7b2';

  return (
    <div className={styles.scoreCard}>
      <div className={styles.scoreHeader}>
        <h2 className={styles.scoreTitle}>City Health Score</h2>
        <p className={styles.scoreCity}>{city}</p>
      </div>

      <div className={`${styles.scoreCircle} ${scoreClass}`}>
        <div className={styles.scoreValue}>{displayScore.toFixed(1)}</div>
        <div className={styles.scoreMax}>/ 100</div>
      </div>

      {arrow && (
        <div className={styles.scoreTrend} style={{ color: arrowColor }}>
          {arrow} {trend}
        </div>
      )}

      <div className={styles.scoreInterpretation}>
        {safeScore >= 75 && <p>Excellent conditions</p>}
        {safeScore >= 50 && safeScore < 75 && <p>Good conditions, keep monitoring</p>}
        {safeScore >= 25 && safeScore < 50 && <p>Elevated stress, precautions advised</p>}
        {safeScore < 25 && <p>Critical conditions, avoid exposure</p>}
      </div>

      <div className={styles.scoreFooter}>
        {riskLevel && <span className={styles.scoreRisk}>{riskLevel.replace('_', ' ')}</span>}
        <small>Updated {formatTime(lastUpdated)}</small>
      </div>
    </div>
  );
};

ScoreCard.propTypes = {
  city: PropTypes.string.isRequired,
  score: PropTypes.number,
  lastUpdated: PropTypes.string,
  riskLevel: PropTypes.string,
  trend: PropTypes.string,
};

export default ScoreCard;
