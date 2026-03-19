#!/usr/bin/env python3
"""
UrbanPulse Lightweight Streaming Intelligence Engine.

Replaces PySpark with a pure-Python Kafka consumer/producer loop.
Uses the same enrichment logic as spark_job.py but without Spark/Hadoop dependencies.

Pipeline:
1. Consume raw payloads from Kafka topic: raw-city-data
2. Enrich with baseline data from baseline-service
3. Compute intelligence metrics and alert context
4. Publish enriched records to Kafka topic: city-intelligence-events
"""

import json
import logging
import os
import signal
import sys
import time
from collections import deque
from datetime import datetime, timedelta
from typing import Any, Dict, Optional, Tuple

import requests
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable

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

city_aqi_windows: Dict[str, deque] = {}
city_latest_snapshot: Dict[str, Dict[str, Any]] = {}

running = True


def signal_handler(sig, frame):
    global running
    logger.info("Shutdown signal received, stopping...")
    running = False


signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)


# ──────────────────────────────────────────────────────────────────────────────
# Baseline Service Client
# ──────────────────────────────────────────────────────────────────────────────

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


# ──────────────────────────────────────────────────────────────────────────────
# Intelligence Functions (same logic as spark_job.py)
# ──────────────────────────────────────────────────────────────────────────────

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
    if len(window) < 10:
        return "STABLE"
    recent = window[-5:]
    previous = window[-10:-5]
    recent_avg = sum(recent) / len(recent)
    previous_avg = sum(previous) / len(previous)
    if recent_avg > previous_avg + 5:
        return "RISING"
    if recent_avg < previous_avg - 5:
        return "FALLING"
    return "STABLE"


def get_aqi_n_readings_ago(city_key: str, n: int) -> Optional[int]:
    window = list(city_aqi_windows.get(city_key, []))
    if len(window) <= n:
        return None
    return window[-(n + 1)]


def trend_delta_percent(current_aqi: int, rolling_avg: Optional[float]) -> float:
    if rolling_avg is None or rolling_avg <= 0:
        return 0.0
    return ((current_aqi - rolling_avg) / rolling_avg) * 100.0


def detect_anomaly(current_aqi: int, rolling_avg: Optional[float]) -> bool:
    if rolling_avg is None or rolling_avg <= 0:
        return False
    return current_aqi > rolling_avg * ANOMALY_MULTIPLIER


def compute_city_health_score(aqi: int, pm25: float,
                              temperature: Optional[float],
                              humidity: Optional[int],
                              baseline_aqi: Optional[float]) -> Tuple[float, Dict[str, float]]:
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
    penalties = {"AQI": aqi_penalty, "PM2.5": pm25_penalty, "temperature": temp_penalty, "humidity": humidity_penalty}
    score = 100.0 - sum(penalties.values())
    score = max(0.0, min(100.0, score))
    return round(score, 1), penalties


def compute_primary_driver(aqi: int, pm25: float, temperature: Optional[float], humidity: Optional[int]) -> str:
    impacts = {
        "AQI": max(0.0, aqi / 300.0),
        "PM2.5": max(0.0, pm25 / 100.0),
        "temperature": max(0.0, (temperature or 0.0) / 50.0),
        "humidity": max(0.0, (humidity or 0) / 100.0),
    }
    return max(impacts, key=impacts.get)


def determine_risk_level(aqi: int, deviation_pct: float) -> str:
    aqi_val = max(0, aqi)
    if aqi_val > 200:
        return "SEVERE"
    if aqi_val > 150:
        return "HIGH_RISK"
    if aqi_val > 100:
        return "ELEVATED"
    return "NORMAL"


def normalize_confidence(score: Optional[float]) -> float:
    if score is None or score <= 0:
        return 0.5
    return round(max(0.0, min(1.0, score)), 2)


def build_alert_message(city, risk_level, deviation_pct, rolling_delta_pct, anomaly, primary_driver, validation_status):
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


def build_recommendation(risk_level, trend, primary_driver, aqi):
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
    else:
        if trend == "RISING":
            return f"Air quality is currently acceptable but trending worse ({primary_driver}). Monitor updates."
        elif trend == "FALLING":
            return f"Conditions are improving. Good time for outdoor activities."
        else:
            return "Air quality is good. Safe for all outdoor activities."


def compute_time_change_pct(city_key: str, current_aqi: int) -> Optional[float]:
    past_aqi = get_aqi_n_readings_ago(city_key, 9)
    if past_aqi is None or past_aqi <= 0:
        return None
    return round(((current_aqi - past_aqi) / past_aqi) * 100.0, 1)


