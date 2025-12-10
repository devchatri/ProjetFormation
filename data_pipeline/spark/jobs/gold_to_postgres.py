#!/usr/bin/env python3

from pyspark.sql import SparkSession
from datetime import datetime
import sys

def create_spark_session():
    """Create Spark session with MinIO & PostgreSQL config"""
    return SparkSession.builder \
        .appName("GoldToPostgres") \
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin") \
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123") \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider") \
        .config("spark.jars.packages", "org.postgresql:postgresql:42.7.1") \
        .getOrCreate()

def read_gold_layer(spark):
    """Read daily aggregations from Gold layer"""
    print("📌 Step 1: Reading Gold layer from MinIO...")
    try:
        gold_df = spark.read.parquet("s3a://datalake/gold/daily_summary/")
        print(f"✅ Gold layer loaded: {gold_df.count()} records")
        return gold_df
    except Exception as e:
        print(f"❌ Error reading Gold layer: {e}")
        return None

def export_to_postgres(df, table_name="email_daily_summary"):
    """Export DataFrame to PostgreSQL"""
    print(f"\n📌 Step 2: Exporting to PostgreSQL table '{table_name}'...")
    
    try:
        # PostgreSQL connection details
        db_url = "jdbc:postgresql://postgres:5432/airflow"
        db_properties = {
            "user": "airflow",
            "password": "airflow",
            "driver": "org.postgresql.Driver"
        }
        
        # Write to PostgreSQL (overwrite mode to keep latest data)
        df.write.jdbc(url=db_url, table=table_name, mode="overwrite", properties=db_properties)
        
        print(f"✅ Successfully exported to PostgreSQL table: {table_name}")
        return True
    except Exception as e:
        print(f"❌ Error exporting to PostgreSQL: {e}")
        return False

def create_summary_report(df):
    """Create and print summary report"""
    print("\n" + "="*60)
    print("📊 EXPORT SUMMARY REPORT")
    print("="*60)
    print(f"Timestamp: {datetime.now()}")
    print(f"\n📈 Records exported: {df.count()}")
    print("\n📄 Sample data:")
    df.show(truncate=False)
    print("="*60)

def main():
    print("\n" + "="*60)
    print("🚀 GOLD TO POSTGRESQL EXPORT JOB STARTED")
    print(f"   Timestamp: {datetime.now()}")
    print("="*60)
    
    # Create Spark session
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("INFO")
    
    # Read Gold layer
    gold_df = read_gold_layer(spark)
    if gold_df is None:
        print("\n❌ Failed to read Gold layer. Exiting.")
        spark.stop()
        sys.exit(1)
    
    # Export to PostgreSQL
    success = export_to_postgres(gold_df)
    
    if success:
        # Create summary report
        create_summary_report(gold_df)
        print("\n✅ EXPORT COMPLETED SUCCESSFULLY")
        exit_code = 0
    else:
        print("\n❌ EXPORT FAILED")
        exit_code = 1
    
    # Stop Spark session
    spark.stop()
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
