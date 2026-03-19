# UrbanPulse Spark Streaming Job - PHASE 3 Complete

## What This Phase Does

The **Spark Streaming Job** is the heart of the analytics pipeline:

1. **Real-time Processing**
   - Consumes from Kafka topic: `raw-city-data`
   - Parses JSON unified payloads from Ingestion Service
   - Processes continuously with micro-batch architecture

2. **Data Enrichment**
   - Calls Baseline Service REST API to fetch historical metrics
   - Caches baseline data for efficiency (1-hour TTL)
   - Joins current data with baseline context

3. **Analytics Computation**
   - Calculates AQI deviation percentage from baseline
   - Computes City Health Score (0-100 scale)
   - Detects anomalies and pollution spikes
   - Determines risk level (NORMAL/ELEVATED/HIGH_RISK)

4. **City Health Score Formula**
   ```
   Score starts at 100
   Minus AQI penalty (max 35 points)
   Minus PM2.5 penalty (max 30 points)
   Minus Temperature stress (max 20 points)
   Minus Humidity stress (max 15 points)
   Result clamped to 0-100
   ```

5. **Risk Assessment**
   - Deviation ≤ 20%: NORMAL
   - Deviation 20-50%: ELEVATED
   - Deviation > 50%: HIGH_RISK

6. **Output**
   - Publishes enriched records to Kafka topic: `processed-city-data`
   - Format: JSON with all computed fields

---

## How to Run PHASE 3

### Prerequisites

1. **Python 3.8+** installed
2. **Apache Spark 3.5+** installed and in PATH
3. **Kafka** running (from docker-compose)
4. **Baseline Service** running (PHASE 1)
5. **Ingestion Service** running and publishing data (PHASE 2)

### Quick Start

#### Step 1: Install Python dependencies

```bash
cd analytics/spark-streaming-job
pip install -r requirements.txt
```

#### Step 2: Set environment variables (optional)

```bash
# Default values work if services are on localhost
export KAFKA_BROKERS="localhost:9092"
export BASELINE_SERVICE_URL="http://localhost:8081/api"
```

#### Step 3: Run Spark job

```bash
spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 spark_job.py
```

**Alternative** (if Spark not in PATH):

```bash
python spark_job.py
```

---

## What Happens

### On Startup

```
===============================================================
UrbanPulse Spark Streaming Job Starting
===============================================================
Kafka Brokers: localhost:9092
Baseline Service: http://localhost:8081/api
Input Topic: raw-city-data
Output Topic: processed-city-data
Spark Session initialized
Reading from Kafka topic: raw-city-data
JSON schema applied to payload
Streaming job running. Press Ctrl+C to stop.
```

### During Execution

For each message from Kafka:

1. **Parse** JSON payload
2. **Fetch baseline** for city (with caching)
3. **Compute** deviation percentage
4. **Calculate** health score
5. **Determine** risk level
6. **Generate** alert message
7. **Publish** processed record to output topic

### Sample Processing Flow

```
Input (from raw-city-data topic):
{
  "city": "Mumbai",
  "timestamp": "2026-03-18T22:35:00Z",
  "aqi": 180,
  "pm25": 75.5,
  "pm10": 105.2,
  "temperature": 31.4,
  "humidity": 68
}

↓ [Fetch baseline: Mumbai baseline AQI = 147.8]

↓ [Compute metrics]:
  - AQI Deviation: 21.7%
  - PM2.5 Deviation: 20.9%
  - Health Score: 62.5
  - Risk Level: ELEVATED

↓ [Generate alert]:
  "AQI is 21.7% above baseline for Mumbai. Monitor the situation."

Output (to processed-city-data topic):
{
  "city": "Mumbai",
  "timestamp": "2026-03-18T22:35:00Z",
  "aqi": 180,
  "pm25": 75.5,
  "baselineAqi": 147.8,
  "aqiDeviationPercent": 21.7,
  "cityHealthScore": 62.5,
  "anomaly": false,
  "riskLevel": "ELEVATED",
  "alertMessage": "AQI is 21.7% above baseline for Mumbai. Monitor the situation."
}
```

---

## Configuration

In `spark_job.py`, adjust these constants:

```python
KAFKA_BROKERS = "localhost:9092"  # Kafka broker address
BASELINE_SERVICE_URL = "http://localhost:8081/api"  # Baseline service
INPUT_TOPIC = "raw-city-data"  # Kafka input topic
OUTPUT_TOPIC = "processed-city-data"  # Kafka output topic

# Adjust health score weights
HEALTH_SCORE_CONFIG = {
    "start_score": 100.0,
    "aqi_weight": 0.40,
    "pm25_weight": 0.30,
    "temp_weight": 0.15,
    "humidity_weight": 0.15
}

# Anomaly detection
ANOMALY_CONFIG = {
    "deviation_threshold": 50.0,  # % deviation to flag as anomaly
    "spike_multiplier": 1.5,      # 1.5x rolling avg = spike
}
```

