#!/usr/bin/env python3


from datetime import datetime, timedelta
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, when, isnan, isnull, datediff, current_timestamp
import sys
import json

# Configuration
MINIO_ENDPOINT = "http://minio:9000"
MINIO_ACCESS_KEY = "minioadmin"
MINIO_SECRET_KEY = "minioadmin123"
BRONZE_PATH = "s3a://datalake/bronze/emails/"
QUALITY_THRESHOLD = 95  # %

# Required fields for completeness check
REQUIRED_FIELDS = [
    "id",
    "from", 
    "to",
    "subject",
    "body",
    "date",
    "sender_domain"
]

# Quality report
quality_report = {
    "timestamp": None,
    "total_records": 0,
    "completeness": {
        "valid": 0,
        "invalid": 0,
        "score": 0
    },
    "freshness": {
        "valid": 0,
        "invalid": 0,
        "score": 0
    },
    "accuracy": {
        "valid": 0,
        "invalid": 0,
        "score": 0
    },
    "overall_score": 0,
    "status": "UNKNOWN"
}


def create_spark_session():
    """Créer une session Spark avec MinIO"""
    return SparkSession.builder \
        .appName("DataQualityCheck") \
        .config("spark.hadoop.fs.s3a.endpoint", MINIO_ENDPOINT) \
        .config("spark.hadoop.fs.s3a.access.key", MINIO_ACCESS_KEY) \
        .config("spark.hadoop.fs.s3a.secret.key", MINIO_SECRET_KEY) \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider") \
        .getOrCreate()


def read_bronze(spark):
    """Lire tous les emails de Bronze"""
    print(f"📂 Reading from: {BRONZE_PATH}")
    try:
        df = spark.read.parquet(BRONZE_PATH)
        return df
    except Exception as e:
        print(f"❌ Error reading Bronze: {e}")
        return None


def check_completeness(df):
    """
    Check 1: COMPLETENESS
    Verify all required fields are non-NULL
    """
    print("\n🔍 CHECK 1: COMPLETENESS")
    print(f"  Required fields: {', '.join(REQUIRED_FIELDS)}")
    
    total = df.count()
    
    # Build condition: all required fields must be non-NULL
    valid_condition = None
    for field in REQUIRED_FIELDS:
        field_check = col(field).isNotNull()
        if valid_condition is None:
            valid_condition = field_check
        else:
            valid_condition = valid_condition & field_check
    
    valid = df.filter(valid_condition).count()
    invalid = total - valid
    
    completeness_score = (valid / total * 100) if total > 0 else 0
    
    print(f"  Total records: {total}")
    print(f"  Complete records: {valid}")
    print(f"  Incomplete records: {invalid}")
    print(f"  Score: {completeness_score:.2f}%")
    
    return completeness_score, valid, invalid


def check_freshness(df):
    """
    Check 2: FRESHNESS
    Verify data is recent (not older than 24 hours)
    """
    print("\n🔍 CHECK 2: FRESHNESS")
    
    total = df.count()
    
    # Add days since processing
    df_with_age = df.withColumn(
        "days_old", 
        datediff(current_timestamp(), col("processed_at"))
    )
    
    # Fresh = processed in last 24 hours
    valid = df_with_age.filter(col("days_old") <= 1).count()
    invalid = total - valid
    
    freshness_score = (valid / total * 100) if total > 0 else 0
    
    print(f"  Total records: {total}")
    print(f"  Fresh records (< 24h): {valid}")
    print(f"  Stale records (> 24h): {invalid}")
    print(f"  Score: {freshness_score:.2f}%")
    
    return freshness_score, valid, invalid


def check_accuracy(df):
    """
    Check 3: ACCURACY
    Verify data quality and validity
    - email_length > 0 (no empty bodies)
    - sender_domain contains @ (valid email format)
    - subject is not just whitespace
    """
    print("\n🔍 CHECK 3: ACCURACY")
    
    total = df.count()
    
    # Check accuracy conditions
    accuracy_condition = (
        (col("email_length") > 0) &  # Non-empty body
        (col("sender_domain").contains(".")) &  # Valid domain format
        (col("subject").rlike("\\S"))  # Non-empty subject
    )
    
    valid = df.filter(accuracy_condition).count()
    invalid = total - valid
    
    accuracy_score = (valid / total * 100) if total > 0 else 0
    
    print(f"  Total records: {total}")
    print(f"  Accurate records: {valid}")
    print(f"  Inaccurate records: {invalid}")
    print(f"  Score: {accuracy_score:.2f}%")
    
    return accuracy_score, valid, invalid


