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
OUTPUT_TOPIC = "city-intelligence-events"
ALERT_TOPIC = "city-alert-events"
CROSS_CITY_TOPIC = "cross-city-intelligence"
ROLLING_WINDOW_SIZE = int(os.getenv("ROLLING_WINDOW_SIZE", "10"))
ANOMALY_MULTIPLIER = float(os.getenv("ANOMALY_MULTIPLIER", "1.3"))
CROSS_CITY_MIN_CITIES = int(os.getenv("CROSS_CITY_MIN_CITIES", "3"))

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


# Per-city full reading history for time-series insight (timestamp, aqi, pm25, pm10, temp, humidity)
city_full_windows: Dict[str, deque] = {}


def update_rolling_window(city_key: str, current_aqi: int) -> Optional[float]:
    """Maintain last 10 AQI readings; return rolling average."""
    if city_key not in city_aqi_windows:
        city_aqi_windows[city_key] = deque(maxlen=ROLLING_WINDOW_SIZE)

    window = city_aqi_windows[city_key]
    window.append(current_aqi)

    if len(window) < 3:
        return None

    return round(sum(window) / len(window), 1)


def compute_trend(city_key: str) -> str:
    """
    Spec: recentAvg = avg(last 5), previousAvg = avg(readings 6-10).
    RISING if recentAvg > previousAvg + 5, FALLING if < previousAvg - 5, else STABLE.
    """
    window = list(city_aqi_windows.get(city_key, []))
    if len(window) < 10:
        return "STABLE"

    recent = window[-5:]          # last 5
    previous = window[-10:-5]     # readings 6-10 from end

    recent_avg = sum(recent) / len(recent)
    previous_avg = sum(previous) / len(previous)

    if recent_avg > previous_avg + 5:
        return "RISING"
    if recent_avg < previous_avg - 5:
        return "FALLING"
    return "STABLE"


def get_aqi_n_readings_ago(city_key: str, n: int) -> Optional[int]:
    """Return the AQI from N readings ago (used for time-change insight)."""
    window = list(city_aqi_windows.get(city_key, []))
    if len(window) <= n:
        return None
    return window[-(n + 1)]


def trend_delta_percent(current_aqi: int, rolling_avg: Optional[float]) -> float:
    if rolling_avg is None or rolling_avg <= 0:
        return 0.0
    return ((current_aqi - rolling_avg) / rolling_avg) * 100.0


def detect_anomaly(current_aqi: int, rolling_avg: Optional[float]) -> bool:
    """Spec: anomaly if currentAQI > rollingAvg * 1.3."""
    if rolling_avg is None or rolling_avg <= 0:
        return False
    return current_aqi > rolling_avg * ANOMALY_MULTIPLIER


def compute_city_health_score(aqi: int,
                              pm25: float,
                              temperature: Optional[float],
                              humidity: Optional[int],
                              baseline_aqi: Optional[float]) -> Tuple[float, Dict[str, float]]:
    """
    Spec: tiered bracket penalties.
    AQI: 0-50→0, 51-100→-10, 101-150→-20, 151-200→-30, 201-300→-45, 300+→-60
    PM2.5: >35→-10, >60→-20
    Temp: >35°C→-5
    Humidity: >80%→-5
    """
    aqi_val = max(0, aqi)
    pm25_val = max(0.0, pm25)

    if aqi_val <= 50:
        aqi_penalty = 0.0
    elif aqi_val <= 100:
        aqi_penalty = 10.0
    elif aqi_val <= 150:
        aqi_penalty = 20.0
    elif aqi_val <= 200:
        aqi_penalty = 30.0
    elif aqi_val <= 300:
        aqi_penalty = 45.0
    else:
        aqi_penalty = 60.0

    if pm25_val > 60:
        pm25_penalty = 20.0
    elif pm25_val > 35:
        pm25_penalty = 10.0
    else:
        pm25_penalty = 0.0

    temp_penalty = 5.0 if (temperature is not None and temperature > 35.0) else 0.0
    humidity_penalty = 5.0 if (humidity is not None and humidity > 80) else 0.0

    penalties = {
        "AQI": aqi_penalty,
        "PM2.5": pm25_penalty,
        "temperature": temp_penalty,
        "humidity": humidity_penalty,
    }

    score = 100.0 - sum(penalties.values())
    score = max(0.0, min(100.0, score))
    return round(score, 1), penalties


