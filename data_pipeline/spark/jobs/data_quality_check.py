#!/usr/bin/env python3
"""
Data Quality Check Script
Vérifie la qualité des emails dans la couche Bronze
- Premier lancement : vérifie tous les emails
- Lancements suivants : vérifie uniquement les nouveaux emails (non vérifiés)

Aspects vérifiés :
1. Complétude : toutes les colonnes obligatoires sont remplies
2. Validité : format email correct, body non vide
3. Fraîcheur : emails récents (< 7 jours)
"""

import sys
import json
import os
from datetime import datetime, timedelta
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, datediff, current_date

# Seuil de qualité minimum (95%)
QUALITY_THRESHOLD = 95.0

# Fichier checkpoint pour suivre les emails déjà vérifiés
CHECKPOINT_FILE = "/opt/airflow/quality_checkpoint.json"


def create_spark_session():
    """Crée une session Spark avec support S3/MinIO"""
    return SparkSession.builder \
        .appName("DataQualityCheck") \
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin") \
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123") \
        .config("spark.hadoop.fs.s3a.path.style.access", "true") \
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
        .config("spark.sql.files.ignoreCorruptFiles", "true") \
        .config("spark.sql.files.ignoreMissingFiles", "true") \
        .getOrCreate()


def load_checkpoint():
    """Charge le checkpoint des emails déjà vérifiés"""
    if os.path.exists(CHECKPOINT_FILE):
        with open(CHECKPOINT_FILE, 'r') as f:
            return json.load(f)
    return {
        "last_check": None,
        "verified_ids": [],
        "total_verified": 0
    }


def save_checkpoint(checkpoint):
    """Sauvegarde le checkpoint"""
    checkpoint["last_check"] = datetime.utcnow().isoformat()
    with open(CHECKPOINT_FILE, 'w') as f:
        json.dump(checkpoint, f, indent=2)


def check_completeness(df):
    """Vérifie que les colonnes obligatoires sont remplies"""
    total = df.count()
    if total == 0:
        return 100.0, {"total": 0, "complete": 0, "incomplete": 0}
    
    # Colonnes obligatoires
    complete = df.filter(
        (col("id").isNotNull()) &
        (col("from").isNotNull()) &
        (col("to").isNotNull()) &
        (col("subject").isNotNull()) &
        (col("body").isNotNull()) &
        (col("user_email").isNotNull())
    ).count()
    
    score = (complete / total) * 100
    
    return score, {
        "total": total,
        "complete": complete,
        "incomplete": total - complete,
        "percentage": round(score, 2)
    }


def check_validity(df):
    """Vérifie le format des emails et que le body n'est pas vide"""
    total = df.count()
    if total == 0:
        return 100.0, {"total": 0, "valid": 0, "invalid": 0}
    
    valid = df.filter(
        (col("from").contains("@")) &
        (col("to").contains("@")) &
        (col("body") != "") &
        (col("body").isNotNull())
    ).count()
    
    score = (valid / total) * 100
    
    return score, {
        "total": total,
        "valid": valid,
        "invalid": total - valid,
        "percentage": round(score, 2)
    }


def check_freshness(df):
    """Vérifie que les emails sont récents (< 7 jours)"""
    total = df.count()
    if total == 0:
        return 100.0, {"total": 0, "recent": 0, "old": 0}
    
    # Emails des 7 derniers jours
    recent = df.filter(
        datediff(current_date(), col("date")) <= 7
    ).count()
    
    score = (recent / total) * 100
    
    return score, {
        "total": total,
        "recent": recent,
        "old": total - recent,
        "percentage": round(score, 2)
    }


