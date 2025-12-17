#!/usr/bin/env python3
"""
Script Spark pour l'agrégation journalière des statistiques d'emails.
Lit les données de la couche Bronze et écrit les résultats dans la couche Gold.
Calcule les statistiques PAR UTILISATEUR et PAR DATE.
"""

import sys
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, count, sum as _sum, when, size, struct, collect_list, 
    desc, hour, dayofweek, date_format, from_unixtime, to_date,
    lit, first, array, slice
)
from datetime import datetime

def create_spark_session():
    """Crée une session Spark configurée pour MinIO"""
    return SparkSession.builder \
        .appName("DailyEmailAggregation") \
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin") \
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123") \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.sql.files.ignoreCorruptFiles", "true") \
        .config("spark.sql.files.ignoreMissingFiles", "true") \
        .getOrCreate()

def aggregate_by_user_and_date(df):
    """
    Agrège les statistiques par utilisateur et par date.
    Retourne un DataFrame avec toutes les stats pour chaque user/date.
    """
    # Convertir email_timestamp en date (timestamp Unix en millisecondes)
    df = df.withColumn("email_date", col("date"))  # La date est déjà dans la partition
    
    # Ajouter les dimensions temporelles à partir de email_timestamp
    df = df.withColumn("hour_of_day", hour(col("email_timestamp")))
    df = df.withColumn("day_of_week", date_format(col("email_timestamp"), "EEEE"))
    
    # Statistiques générales par user/date
    print("  → Calcul des statistiques générales...")
    overview = df.groupBy("user_email", "email_date") \
        .agg(
            count("*").alias("total_emails"),
            _sum(when(col("is_important") == True, 1).otherwise(0)).alias("unread_emails"),
            _sum(when(col("is_important") == True, 1).otherwise(0)).alias("important_emails")
        )
    
    # Top 10 expéditeurs par user/date
    print("  → Calcul des top expéditeurs...")
    senders_agg = df.groupBy("user_email", "email_date", "from", "sender_domain") \
        .agg(count("*").alias("count")) \
        .orderBy("user_email", "email_date", desc("count"))
    
    # Utiliser window function pour limiter à top 10 par user/date
    from pyspark.sql.window import Window
    from pyspark.sql.functions import row_number
    
    window = Window.partitionBy("user_email", "email_date").orderBy(desc("count"))
    
    top_senders = senders_agg.withColumn("rank", row_number().over(window)) \
        .filter(col("rank") <= 10) \
        .groupBy("user_email", "email_date") \
        .agg(
            collect_list(
                struct(
                    col("from").alias("name"),
                    col("sender_domain").alias("domain"),
                    col("count")
                )
            ).alias("top_senders")
        )
    
    # Activité horaire par user/date
    print("  → Calcul de l'activité horaire...")
    hourly = df.groupBy("user_email", "email_date", "hour_of_day") \
        .agg(count("*").alias("count")) \
        .groupBy("user_email", "email_date") \
        .agg(
            collect_list(
                struct(
                    col("hour_of_day").alias("hour"),
                    col("count")
                )
            ).alias("hourly_activity")
        )
    
    # Activité hebdomadaire par user/date
    print("  → Calcul de l'activité hebdomadaire...")
    weekly = df.groupBy("user_email", "email_date", "day_of_week") \
        .agg(count("*").alias("count")) \
        .groupBy("user_email", "email_date") \
        .agg(
            collect_list(
                struct(
                    col("day_of_week").alias("day"),
                    col("count")
                )
            ).alias("weekly_activity")
        )
    
    # Joindre toutes les statistiques
    print("  → Assemblage des statistiques...")
    result = overview \
        .join(top_senders, ["user_email", "email_date"], "left") \
        .join(hourly, ["user_email", "email_date"], "left") \
        .join(weekly, ["user_email", "email_date"], "left")
    
    # Renommer email_date en aggregation_date pour éviter confusion avec partition date
    result = result.withColumnRenamed("email_date", "aggregation_date")
    
    return result

def main(input_path, output_path):
    """Fonction principale"""
    print("=" * 60)
    print("AGRÉGATION JOURNALIÈRE DES EMAILS")
    print("=" * 60)
    
    # Créer la session Spark
    spark = create_spark_session()
    
    try:
        # Lire les données de la couche Bronze
        print(f"\n📂 Lecture des données depuis : {input_path}")
        df = spark.read.parquet(input_path)
        
        total_emails = df.count()
        print(f"✓ {total_emails} emails chargés")
        
        # Afficher les utilisateurs et dates trouvés
        users = df.select("user_email").distinct().count()
        # La date est déjà partitionnée dans le DataFrame
        dates = df.select("date").distinct().count()
        print(f"✓ {users} utilisateur(s) trouvé(s)")
        print(f"✓ {dates} date(s) différente(s)")
        
        # Calculer les agrégations par user et date
        print("\n📊 Calcul des statistiques par utilisateur et par date...")
        aggregated = aggregate_by_user_and_date(df)
        
        # Afficher un aperçu
        print("\n📋 Aperçu des résultats :")
        aggregated.select("user_email", "aggregation_date", "total_emails", "unread_emails", "important_emails").show(10, truncate=False)
        
        # Écrire les résultats dans la couche Gold (partitionné par user_email et aggregation_date)
        print(f"\n💾 Écriture des résultats dans : {output_path}")
        print("   Structure : user_email=xxx/aggregation_date=YYYY-MM-DD/")
        
        aggregated.write \
            .mode("overwrite") \
            .partitionBy("user_email", "aggregation_date") \
            .parquet(output_path)
        
        print("✓ Statistiques écrites avec succès")
        
        # Afficher le nombre de partitions créées
        partitions_count = aggregated.select("user_email", "aggregation_date").distinct().count()
        print(f"✓ {partitions_count} partition(s) créée(s) (combinaisons user/date)")
        
        print("\n" + "=" * 60)
        print("✅ AGRÉGATION TERMINÉE AVEC SUCCÈS")
        print("=" * 60)
        
    except Exception as e:
        print(f"\n❌ Erreur lors de l'agrégation : {e}")
        import traceback
        traceback.print_exc()
        raise
    finally:
        spark.stop()

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: daily_aggregation.py <input_path> <output_path>")
        print("Exemple: daily_aggregation.py s3a://datalake/bronze/emails s3a://datalake/gold/daily_stats")
        sys.exit(1)
    
    input_path = sys.argv[1]
    output_path = sys.argv[2]
    
    main(input_path, output_path)
