#!/usr/bin/env python3
"""
UrbanPulse Spark Streaming Intelligence Engine.

Pipeline:
1. Consume raw payloads from Kafka topic: raw-city-data
2. Enrich with baseline data from baseline-service
3. Compute intelligence metrics and alert context
4. Publish enriched records to Kafka topic: processed-city-data
"""

import json
import logging
import os
from collections import deque
from datetime import datetime, timedelta
from typing import Any, Dict, Optional, Tuple

import requests
from kafka import KafkaProducer
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, from_json
from pyspark.sql.types import DoubleType, IntegerType, StringType, StructField, StructType

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092")
BASELINE_SERVICE_URL = os.getenv("BASELINE_SERVICE_URL", "http://localhost:8081/api")
INPUT_TOPIC = "raw-city-data"
OUTPUT_TOPIC = "processed-city-data"
ROLLING_WINDOW_SIZE = int(os.getenv("ROLLING_WINDOW_SIZE", "12"))
ANOMALY_SPIKE_THRESHOLD_PCT = float(os.getenv("ANOMALY_SPIKE_THRESHOLD_PCT", "22"))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CHECKPOINT_DIR = os.path.join(SCRIPT_DIR, "checkpoints")
KAFKA_CHECKPOINT_DIR = os.path.join(SCRIPT_DIR, "kafka-checkpoints")

city_aqi_windows: Dict[str, deque] = {}


def get_input_schema() -> StructType:
    return StructType([
        StructField("city", StringType(), True),
        StructField("timestamp", StringType(), True),
        StructField("latitude", DoubleType(), True),
        StructField("longitude", DoubleType(), True),
        StructField("temperature", DoubleType(), True),
        StructField("humidity", IntegerType(), True),
        StructField("pressure", IntegerType(), True),
        StructField("windSpeed", DoubleType(), True),
        StructField("cloudPercentage", IntegerType(), True),
        StructField("weatherDescription", StringType(), True),
        StructField("aqi", IntegerType(), True),
        StructField("pm25", DoubleType(), True),
        StructField("pm10", DoubleType(), True),
        StructField("no2", DoubleType(), True),
        StructField("o3", DoubleType(), True),
        StructField("so2", DoubleType(), True),
        StructField("co", DoubleType(), True),
        StructField("source", StringType(), True),
        StructField("apiVersion", StringType(), True),
        StructField("openAqPm25", DoubleType(), True),
        StructField("openAqPm10", DoubleType(), True),
        StructField("validationStatus", StringType(), True),
        StructField("dataConfidenceScore", DoubleType(), True),
    ])


class BaselineServiceClient:
    def __init__(self, base_url: str = BASELINE_SERVICE_URL):
        self.base_url = base_url
        self.cache: Dict[str, Dict[str, Any]] = {}
        self.cache_ttl = timedelta(hours=1)
        self.cache_time: Dict[str, datetime] = {}

    def get_baseline(self, city: str) -> Optional[Dict[str, Any]]:
        city_key = city.strip().lower()

        if city_key in self.cache:
            created_at = self.cache_time.get(city_key)
            if created_at and datetime.now() - created_at < self.cache_ttl:
                return self.cache[city_key]

        try:
            url = f"{self.base_url}/baseline/{city_key}"
            response = requests.get(url, timeout=5)
            if response.status_code != 200:
                logger.warning("Baseline not found for %s (status=%s)", city, response.status_code)
                return None

            payload = response.json()
            self.cache[city_key] = payload
            self.cache_time[city_key] = datetime.now()
            return payload
        except Exception as exc:
            logger.error("Failed to fetch baseline for %s: %s", city, exc)
            return None


baseline_client = BaselineServiceClient()


def update_rolling_window(city_key: str, current_aqi: int) -> Optional[float]:
    if city_key not in city_aqi_windows:
        city_aqi_windows[city_key] = deque(maxlen=ROLLING_WINDOW_SIZE)

    window = city_aqi_windows[city_key]
    window.append(current_aqi)

    if len(window) < 3:
        return None

    return round(sum(window) / len(window), 1)


def compute_trend(city_key: str) -> str:
    window = list(city_aqi_windows.get(city_key, []))
    if len(window) < 5:
        return "STABLE"

    midpoint = len(window) // 2
    first = window[:midpoint]
    second = window[midpoint:]
    if not first or not second:
        return "STABLE"

    first_avg = sum(first) / len(first)
    second_avg = sum(second) / len(second)
    if first_avg <= 0:
        return "STABLE"

    change_pct = ((second_avg - first_avg) / first_avg) * 100.0
    if change_pct >= 8:
        return "RISING"
    if change_pct <= -8:
        return "FALLING"
    return "STABLE"


