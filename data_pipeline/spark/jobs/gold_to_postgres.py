#!/usr/bin/env python3
"""
Script Spark pour charger les statistiques de la couche Gold (MinIO) 
vers PostgreSQL.
"""

import sys
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, to_json, struct
from pyspark.sql.types import StringType

def create_spark_session():
    """Crée une session Spark configurée pour MinIO et PostgreSQL"""
    return SparkSession.builder \
        .appName("GoldToPostgreSQL") \
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin") \
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123") \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.jars", "/opt/spark/jars/postgresql-42.7.1.jar") \
        .getOrCreate()

def main(gold_path, postgres_url, postgres_user, postgres_password):
    """Fonction principale"""
    print("=" * 60)
    print("CHARGEMENT GOLD → POSTGRESQL")
    print("=" * 60)
    
    # Créer la session Spark
    spark = create_spark_session()
    
    try:
        # Lire les données de la couche Gold
        print(f"\n📂 Lecture des données depuis : {gold_path}")
        df = spark.read.parquet(gold_path)
        
        total_records = df.count()
        print(f"✓ {total_records} enregistrement(s) chargé(s)")
        
        # Afficher le schéma
        print("\n📋 Schéma des données :")
        df.printSchema()
        
        # Convertir les colonnes complexes (arrays/structs) en JSON
        print("\n🔄 Conversion des colonnes complexes en JSON...")
        df = df.withColumn("top_senders", to_json(col("top_senders")).cast(StringType())) \
               .withColumn("hourly_activity", to_json(col("hourly_activity")).cast(StringType())) \
               .withColumn("weekly_activity", to_json(col("weekly_activity")).cast(StringType()))
        
        # Afficher un aperçu
        print("\n📊 Aperçu des données à insérer :")
        df.select("user_email", "aggregation_date", "total_emails", "unread_emails", "important_emails").show(5, truncate=False)
        
        # Écrire dans PostgreSQL
        print(f"\n💾 Écriture dans PostgreSQL : {postgres_url}")
        df.write \
            .format("jdbc") \
            .option("url", postgres_url) \
            .option("dbtable", "daily_email_stats") \
            .option("user", postgres_user) \
            .option("password", postgres_password) \
            .option("driver", "org.postgresql.Driver") \
            .mode("overwrite") \
            .save()
        
        print("✓ Données écrites avec succès dans PostgreSQL")
        
        # Vérification
        print("\n🔍 Vérification des données insérées...")
        df_check = spark.read \
            .format("jdbc") \
            .option("url", postgres_url) \
            .option("dbtable", "daily_email_stats") \
            .option("user", postgres_user) \
            .option("password", postgres_password) \
            .option("driver", "org.postgresql.Driver") \
            .load()
        
        count_in_db = df_check.count()
        print(f"✓ {count_in_db} enregistrement(s) présent(s) dans PostgreSQL")
        
        print("\n" + "=" * 60)
        print("✅ CHARGEMENT TERMINÉ AVEC SUCCÈS")
        print("=" * 60)
        
    except Exception as e:
        print(f"\n❌ Erreur lors du chargement : {e}")
        import traceback
        traceback.print_exc()
        raise
    finally:
        spark.stop()

if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("Usage: gold_to_postgres.py <gold_path> <postgres_url> <postgres_user> <postgres_password>")
        print("Exemple: gold_to_postgres.py s3a://datalake/gold/daily_stats jdbc:postgresql://postgres:5432/projetformationdb postgres secret")
        sys.exit(1)
    
    gold_path = sys.argv[1]
    postgres_url = sys.argv[2]
    postgres_user = sys.argv[3]
    postgres_password = sys.argv[4]
    
    main(gold_path, postgres_url, postgres_user, postgres_password)
