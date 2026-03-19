import React from 'react';
import PropTypes from 'prop-types';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ReferenceArea,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import styles from './Dashboard.module.css';

const TREND_STROKE = { RISING: '#ff5c7a', FALLING: '#3ddc97', STABLE: '#30c4ff' };

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  return (
    <div className={styles.chartTooltip}>
      <p className={styles.chartTooltipLabel}>{label}</p>
      {payload.map((point, idx) => (
        <p key={idx} style={{ color: point.color }}>
          {point.name}: {typeof point.value === 'number' ? point.value.toFixed(1) : point.value}
        </p>
      ))}
    </div>
  );
};

const TrendChart = ({ data, title, baselineAqi, rollingAvg, trend }) => {
  const strokeColor = TREND_STROKE[trend] || '#30c4ff';
  const gradientId = `aqi-gradient-${trend || 'default'}`;
  const latest = data?.length ? data[data.length - 1] : null;

  if (!data || data.length === 0) {
    return (
      <div className={styles.chartBox}>
        <h3>{title}</h3>
        <p className={styles.noData}>Collecting trend points from live stream...</p>
      </div>
    );
  }

  return (
    <div className={styles.chartBox}>
      <h3>{title}</h3>
      <ResponsiveContainer width="100%" height={280}>
        <AreaChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={strokeColor} stopOpacity={0.35} />
              <stop offset="95%" stopColor={strokeColor} stopOpacity={0.04} />
            </linearGradient>
          </defs>

          <ReferenceArea y1={0} y2={100} fill="rgba(61,220,151,0.06)" />
          <ReferenceArea y1={100} y2={200} fill="rgba(245,189,63,0.06)" />
          <ReferenceArea y1={200} y2={500} fill="rgba(255,92,122,0.06)" />

          <CartesianGrid strokeDasharray="3 3" stroke="rgba(55,66,88,0.65)" vertical={false} />
          <XAxis
            dataKey="time"
            stroke="#90a0b3"
            tick={{ fill: '#90a0b3', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            stroke="#90a0b3"
            tick={{ fill: '#90a0b3', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            width={36}
          />

          <Tooltip content={<CustomTooltip />} />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: '0.78rem', color: '#90a0b3' }} />

          {baselineAqi != null && (
            <ReferenceLine
              y={baselineAqi}
              stroke="rgba(245,189,63,0.8)"
              strokeDasharray="5 4"
              label={{
                value: `Baseline ${baselineAqi.toFixed(0)}`,
                position: 'insideTopRight',
                fill: '#f5bd3f',
                fontSize: 10,
              }}
            />
          )}

          {rollingAvg != null && (
            <ReferenceLine
              y={rollingAvg}
              stroke="rgba(48,196,255,0.75)"
              strokeDasharray="3 4"
              label={{
                value: `Rolling ${rollingAvg.toFixed(0)}`,
                position: 'insideTopLeft',
                fill: '#30c4ff',
                fontSize: 10,
              }}
            />
          )}

          <Area
            type="monotone"
            dataKey="value"
            name="AQI"
            stroke={strokeColor}
            strokeWidth={2.7}
            fill={`url(#${gradientId})`}
            dot={false}
            activeDot={{ r: 4, fill: strokeColor, stroke: '#0a111f', strokeWidth: 2 }}
            isAnimationActive
            animationDuration={380}
          />

          {latest && (
            <ReferenceDot
              x={latest.time}
              y={latest.value}
              r={5}
              fill={strokeColor}
              stroke="#0a111f"
              strokeWidth={2}
            />
          )}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};

TrendChart.propTypes = {
  data: PropTypes.arrayOf(PropTypes.shape({
    time: PropTypes.string,
    value: PropTypes.number,
  })),
  title: PropTypes.string.isRequired,
  baselineAqi: PropTypes.number,
  rollingAvg: PropTypes.number,
  trend: PropTypes.string,
};

export default TrendChart;
