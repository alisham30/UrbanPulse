import React from 'react';
import PropTypes from 'prop-types';
import styles from './Dashboard.module.css';

const Loading = ({ message = 'Loading...' }) => (
  <div className={styles.loadingContainer}>
    <div className={styles.spinner}></div>
    <p>{message}</p>
  </div>
);

Loading.propTypes = {
  message: PropTypes.string
};

export default Loading;