---

## Health Score Calculation

### Rationale

- **Start at 100** (perfect conditions)
- **Deduct penalties** based on pollution and climate stress
- **Cap each penalty** to keep scores meaningful

### Score Breakdown

1. **AQI Penalty** (max 35 points)
   - Compares current AQI vs baseline
   - Or: current AQI / 500 (worst case)
   - Higher AQI → larger penalty

2. **PM2.5 Penalty** (max 30 points)
   - WHO guideline: PM2.5 < 5 μg/m³ is good
   - > 35 μg/m³ is unhealthy
   - Linear scaling

3. **Temperature Penalty** (max 20 points)
   - Optimal: 20-25°C (0 penalty)
   - Uncomfortable: <10°C or >30°C (5 points)
   - Extreme: <0°C or >40°C (20 points)

4. **Humidity Penalty** (max 15 points)
   - Optimal: 30-60% (0 penalty)
   - Uncomfortable: 20-30% or 60-80% (5 points)
   - Extreme: <20% or >80% (15 points)

### Example Score Calculation

```
Input:
- Current AQI: 180
- Baseline AQI: 147.8
- PM2.5: 75.5
- Temperature: 31.4°C
- Humidity: 68%

Computation:
- Start: 100
- AQI Deviation: 21.7% → penalty = 7.6 points
- PM2.5: 75.5 < 100 → penalty = 22 points
- Temperature: 31.4°C (> 30°C) → penalty = 15 points
- Humidity: 68% (60-80% range) → penalty = 5 points

Final Score: 100 - 7.6 - 22 - 15 - 5 = 50.4 → 50
```

---

## Kafka Topics

### Input: `raw-city-data`

Messages from Ingestion Service

```json
{
  "city": "Mumbai",
  "timestamp": "2026-03-18T22:35:00Z",
  "aqi": 180,
  "pm25": 75.5,
  "pm10": 105.2,
  "temperature": 31.4,
  "humidity": 68,
  "pressure": 1013,
  "windSpeed": 4.5,
  "cloudPercentage": 45,
  "weatherDescription": "partly cloudy",
  "no2": 45.3,
  "o3": 65.2,
  "so2": 12.1,
  "co": 0.85
}
```

### Output: `processed-city-data`

Processed records with analytics

```json
{
  "city": "Mumbai",
  "timestamp": "2026-03-18T22:35:00Z",
  "aqi": 180,
  "pm25": 75.5,
  "baselineAqi": 147.8,
  "aqiDeviationPercent": 21.7,
  "cityHealthScore": 62.5,
  "anomaly": false,
  "riskLevel": "ELEVATED",
  "alertMessage": "AQI is 21.7% above baseline for Mumbai. Monitor the situation."
}
```

---

## Monitoring & Debugging

### View Kafka messages

```bash
# Consume from input topic
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic raw-city-data --from-beginning

# Consume from output topic
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic processed-city-data --from-beginning
```

### Check Spark UI

Spark provides a web UI (usually at `http://localhost:4040`)

### Expected Logs

```
... Spark Session initialized
... Reading from Kafka topic: raw-city-data
... Cached baseline for Mumbai: AQI=147.8
... Processing batch of 3 messages
... Successfully published 3 records to processed-city-data
... [repeats every micro-batch]
```

### Troubleshooting

**Issue:** "Cannot connect to Kafka"
- Check Kafka is running: `docker ps`
- Verify broker address in spark_job.py

**Issue:** "Baseline Service unreachable"
- Ensure Baseline Service (PHASE 1) is running on 8081
- Check URL: `curl http://localhost:8081/api/cities`

**Issue:** "No messages in output topic"
- Verify Ingestion Service (PHASE 2) is publishing
- Check Kafka topic: `kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic raw-city-data`

---

## Performance Notes

- **Micro-batch processing**: Default 500ms batches
- **Baseline caching**: 1-hour TTL reduces API calls
- **Partitioning**: Messages keyed by city for parallelism
- **Scalability**: PySpark scales horizontally (cluster mode)

---

## Next Steps

Once PHASE 3 is running successfully:

1. Verify output in `processed-city-data` topic
2. Move to **PHASE 4**: Build Notification Service to consume this output
3. The Notification Service will expose REST API + WebSocket for the dashboard

**PHASE 3 is the data engine that powers real-time insights.**

---

## Running End-to-End

1. Start Kafka: `docker-compose -f docker/docker-compose.yml up -d`
2. Run Baseline Service: `cd backend/baseline-service && mvn spring-boot:run`
3. Run Ingestion Service: `cd backend/ingestion-service && mvn spring-boot:run`
4. Run Spark Job: `cd analytics/spark-streaming-job && spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 spark_job.py`
5. Watch Kafka output: `kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic processed-city-data --from-beginning`

All three services feed data through the pipeline in real-time!