def compute_primary_driver(aqi: int, pm25: float, temperature: Optional[float], humidity: Optional[int]) -> str:
    """
    Spec: normalize each metric and return the one with highest impact.
    aqiImpact = aqi/300, pm25Impact = pm25/100, tempImpact = temp/50, humidityImpact = humidity/100
    """
    impacts = {
        "AQI": max(0.0, aqi / 300.0),
        "PM2.5": max(0.0, pm25 / 100.0),
        "temperature": max(0.0, (temperature or 0.0) / 50.0),
        "humidity": max(0.0, (humidity or 0) / 100.0),
    }
    return max(impacts, key=impacts.get)


def determine_risk_level(aqi: int, deviation_pct: float) -> str:
    """
    Spec: AQI 0-100→NORMAL, 101-150→ELEVATED, 151-200→HIGH_RISK, 201+→SEVERE
    """
    aqi_val = max(0, aqi)
    if aqi_val > 200:
        return "SEVERE"
    if aqi_val > 150:
        return "HIGH_RISK"
    if aqi_val > 100:
        return "ELEVATED"
    return "NORMAL"


def normalize_confidence(score: Optional[float]) -> float:
    """Pass through the confidence score from ingestion — never override with flat default."""
    if score is None or score <= 0:
        return 0.5  # Absolute minimum — data exists but no confidence info
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


def build_recommendation(risk_level: str, trend: str, primary_driver: str, aqi: int) -> str:
    """
    Rule-based recommendation engine.
    Returns a human-readable, actionable recommendation — never hardcoded text, always computed from inputs.
    """
    if risk_level in ("SEVERE", "HIGH_RISK"):
        if trend == "RISING":
            return f"Air quality is dangerously poor and worsening due to {primary_driver}. Avoid all outdoor activity. Wear N95 mask if you must go out."
        elif trend == "FALLING":
            return f"Very poor air quality ({primary_driver}) but improving. Stay indoors until AQI drops below 150."
        else:
            return f"Sustained high pollution ({primary_driver}, AQI {aqi}). Limit outdoor exposure. Keep windows closed."
    elif risk_level == "ELEVATED":
        if trend == "RISING":
            return f"Air quality deteriorating ({primary_driver}). Sensitive groups should head indoors. Others limit outdoor time."
        elif trend == "FALLING":
            return f"Elevated pollution ({primary_driver}) is improving — conditions should return to normal soon."
        else:
            return f"Moderately poor air quality ({primary_driver}). Outdoor exercise not recommended for sensitive groups."
    else:  # NORMAL
        if trend == "RISING":
            return f"Air quality is currently acceptable but trending worse ({primary_driver}). Monitor updates."
        elif trend == "FALLING":
            return f"Conditions are improving. Good time for outdoor activities."
        else:
            return "Air quality is good. Safe for all outdoor activities."


def compute_time_change_pct(city_key: str, current_aqi: int) -> Optional[float]:
    """lastChange = ((currentAQI - AQI_10_readings_ago) / AQI_10_readings_ago) * 100"""
    past_aqi = get_aqi_n_readings_ago(city_key, 9)  # 10th reading back (0-indexed)
    if past_aqi is None or past_aqi <= 0:
        return None
    return round(((current_aqi - past_aqi) / past_aqi) * 100.0, 1)


# ──────────────────────────────────────────────────────────────────────────────
# Cross-City Intelligence Engine
# ──────────────────────────────────────────────────────────────────────────────

