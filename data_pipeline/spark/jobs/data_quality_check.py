"""
📊 Simple Data Quality Check – BRONZE ONLY
Checks:
- Completeness
- Freshness
- Accuracy
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, when, current_timestamp, unix_timestamp, avg, max
from datetime import datetime
import sys

# ---------------- CONFIG ----------------

BRONZE_PATH = "s3a://bronze/emails/"
QUALITY_THRESHOLD = 95.0
FRESHNESS_MAX_HOURS = 48

# ---------------- SPARK ----------------

def create_spark_session():
    return SparkSession.builder \
        .appName("SimpleDataQualityBronze") \
        .getOrCreate()

# ---------------- LOAD DATA ----------------

def load_bronze_data(spark, user_id=None):
    df = spark.read.parquet(BRONZE_PATH)

    if user_id and "user_id" in df.columns:
        df = df.filter(col("user_id") == user_id)

    return df

# ---------------- COMPLETENESS ----------------

def check_completeness(df):
    required_fields = ["message_id", "sender", "subject", "timestamp"]
    fields = [f for f in required_fields if f in df.columns]

    total_records = df.count()
    if total_records == 0:
        return 0.0

    nulls = 0
    for f in fields:
        nulls += df.filter(col(f).isNull() | (col(f) == "")).count()

    total_checks = total_records * len(fields)
    score = ((total_checks - nulls) / total_checks) * 100

    return round(score, 2)

# ---------------- FRESHNESS ----------------

def check_freshness(df):
    if "timestamp" not in df.columns:
        return 0.0

    df_age = df.withColumn(
        "age_hours",
        (unix_timestamp(current_timestamp()) - unix_timestamp(col("timestamp"))) / 3600
    )

    max_age = df_age.agg(max("age_hours")).collect()[0][0]

    if max_age <= FRESHNESS_MAX_HOURS:
        return 100.0
    else:
        return max(0, 100 - (max_age - FRESHNESS_MAX_HOURS) * 2)

# ---------------- ACCURACY ----------------

def check_accuracy(df):
    if "sender" not in df.columns:
        return 100.0

    total = df.count()
    if total == 0:
        return 0.0

    valid = df.filter(
        col("sender").rlike(r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
    ).count()

    return round((valid / total) * 100, 2)

# ---------------- MAIN ----------------

def main():
    user_id = sys.argv[1] if len(sys.argv) > 1 else None

    spark = create_spark_session()
    df = load_bronze_data(spark, user_id)

    print("\n📊 DATA QUALITY CHECK – BRONZE\n")

    completeness = check_completeness(df)
    freshness = check_freshness(df)
    accuracy = check_accuracy(df)

    print(f"✅ Completeness : {completeness}%")
    print(f"⏱️ Freshness    : {freshness}%")
    print(f"🎯 Accuracy     : {accuracy}%")

    overall = round((completeness + freshness + accuracy) / 3, 2)

    print(f"\n📈 OVERALL SCORE : {overall}%")

    if overall >= QUALITY_THRESHOLD:
        print("✅ STATUS : PASS")
    else:
        print("⚠️ STATUS : WARNING (pipeline continues)")

    spark.stop()
    sys.exit(0)

if __name__ == "__main__":
    main()