def compute_cross_city_intelligence(records: list) -> Optional[Dict[str, Any]]:
    for r in records:
        city_key = r["city"].strip().lower()
        city_latest_snapshot[city_key] = r

    if len(city_latest_snapshot) < CROSS_CITY_MIN_CITIES:
        return None

    snapshots = list(city_latest_snapshot.values())

    ranked = sorted(snapshots, key=lambda s: s.get("cityHealthScore", 100))
    rankings = [
        {"rank": idx + 1, "city": s["city"], "healthScore": s.get("cityHealthScore"),
         "aqi": s.get("aqi"), "riskLevel": s.get("riskLevel")}
        for idx, s in enumerate(ranked)
    ]

    all_aqi = [s["aqi"] for s in snapshots if s.get("aqi") is not None]
    all_scores = [s["cityHealthScore"] for s in snapshots if s.get("cityHealthScore") is not None]
    all_pm25 = [s["pm25"] for s in snapshots if s.get("pm25") is not None]

    avg_aqi = sum(all_aqi) / len(all_aqi) if all_aqi else 0
    avg_score = sum(all_scores) / len(all_scores) if all_scores else 0
    avg_pm25 = sum(all_pm25) / len(all_pm25) if all_pm25 else 0

    anomalous_cities = [s["city"] for s in snapshots if s.get("anomaly")]
    high_risk_cities = [s["city"] for s in snapshots if s.get("riskLevel") in ("HIGH_RISK", "SEVERE")]
    is_regional_event = len(anomalous_cities) >= 2 or len(high_risk_cities) >= 3

    if len(all_aqi) > 1:
        mean_aqi = sum(all_aqi) / len(all_aqi)
        variance = sum((x - mean_aqi) ** 2 for x in all_aqi) / len(all_aqi)
        aqi_spread = round(variance ** 0.5, 1)
    else:
        aqi_spread = 0

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

    correlated_pairs = []
    city_keys = list(city_aqi_windows.keys())
    for i in range(len(city_keys)):
        for j in range(i + 1, len(city_keys)):
            w1 = list(city_aqi_windows.get(city_keys[i], []))
            w2 = list(city_aqi_windows.get(city_keys[j], []))
            if len(w1) >= 5 and len(w2) >= 5:
                delta1 = w1[-1] - w1[-5]
                delta2 = w2[-1] - w2[-5]
                if (delta1 > 10 and delta2 > 10) or (delta1 < -10 and delta2 < -10):
                    correlated_pairs.append({
                        "city1": city_keys[i], "city2": city_keys[j],
                        "direction": "BOTH_RISING" if delta1 > 0 else "BOTH_FALLING",
                        "delta1": delta1, "delta2": delta2,
                    })

    insight_parts = []
    best = ranked[-1] if ranked else None
    worst = ranked[0] if ranked else None
    if worst and best:
        score_gap = (best.get("cityHealthScore", 0) or 0) - (worst.get("cityHealthScore", 0) or 0)
        insight_parts.append(
            f"{worst['city']} is under the most stress (score {worst.get('cityHealthScore', 0):.0f}), "
            f"while {best['city']} leads (score {best.get('cityHealthScore', 0):.0f}). Gap: {score_gap:.0f} points."
        )
    if is_regional_event:
        affected = ", ".join(anomalous_cities or high_risk_cities)
        insight_parts.append(f"Regional pollution event detected across {affected}.")
    if correlated_pairs:
        pair = correlated_pairs[0]
        insight_parts.append(f"Pollution in {pair['city1']} and {pair['city2']} is moving together ({pair['direction']}).")
    insight_parts.append(f"Network trend: {trend_consensus}. AQI spread: {aqi_spread} (std dev).")

    return {
        "timestamp": datetime.now().isoformat(),
        "cityCount": len(snapshots),
        "rankings": rankings,
        "networkStats": {
            "avgAqi": round(avg_aqi, 1), "avgHealthScore": round(avg_score, 1),
            "avgPm25": round(avg_pm25, 1), "aqiSpread": aqi_spread, "trendConsensus": trend_consensus,
        },
        "regionalEvent": is_regional_event,
        "anomalousCities": anomalous_cities,
        "highRiskCities": high_risk_cities,
        "correlatedPairs": correlated_pairs,
        "insight": " ".join(insight_parts),
    }


# ──────────────────────────────────────────────────────────────────────────────
# Main Processing Loop
# ──────────────────────────────────────────────────────────────────────────────