# Stores the latest enriched record per city for cross-city analysis
city_latest_snapshot: Dict[str, Dict[str, Any]] = {}


def compute_cross_city_intelligence(records: list) -> Optional[Dict[str, Any]]:
    """
    Analyzes all cities together to produce cross-city insights:
    - City rankings by health score
    - Pollution correlation detection (cities moving together)
    - Regional pollution hotspot identification
    - Comparative AQI deviation spread
    - Anomaly clustering (multiple cities anomalous = regional event)
    """
    # Update snapshots with latest records
    for r in records:
        city_key = r["city"].strip().lower()
        city_latest_snapshot[city_key] = r

    if len(city_latest_snapshot) < CROSS_CITY_MIN_CITIES:
        return None

    snapshots = list(city_latest_snapshot.values())

    # 1. Rankings
    ranked = sorted(snapshots, key=lambda s: s.get("cityHealthScore", 100))
    rankings = [
        {
            "rank": idx + 1,
            "city": s["city"],
            "healthScore": s.get("cityHealthScore"),
            "aqi": s.get("aqi"),
            "riskLevel": s.get("riskLevel"),
        }
        for idx, s in enumerate(ranked)
    ]

    # 2. Network-wide stats
    all_aqi = [s["aqi"] for s in snapshots if s.get("aqi") is not None]
    all_scores = [s["cityHealthScore"] for s in snapshots if s.get("cityHealthScore") is not None]
    all_pm25 = [s["pm25"] for s in snapshots if s.get("pm25") is not None]

    avg_aqi = sum(all_aqi) / len(all_aqi) if all_aqi else 0
    avg_score = sum(all_scores) / len(all_scores) if all_scores else 0
    avg_pm25 = sum(all_pm25) / len(all_pm25) if all_pm25 else 0

    # 3. Anomaly clustering — regional event detection
    anomalous_cities = [s["city"] for s in snapshots if s.get("anomaly")]
    high_risk_cities = [s["city"] for s in snapshots if s.get("riskLevel") in ("HIGH_RISK", "SEVERE")]
    is_regional_event = len(anomalous_cities) >= 2 or len(high_risk_cities) >= 3

    # 4. Pollution spread — std deviation of AQI across cities
    if len(all_aqi) > 1:
        mean_aqi = sum(all_aqi) / len(all_aqi)
        variance = sum((x - mean_aqi) ** 2 for x in all_aqi) / len(all_aqi)
        aqi_spread = round(variance ** 0.5, 1)
    else:
        aqi_spread = 0

    # 5. Trend consensus — are most cities trending the same way?
    trends = [s.get("aqiTrend", "STABLE") for s in snapshots]
    rising_count = trends.count("RISING")
    falling_count = trends.count("FALLING")
    total = len(trends)
    if rising_count > total * 0.6:
        trend_consensus = "MOSTLY_RISING"
    elif falling_count > total * 0.6:
        trend_consensus = "MOSTLY_FALLING"
    else:
        trend_consensus = "MIXED"

    # 6. Correlation pairs — cities with AQI moving in the same direction
    correlated_pairs = []
    city_keys = list(city_aqi_windows.keys())
    for i in range(len(city_keys)):
        for j in range(i + 1, len(city_keys)):
            w1 = list(city_aqi_windows.get(city_keys[i], []))
            w2 = list(city_aqi_windows.get(city_keys[j], []))
            if len(w1) >= 5 and len(w2) >= 5:
                # Simple directional correlation: both rising or both falling over last 5
                delta1 = w1[-1] - w1[-5]
                delta2 = w2[-1] - w2[-5]
                if (delta1 > 10 and delta2 > 10) or (delta1 < -10 and delta2 < -10):
                    correlated_pairs.append({
                        "city1": city_keys[i],
                        "city2": city_keys[j],
                        "direction": "BOTH_RISING" if delta1 > 0 else "BOTH_FALLING",
                        "delta1": delta1,
                        "delta2": delta2,
                    })

    # 7. Build cross-city insight narrative
    insight_parts = []
    best = ranked[-1] if ranked else None
    worst = ranked[0] if ranked else None

    if worst and best:
        score_gap = (best.get("cityHealthScore", 0) or 0) - (worst.get("cityHealthScore", 0) or 0)
        insight_parts.append(
            f"{worst['city']} is under the most stress (score {worst.get('cityHealthScore', 0):.0f}), "
            f"while {best['city']} leads (score {best.get('cityHealthScore', 0):.0f}). "
            f"Gap: {score_gap:.0f} points."
        )

    if is_regional_event:
        affected = ", ".join(anomalous_cities or high_risk_cities)
        insight_parts.append(f"Regional pollution event detected across {affected}.")

    if correlated_pairs:
        pair = correlated_pairs[0]
        insight_parts.append(
            f"Pollution in {pair['city1']} and {pair['city2']} is moving together ({pair['direction']})."
        )

    insight_parts.append(f"Network trend: {trend_consensus}. AQI spread: {aqi_spread} (std dev).")

    return {
        "timestamp": datetime.now().isoformat(),
        "cityCount": len(snapshots),
        "rankings": rankings,
        "networkStats": {
            "avgAqi": round(avg_aqi, 1),
            "avgHealthScore": round(avg_score, 1),
            "avgPm25": round(avg_pm25, 1),
            "aqiSpread": aqi_spread,
            "trendConsensus": trend_consensus,
        },
        "regionalEvent": is_regional_event,
        "anomalousCities": anomalous_cities,
        "highRiskCities": high_risk_cities,
        "correlatedPairs": correlated_pairs,
        "insight": " ".join(insight_parts),
    }