def calculate_overall_score(completeness, freshness, accuracy):
    """
    Calculate overall quality score
    Weighted average: 50% completeness, 30% freshness, 20% accuracy
    """
    overall = (completeness * 0.5) + (freshness * 0.3) + (accuracy * 0.2)
    return overall


def generate_report(df, completeness, freshness, accuracy, overall):
    """Generate quality report"""
    
    print("\n" + "="*60)
    print("📊 QUALITY REPORT")
    print("="*60)
    print(f"Timestamp: {datetime.now().isoformat()}")
    print(f"Total records analyzed: {df.count()}")
    print()
    print(f"  Completeness: {completeness:.2f}% (weight: 50%)")
    print(f"  Freshness:    {freshness:.2f}% (weight: 30%)")
    print(f"  Accuracy:     {accuracy:.2f}% (weight: 20%)")
    print()
    print(f"📈 OVERALL QUALITY SCORE: {overall:.2f}%")
    print(f"   Threshold: {QUALITY_THRESHOLD}%")
    print()
    
    if overall >= QUALITY_THRESHOLD:
        print(f"✅ PASS: Quality {overall:.2f}% >= {QUALITY_THRESHOLD}%")
        status = "PASS"
    else:
        print(f"❌ FAIL: Quality {overall:.2f}% < {QUALITY_THRESHOLD}%")
        status = "FAIL"
    
    print("="*60)
    
    # Update report
    quality_report["timestamp"] = datetime.now().isoformat()
    quality_report["total_records"] = df.count()
    quality_report["completeness"]["score"] = round(completeness, 2)
    quality_report["freshness"]["score"] = round(freshness, 2)
    quality_report["accuracy"]["score"] = round(accuracy, 2)
    quality_report["overall_score"] = round(overall, 2)
    quality_report["status"] = status
    
    # Save report to JSON
    with open("/tmp/quality_report.json", "w") as f:
        json.dump(quality_report, f, indent=2)
    
    print(f"\n📄 Report saved to: /tmp/quality_report.json")
    
    return status


def main():
    print("🚀 Starting Data Quality Check")
    print(f"📋 Required fields: {', '.join(REQUIRED_FIELDS)}")
    print(f"📈 Quality threshold: {QUALITY_THRESHOLD}%\n")
    
    print("📍 Step 1: Creating Spark Session...")
    spark = create_spark_session()
    print("✅ Spark Session created successfully!\n")
    
    # 1. Read Bronze
    print("📍 Step 2: Reading Bronze Layer...")
    df = read_bronze(spark)
    if df is None or df.count() == 0:
        print("❌ No data found in Bronze layer!")
        spark.stop()
        return 1
    print(f"✅ Bronze layer read successfully! Records: {df.count()}\n")
    
    # 2. Run quality checks
    print("📍 Step 3: Running Quality Checks...")
    completeness, comp_valid, comp_invalid = check_completeness(df)
    freshness, fresh_valid, fresh_invalid = check_freshness(df)
    accuracy, acc_valid, acc_invalid = check_accuracy(df)
    print("✅ Quality checks completed!\n")
    
    # 3. Calculate overall score
    print("📍 Step 4: Calculating Overall Score...")
    overall_score = calculate_overall_score(completeness, freshness, accuracy)
    print(f"✅ Overall score calculated: {overall_score:.2f}%\n")
    
    # 4. Generate report
    print("📍 Step 5: Generating Report...")
    status = generate_report(df, completeness, freshness, accuracy, overall_score)
    print(f"✅ Report generated: {status}\n")
    
    print("📍 Step 6: Stopping Spark Session...")
    spark.stop()
    print("✅ Spark Session stopped!\n")
    
    # 5. Return exit code
    print("📍 Step 7: Returning Exit Code...")
    if status == "PASS":
        print("✅ FINAL RESULT: EXIT CODE 0 (SUCCESS)\n")
        return 0
    else:
        print(f"❌ FINAL RESULT: EXIT CODE 1 (FAILURE - {overall_score:.2f}% < {QUALITY_THRESHOLD}%)\n")
        raise Exception(f"Data quality check failed: {overall_score:.2f}% < {QUALITY_THRESHOLD}%")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        sys.exit(1)