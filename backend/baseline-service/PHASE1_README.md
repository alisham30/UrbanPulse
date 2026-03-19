# UrbanPulse Baseline Service - PHASE 1 Complete

## What This Phase Does

The **Baseline Service** loads historical environmental data from a Kaggle CSV file and provides:
1. **Baseline Metrics API** - Statistical summaries per city (average AQI, PM2.5, PM10, etc.)
2. **Comparison API** - Compare current metrics against baseline and get risk levels
3. **City List API** - Know which cities have baseline data available

This is the foundation that all other services depend on.

---

## How to Run PHASE 1

### Prerequisites

- **Java 17+** and **Maven** installed
- **CSV Data File**: Place your Kaggle air quality CSV at:
  ```
  data/city_day.csv
  ```

### Quick Start

#### Step 1: Build the baseline-service

```bash
cd backend/baseline-service
mvn clean package -DskipTests
```

#### Step 2: Run the service

```bash
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/baseline-service-1.0.0.jar
```

The service starts on **port 8081**.

---

## API Endpoints

### 1. GET /api/baseline

**Description**: Get all city baselines

**Response**:
```json
{
  "mumbai": {
    "city": "mumbai",
    "averageAqi": 147.8,
    "averagePm25": 62.45,
    "averagePm10": 89.51,
    "minAqi": 138,
    "maxAqi": 155,
    "temperatureAverage": 28.6,
    "humidityAverage": 65.2,
    "recordCount": 10
  },
  "delhi": {
    "city": "delhi",
    "averageAqi": 288.3,
    "averagePm25": 145.8,
    "averagePm10": 190.5,
    "minAqi": 260,
    "maxAqi": 310,
    "temperatureAverage": 12.5,
    "humidityAverage": 45.3,
    "recordCount": 10
  }
}
```

### 2. GET /api/baseline/{city}

**Description**: Get baseline for a specific city

**Example**: `GET /api/baseline/mumbai`

**Response**:
```json
{
  "city": "mumbai",
  "averageAqi": 147.8,
  "averagePm25": 62.45,
  "averagePm10": 89.51,
  "minAqi": 138,
  "maxAqi": 155,
  "recordCount": 10
}
```

### 3. POST /api/compare

**Description**: Compare current environmental data against baseline → Get risk assessment

**Request Body**:
```json
{
  "city": "mumbai",
  "currentAqi": 180,
  "currentPm25": 75.5,
  "currentPm10": 105.2
}
```

**Response**:
```json
{
  "city": "mumbai",
  "currentAqi": 180,
  "baselineAqi": 147.8,
  "aqiDeviationPercent": 21.7,
  "pm25Current": 75.5,
  "pm25Baseline": 62.45,
  "pm10Current": 105.2,
  "pm10Baseline": 89.51,
  "riskLevel": "ELEVATED",
  "alertMessage": "AQI is 21.7% above baseline for mumbai. Monitor the situation.",
  "timestamp": "2026-03-18T22:30:00Z"
}
```

### 4. GET /api/cities

**Description**: List all cities with baseline data available

**Response**:
```json
{
  "count": 3,
  "cities": ["delhi", "mumbai", "pune"]
}
```

### 5. GET /api/health

**Description**: Service health check

**Response**:
```json
{
  "status": "UP",
  "service": "baseline-service",
  "timestamp": 1710788400000
}
```

---

## Sample Workflow

### Test 1: Get All Baselines

```bash
curl http://localhost:8081/api/baseline
```

### Test 2: Get Mumbai Baseline Specifically

```bash
curl http://localhost:8081/api/baseline/mumbai
```

### Test 3: Compare Current AQI Against Baseline

```bash
curl -X POST http://localhost:8081/api/compare \
  -H "Content-Type: application/json" \
  -d '{
    "city": "mumbai",
    "currentAqi": 180,
    "currentPm25": 75.5,
    "currentPm10": 105.2
  }'
```

**Expected Output**: Risk level = ELEVATED (because 21.7% deviation is between 20% and 50%)

### Test 4: Check Available Cities

```bash
curl http://localhost:8081/api/cities
```

---

## Risk Level Logic

The comparison API uses deviation percentage to assess risk:

| Deviation | Risk Level | Meaning |
|-----------|-----------|---------|
| ≤ 20% | **NORMAL** | Conditions within acceptable range |
| 20% - 50% | **ELEVATED** | Noticeable increase; monitor situation |
| > 50% | **HIGH_RISK** | Significant pollution event; alert required |

---

## Data Format Expected (city_day.csv)

The CSV parser is flexible with column names, but expects these fields:

```
City,Date,AQI,PM2.5,PM10,Temperature,Humidity
```

- **City**: City name (gets normalized to lowercase)
- **Date**: Date format YYYY-MM-DD
- **AQI**: Air Quality Index (0-500)
- **PM2.5**: Fine particulate matter in μg/m³
- **PM10**: Coarse particulate matter in μg/m³
- **Temperature**: (Optional) in °C
- **Humidity**: (Optional) in %

---

## Logs to Expect

When the service starts, you should see:

```
... Initializing baseline service with CSV: ../../data/city_day.csv
... Loaded 30 environmental records from CSV
... Computed baselines for 3 cities
... Baseline for mumbai: AQI avg=147.8, PM2.5 avg=62.45, records=10
... Baseline for delhi: AQI avg=288.3, PM2.5 avg=145.8, records=10
... Baseline for pune: AQI avg=96.2, PM2.5 avg=40.3, records=10
```

---

## Key Design Points

1. **CSV Parsing**: Handles flexible column names, missing values, and normalization
2. **In-Memory Storage**: Baselines are loaded once at startup into a HashMap
3. **Normalization**: City names are normalized to lowercase for case-insensitive queries
4. **Validation**: Records must have all required fields (city, date, AQI, PM2.5, PM10) to be included
5. **Comparison Logic**: Uses percentage deviation to determine risk levels
6. **CORS Enabled**: Ready for frontend consumption from different origins

---

## Next Steps

Once PHASE 1 is running successfully:
1. Move to **PHASE 2**: Build the Ingestion Service to fetch real-time data from OpenWeather
2. Integrate Kafka for event streaming
3. Add the Spark streaming job for real-time analytics

**PHASE 1 provides the baseline foundation that PHASE 2 and beyond will use.**
