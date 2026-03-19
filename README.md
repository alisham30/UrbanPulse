<div align="center">

<h1>🏙️ UrbanPulse</h1>

<p><strong>Real-Time City Intelligence & Environmental Analytics Platform</strong></p>

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)](https://react.dev/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.5-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Apache Spark](https://img.shields.io/badge/Apache%20Spark-3.5-E25A1C?style=for-the-badge&logo=apachespark&logoColor=white)](https://spark.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Python](https://img.shields.io/badge/PySpark-3.5-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://spark.apache.org/docs/latest/api/python/)
[![Leaflet](https://img.shields.io/badge/Leaflet-1.9-199900?style=for-the-badge&logo=leaflet&logoColor=white)](https://leafletjs.com/)

<br/>

<p>
A production-grade data engineering platform that monitors the environmental health of 8 major Indian cities in real time —<br/>
ingesting live air quality data, streaming it through Kafka, enriching it with Spark analytics,<br/>
computing cross-city intelligence, detecting anomalies, persisting to MySQL,<br/>
and visualizing everything on a polished interactive dashboard with maps, comparison charts, and live alerts.
</p>

</div>

---

## ✨ Highlights

- 📡 **Live ingestion** from OpenWeather + OpenAQ APIs, polling 8 Indian cities every 30 seconds
- 📜 **Historical baselines** computed from 3 years of Kaggle air quality data (2015–2020)
- ⚡ **Kafka event streaming** — fully decoupled microservices across 4 topics (`raw-city-data`, `city-intelligence-events`, `city-alert-events`, `cross-city-intelligence`)
- 🔥 **Apache Spark Structured Streaming** — enriches raw data, computes City Health Scores (0–100), and runs cross-city intelligence analytics
- 🚨 **Smart alert engine** — anomaly detection, escalation tracking, cooldowns, deduplication with fingerprinting
- 🗄️ **MySQL persistence** — hybrid in-memory + database storage with JPA/Hibernate and automated 7-day retention cleanup
- 🗺️ **Interactive Leaflet map** — live city markers color-coded by risk, AQI-sized, with detailed popups and user geolocation
- 📊 **Multi-city comparison** — side-by-side bar charts, radar charts, and detailed tables for up to 6 cities
- 🖥️ **Live React dashboard** — dark-theme UI with WebSocket (STOMP) real-time updates, trend charts, time-range filtering, and toast notifications
- 📍 **Browser geolocation** — auto-detects nearest monitored city using haversine distance
- 🐳 **Docker Compose** — MySQL, Kafka, ZooKeeper, and Kafka UI all spin up with a single command

---

## 🏗️ Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                      React Dashboard  :5173                               │
│   Tabbed Navigation: Dashboard │ Map │ Compare                            │
│   Leaflet Map · Recharts · Time-Range Filter · Toast Alerts · Geolocation│
└────────────────────────────┬──────────────────────────────────────────────┘
                             │  REST API + WebSocket (STOMP/SockJS)
                             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                   Notification Service  :8083                             │
│   Kafka Consumer · Metrics Store · Alert Engine · WebSocket Broadcast     │
│   JPA/Hibernate → MySQL · REST API (12+ endpoints) · Data Retention       │
└──────────┬──────────────────────────┬─────────────────────────────────────┘
           │                          │
   Kafka: city-intelligence-events    │  MySQL 8.0
   Kafka: city-alert-events           │  (city_metrics + alerts tables)
           │                          │
           ▼                          ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                   Apache Spark Streaming Job                              │
│   Enrich · Health Score · Anomaly Detection · Cross-City Intelligence     │
│   Rankings · Pollution Correlation · Regional Event Detection             │
└────────────────────────────┬──────────────────────────────────────────────┘
                             │  Kafka: raw-city-data
                             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    Ingestion Service  :8082                                │
│            OpenWeather API · OpenAQ API · Scheduled 30s polling            │
└────────────────────────────┬──────────────────────────────────────────────┘
                             │  REST — baseline lookup
                             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     Baseline Service  :8081                                │
│          Kaggle CSV · Historical Averages · Per-city Baselines             │
└───────────────────────────────────────────────────────────────────────────┘

         Docker Compose — MySQL · Kafka · ZooKeeper · Kafka UI
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 17, Spring Boot 3.2.2, Spring Web, Spring WebSocket |
| **Database** | MySQL 8.0, Spring Data JPA, Hibernate (auto DDL) |
| **Messaging** | Apache Kafka (Confluent 7.5.0), ZooKeeper |
| **Stream Processing** | Apache Spark 3.5.0, PySpark Structured Streaming |
| **Frontend** | React 18, Vite 5, Recharts, Leaflet, react-toastify |
| **Real-time Delivery** | Spring WebSocket, STOMP protocol, SockJS |
| **Data Sources** | OpenWeather API (live AQI + weather), OpenAQ (cross-validation), Kaggle `city_day.csv` (historical) |
| **Infrastructure** | Docker Compose (MySQL, Kafka, ZooKeeper, Kafka UI) |
| **Build** | Maven 3.8+, npm |

---

## 📁 Project Structure

```
UrbanPulse/
├── backend/
│   ├── baseline-service/              # :8081 — historical baselines from Kaggle CSV
│   ├── ingestion-service/             # :8082 — live API polling → Kafka producer
│   └── notification-service/          # :8083 — Kafka consumer, alerts, REST API, WebSocket
│       └── src/main/java/com/urbanpulse/notification/
│           ├── controller/
│           │   ├── ApiController.java         # 12+ REST endpoints
│           │   └── WebSocketController.java   # STOMP message handler
│           ├── service/
│           │   ├── KafkaConsumerService.java   # Kafka → metrics + alert pipeline
│           │   ├── MetricsStoreService.java    # Hybrid in-memory + MySQL storage
│           │   ├── AlertService.java           # Smart alert engine with cooldowns
│           │   └── DataRetentionService.java   # Scheduled 7-day cleanup
│           ├── entity/
│           │   ├── CityMetricsEntity.java      # JPA entity → city_metrics table
│           │   └── AlertEntity.java            # JPA entity → alerts table
│           ├── repository/
│           │   ├── CityMetricsRepository.java  # Time-range queries, per-city lookups
│           │   └── AlertRepository.java        # Risk-level filtering, city alerts
│           ├── model/                          # CityMetrics, Alert, TimeSeriesReading, WebSocketMessage
│           └── config/                         # KafkaConfig, WebSocketConfig
│
├── analytics/
│   └── spark-streaming-job/
│       ├── spark_job.py               # Spark Structured Streaming intelligence engine
│       ├── stream_processor.py        # Lightweight pure-Python Kafka processor (no Hadoop needed)
│       └── requirements.txt
│
├── frontend/
│   └── dashboard/                     # React 18 + Vite
│       └── src/
│           ├── App.jsx                # Main orchestrator — state, WebSocket, view routing
│           ├── components/
│           │   ├── Header.jsx         # Sticky nav tabs, city search, network stats, live indicator
│           │   ├── Dashboard.jsx      # Main dashboard — metrics cards, time-range filter, charts
│           │   ├── CityMap.jsx        # Interactive Leaflet map — risk markers, popups, geolocation
│           │   ├── CompareView.jsx    # Multi-city comparison — bar/radar charts, ranking table
│           │   ├── TrendChart.jsx     # AQI trend line chart
│           │   ├── AlertsPanel.jsx    # Recent alerts panel
│           │   ├── ScoreCard.jsx      # City health score display
│           │   ├── MetricCard.jsx     # Individual metric card
│           │   ├── RiskBadge.jsx      # Risk level badge
│           │   └── Loading.jsx        # Loading spinner
│           ├── hooks/
│           │   └── useUserLocation.js # Browser geolocation + nearest city detection
│           ├── services/
│           │   └── api.js             # REST client + WebSocket (STOMP/SockJS)
│           └── styles/
│
├── data/
│   └── city_day.csv                   # Kaggle — Air Quality Data in India (2015–2020)
│
├── docker/
│   └── docker-compose.yml             # MySQL + Kafka + ZooKeeper + Kafka UI
│
├── .env                               # API keys (not committed)
└── .gitignore
```

---

## 🌐 Data Pipeline

```
OpenWeather + OpenAQ APIs  (every 30s)
       │
       ▼
  ingestion-service ──────────►  Kafka: raw-city-data
                                          │
                                          ▼
                              Spark Structured Streaming
                              + baseline-service lookup
                                          │
                                ┌─────────┴──────────┐
                                ▼                    ▼
                         Per-city enrichment   Cross-city intelligence
                         Health score          Rankings, correlation
                         Anomaly detection     Regional event detection
                         Risk classification   Trend consensus
                                │                    │
                                ▼                    ▼
               Kafka: city-intelligence-events    Kafka: cross-city-intelligence
               Kafka: city-alert-events
                                │
                                ▼
                      notification-service
                       ├── KafkaConsumerService → deserialize CityMetrics
                       ├── MetricsStoreService → in-memory + MySQL persist
                       ├── AlertService → evaluate, deduplicate, persist
                       └── WebSocket broadcast → /topic/city-updates
                                │
                        ┌───────┴────────┐
                        ▼                ▼
                   REST API (12+)    WebSocket (STOMP)
                        │                │
                        ▼                ▼
                     React Dashboard :5173
               ┌────────┼────────┐
               ▼        ▼        ▼
           Dashboard   Map    Compare
```

### Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `raw-city-data` | ingestion-service | Spark | Raw weather + AQI payloads |
| `city-intelligence-events` | Spark | notification-service | Enriched metrics with scores |
| `city-alert-events` | Spark | notification-service | Elevated/high-risk/anomaly alerts |
| `cross-city-intelligence` | Spark | notification-service | Network-wide rankings & insights |

### Sample Enriched Payload

```json
{
  "city": "Mumbai",
  "aqi": 150,
  "pm25": 29.15,
  "temperature": 27.4,
  "humidity": 62,
  "baselineAqi": 146.8,
  "aqiDeviationPercent": 2.2,
  "rollingAqiAverage": 148.5,
  "aqiTrend": "STABLE",
  "anomaly": false,
  "cityHealthScore": 80.0,
  "riskLevel": "ELEVATED",
  "primaryDriver": "humidity",
  "alertMessage": "AQI is 2.2% above baseline. Primary driver: humidity.",
  "recommendation": "Moderately poor air quality. Outdoor exercise not recommended for sensitive groups.",
  "validationStatus": "MAJOR_DEVIATION",
  "dataConfidenceScore": 0.7
}
```

---

## 📊 City Health Score

Spark computes a **City Health Score (0–100)** every 30 seconds per city using tiered bracket penalties:

| Metric | Condition | Penalty |
|---|---|---|
| **AQI** | 0–50 → 0, 51–100 → −10, 101–150 → −20, 151–200 → −30, 201–300 → −45, 300+ → −60 | Up to −60 |
| **PM2.5** | >35 µg/m³ → −10, >60 µg/m³ → −20 | Up to −20 |
| **Temperature** | >35°C → −5 | −5 |
| **Humidity** | >80% → −5 | −5 |

### Risk Levels

| Score | Risk Level | Meaning |
|---|---|---|
| 75 – 100 | 🟢 **NORMAL** | Clean air, within baseline range |
| 50 – 74 | 🟡 **ELEVATED** | AQI 101–150, moderate deviations |
| 25 – 49 | 🟠 **HIGH_RISK** | AQI 151–200, limit outdoor exposure |
| 0 – 24 | 🔴 **SEVERE** | AQI 201+, stay indoors |

---

## 🚨 Alert Engine

The notification service runs a smart alert pipeline with:

- **Alert Decisions**: RECOVERY, ANOMALY, ESCALATION, UPDATE, or NONE
- **10-minute cooldown** between non-escalating alerts per city
- **Fingerprint deduplication** — hash of (risk level + AQI deviation + trend + anomaly)
- **WebSocket broadcast** — real-time push to all connected dashboards
- **Toast notifications** — browser toasts for HIGH_RISK and SEVERE alerts
- **MySQL persistence** — all alerts stored with full audit trail

---

## 🗺️ Interactive Map

The Leaflet-powered map view shows:

- **CircleMarkers** for each city — sized by AQI value, color-coded by risk level
- **Detailed popups** with health score, AQI, PM2.5, temperature, humidity, weather, and recommendation
- **User geolocation** — blue pulsing dot showing your position, auto-detects nearest city
- **Dark CartoDB tiles** for the dark theme aesthetic
- **AQI legend** overlay

---

## 📈 Compare View

Select 2–6 cities for side-by-side analysis:

- **Bar charts** — AQI and health score comparison (Recharts)
- **Radar chart** — multi-dimensional comparison across AQI, PM2.5, temperature, humidity, wind speed
- **Detailed table** — all metrics with best/worst highlighting
- **City rankings** — sorted by health score with risk badges

---

## 🏙️ Monitored Cities

| City | State | Lat | Lon |
|---|---|---|---|
| Mumbai | Maharashtra | 19.0760° N | 72.8777° E |
| Delhi | NCT | 28.6139° N | 77.2090° E |
| Bengaluru | Karnataka | 12.9716° N | 77.5946° E |
| Chennai | Tamil Nadu | 13.0827° N | 80.2707° E |
| Kolkata | West Bengal | 22.5726° N | 88.3639° E |
| Hyderabad | Telangana | 17.3850° N | 78.4867° E |
| Pune | Maharashtra | 18.5204° N | 73.8567° E |
| Ahmedabad | Gujarat | 23.0225° N | 72.5714° E |

---

## 🔌 REST API

All endpoints served by notification-service on port **8083**:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/dashboard` | Full dashboard summary — health scores, risk counts, city data, recent alerts |
| GET | `/api/latest` | Latest metrics for all cities |
| GET | `/api/latest/{city}` | Latest metrics for a specific city |
| GET | `/api/alerts` | Recent alerts (paginated) |
| GET | `/api/alerts/{city}` | Alerts for a specific city |
| GET | `/api/cities` | List of all monitored cities |
| GET | `/api/history` | AQI history (50 in-memory readings) |
| GET | `/api/history/{city}/range?range=1h\|6h\|24h\|7d` | Time-range filtered history from MySQL |
| GET | `/api/compare?cities=Mumbai,Delhi,Chennai` | Multi-city comparison data (2–5 cities) |
| GET | `/api/rankings` | All cities ranked by health score |
| GET | `/api/timeseries/{city}` | Full 50-reading time-series for a city |
| GET | `/api/top-risk` | City with lowest health score |
| GET | `/api/most-improved` | Recently improving city |
| GET | `/api/health` | Service health check |

---

## 🚀 Getting Started

### Prerequisites

- **Java 17** (JDK)
- **Maven 3.8+**
- **Node.js 18+** & npm
- **Python 3.10+** with PySpark 3.5.0
- **Docker** & Docker Compose
- **OpenWeather API key** (free tier works)

### 1. Clone & Configure

```bash
git clone https://github.com/your-username/UrbanPulse.git
cd UrbanPulse
```

Create a `.env` file (or copy from `.env.example`):
```env
OPENWEATHER_API_KEY=your_api_key_here
KAFKA_BROKERS=localhost:9092
```

### 2. Start Infrastructure

```bash
cd docker
docker compose up -d
```

This starts MySQL (port 3306), Kafka (port 9092), ZooKeeper (port 2181), and Kafka UI (port 8080).

### 3. Build & Start Backend Services

```bash
# Terminal 1 — Baseline Service
cd backend/baseline-service
mvn clean package -DskipTests -q
java -jar target/baseline-service-1.0.0.jar

# Terminal 2 — Ingestion Service
cd backend/ingestion-service
mvn clean package -DskipTests -q
OPENWEATHER_API_KEY=your_key java -jar target/ingestion-service-1.0.0.jar

# Terminal 3 — Notification Service
cd backend/notification-service
mvn clean package -DskipTests -q
java -jar target/notification-service-1.0.0.jar
```

### 4. Start Spark Streaming

```bash
cd analytics/spark-streaming-job
pip install -r requirements.txt
python spark_job.py
```

> **Windows users**: You need `winutils.exe` and `hadoop.dll` in `C:\hadoop\bin\` — see [Spark on Windows setup](#spark-on-windows). Alternatively, use the lightweight processor: `python stream_processor.py` (pure Python, no Hadoop needed).

### 5. Start Frontend

```bash
cd frontend/dashboard
npm install
npm run dev
```

Open **http://localhost:5173** — the dashboard will connect via WebSocket and start showing live data.

### Spark on Windows

PySpark requires Hadoop binaries on Windows:

1. Create `C:\hadoop\bin\`
2. Download `winutils.exe` and `hadoop.dll` for Hadoop 3.3.x from [cdarlint/winutils](https://github.com/cdarlint/winutils)
3. Set environment variable: `HADOOP_HOME=C:\hadoop`
4. Add `C:\hadoop\bin` to your PATH

Or skip Spark entirely and use the lightweight `stream_processor.py` which uses pure kafka-python with no Hadoop dependency.

---



<div align="center">

Built with ☕ Java · ⚡ Spark · 🗄️ MySQL · 💙 React

**UrbanPulse** — *Because cities deserve smarter eyes.*

</div>
