# 📊 Data Quality Check & Gold Layer Implementation

## Vue d'ensemble

Cette étape implémente la **validation de qualité des données** et l'**agrégation quotidienne** pour créer la **couche Gold** (Gold Layer) de notre data lake.

---

## 🏗️ Architecture Pipeline Complète

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Gmail API      │────▶│    Kafka     │────▶│  Bronze Layer   │
│  (Extraction)   │     │  (Streaming) │     │  (Raw Enriched) │
└─────────────────┘     └──────────────┘     └─────────────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │ Quality Check   │
                                              │  (≥ 95% Score)  │
                                              └─────────────────┘
                                                       │
                                           ┌───────────┴───────────┐
                                           │ PASS       │   FAIL   │
                                           ▼            ▼          
                                   ┌─────────────┐  Pipeline Fails
                                   │ Gold Layer  │
                                   │ (Daily Stats)│
                                   └─────────────┘
                                           │
                                           ▼
                                   ┌─────────────┐
                                   │ PostgreSQL  │
                                   │  (Export)   │
                                   └─────────────┘
```

---

## 📁 Fichiers Créés/Modifiés

### 1. **data_quality_check.py** (Spark Job)
**Emplacement**: `data_pipeline/spark/jobs/data_quality_check.py`

**Rôle**: Valider la qualité des données dans la couche Bronze

**Fonctionnalités**:
- ✅ **Completeness Check** (50% du score): Vérifie que tous les champs requis sont présents
  - Champs requis: `id`, `from`, `to`, `subject`, `body`, `date`, `sender_domain`
- ✅ **Freshness Check** (30% du score): Vérifie que les données sont récentes (< 24h)
- ✅ **Accuracy Check** (20% du score): Vérifie la validité des données
  - `email_length > 0` (pas de body vide)
  - `sender_domain` contient un `.` (format valide)
  - `subject` n'est pas vide

**Sortie**:
- Score global calculé avec pondération: `(50% × completeness) + (30% × freshness) + (20% × accuracy)`
- Rapport JSON sauvegardé dans `/tmp/quality_report.json`
- **Exit code 0** si score ≥ 95% (PASS)
- **Exit code 1** si score < 95% (FAIL) → Pipeline s'arrête

**Exemple de rapport**:
```json
{
  "timestamp": "2025-01-17T10:30:00",
  "total_records": 1000,
  "completeness": {
    "score": 98.5
  },
  "freshness": {
    "score": 100.0
  },
  "accuracy": {
    "score": 97.2
  },
  "overall_score": 98.4,
  "status": "PASS"
}
```

---

### 2. **daily_aggregation.py** (Spark Job)
**Emplacement**: `data_pipeline/spark/jobs/daily_aggregation.py`

**Rôle**: Créer des statistiques quotidiennes pour la couche Gold

**Fonctionnalités**:
1. **Total Emails par jour**: Compte le nombre total d'emails reçus
2. **Top 10 Sender Domains**: Classement des 10 domaines qui envoient le plus d'emails
3. **Busiest Hour**: L'heure de la journée avec le plus d'activité

**Exemple de données Gold**:
| date       | total_emails | domain_counts                        | busiest_hour | busiest_hour_count |
|------------|--------------|--------------------------------------|--------------|--------------------|
| 2025-01-17 | 1245         | gmail.com:450\|linkedin.com:320\|... | 14           | 203                |

**Structure de sortie**:
```
s3a://datalake/gold/daily_summary/
├── date=2025-01-17/
│   └── part-00000.parquet
├── date=2025-01-18/
│   └── part-00000.parquet
└── date=2025-01-19/
    └── part-00000.parquet
```

**Avantages du partitionnement par date**:
- ✅ Pas de duplications (mode `overwrite` par date)
- ✅ Historique préservé (chaque date dans un dossier séparé)
- ✅ Requêtes rapides (partition pruning)

---

### 3. **gold_to_postgres.py** (Spark Job)
**Emplacement**: `data_pipeline/spark/jobs/gold_to_postgres.py`

**Rôle**: Exporter les statistiques Gold vers PostgreSQL

**Fonctionnalités**:
- Lit les données agrégées depuis `s3a://datalake/gold/daily_summary/`
- Exporte vers la table PostgreSQL `email_daily_summary`
- Mode `overwrite` pour garder uniquement les données les plus récentes

**Configuration PostgreSQL**:
```python
db_url = "jdbc:postgresql://postgres:5432/airflow"
db_properties = {
    "user": "airflow",
    "password": "airflow",
    "driver": "org.postgresql.Driver"
}
```

