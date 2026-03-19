# UrbanPulse React Dashboard - PHASE 5 Complete

## What This Phase Does

The **React Dashboard** is the polished user interface:

1. **Real-time Display**
   - Shows City Health Score prominently (0-100 scale, color-coded)
   - Displays current metrics: AQI, PM2.5, PM10, Temperature, Humidity
   - Shows deviation from baseline and risk level
   - Live alerts panel with recent events

2. **Multi-City Support**
   - City selector dropdown
   - Easy switching between monitored cities
   - Separate metrics view per city

3. **Live Updates**
   - REST API for initial data load
   - WebSocket for real-time updates
   - Automatic UI refresh as data arrives
   - Connection status indicator ("Live" / "Offline")

4. **Visualization**
   - AQI trend chart (last 15 readings)
   - Color-coded risk indicators (green/yellow/red)
   - Intuitive cards and layout
   - Professional dark theme with accent colors

5. **Responsive Design**
   - Desktop optimized
   - Tablet support
   - Mobile friendly
   - Adapts to all screen sizes

---

## How to Run PHASE 5

### Prerequisites

1. Node.js 16+ and npm installed
2. All backend services running:
   - Baseline Service (8081)
   - Ingestion Service (8082)
   - Notification Service (8083)
   - Spark job processing data

### Quick Start

#### Step 1: Install dependencies

```bash
cd frontend/dashboard
npm install
```

#### Step 2: Start development server

```bash
npm run dev
```

Dashboard will be available at `http://localhost:5173` (or shown in terminal)

#### Step 3: Build for production

```bash
npm run build
```

Output goes to `dist/` folder.

---

## Architecture

### Components

- **Dashboard.jsx** - Main container component
  - State management for metrics, alerts, cities
  - WebSocket connection lifecycle
  - Real-time update handling

- **ScoreCard.jsx** - City Health Score display
  - Color-coded score (0-100)
  - Visual indicators
  - Health interpretation

- **MetricCard.jsx** - Individual metric display
  - Reusable metric display
  - Trend indicators
  - Icon support

- **RiskBadge.jsx** - Risk level badge
  - Color-coded risk levels
  - Supports NORMAL, ELEVATED, HIGH_RISK

- **TrendChart.jsx** - AQI trend visualization
  - Recharts line chart
  - Last 15 data points
  - Responsive sizing

- **AlertsPanel.jsx** - Recent alerts list
  - Sorted by time (newest first)
  - Max 10 visible with scroll
  - Risk level indicators

- **Loading.jsx** - Loading state
  - Spinner animation
  - Custom messages

### Services

- **api.js** - API communication
  - REST client for initial data
  - WebSocket client for real-time updates
  - Automatic reconnection on disconnect

---

## Features

### Real-time Updates

1. **REST API (Initial Load)**
   ```javascript
   GET /api/dashboard    // Full dashboard data
   GET /api/latest       // All city metrics
   GET /api/latest/{city}// Specific city
   GET /api/alerts       // Recent alerts
   ```

2. **WebSocket (Live Updates)**
   ```
   Connect: ws://localhost:8083/ws
   Subscribe: /topic/city-updates
   Messages: { type, city, data, timestamp }
   ```

### Data Display

- **Health Score**: 0-100 scale with color progression
  - 75+: Excellent (green)
  - 50-75: Good (blue)
  - 25-50: Warning (yellow)
  -  <25: Critical (red)

- **Metrics**: 
  - AQI (0-500)
  - PM2.5 (μg/m³)
  - PM10 (μg/m³)
  - Temperature (°C)
  - Humidity (%)
  - Wind Speed (m/s)

- **Alerts**: 
  - Type: NORMAL, ELEVATED, HIGH_RISK
  - Recent alerts sorted by time
  - Max 100 alerts stored

---

## Environment Variables

Create `.env.local` file:

```
REACT_APP_API_URL=http://localhost:8083/api
REACT_APP_WS_URL=ws://localhost:8083/ws
```

Default values work if all services are on localhost.

---

## UI Color Scheme

```css
--bg-primary: #0a0e27       (dark navy)
--bg-secondary: #131929     (darker navy)
--bg-tertiary: #1a1f3a      (deep blue)
--text-primary: #e0e6ed     (light gray)
--text-secondary: #a0a9b8   (medium gray)
--accent-green: #00ff88     (mint green - good)
--accent-blue: #00d4ff      (cyan - info)
--accent-yellow: #ffc107    (amber - warn)
--accent-red: #ff3860       (red - danger)
```

---

## Testing Endpoints

### Test REST API

