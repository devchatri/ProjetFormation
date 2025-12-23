from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, current_timestamp, split, length, lower, when, lit, 
    window, count, avg, collect_list, sum, min, max, approx_count_distinct,
    date_trunc, first, hour, desc, row_number, trim, regexp_extract, to_timestamp, from_utc_timestamp,
    array_contains)
from pyspark.sql.types import (StructType, StructField, StringType, ArrayType)
from pyspark.sql.functions import to_date, coalesce, current_date, substring

# Configuration Kafka
KAFKA_TOPIC = "processed-emails-topic"
KAFKA_BOOTSTRAP_SERVERS = "kafka:29092"

# Configuration MinIO
MINIO_ENDPOINT = "http://minio:9000"
MINIO_ACCESS_KEY = "minioadmin"
MINIO_SECRET_KEY = "minioadmin123"

# Schéma des emails validés (format du validator Kafka)
email_schema = StructType([
    StructField("message_id", StringType(), True),
    StructField("sender", StringType(), True),
    StructField("recipient", StringType(), True),
    StructField("subject", StringType(), True),
    StructField("body", StringType(), True),
    StructField("labels", ArrayType(StringType()), True),  # Labels Gmail: ["INBOX", "IMPORTANT", "STARRED", "SPAM"]
    StructField("timestamp", StringType(), True)
])


def create_spark_session():
    """Crée une session Spark avec support S3/MinIO"""
    return SparkSession.builder \
        .appName("EmailProcessor") \
        .config("spark.jars.packages", 
                "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,"
                "org.apache.hadoop:hadoop-aws:3.3.4,"
                "com.amazonaws:aws-java-sdk-bundle:1.12.262") \
        .config("spark.hadoop.fs.s3a.endpoint", MINIO_ENDPOINT) \
        .config("spark.hadoop.fs.s3a.access.key", MINIO_ACCESS_KEY) \
        .config("spark.hadoop.fs.s3a.secret.key", MINIO_SECRET_KEY) \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider") \
        .config("spark.sql.session.timeZone", "UTC") \
        .getOrCreate()


def read_from_kafka(spark):
    """Lit les emails depuis Kafka en streaming"""
    return spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS) \
        .option("subscribe", KAFKA_TOPIC) \
        .option("startingOffsets", "earliest") \
        .option("failOnDataLoss", "false") \
        .load()


def transform_emails(df):
    """Parse le JSON et enrichit les données (Task 3.1)"""
    # 1. Parser le JSON depuis Kafka
    parsed_df = df.select(
        from_json(col("value").cast("string"), email_schema).alias("email")
    ).select("email.*")
    
    # 2. Renommer les colonnes pour cohérence + Ajouter les champs d'enrichissement
    # Le timestamp vient du producer au format ISO 8601 UTC (YYYY-MM-DDTHH:MM:SSZ)
    # Spark cast("timestamp") parse automatiquement ce format correctement
    enriched_df = parsed_df \
        .withColumnRenamed("message_id", "id") \
        .withColumnRenamed("sender", "from") \
        .withColumnRenamed("recipient", "to") \
        .withColumn("email_timestamp", col("timestamp").cast("timestamp")) \
        .withColumn("date", to_date(col("email_timestamp"))) \
        .withColumn("processed_at", current_timestamp()) \
        .withColumn("sender_domain", split(col("from"), "@").getItem(1)) \
        .withColumn("email_length", length(col("body"))) \
        .withColumn(
            "is_spam",
            # Priorité 1: Si labels contient "SPAM" → is_spam=true
            when(array_contains(col("labels"), "SPAM"), lit(True)).otherwise(lit(False))
        ) \
        .withColumn(
            "is_important",
            # Priorité 1: si c'est du spam => pas important
            when(col("is_spam") == True, lit(False))
            # Priorité 2: Labels Gmail (IMPORTANT ou STARRED)
            .when(array_contains(col("labels"), "IMPORTANT") | array_contains(col("labels"), "STARRED"), lit(True))
            # Sinon pas important
            .otherwise(lit(False))
        )
    # Normalisation de l'adresse email pour le partitionnement
    enriched_df = enriched_df.withColumn(
        "user_email",
        lower(trim(regexp_extract(col("to"), r'([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+)', 1)))
    ).drop("timestamp")
    return enriched_df
def create_hourly_aggregations(df):
    """Crée les agrégations par fenêtre de 1 heure (Production)"""
    return df \
        .withWatermark("processed_at", "5 seconds") \
        .groupBy(
            window(col("processed_at"), "15 seconds"),
            col("sender_domain")
        ) \
        .agg(
            # Agrégations de base (demandées)
            count("*").alias("emails_count"),
            avg("email_length").alias("avg_email_length"),
            collect_list("subject").alias("subjects_list"),
            
          
        )


def create_daily_aggregations(df):
    """
    DEPRECATED: Moved to daily_aggregation.py (Batch Job - Day 4)
    This function is kept for reference only.
    Gold layer is now created as a BATCH job after Data Quality Check.
    """
    pass

def write_to_bronze(df):
    """Écrit les données enrichies dans Bronze (Task 3.1)"""
    # Partitionne par user_email et date (YYYY-MM-DD)
    # email_timestamp (date+heure exacte) reste dans les fichiers Parquet
    return df.writeStream \
        .format("parquet") \
        .partitionBy("user_email", "date") \
        .option("path", "s3a://datalake/bronze/emails/") \
        .option("checkpointLocation", "s3a://datalake/checkpoints/bronze/") \
        .outputMode("append") \
        .start()
def write_to_silver(df):
    """Écrit les agrégations horaires dans Silver (Task 3.2)"""
    return df.writeStream \
        .format("parquet") \
        .option("path", "s3a://datalake/silver/email_stats/") \
        .option("checkpointLocation", "s3a://datalake/checkpoints/silver/") \
        .outputMode("append") \
        .start()


def write_to_gold(df):
    """DEPRECATED - Gold layer is now created as BATCH job (Day 4)"""
    pass
    # Old code (for reference):
    # return df.writeStream \
    #     .format("parquet") \
    #     .option("path", "s3a://datalake/gold/daily_summary/") \
    #     .option("checkpointLocation", "s3a://datalake/checkpoints/gold/") \
    #     .outputMode("append") \
    #     .start()


def main():
    print("🚀 Démarrage Spark Streaming - Email Processor (Bronze + Silver )")
    
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    print(f"📡 Kafka: {KAFKA_BOOTSTRAP_SERVERS}")
    print(f"📥 Topic: {KAFKA_TOPIC}")
    
    # Lire depuis Kafka
    kafka_df = read_from_kafka(spark)
    
    # === BRONZE ===
    # Enrichir les données
    enriched_df = transform_emails(kafka_df)
    
    # Écrire dans Bronze
    print("📂 Bronze: s3a://datalake/bronze/emails/")
    bronze_query = write_to_bronze(enriched_df)
    
    # === SILVER ===
    # Créer les agrégations horaires
    hourly_stats = create_hourly_aggregations(enriched_df)
    
    # Écrire dans Silver
    print("📂 Silver: s3a://datalake/silver/email_stats/")
    silver_query = write_to_silver(hourly_stats)
    
    print("✅ Streaming Bronze + Silver démarré!")
    print("⏳ En attente de messages Kafka... (exécution indéfinie)")

    
    # Attendre tous les streams indéfiniment (s'exécute en arrière-plan via nohup)
    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()