---

### 4. **email_pipeline_dag.py** (Airflow DAG - MODIFIÉ)
**Emplacement**: `data_pipeline/airflow/dags/email_pipeline_dag.py`

**Modifications**:
- ✅ Décommenté les tâches `quality_gate`, `aggregate_insights`, `export_postgres`
- ✅ Mis à jour le pipeline flow complet
- ✅ Simplifié la logique de quality gate (exit 1 si fail au lieu de skip silencieux)

**Nouveau Pipeline Flow**:
```
extract_emails 
    ↓
validate_emails 
    ↓
enrich_stream (Bronze + Silver)
    ↓
quality_gate (≥ 95%)
    ↓
aggregate_insights (Gold)
    ↓
export_postgres
```

**Détails des tâches**:

#### Task 4: `quality_assurance_gate`
```bash
/opt/spark/bin/spark-submit --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    --name QualityAssuranceGate \
    /opt/spark/jobs/data_quality_check.py
```
- **Timeout**: 20 minutes
- **Retries**: 0 (fail fast si qualité insuffisante)
- **Trigger Rule**: `all_success` (par défaut)

#### Task 5: `daily_insights_aggregation`
```bash
/opt/spark/bin/spark-submit --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    --name DailyInsightsAggregation \
    /opt/spark/jobs/daily_aggregation.py
```
- **Timeout**: 20 minutes
- **Dépendance**: S'exécute seulement si `quality_gate` réussit

#### Task 6: `export_to_postgres`
```bash
/opt/spark/bin/spark-submit --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4,org.postgresql:postgresql:42.7.1 \
    --name GoldToPostgres \
    /opt/spark/jobs/gold_to_postgres.py
```
- **Timeout**: 15 minutes
- **Dépendance**: S'exécute seulement si `aggregate_insights` réussit

---

## 🚀 Comment Tester

### 1. Vérifier que le DAG est chargé
```bash
# Se connecter au conteneur Airflow
docker exec -it airflow-scheduler bash

# Vérifier que le DAG est sans erreurs
airflow dags list | grep email_intelligence_pipeline

# Vérifier les tâches du DAG
airflow tasks list email_intelligence_pipeline
```

**Sortie attendue**:
```
gmail_extraction
email_validation
enrich_bronze_silver
quality_assurance_gate
daily_insights_aggregation
export_to_postgres
```

### 2. Déclencher le pipeline manuellement
```bash
# Déclencher le DAG avec une configuration spécifique
curl -X POST "http://localhost:8080/api/v1/dags/email_intelligence_pipeline/dagRuns" \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "conf": {
      "email": "votre.email@gmail.com",
      "refrechtoken": "votre_refresh_token"
    }
  }'
```

### 3. Tester individuellement chaque Spark job

#### Quality Check
```bash
docker exec -it spark-master bash
/opt/spark/bin/spark-submit \
    --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    /opt/spark/jobs/data_quality_check.py
```

**Résultat attendu**:
```
🚀 Starting Data Quality Check
📋 Required fields: id, from, to, subject, body, date, sender_domain
📈 Quality threshold: 95%

🔍 CHECK 1: COMPLETENESS
  Score: 98.50%

🔍 CHECK 2: FRESHNESS
  Score: 100.00%

🔍 CHECK 3: ACCURACY
  Score: 97.20%

📈 OVERALL QUALITY SCORE: 98.40%
✅ PASS: Quality 98.40% >= 95%
```

#### Daily Aggregation
```bash
/opt/spark/bin/spark-submit \
    --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    /opt/spark/jobs/daily_aggregation.py
```

**Résultat attendu**:
```
🚀 DAILY AGGREGATION JOB STARTED

📌 Step 1: Reading Bronze layer from MinIO...
✅ Bronze layer loaded successfully: 1245 records

📌 Step 2: Creating daily aggregations...
   • Calculating total emails per day...
   • Calculating top 10 sender domains...
   • Calculating busiest hour...
✅ Daily aggregations created successfully: 1 records

📌 Step 3: Writing aggregations to Gold layer...
✅ Gold layer written successfully!
   └─ Location: s3a://datalake/gold/daily_summary/date=2025-01-17/

✅ DAILY AGGREGATION JOB COMPLETED SUCCESSFULLY
```

#### Export PostgreSQL
```bash
/opt/spark/bin/spark-submit \
    --master local[2] \
    --packages org.apache.hadoop:hadoop-aws:3.3.4,org.postgresql:postgresql:42.7.1 \
    /opt/spark/jobs/gold_to_postgres.py
```

