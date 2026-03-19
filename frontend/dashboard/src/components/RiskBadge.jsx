import React from 'react';
import PropTypes from 'prop-types';
import styles from './Dashboard.module.css';

const RiskBadge = ({ level }) => {
  const riskClass = {
    'NORMAL': styles.riskNormal,
    'ELEVATED': styles.riskElevated,
    'HIGH_RISK': styles.riskHigh,
    'SEVERE': styles.riskSevere,
    'UNKNOWN': styles.riskUnknown
  }[level] || styles.riskUnknown;

  return (
    <span className={`${styles.badge} ${riskClass}`}>
      {level || 'UNKNOWN'}
    </span>
  );
};

RiskBadge.propTypes = {
  level: PropTypes.string
};

export default RiskBadge;
