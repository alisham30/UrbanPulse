# UrbanPulse Setup Guide

Complete step-by-step instructions for running the entire UrbanPulse real-time intelligence platform locally.

---

## 📋 Prerequisites Checklist

Before starting, ensure you have:

- [ ] **Java 17 or higher** installed
  ```bash
  java -version
  # Output should show 17.x or higher
  ```

- [ ] **Maven 3.8+** installed
  ```bash
  mvn -version
  # Output should show 3.8 or higher
  ```

- [ ] **Docker & Docker Compose** installed
  ```bash
  docker --version
  docker-compose --version
  ```

- [ ] **Node.js 16+** installed
  ```bash
  node --version
  npm --version
  ```

- [ ] **Python 3.8+** installed
  ```bash
  python --version
  ```

- [ ] **Apache Spark 3.5+** installed
  ```bash
  spark-submit --version
  ```

- [ ] **OpenWeather API Key** (free tier: https://openweathermap.org/api)

---

## 🏗️ Complete Setup Process

### Step 1: Project Structure Verification

Navigate to your UrbanPulse directory:

```bash
cd ~/UrbanPulse
```

Verify the complete project structure exists:

```
urbanpulse/
├── backend/
│   ├── baseline-service/
│   ├── ingestion-service/
│   └── notification-service/
├── analytics/
│   └── spark-streaming-job/
├── frontend/
│   └── dashboard/
├── data/
│   └── city_day.csv
├── docker/
│   └── docker-compose.yml
├── .env.example
└── README.md
```

### Step 2: Environment Configuration

**2a. Copy env file**

```bash
cp .env.example .env
```

**2b. Edit `.env` and add your API key**

```bash
# MacOS/Linux
nano .env

# Windows
notepad .env
```

Add your OpenWeather API key:

```
OPENWEATHER_API_KEY=sk_test_XXXXXXXXXXXXXXXX
KAFKA_BROKERS=localhost:9092
BASELINE_SERVICE_URL=http://localhost:8081/api
REACT_APP_API_URL=http://localhost:8083/api
REACT_APP_WS_URL=ws://localhost:8083/ws
```

**2c. Verify Kaggle CSV Data**

The file should be at: `data/city_day.csv`

Expected format:
```
City,Date,AQI,PM2.5,PM10,Temperature,Humidity
Mumbai,2024-01-15,180,75.5,105.2,31.4,68
Delhi,2024-01-15,210,89.3,138.7,15.2,42
Pune,2024-01-15,145,62.1,88.9,28.5,55
```

---

### Step 3: Start Kafka Infrastructure

Before starting any services, Kafka and ZooKeeper must be running.

**3a. Navigate to docker directory**

```bash
cd docker
```

**3b. Start Kafka and ZooKeeper**

```bash
docker-compose up -d
```

**3c. Verify services are running**

```bash
# Check if containers are running
docker-compose ps

# Expected output:
# NAME                   STATUS
# docker_kafka_1         Up 2 seconds
# docker_zookeeper_1     Up 3 seconds
# docker_kafka-ui_1      Up 1 second

# Check Kafka logs
docker-compose logs kafka | tail -20
```

**3d. Access Kafka UI (optional but recommended)**

Open browser and go to: `http://localhost:8080`

You should see:
- Kafka cluster status
- Topics list (empty initially)
- Consumer groups
- Brokers information

---

### Step 4: Start Backend Services

Open 3 new terminal windows and navigate to project root. **Do NOT close them during execution.**

#### Terminal 1: Baseline Service (Port 8081)

```bash
cd backend/baseline-service

# Clean and build
mvn clean package -DskipTests

# Run service
mvn spring-boot:run
```

**Expected output (wait for this message):**
```
Started BaselineServiceApplication in XX.XXX seconds
Loaded X baseline records from CSV
```

**Verify in another terminal:**
```bash
curl http://localhost:8081/api/cities
# Returns: ["Mumbai", "Delhi", "Pune"]
```

---

#### Terminal 2: Ingestion Service (Port 8082)

```bash
cd backend/ingestion-service

# Clean and build
mvn clean package -DskipTests

# Run service (with API key)
export OPENWEATHER_API_KEY=sk_test_XXXXXXXXXXXXXXXX
mvn spring-boot:run
```

**Expected output (wait for this message):**
```
Started IngestionServiceApplication in XX.XXX seconds
Scheduled ingestion task initialized for cities: Mumbai, Delhi, Pune
Fetching data from OpenWeather APIs...
Published UnifiedPayload to Kafka topic: raw-city-data
```

The service will automatically:
- Fetch data every 30 seconds
- Publish to Kafka `raw-city-data` topic
- Print success/error logs

---

#### Terminal 3: Notification Service (Port 8083)

```bash
cd backend/notification-service

# Clean and build
mvn clean package -DskipTests

# Run service
mvn spring-boot:run
```

**Expected output (wait for this message):**
```
Started NotificationServiceApplication in XX.XXX seconds
WebSocket endpoint ready at: ws://localhost:8083/ws
Listening on Kafka topic: processed-city-data
```

---

### Step 5: Start Spark Streaming Job

Open a new terminal:

```bash
cd analytics/spark-streaming-job

# Install Python dependencies
pip install -r requirements.txt

# Run the Spark job
spark-submit \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 \
  --master local[*] \
  spark_job.py
```

**Expected output (wait for these messages):**
```
24/01/15 10:45:30 INFO SparkSession: SparkSession started
Spark Streaming job initialized
Baseline cache initialized
Processing batches from Kafka...
Batch 1: Processed 3 records
Published to topic: processed-city-data
```

The job will:
- Connect to Kafka
- Process microbatches (1-2 second intervals)
- Enrich with baseline data
- Compute health scores
- Publish to Kafka

---

### Step 6: Start React Dashboard

Open a new terminal:

```bash
cd frontend/dashboard

# Install npm dependencies (first time only)
npm install

# Start development server
npm run dev
```

**Expected output:**
```
  VITE v4.3.0  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  press h + enter to show help
```

---

### Step 7: Accessing the Dashboard

**Open your browser and navigate to:**

```
http://localhost:5173
```

You should see:
1. ✅ **Loading spinner** (first 3-5 seconds)
2. ✅ **City selector dropdown** (Mumbai, Delhi, Pune)
3. ✅ **Health Score Card** (0-100 with color gradient)
4. ✅ **Metric Cards** (AQI, PM2.5, PM10, Temperature, Humidity)
5. ✅ **Risk Badge** (NORMAL/ELEVATED/HIGH_RISK with color coding)
6. ✅ **Trend Chart** (last 15 data points)
7. ✅ **Alerts Panel** (real-time alert messages)

---

## ✅ Verification Checklist

### 1. All Services Running

```bash
# Terminal command (different terminal):
curl http://localhost:8081/api/health  # Baseline
curl http://localhost:8082/api/health  # Ingestion
curl http://localhost:8083/api/health  # Notification
```

All should return `{"status":"UP"}`

### 2. Kafka Topics Created

```bash
# List Kafka topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Expected output:
# raw-city-data
# processed-city-data
```

### 3. Data Flowing Through Pipeline

```bash
# Monitor raw messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic raw-city-data --from-beginning --max-messages 3

# Monitor processed messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic processed-city-data --from-beginning --max-messages 3
```

### 4. Dashboard Receiving Real-Time Updates

- Open browser DevTools (F12)
- Go to Console tab
- You should see WebSocket messages:
  ```javascript
  {type: "CITY_UPDATE", city: "Mumbai", data: {...}, timestamp: ...}
  ```

### 5. API Endpoints Responding

```bash
# Get latest metrics for a city
curl http://localhost:8083/api/latest/Mumbai | jq

# Get all recent alerts
curl http://localhost:8083/api/alerts | jq

# Get dashboard summary
curl http://localhost:8083/api/dashboard | jq

# Get list of monitored cities
curl http://localhost:8083/api/cities | jq
```

---

## 🎯 End-to-End Data Flow Walkthrough

### What Happens in 30 Seconds

```
T = 0 seconds
→ Ingestion Service fetches OpenWeather APIs
  - Calls: /data/2.5/weather for current weather
  - Calls: /data/2.5/air_pollution for AQI data
  - Merges payloads → UnifiedPayload
  - Publishes to: Kafka topic "raw-city-data"

T = 1 second
→ Spark Streaming receives batch
  - Consumes 1-N messages from "raw-city-data"
  - Calls REST API: baseline-service for historical context
  - Computes health score (0-100 weighted formula)
  - Determines risk level (NORMAL/ELEVATED/HIGH_RISK)
  - Publishes enriched payload to: "processed-city-data"

T = 2 seconds
→ Notification Service receives batch
  - Consumes message from "processed-city-data"
  - Updates in-memory store (metrics per city)
  - Checks if risk level changed → Creates alert if true
  - Broadcasts via WebSocket to all connected clients
  - Stores alert in alert queue

T = 2.5 seconds
→ React Dashboard receives WebSocket message
  - Updates city metrics display
  - Shows new health score and badges
  - Adds alert to alerts panel (newest first)
  - Updates trend chart with new data point
  - Animates changes

T = 3-30 seconds
→ Cycle repeats every 30 seconds
```

### Example: Watch Data Flow in Logs

**Terminal A: Ingestion logs**
```
[time] INFO: Fetching OpenWeather for Mumbai...
[time] INFO: Publishing UnifiedPayload to raw-city-data
```

**Terminal B: Spark logs**
```
[time] INFO: Batch 1: Processing 3 records
[time] INFO: Health score for Mumbai: 62.5
[time] INFO: Publishing to processed-city-data
```

**Terminal C: Notification logs**
```
[time] INFO: Received CityMetrics for Mumbai
[time] INFO: Risk changed from NORMAL to ELEVATED → Alert created
[time] INFO: Broadcasting to 5 connected clients
```

**Browser Console:**
```javascript
Received: {type: "CITY_UPDATE", city: "Mumbai", healthScore: 62.5, ...}
UI Updated: Score 62.5, Risk ELEVATED 🟡
```

---

## 🔧 Common Configuration Changes

### Add a New City to Monitor

Edit: `backend/ingestion-service/src/main/resources/application.yml`

```yaml
urbanpulse:
  ingestion:
    cities:
      - name: NewCity
        latitude: XX.XXXX
        longitude: XX.XXXX
```

Restart Ingestion Service. New city will appear in dropdown automatically.

### Change Update Frequency

Edit: `backend/ingestion-service/src/main/resources/application.yml`

```yaml
urbanpulse:
  ingestion:
    schedule:
      interval-ms: 15000  # Change from 30000 to 15000 (fetch every 15 seconds)
```

### Adjust Health Score Formula

Edit: `analytics/spark-streaming-job/spark_job.py`

```python
HEALTH_SCORE_CONFIG = {
    "starting_score": 100,
    "aqi_penalty": 35,        # Max penalty for AQI
    "pm25_penalty": 30,       # Max penalty for PM2.5
    "temp_penalty": 20,       # Max penalty for temperature
    "humidity_penalty": 15    # Max penalty for humidity
}
```

Change penalties and restart Spark job.

### Modify Risk Level Thresholds

Edit: `backend/notification-service/src/main/java/.../AlertService.java`

```java
// Change thresholds (currently: NORMAL≤20%, ELEVATED 20-50%, HIGH>50%)
private static final double ELEVATED_THRESHOLD = 0.30;  // 30% instead of 20%
private static final double HIGH_RISK_THRESHOLD = 0.60; // 60% instead of 50%
```

---

## 🚨 Troubleshooting

### Issue: "connection refused" on port 8081/8082/8083

**Cause**: Service didn't start or crashed

**Solution**:
```bash
# Check if process is running
lsof -i :8081

# Kill and restart service
pkill -f BaselineServiceApplication
mvn spring-boot:run
```

### Issue: Kafka won't start

**Cause**: Ports 2181 (ZooKeeper) or 9092 (Kafka) already in use

**Solution**:
```bash
# Kill existing containers
docker-compose down
docker-compose up -d

# Or free specific ports
lsof -i :2181
kill -9 <PID>
```

### Issue: No data appearing in dashboard

**Cause**: 
1. Ingestion Service crashed
2. Kafka topics not created
3. Spark job not running

**Solution**:
```bash
# Check Ingestion logs
# Check Spark logs
# Verify Kafka topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Manually trigger ingestion
curl -X POST http://localhost:8082/api/ingest \
  -H "Content-Type: application/json" \
  -d '{"city": "Mumbai", "latitude": 19.0760, "longitude": 72.8777}'

# Check Kafka messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic raw-city-data --from-beginning --max-messages 1
```

### Issue: WebSocket connection failing

**Cause**:
1. Notification Service not running
2. CORS not configured
3. Wrong URL in frontend config

**Solution**:
```bash
# Verify Notification Service is running
curl http://localhost:8083/api/health

# Check WebSocket endpoint
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  http://localhost:8083/ws

# Check browser console for actual error
# Firefox: F12 → Console → Check WebSocket errors
```

### Issue: Spark job crashes with "ClassNotFoundException"

**Cause**: Missing Kafka packages

**Solution**:
```bash
# Ensure using correct submit command
spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 \
  --master local[*] spark_job.py
```

### Issue: Out of memory error

**Cause**: Spark job running out of heap

**Solution**:
```bash
# Allocate more memory
spark-submit --driver-memory 4g --executor-memory 4g \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 \
  spark_job.py
```

---

## 📊 Monitoring the Pipeline

### View Real-time Kafka Messages

**Terminal**:
```bash
# Watch raw data from Ingestion Service
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic raw-city-data --from-beginning

# Watch enriched data from Spark job
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic processed-city-data --from-beginning
```

### Check Service Health

```bash
# All services provide health endpoints
curl http://localhost:8081/api/health
curl http://localhost:8082/api/health
curl http://localhost:8083/api/health

# Response format:
# {"status":"UP"}
```

### Monitor Kafka UI

Visit: `http://localhost:8080`

See in real-time:
- Message count per topic
- Consumer group lag
- Broker status
- Topic configurations

---

## 🛑 Stopping Everything

### Stop in Order (Reverse Startup)

```bash
# Terminal 6 (React): Ctrl + C
# Terminal 5 (Spark): Ctrl + C
# Terminal 4 (Notification): Ctrl + C
# Terminal 3 (Ingestion): Ctrl + C
# Terminal 2 (Baseline): Ctrl + C

# Stop Kafka/ZooKeeper (from docker directory):
cd docker
docker-compose down
```

### Full Cleanup

```bash
# Stop containers
docker-compose down

# Remove volumes (if needed)
docker-compose down -v

# Kill any lingering processes
pkill -f java
pkill -f spark-submit
pkill -f node
```

---

## 🔄 Quick Restart

Once everything is configured, use this script for rapid restart:

```bash
#!/bin/bash

# restart_urbanpulse.sh

echo "Starting Kafka..."
cd docker
docker-compose up -d
sleep 3

cd ../..

echo "Starting Baseline Service..."
cd backend/baseline-service
nohup mvn spring-boot:run > baseline.log 2>&1 &
cd ../..

echo "Starting Ingestion Service..."
cd backend/ingestion-service
export OPENWEATHER_API_KEY=sk_test_XXXXXXXXXXXXXXXX
nohup mvn spring-boot:run > ingestion.log 2>&1 &
cd ../..

echo "Starting Notification Service..."
cd backend/notification-service
nohup mvn spring-boot:run > notification.log 2>&1 &
cd ../..

echo "Starting Spark Job..."
cd analytics/spark-streaming-job
nohup spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 spark_job.py > spark.log 2>&1 &
cd ../..

echo "Starting Dashboard..."
cd frontend/dashboard
nohup npm run dev > dashboard.log 2>&1 &

echo "All services started! Dashboard: http://localhost:5173"
```

---

## 📞 Support

If you encounter issues:

1. **Check specific phase README**: Each service has detailed PHASE#_README.md
2. **Check logs**: Each service outputs detailed logs
3. **Verify prerequisites**: Run all prerequisite checks again
4. **Test individually**: Start services one at a time and verify each
5. **Check Kafka**: Use Kafka UI to verify data is flowing

---

## 🎯 Success Indicators

You'll know everything is working when:

✅ Dashboard loads at `http://localhost:5173`  
✅ Health Score displays for selected city  
✅ Metrics update every 30 seconds  
✅ WebSocket shows real-time updates (Console tab)  
✅ Alerts appear when risk levels change  
✅ Trend chart shows 15 data points  
✅ Kafka UI shows both topics with messages  
✅ All services show "UP" health status  

---

**Congratulations! You now have a complete real-time smart city intelligence platform running locally!**

Next steps: Deploy to production, add more cities, integrate additional data sources, or explore the code further.

For more details, see individual PHASE#_README.md files for each component.