### 4. Vérifier les données dans MinIO

```bash
# Se connecter au conteneur MinIO
docker exec -it minio mc ls minio/datalake/gold/daily_summary/

# Lister les partitions par date
mc ls minio/datalake/gold/daily_summary/ --recursive
```

**Structure attendue**:
```
[2025-01-17 10:30:00] date=2025-01-17/
[2025-01-18 10:30:00] date=2025-01-18/
[2025-01-19 10:30:00] date=2025-01-19/
```

### 5. Vérifier les données dans PostgreSQL

```bash
# Se connecter à PostgreSQL
docker exec -it postgres psql -U airflow -d airflow

# Voir le schéma de la table
\d email_daily_summary

# Voir les données
SELECT * FROM email_daily_summary ORDER BY date DESC LIMIT 5;
```

**Résultat attendu**:
```
    date    | total_emails |          domain_counts          | busiest_hour | busiest_hour_count
------------+--------------+---------------------------------+--------------+--------------------
 2025-01-19 |         1532 | gmail.com:580|linkedin.com:420  |           14 |                245
 2025-01-18 |         1245 | gmail.com:450|outlook.com:320   |           10 |                203
 2025-01-17 |         1103 | gmail.com:400|yahoo.com:290     |           15 |                178
```

---

## 📊 Métriques de Qualité

### Seuils de qualité configurés

| Métrique      | Pondération | Seuil Minimum | Description                        |
|---------------|-------------|---------------|------------------------------------|
| Completeness  | 50%         | N/A           | Tous les champs requis présents    |
| Freshness     | 30%         | < 24h         | Données récentes                   |
| Accuracy      | 20%         | N/A           | Formats valides                    |
| **Overall**   | **100%**    | **≥ 95%**     | **Score global pour passer gate**  |

### Que se passe-t-il si qualité < 95% ?

1. ❌ Task `quality_assurance_gate` **FAIL** (exit code 1)
2. ⛔ Tasks `daily_insights_aggregation` et `export_to_postgres` sont **SKIPPED**
3. 🔔 Airflow envoie une notification d'échec
4. 📊 Rapport JSON disponible dans `/tmp/quality_report.json` pour diagnostic

**Action corrective**:
- Identifier les problèmes de qualité dans le rapport
- Corriger les données source (emails manquants, formats invalides, etc.)
- Relancer le DAG

---

## 🔍 Monitoring & Debugging

### Logs Airflow
```bash
# Voir les logs d'une tâche spécifique
docker exec -it airflow-scheduler bash
cat /opt/airflow/logs/dag_id=email_intelligence_pipeline/run_id=*/task_id=quality_assurance_gate/attempt=1.log
```

### Logs Spark
```bash
# Quality Check logs
docker exec -it spark-master cat /tmp/quality_report.json

# Daily Aggregation logs
docker exec -it spark-master cat /opt/spark/work-dir/daily_aggregation.log
```

### MinIO Console
Accéder via: http://localhost:9001
- **Username**: minioadmin
- **Password**: minioadmin123

---

## 📈 Améliorations Futures

1. **Alertes Slack/Email** si qualité < 95%
2. **Visualisation Grafana** des métriques de qualité
3. **Anomaly Detection** sur les agrégations (détection de pics anormaux)
4. **Retention Policy** sur les données Bronze/Silver (supprimer après 30 jours)
5. **Schema Evolution** avec Delta Lake pour versioning

---

## ✅ Checklist de Déploiement

- [x] Fichiers Spark jobs créés (`data_quality_check.py`, `daily_aggregation.py`, `gold_to_postgres.py`)
- [x] DAG Airflow mis à jour avec les nouvelles tâches
- [x] Pipeline flow configuré: Extract → Validate → Enrich → Quality → Aggregate → Export
- [x] Quality gate avec seuil 95%
- [x] Partitionnement Gold layer par date
- [ ] Tests unitaires pour les Spark jobs
- [ ] Documentation utilisateur
- [ ] Monitoring & alerting configuré

---

## 🎯 Résumé

Cette implémentation ajoute:
1. ✅ **Validation de qualité** automatique avec gate à 95%
2. ✅ **Agrégations quotidiennes** (Gold layer) avec statistiques business
3. ✅ **Export PostgreSQL** pour visualisation/reporting
4. ✅ **Pipeline complet** de bout en bout avec orchestration Airflow

Le système garantit maintenant que seules les données de **haute qualité** sont propagées vers les couches supérieures et l'export analytique.
