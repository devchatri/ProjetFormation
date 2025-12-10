from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, collect_list, row_number, rank, hour, max, min, desc, to_date, concat_ws, struct
from pyspark.sql.window import Window
from datetime import datetime
import sys

def create_spark_session():
    """Create Spark session with MinIO configuration"""
    spark = SparkSession.builder \
        .appName("DailyAggregation") \
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin") \
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123") \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider") \
        .getOrCreate()
    
    spark.sparkContext.setLogLevel("INFO")
    return spark

def read_bronze(spark):
    """Read Bronze layer (quality-checked emails)"""
    print("📌 Step 1: Reading Bronze layer from MinIO...")
    try:
        bronze_df = spark.read.parquet("s3a://datalake/bronze/emails/")
        print(f"✅ Bronze layer loaded successfully: {bronze_df.count()} records")
        return bronze_df
    except Exception as e:
        print(f"❌ Error reading Bronze layer: {e}")
        return None

def create_daily_aggregations(spark, bronze_df):
    """Create daily aggregations from Bronze layer"""
    print("📌 Step 2: Creating daily aggregations...")
    
    # Add date column from the date field
    daily_df = bronze_df.withColumn("email_date", to_date(col("date")))
    
    # 1️⃣ Total emails per day
    print("   • Calculating total emails per day...")
    total_emails = daily_df.groupBy("email_date").agg(
        count("*").alias("total_emails")
    )
    
    # 2️⃣ Top 10 sender domains (with ranking)
    print("   • Calculating top 10 sender domains...")
    domain_stats = daily_df.groupBy("email_date", "sender_domain").agg(
        count("*").alias("domain_count")
    ).withColumn(
        "domain_rank",
        rank().over(Window.partitionBy("email_date").orderBy(desc("domain_count")))
    )
    
    # Filter top 10 and create array
    top_domains = domain_stats.filter(col("domain_rank") <= 10) \
        .groupBy("email_date").agg(
            collect_list(struct(col("sender_domain"), col("domain_count"), col("domain_rank"))).alias("top_10_domains")
        )
    
    # Also create domain_counts as simple key-value pairs
    domain_counts = domain_stats.filter(col("domain_rank") <= 10) \
        .groupBy("email_date").agg(
            concat_ws("|", collect_list(concat_ws(":", col("sender_domain"), col("domain_count")))).alias("domain_counts")
        )
    
    # 3️⃣ Busiest hour (hour with most emails)
    print("   • Calculating busiest hour...")
    hourly_stats = daily_df.groupBy("email_date", hour(col("date")).alias("hour")).agg(
        count("*").alias("hour_count")
    )
    
    busiest_hour = hourly_stats.withColumn(
        "rank",
        rank().over(Window.partitionBy("email_date").orderBy(desc("hour_count")))
    ).filter(col("rank") == 1).select(
        "email_date",
        col("hour").alias("busiest_hour"),
        col("hour_count").alias("busiest_hour_count")
    )
    
    # Combine all aggregations
    print("   • Combining all aggregations...")
    result = total_emails \
        .join(domain_counts, "email_date", "left") \
        .join(busiest_hour, "email_date", "left") \
        .select(
            col("email_date").alias("date"),
            "total_emails",
            "domain_counts",
            "busiest_hour",
            "busiest_hour_count"
        )
    
    print(f"✅ Daily aggregations created successfully: {result.count()} records")
    result.show(truncate=False)
    return result

def write_to_gold(spark, agg_df):
    """Write daily aggregations to Gold layer """
    print("📌 Step 3: Writing aggregations to Gold layer...")
    print("   ├─ Mode: OVERWRITE (replaces same date, keeps history)")
    print("   └─ Partition: By date (date=YYYY-MM-DD folders)")
    try:
        # ✅ OPTION 2: OVERWRITE + PARTITIONBY(date)
        # This ensures:
        # 1. No duplicates if same date is rerun
        # 2. Historical data preserved (different dates in separate folders)
        # 3. Fast queries (partition pruning)
        agg_df.write \
            .format("parquet") \
            .mode("overwrite") \
            .partitionBy("date") \
            .option("path", "s3a://datalake/gold/daily_summary/") \
            .save()
        
        print("✅ Gold layer written successfully!")
        print(f"   ├─ Location: s3a://datalake/gold/daily_summary/")
        print(f"   └─ Organization: date=YYYY-MM-DD/ folders")
        return True
    except Exception as e:
        print(f"❌ Error writing to Gold layer: {e}")
        return False

def main():
    """Main orchestration function"""
    print("=" * 60)
    print("🚀 DAILY AGGREGATION JOB STARTED")
    print(f"   Timestamp: {datetime.now()}")
    print("=" * 60)
    
    # Create Spark session
    spark = create_spark_session()
    
    # Read Bronze layer
    bronze_df = read_bronze(spark)
    if bronze_df is None:
        print("❌ Failed to read Bronze layer. Exiting.")
        spark.stop()
        sys.exit(1)
    
    # Create daily aggregations
    agg_df = create_daily_aggregations(spark, bronze_df)
    if agg_df is None or agg_df.count() == 0:
        print("❌ No aggregations created. Exiting.")
        spark.stop()
        sys.exit(1)
    
    # Write to Gold layer
    success = write_to_gold(spark, agg_df)
    
    spark.stop()
    
    print("=" * 60)
    if success:
        print("✅ DAILY AGGREGATION JOB COMPLETED SUCCESSFULLY")
        sys.exit(0)
    else:
        print("❌ DAILY AGGREGATION JOB FAILED")
        sys.exit(1)

if __name__ == "__main__":
    main()