def trend_delta_percent(current_aqi: int, rolling_avg: Optional[float]) -> float:
    if rolling_avg is None or rolling_avg <= 0:
        return 0.0
    return ((current_aqi - rolling_avg) / rolling_avg) * 100.0


def detect_anomaly(current_aqi: int, rolling_avg: Optional[float]) -> bool:
    if rolling_avg is None or rolling_avg <= 0:
        return False
    delta = trend_delta_percent(current_aqi, rolling_avg)
    return delta >= ANOMALY_SPIKE_THRESHOLD_PCT


def compute_city_health_score(aqi: int,
                              pm25: float,
                              temperature: Optional[float],
                              humidity: Optional[int],
                              baseline_aqi: Optional[float]) -> Tuple[float, Dict[str, float]]:
    aqi_val = max(0, aqi)
    pm25_val = max(0.0, pm25)

    aqi_penalty = min(45.0, (aqi_val / 300.0) * 45.0)
    if baseline_aqi and baseline_aqi > 0 and aqi_val > baseline_aqi:
        extra = min(12.0, ((aqi_val - baseline_aqi) / baseline_aqi) * 20.0)
        aqi_penalty += max(0.0, extra)

    pm25_penalty = min(30.0, (pm25_val / 90.0) * 30.0)

    temp_penalty = 0.0
    if temperature is not None:
        temp_penalty = min(15.0, abs(temperature - 24.0) * 1.2)

    humidity_penalty = 0.0
    if humidity is not None:
        if humidity < 35:
            humidity_penalty = min(10.0, (35 - humidity) * 0.6)
        elif humidity > 60:
            humidity_penalty = min(10.0, (humidity - 60) * 0.4)

    penalties = {
        "AQI": round(aqi_penalty, 2),
        "PM2.5": round(pm25_penalty, 2),
        "temperature": round(temp_penalty, 2),
        "humidity": round(humidity_penalty, 2),
    }

    score = 100.0 - sum(penalties.values())
    score = max(0.0, min(100.0, score))
    return round(score, 1), penalties


def compute_primary_driver(penalties: Dict[str, float]) -> str:
    if not penalties:
        return "AQI"
    return max(penalties, key=penalties.get)


def determine_risk_level(aqi: int, deviation_pct: float) -> str:
    aqi_val = max(0, aqi)
    abs_dev = abs(deviation_pct)

    if aqi_val >= 300 or abs_dev >= 100:
        return "SEVERE"
    if aqi_val >= 200 or abs_dev >= 60:
        return "HIGH_RISK"
    if aqi_val >= 120 or abs_dev >= 25:
        return "ELEVATED"
    return "NORMAL"


def normalize_confidence(score: Optional[float]) -> float:
    if score is None:
        return 0.6
    return round(max(0.0, min(1.0, score)), 2)


def build_alert_message(city: str,
                        risk_level: str,
                        deviation_pct: float,
                        rolling_delta_pct: float,
                        anomaly: bool,
                        primary_driver: str,
                        validation_status: str) -> str:
    baseline_direction = "above" if deviation_pct >= 0 else "below"
    trend_direction = "above" if rolling_delta_pct >= 0 else "below"

    if risk_level == "NORMAL":
        message = f"Conditions normal for {city}. AQI is {abs(deviation_pct):.1f}% {baseline_direction} baseline."
    else:
        message = f"AQI is {abs(deviation_pct):.1f}% {baseline_direction} baseline"
        if abs(rolling_delta_pct) > 0.1:
            message += f" and {abs(rolling_delta_pct):.1f}% {trend_direction} recent trend"
        if anomaly:
            message += "; anomaly detected"
        message += "."

    if primary_driver != "AQI":
        message += f" Primary driver: {primary_driver}."

    if validation_status == "MATCH":
        message += " OpenAQ confirms strong agreement."
    elif validation_status == "MINOR_DEVIATION":
        message += " OpenAQ shows minor deviation from live readings."
    elif validation_status == "MAJOR_DEVIATION":
        message += " OpenAQ shows major deviation; confidence reduced."

    return message