def generate_quality_report(spark, report_path):
    """Génère le rapport de qualité"""
    print("🔍 Starting Data Quality Check...")
    print(f"⏰ Time: {datetime.utcnow().isoformat()}\n")
    
    # Charger le checkpoint
    checkpoint = load_checkpoint()
    is_first_run = checkpoint["last_check"] is None
    
    if is_first_run:
        print("📌 PREMIER LANCEMENT : Vérification de tous les emails")
    else:
        print(f"📌 LANCEMENT SUIVANT : Vérification des nouveaux emails")
        print(f"   Dernière vérification : {checkpoint['last_check']}")
        print(f"   Emails déjà vérifiés : {checkpoint['total_verified']}\n")
    
    # Lire les données Bronze
    bronze_path = "s3a://datalake/bronze/emails/"
    
    try:
        df = spark.read.format("parquet").load(bronze_path)
        total_rows = df.count()
        print(f"✅ {total_rows} emails trouvés dans Bronze\n")
        
        if total_rows == 0:
            print("⚠️ Aucune donnée à vérifier")
            report = {
                "timestamp": datetime.utcnow().isoformat(),
                "status": "NO_DATA",
                "overall_score": 0.0,
                "message": "Aucune donnée dans Bronze"
            }
            with open(report_path, "w") as f:
                json.dump(report, f, indent=2)
            return False
        
        # Filtrer les nouveaux emails (non vérifiés)
        if not is_first_run and checkpoint["verified_ids"]:
            df_to_check = df.filter(~col("id").isin(checkpoint["verified_ids"]))
            new_count = df_to_check.count()
            print(f"🔎 {new_count} nouveaux emails à vérifier")
        else:
            df_to_check = df
            new_count = total_rows
            print(f"🔎 Vérification de tous les {total_rows} emails")
        
        if new_count == 0:
            print("✅ Aucun nouvel email à vérifier")
            report = {
                "timestamp": datetime.utcnow().isoformat(),
                "status": "NO_NEW_DATA",
                "overall_score": 100.0,
                "message": "Aucun nouvel email depuis la dernière vérification"
            }
            with open(report_path, "w") as f:
                json.dump(report, f, indent=2)
            return True
        
        # Exécuter les checks
        print("\n📊 Vérification de la qualité...")
        print("="*60)
        
        completeness_score, completeness_details = check_completeness(df_to_check)
        print(f"✓ Complétude : {completeness_score:.2f}%")
        
        validity_score, validity_details = check_validity(df_to_check)
        print(f"✓ Validité   : {validity_score:.2f}%")
        
        freshness_score, freshness_details = check_freshness(df_to_check)
        print(f"✓ Fraîcheur  : {freshness_score:.2f}%")
        
        # Score global (moyenne pondérée)
        overall_score = (
            completeness_score * 0.40 +  # 40% complétude
            validity_score * 0.40 +       # 40% validité
            freshness_score * 0.20        # 20% fraîcheur
        )
        
        status = "PASSED" if overall_score >= QUALITY_THRESHOLD else "FAILED"
        
        print("="*60)
        print(f"\n📋 RÉSULTAT : {status}")
        print(f"   Score global : {overall_score:.2f}% (seuil: {QUALITY_THRESHOLD}%)")
        
        # Générer le rapport
        report = {
            "timestamp": datetime.utcnow().isoformat(),
            "status": status,
            "overall_score": round(overall_score, 2),
            "threshold": QUALITY_THRESHOLD,
            "emails_checked": new_count,
            "is_first_run": is_first_run,
            "checks": {
                "completeness": {
                    "score": round(completeness_score, 2),
                    "weight": 0.40,
                    "details": completeness_details
                },
                "validity": {
                    "score": round(validity_score, 2),
                    "weight": 0.40,
                    "details": validity_details
                },
                "freshness": {
                    "score": round(freshness_score, 2),
                    "weight": 0.20,
                    "details": freshness_details
                }
            }
        }
        
        # Sauvegarder le rapport
        with open(report_path, "w") as f:
            json.dump(report, f, indent=2)
        
        print(f"\n💾 Rapport sauvegardé : {report_path}\n")
        
        # Mettre à jour le checkpoint
        if status == "PASSED":
            new_ids = [row.id for row in df_to_check.select("id").collect()]
            checkpoint["verified_ids"].extend(new_ids)
            checkpoint["total_verified"] = len(checkpoint["verified_ids"])
            save_checkpoint(checkpoint)
            print(f"✅ Checkpoint mis à jour : {checkpoint['total_verified']} emails vérifiés au total\n")
        
        return status == "PASSED"
        
    except Exception as e:
        print(f"\n❌ Erreur : {e}")
        import traceback
        traceback.print_exc()
        
        report = {
            "timestamp": datetime.utcnow().isoformat(),
            "status": "ERROR",
            "overall_score": 0.0,
            "error": str(e)
        }
        with open(report_path, "w") as f:
            json.dump(report, f, indent=2)
        
        return False


def main():
    if len(sys.argv) < 2:
        print("Usage: spark-submit data_quality_check.py <report_path>")
        print("Exemple: spark-submit data_quality_check.py quality_report.json")
        sys.exit(1)
    
    report_path = sys.argv[1]
    
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    try:
        passed = generate_quality_report(spark, report_path)
        
        if passed:
            print("="*60)
            print("✅ QUALITY CHECK PASSED")
            print("="*60)
            sys.exit(0)
        else:
            print("="*60)
            print("❌ QUALITY CHECK FAILED")
            print("="*60)
            sys.exit(1)
    
    except Exception as e:
        print(f"❌ Erreur fatale : {e}")
        sys.exit(1)
    
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
