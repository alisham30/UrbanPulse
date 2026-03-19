# UrbanPulse Ingestion Service - PHASE 2 Complete

## What This Phase Does

The **Ingestion Service** continuously fetches real-time environmental data from OpenWeather APIs and publishes it to Kafka:

1. **OpenWeather Integration**
   - Current Weather API: Gets temperature, humidity, pressure, wind, clouds
   - Air Pollution API: Gets AQI level, PM2.5, PM10, and trace gases (NO2, O3, SO2, CO)

2. **Data Normalization**
   - Merges weather + pollution data
   - Converts OpenWeather AQI scale (1-5) to 0-500 scale
   - Converts temperature from Kelvin to Celsius
   - Creates unified payload for consistency

3. **Kafka Publishing**
   - Publishes to topic: `raw-city-data`
   - Message key: city name (for partitioning)
   - Format: JSON serialized UnifiedPayload

4. **Scheduled Operations**
   - Fetches data every 30 seconds (configurable)
   - Supports manual trigger via REST API

---

## How to Run PHASE 2

### Prerequisites

1. **Kafka running locally**
   ```bash
   # Start Kafka broker (assuming you have Kafka installed)
   # Using docker-compose (easiest):
   docker-compose -f docker/docker-compose.yml up -d
   ```

2. **OpenWeather API Key**
   - Get a free API key from: https://openweathermap.org/api
   - Free tier includes current weather and air pollution APIs
   - Set via environment variable: `OPENWEATHER_API_KEY=your-key-here`

### Quick Start

#### Step 1: Set Environment Variables

```bash
# On Windows (PowerShell)
$env:OPENWEATHER_API_KEY = "your-openweather-api-key"

# On Linux/Mac
export OPENWEATHER_API_KEY="your-openweather-api-key"
```

#### Step 2: Build the service

```bash
cd backend/ingestion-service
mvn clean package -DskipTests
```

#### Step 3: Run the service

```bash
mvn spring-boot:run
```

The service starts on **port 8082**.

---

## API Endpoints

### 1. POST /api/ingest

**Manual trigger to fetch and publish data for a city**

**Request**:
```bash
curl -X POST http://localhost:8082/api/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "city": "Mumbai",
    "latitude": 19.0760,
    "longitude": 72.8777
  }'
```

**Response**:
```json
{
  "status": "success",
  "message": "Data ingested and published for Mumbai",
  "data": {
    "city": "Mumbai",
    "timestamp": "2026-03-18T22:30:00Z",
    "latitude": 19.0760,
    "longitude": 72.8777,
    "temperature": 31.4,
    "humidity": 68,
    "pressure": 1013,
    "windSpeed": 4.5,
    "cloudPercentage": 45,
    "weatherDescription": "partly cloudy",
    "aqi": 150,
    "pm25": 61.2,
    "pm10": 89.5,
    "no2": 45.3,
    "o3": 65.2,
    "so2": 12.1,
    "co": 0.85,
    "source": "openweather",
    "apiVersion": "2.5"
  }
}
```

### 2. GET /api/health

**Service health check**

```bash
curl http://localhost:8082/api/health
```

---

## Configuration

In `application.yml`, customize:

```yaml
urbanpulse:
  ingestion:
    openweather:
      api-key: ${OPENWEATHER_API_KEY}  # Use environment variable

    cities:  # Cities to automatically fetch
      - name: Mumbai
        latitude: 19.0760
        longitude: 72.8777
      - name: Delhi
        latitude: 28.6139
        longitude: 77.2090
      - name: Pune
        latitude: 18.5204
        longitude: 73.8567

    schedule:
      interval-ms: 30000  # Fetch every 30 seconds
```

---

## Sample Execution Flow

1. **Service starts**
   - Loads city configuration from application.yml
   - Initializes Kafka producer
   - Schedules ingestion job to run every 30 seconds

2. **Every 30 seconds**
   - For each configured city:
     - Fetch current weather from OpenWeather API
     - Fetch air pollution from OpenWeather API
     - Merge into UnifiedPayload
     - Publish to Kafka topic: `raw-city-data`
   - Log success/failure for each city

3. **Data flows to Kafka**
   - Topic: `raw-city-data`
   - Partition key: city name
   - Format: JSON UnifiedPayload

---

## Sample Kafka Message

Message published to topic `raw-city-data`:

```json
{
  "city": "Mumbai",
  "timestamp": "2026-03-18T22:30:00Z",
  "latitude": 19.0760,
  "longitude": 72.8777,
  "temperature": 31.4,
  "humidity": 68,
  "pressure": 1013,
  "windSpeed": 4.5,
  "cloudPercentage": 45,
  "weatherDescription": "partly cloudy",
  "aqi": 150,
  "pm25": 61.2,
  "pm10": 89.5,
  "no2": 45.3,
  "o3": 65.2,
  "so2": 12.1,
  "co": 0.85,
  "source": "openweather",
  "apiVersion": "2.5"
}
```

---

## Expected Logs

```
... Initializing Spring-based services
... Scheduled ingestion job started, runs every 30000ms
... Starting scheduled ingestion job
... Ingesting data for Mumbai (lat=19.0760, lon=72.8777)
... Fetching current weather for lat=19.0760, lon=72.8777
... Fetching air pollution data for lat=19.0760, lon=72.8777
... Successfully ingested data for Mumbai: AQI=150, PM2.5=61.2, Temp=31.4
... Published data for Mumbai to Kafka. Topic: raw-city-data, Partition: 0, Offset: 1
... [repeated for Delhi and Pune]
```

---

## Stopping the Service

To stop the scheduled ingestion:
```bash
# Press Ctrl+C in terminal running the service
```

Kafka message publishing will stop immediately.

---

## Troubleshooting

### Issue: "Cannot connect to Kafka broker"
**Solution**: Ensure Kafka is running on localhost:9092
```bash
docker-compose -f docker/docker-compose.yml up -d
```

### Issue: "API key invalid" or "401 Unauthorized"
**Solution**: Verify your OpenWeather API key:
```bash
# Test the API manually
curl "https://api.openweathermap.org/data/2.5/weather?lat=19.0760&lon=72.8777&appid=YOUR_KEY&units=metric"
```

### Issue: "No cities configured"
**Solution**: Check application.yml has cities array under urbanpulse.ingestion.cities

### Issue: "Data is null" for a city
**Solution**: OpenWeather API key might have air pollution API disabled. Upgrade API key tier (pro version needed sometimes).

---

## Next Steps

Once PHASE 2 is running:
1. Verify Kafka is receiving messages: `kafka-console-consumer` 
2. Move to **PHASE 3**: Spark streaming job to process data in real-time
3. The Spark job will consume from `raw-city-data` and enrich with baseline data

**PHASE 2 provides the real-time data feed that powers the entire system.**