def main() -> None:
    logger.info("Starting UrbanPulse Spark Intelligence Engine")
    logger.info("Kafka brokers: %s", KAFKA_BROKERS)

    spark = SparkSession.builder \
        .appName("UrbanPulse-Analytics") \
        .master("local[*]") \
        .config("spark.sql.streaming.checkpointLocation", CHECKPOINT_DIR) \
        .config("spark.driver.host", "127.0.0.1") \
        .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    df_raw = spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", KAFKA_BROKERS) \
        .option("subscribe", INPUT_TOPIC) \
        .option("startingOffsets", "latest") \
        .load()

    schema = get_input_schema()
    df_parsed = df_raw \
        .select(from_json(col("value").cast(StringType()), schema).alias("data")) \
        .select("data.*")

    def process_batch(batch_df, batch_id: int) -> None:
        if batch_df.isEmpty():
            return

        rows = batch_df.collect()
        logger.info("Processing batch %s with %s rows", batch_id, len(rows))

        enriched_records = []
        for row in rows:
            try:
                city = row.city
                if not city:
                    continue

                city_key = city.strip().lower()
                aqi = int(row.aqi) if row.aqi is not None else 0
                pm25 = float(row.pm25) if row.pm25 is not None else 0.0

                baseline = baseline_client.get_baseline(city)
                baseline_aqi = baseline.get("averageAqi") if baseline else None

                deviation_pct = 0.0
                if baseline_aqi and baseline_aqi > 0:
                    deviation_pct = ((aqi - baseline_aqi) / baseline_aqi) * 100.0

                rolling_avg = update_rolling_window(city_key, aqi)
                trend = compute_trend(city_key)
                rolling_delta = trend_delta_percent(aqi, rolling_avg)
                anomaly = detect_anomaly(aqi, rolling_avg)

                score, penalties = compute_city_health_score(
                    aqi=aqi,
                    pm25=pm25,
                    temperature=row.temperature,
                    humidity=row.humidity,
                    baseline_aqi=baseline_aqi,
                )
                primary_driver = compute_primary_driver(penalties)

                risk_level = determine_risk_level(aqi, deviation_pct)

                validation_status = row.validationStatus if row.validationStatus else "UNAVAILABLE"
                confidence = normalize_confidence(row.dataConfidenceScore)

                alert_message = build_alert_message(
                    city=city,
                    risk_level=risk_level,
                    deviation_pct=deviation_pct,
                    rolling_delta_pct=rolling_delta,
                    anomaly=anomaly,
                    primary_driver=primary_driver,
                    validation_status=validation_status,
                )

                enriched_records.append({
                    "city": city,
                    "timestamp": row.timestamp,
                    "latitude": row.latitude,
                    "longitude": row.longitude,
                    "aqi": aqi,
                    "pm25": row.pm25,
                    "pm10": row.pm10,
                    "temperature": row.temperature,
                    "humidity": row.humidity,
                    "pressure": row.pressure,
                    "windSpeed": row.windSpeed,
                    "cloudPercentage": row.cloudPercentage,
                    "weatherDescription": row.weatherDescription,
                    "baselineAqi": baseline_aqi,
                    "aqiDeviationPercent": round(deviation_pct, 1),
                    "rollingAqiAverage": rolling_avg,
                    "aqiTrend": trend,
                    "anomaly": anomaly,
                    "cityHealthScore": score,
                    "riskLevel": risk_level,
                    "primaryDriver": primary_driver,
                    "alertMessage": alert_message,
                    "openAqPm25": row.openAqPm25,
                    "openAqPm10": row.openAqPm10,
                    "validationStatus": validation_status,
                    "dataConfidenceScore": confidence,
                })
            except Exception as exc:
                logger.error("Error processing row: %s", exc)

        if not enriched_records:
            return

        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BROKERS,
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
            key_serializer=lambda key: key.encode("utf-8") if key else None,
        )
        for record in enriched_records:
            producer.send(OUTPUT_TOPIC, key=record["city"], value=record)
        producer.flush()
        producer.close()

        logger.info("Published %s enriched records to %s", len(enriched_records), OUTPUT_TOPIC)

    query = df_parsed.writeStream \
        .foreachBatch(process_batch) \
        .option("checkpointLocation", KAFKA_CHECKPOINT_DIR) \
        .start()

    logger.info("Spark intelligence engine is running")
    try:
        query.awaitTermination()
    except KeyboardInterrupt:
        logger.info("Stopping Spark intelligence engine")
        query.stop()


if __name__ == "__main__":
    main()