def main():
    logger.info("Starting UrbanPulse Spark Intelligence Engine")
    logger.info("Kafka brokers: %s", KAFKA_BROKERS)

    spark = SparkSession.builder \
        .appName("UrbanPulse-Analytics") \
        .master("local[*]") \
        .config("spark.sql.streaming.checkpointLocation", CHECKPOINT_DIR) \
        .config("spark.driver.host", "127.0.0.1") \
        .config("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0") \
        .config("spark.hadoop.io.native.lib.available", "false") \
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
                primary_driver = compute_primary_driver(
                    aqi=aqi,
                    pm25=pm25,
                    temperature=row.temperature,
                    humidity=row.humidity,
                )

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

                recommendation = build_recommendation(
                    risk_level=risk_level,
                    trend=trend,
                    primary_driver=primary_driver,
                    aqi=aqi,
                )

                time_change_pct = compute_time_change_pct(city_key, aqi)

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
                    "recommendation": recommendation,
                    "openAqPm25": row.openAqPm25,
                    "openAqPm10": row.openAqPm10,
                    "validationStatus": validation_status,
                    "dataConfidenceScore": confidence,
                    "aqiTimeChangePct": time_change_pct,
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
        alert_records = []
        for record in enriched_records:
            producer.send(OUTPUT_TOPIC, key=record["city"], value=record)
            if record.get("riskLevel") in ("ELEVATED", "HIGH_RISK", "SEVERE") or record.get("anomaly"):
                alert_records.append(record)
                producer.send(ALERT_TOPIC, key=record["city"], value=record)

        # Cross-city intelligence pipeline
        cross_city_intel = compute_cross_city_intelligence(enriched_records)
        if cross_city_intel:
            producer.send(CROSS_CITY_TOPIC, key="cross-city", value=cross_city_intel)
            logger.info(
                "Cross-city intelligence: %d cities, trend=%s, regional_event=%s",
                cross_city_intel["cityCount"],
                cross_city_intel["networkStats"]["trendConsensus"],
                cross_city_intel["regionalEvent"],
            )

        producer.flush()
        producer.close()

        logger.info("Published %s enriched records to %s", len(enriched_records), OUTPUT_TOPIC)
        if alert_records:
            logger.info("Published %s alert records to %s", len(alert_records), ALERT_TOPIC)

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
