# UrbanPulse Notification Service - PHASE 4 Complete

## What This Phase Does

The **Notification Service** is the communication hub:

1. **Real-time Data Consumption**
   - Listens to Kafka topic: `processed-city-data`
   - Receives enriched metrics from Spark Streaming job
   - Immediate processing upon arrival

2. **In-Memory Storage**
   - Stores latest metrics for each city in a ConcurrentHashMap
   - Maintains bounded alert queue (max 100 recent alerts)
   - Fast reads for REST API queries

3. **Alert Generation**
   - Creates alerts when risk level is ELEVATED or HIGH_RISK
   - Stores alert with timestamp, city, risk level, and message
   - Broadcasts to WebSocket subscribers

4. **REST API**
   - `/api/latest` - All city metrics
   - `/api/latest/{city}` - Specific city metrics
   - `/api/alerts` - Recent alerts (configurable limit)
   - `/api/alerts/{city}` - Alerts for specific city
   - `/api/cities` - List of monitored cities
   - `/api/dashboard` - Dashboard summary
   - `/api/health` - Service health

5. **WebSocket Updates**
   - Real-time push to connected clients
   - Topic: `/topic/city-updates`
   - Message types: "update", "alert", "subscription"
   - Instant delivery as data arrives

---

## How to Run PHASE 4

### Prerequisites

1. Kafka running and topics created (`raw-city-data`, `processed-city-data`)
2. Baseline Service running (PHASE 1, port 8081)
3. Ingestion Service running (PHASE 2, port 8082)
4. Spark Streaming job running (PHASE 3)

### Quick Start

#### Step 1: Build the service

```bash
cd backend/notification-service
mvn clean package -DskipTests
```

#### Step 2: Run the service

```bash
mvn spring-boot:run
```

Service starts on **port 8083**.

You should see logs like:

```
... Spring Boot Application started
... Listening to Kafka topic: processed-city-data
... WebSocket endpoint available at: /ws
... REST API available at: /api/*
```

---

## REST API Endpoints

### 1. GET /api/latest

**Get latest metrics for all cities**

```bash
curl http://localhost:8083/api/latest
```

**Response**:
```json
{
  "mumbai": {
    "city": "mumbai",
    "timestamp": "2026-03-18T22:35:00Z",
    "aqi": 180,
    "pm25": 75.5,
    "pm10": 105.2,
    "temperature": 31.4,
    "humidity": 68,
    "baselineAqi": 147.8,
    "aqiDeviationPercent": 21.7,
    "cityHealthScore": 62.5,
    "anomaly": false,
    "riskLevel": "ELEVATED",
    "alertMessage": "AQI is 21.7% above baseline"
  },
  "delhi": { ... },
  "pune": { ... }
}
```

### 2. GET /api/latest/{city}

**Get latest metrics for specific city**

```bash
curl http://localhost:8083/api/latest/mumbai
```

### 3. GET /api/alerts

**Get recent alerts (default: 20 latest)**

```bash
curl http://localhost:8083/api/alerts?limit=10
```

**Response**:
```json
{
  "count": 3,
  "alerts": [
    {
      "id": "uuid-123",
      "city": "mumbai",
      "timestamp": "2026-03-18T22:35:00Z",
      "riskLevel": "ELEVATED",
      "message": "AQI is 21.7% above baseline for Mumbai. Monitor the situation.",
      "aqi": 180,
      "cityHealthScore": 62.5
    }
  ]
}
```

### 4. GET /api/alerts/{city}

**Get all alerts for a specific city**

```bash
curl http://localhost:8083/api/alerts/mumbai
```

### 5. GET /api/cities

**Get list of all monitored cities**

```bash
curl http://localhost:8083/api/cities
```

**Response**:
```json
{
  "count": 3,
  "cities": ["mumbai", "delhi", "pune"]
}
```

### 6. GET /api/dashboard

**Get dashboard summary (used by frontend)**

```bash
curl http://localhost:8083/api/dashboard
```

**Response**:
```json
{
  "summary": {
    "citiesMonitored": 3,
    "averageHealthScore": 58.3,
    "highRiskCities": 1,
    "elevatedCities": 2
  },
  "cities": { ... },
  "recentAlerts": [ ... ]
}
```

### 7. GET /api/health

**Service health check**

```bash
curl http://localhost:8083/api/health
```

---

## WebSocket Connection

### Connecting from JavaScript

```javascript
// Connect to WebSocket
const socket = new SockJS('http://localhost:8083/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame.headers['server']);
    
    // Subscribe to city updates
    stompClient.subscribe('/topic/city-updates', function(message) {
        const update = JSON.parse(message.body);
        console.log('Update received:', update);
        // Handle the update (e.g., update UI)
    });
});
```