def enrich_record(raw: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Enrich a single raw city data record with intelligence metrics."""
    city = raw.get("city")
    if not city:
        return None

    city_key = city.strip().lower()
    aqi = int(raw.get("aqi", 0) or 0)
    pm25 = float(raw.get("pm25", 0.0) or 0.0)
    temperature = raw.get("temperature")
    humidity = raw.get("humidity")

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
        aqi=aqi, pm25=pm25, temperature=temperature,
        humidity=humidity, baseline_aqi=baseline_aqi,
    )
    primary_driver = compute_primary_driver(aqi=aqi, pm25=pm25, temperature=temperature, humidity=humidity)
    risk_level = determine_risk_level(aqi, deviation_pct)
    validation_status = raw.get("validationStatus") or "UNAVAILABLE"
    confidence = normalize_confidence(raw.get("dataConfidenceScore"))
    alert_message = build_alert_message(city, risk_level, deviation_pct, rolling_delta, anomaly, primary_driver, validation_status)
    recommendation = build_recommendation(risk_level, trend, primary_driver, aqi)
    time_change_pct = compute_time_change_pct(city_key, aqi)

    return {
        "city": city,
        "timestamp": raw.get("timestamp"),
        "latitude": raw.get("latitude"),
        "longitude": raw.get("longitude"),
        "aqi": aqi,
        "pm25": raw.get("pm25"),
        "pm10": raw.get("pm10"),
        "temperature": temperature,
        "humidity": humidity,
        "pressure": raw.get("pressure"),
        "windSpeed": raw.get("windSpeed"),
        "cloudPercentage": raw.get("cloudPercentage"),
        "weatherDescription": raw.get("weatherDescription"),
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
        "openAqPm25": raw.get("openAqPm25"),
        "openAqPm10": raw.get("openAqPm10"),
        "validationStatus": validation_status,
        "dataConfidenceScore": confidence,
        "aqiTimeChangePct": time_change_pct,
    }


def main():
    global running
    logger.info("Starting UrbanPulse Lightweight Streaming Intelligence Engine")
    logger.info("Kafka brokers: %s", KAFKA_BROKERS)
    logger.info("Input topic: %s → Output topic: %s", INPUT_TOPIC, OUTPUT_TOPIC)

    # Wait for Kafka to be available
    consumer = None
    for attempt in range(10):
        try:
            consumer = KafkaConsumer(
                INPUT_TOPIC,
                bootstrap_servers=KAFKA_BROKERS,
                group_id="urbanpulse-stream-processor",
                auto_offset_reset="latest",
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                consumer_timeout_ms=5000,
            )
            logger.info("Connected to Kafka consumer")
            break
        except NoBrokersAvailable:
            logger.warning("Kafka not available, retrying in 5s... (attempt %d/10)", attempt + 1)
            time.sleep(5)

    if consumer is None:
        logger.error("Failed to connect to Kafka after 10 attempts")
        sys.exit(1)

    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BROKERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k else None,
    )
    logger.info("Connected to Kafka producer")

    batch_count = 0
    total_processed = 0

    logger.info("Stream processor is running — waiting for messages...")

    while running:
        try:
            # Poll with timeout so we can check the running flag
            messages = consumer.poll(timeout_ms=2000)
            if not messages:
                continue

            enriched_records = []
            for tp, records in messages.items():
                for record in records:
                    try:
                        enriched = enrich_record(record.value)
                        if enriched:
                            enriched_records.append(enriched)
                    except Exception as exc:
                        logger.error("Error processing record: %s", exc)

            if not enriched_records:
                continue

            batch_count += 1
            for rec in enriched_records:
                producer.send(OUTPUT_TOPIC, key=rec["city"], value=rec)
                if rec.get("riskLevel") in ("ELEVATED", "HIGH_RISK", "SEVERE") or rec.get("anomaly"):
                    producer.send(ALERT_TOPIC, key=rec["city"], value=rec)

            # Cross-city intelligence
            cross_city_intel = compute_cross_city_intelligence(enriched_records)
            if cross_city_intel:
                producer.send(CROSS_CITY_TOPIC, key="cross-city", value=cross_city_intel)
                logger.info(
                    "Cross-city intel: %d cities, trend=%s, regional_event=%s",
                    cross_city_intel["cityCount"],
                    cross_city_intel["networkStats"]["trendConsensus"],
                    cross_city_intel["regionalEvent"],
                )

            producer.flush()
            total_processed += len(enriched_records)
            logger.info("Batch %d: enriched %d records (total: %d)", batch_count, len(enriched_records), total_processed)

        except Exception as exc:
            logger.error("Error in processing loop: %s", exc)
            time.sleep(1)

    logger.info("Shutting down stream processor (processed %d records total)", total_processed)
    consumer.close()
    producer.close()


if __name__ == "__main__":
    main()