```bash
# Dashboard summary
curl http://localhost:8083/api/dashboard | jq

# All cities
curl http://localhost:8083/api/latest | jq

# Specific city
curl http://localhost:8083/api/latest/mumbai | jq

# Recent alerts
curl http://localhost:8083/api/alerts | jq

# Available cities
curl http://localhost:8083/api/cities | jq
```

### Test WebSocket

Use WebSocket client (e.g., Chrome DevTools, Postman, or wscat):

```
Connect to: ws://localhost:8083/ws
Subscribe to: /topic/city-updates
```

You should receive messages like:
```json
{
  "type": "update",
  "city": "mumbai",
  "data": { ... CityMetrics ... },
  "timestamp": 1710788400000
}
```

---

## Building for Production

### Build to static files

```bash
npm run build
```

### Deploy to Nginx

```bash
# build/
server {
    listen 80;
    server_name yourdomain.com;
    
    root /var/www/urbanpulse/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://notification-service:8083;
    }
    
    location /ws {
        proxy_pass http://notification-service:8083;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### Docker deployment

```dockerfile
FROM node:18 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:latest
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## Performance Optimizations

1. **Lazy Loading**: Components load on demand
2. **Memoization**: Prevents unnecessary re-renders
3. **Chart Optimization**: Limited to 15 data points
4. **WebSocket**: Real-time without polling
5. **CSS Modules**: Scoped styling, no conflicts

---

## Responsive Breakpoints

- **Desktop**: > 1024px (full 3-column layout)
- **Tablet**: 768px - 1024px (2-column grid)
- **Mobile**: < 768px (1-column stacked)
- **Small Mobile**: < 480px (minimal layout)

---

## Browser Support

- Chrome / Edge: Latest 2 versions
- Firefox: Latest 2 versions
- Safari: Latest 2 versions
- iOS Safari: Latest 2 versions

---

## Troubleshooting

### Issue: "Cannot connect to backend"
- Check Notification Service running on 8083
- Verify CORS enabled in backend

### Issue: "WebSocket connection failed"
- Ensure WebSocket endpoint `/ws` available
- Check firewall allows WebSocket traffic
- Verify URL: `ws://localhost:8083/ws`

### Issue: "No metrics displaying"
- Check REST API: `curl http://localhost:8083/api/dashboard`
- Verify Ingestion Service publishing data
- Check Kafka topic has data

### Issue: "Alerts not updating"
- Verify alert threshold configured in Spark job
- Check Kafka topic: `processed-city-data`
- Monitor browser console for errors

---

## Development Notes

### Add a new metric card

```javascript
<MetricCard 
  label="New Metric"
  value={metrics.newValue}
  unit="unit"
  icon="🔧"
/>
```

### Add a new chart

```javascript
import TrendChart from './TrendChart';

// In component:
<TrendChart 
  data={chartData}
  title="New Chart Title"
/>
```

### Customize colors

Edit `src/styles/common.css`:
```css
:root {
  --accent-green: #00ff88;  /* Change here */
}
```

---

## Next Steps

1. Run full system locally
2. Deploy to production servers
3. Set up monitoring/alerting
4. Customize for specific cities
5. Add historical data export
6. Implement user preferences

**PHASE 5 completes the user-facing side of UrbanPulse.**

---

## Full System Architecture in Action

```
┌─────────────────────────────────────────────────────────┐
│          React Dashboard (PHASE 5)                       │
│  - City Health Score                                     │
│  - Real-time metrics                                     │
│  - Live alerts                                           │
│  - Trend charts                                          │
└────────────────┬──────────────────────────────────────────┘
                 │
        REST API │ WebSocket
                 ↓
┌─────────────────────────────────────────────────────────┐
│    Notification Service (PHASE 4)                        │
│  - Latest metrics store                                  │
│  - WebSocket push                                        │
│  - REST endpoints                                        │
└────────────────┬──────────────────────────────────────────┘
                 │
              Kafka Consumer
                 │
         ┌───────┴───────┐
         ↓               ↓
    Topic:          Topic:
    processed-      processed-
    city-data       city-data
         ↑
   Kafka Topic
         ↑
┌────────┴───────────────────────────────────────────────┐
│   Spark Streaming Job (PHASE 3)                        │
│  - Health scoring                                       │
│  - Anomaly detection                                    │
│  - Risk assessment                                      │
└────────────────┬──────────────────────────────────────────┘
                 │
              Kafka Consumer
                 │
         Topic: raw-city-data
                 ↑
    ┌────────────┴────────────┬────────────┐
    ↓                         ↓            ↓
Ingestion      OpenWeather     Baseline
Service        APIs            Service
(PHASE 2)      (live)          (PHASE 1)
    │                                  ↑
    └──────────────Kafka──────────────┘
              raw-city-data
```

This architecture demonstrates a complete real-time data engineering pipeline!