### Message Format

Messages sent to `/topic/city-updates`:

```json
{
  "type": "update|alert|subscription",
  "city": "mumbai",
  "data": { ... CityMetrics or Alert ... },
  "timestamp": 1710788400000
}
```

### Message Types

1. **update**: New metrics for a city
   ```json
   {"type": "update", "city": "mumbai", "data": {...CityMetrics...}}
   ```

2. **alert**: Risk level changed to ELEVATED or HIGH_RISK
   ```json
   {"type": "alert", "city": "mumbai", "data": {...Alert...}}
   ```

3. **subscription**: Client just connected
   ```json
   {"type": "subscription", "message": "Connected to city updates"}
   ```

---

## Data Flow

1. **Kafka publishes** processed-city-data
2. **Consumer service** receives message
3. **MetricsStoreService** stores latest for city
4. **AlertService** creates alert if HIGH_RISK/ELEVATED
5. **WebSocket** broadcasts to all connected clients
6. **Frontend** receives and updates UI in real-time

---

## Configuration

In `application.yml`:

```yaml
urbanpulse:
  notification:
    max-alerts: 100       # Keep last 100 alerts in memory
    max-cities: 50        # Track max 50 cities

spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service-group
```

---

## In-Memory Storage

### Latest Metrics Map
- **Key**: City name (lowercase)
- **Value**: CityMetrics object
- **Size**: Max 50 cities (configurable)
- **Access**: O(1) lookup

### Alert Queue
- **Type**: ConcurrentLinkedQueue
- **Capacity**: Max 100 alerts (configurable)
- **Order**: FIFO (oldest removed first when limit exceeded)
- **Thread-safe**: Yes, for concurrent access

---

## Expected Logs

When running:

```
... Started Spring-based Application Context
... Kafka container started
... WebSocket endpoint registered
... Successfully created Kafka partition: processed-city-data-0
... Listening to Kafka topic: processed-city-data
... Connected to Kafka broker: localhost:9092

# When data arrives:
... Received metrics for city: mumbai, AQI: 180, HealthScore: 62.5
... Stored metrics for city: mumbai
... Alert generated for mumbai: ELEVATED
... Broadcast alert to WebSocket subscribers
... Broadcast metrics update to WebSocket subscribers
```

---

## Performance Characteristics

- **REST API Response Time**: < 10ms (in-memory lookup)
- **WebSocket Message Latency**: < 50ms (from Kafka to client)
- **Memory Usage**: ~ 10KB per city metrics + alerts
- **Kafka Consumer Lag**: < 1 second (configurable)

---

## Testing Endpoints

### Test 1: Get Dashboard Data
```bash
curl http://localhost:8083/api/dashboard | jq
```

### Test 2: Get Alerts
```bash
curl http://localhost:8083/api/alerts | jq
```

### Test 3: Get Specific City
```bash
curl http://localhost:8083/api/latest/mumbai | jq
```

### Test 4: WebSocket Connection (using WebSocket client tool)
- URL: `ws://localhost:8083/ws`
- Subscribe to: `/topic/city-updates`
- Messages should arrive in real-time

---

## Troubleshooting

### Issue: "Could not connect to Kafka"
- Check Kafka is running: `docker ps`
- Verify bootstrap servers: `docker logs <kafka-container>`

### Issue: "No metrics/alerts data"
- Ensure Ingestion Service is running and publishing data
- Check: `curl http://localhost:8083/api/cities`
- Monitor Kafka topic: `kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic processed-city-data`

### Issue: "WebSocket connection refused"
- Verify service is running on 8083
- Check firewall allows 8083
- Ensure WebSocket endpoint is `/ws`

---

## Next Steps

Once PHASE 4 is running:

1. Verify REST endpoints return data
2. Test WebSocket connection
3. Move to **PHASE 5**: Build React Dashboard
4. Dashboard will consume both REST API and WebSocket for real-time updates

**PHASE 4 bridges real-time analytics with the user interface.**

---

## Running Full Pipeline

All services together:

```bash
# Terminal 1: Kafka
docker-compose -f docker/docker-compose.yml up -d

# Terminal 2: Baseline Service (PHASE 1)
cd backend/baseline-service && mvn spring-boot:run

# Terminal 3: Ingestion Service (PHASE 2)
cd backend/ingestion-service && mvn spring-boot:run

# Terminal 4: Notification Service (PHASE 4)
cd backend/notification-service && mvn spring-boot:run

# Terminal 5: Spark Streaming Job (PHASE 3)
cd analytics/spark-streaming-job && spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 spark_job.py

# Test REST API
curl http://localhost:8083/api/dashboard | jq
```

Data flows: Ingestion → Kafka → Spark → Kafka → Notification → REST + WebSocket
