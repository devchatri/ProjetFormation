from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
import subprocess
from airflow.models import Variable
from datetime import datetime, timedelta
import os

default_args = {
    'owner': 'data_team',                  # Qui est responsable
    'retries': 2,                          # Réessayer 2 fois si erreur
    'retry_delay': timedelta(minutes=5),   # Attendre 5 min avant réessai
}


# 📧 Chemins des scripts (dans le conteneur Airflow)
GMAIL_EXTRACTOR_PATH = '/opt/airflow/kafka/Producer/email_producer.py'
EMAIL_VALIDATOR_PATH = '/opt/airflow/kafka/Consumer/validator.py'

def run_gmail_extractor(**context):
    env = build_env_vars(**context)
    subprocess.run(
        ["python", GMAIL_EXTRACTOR_PATH],
        env={**os.environ, **env},
        check=True
    )

def build_env_vars(**context):
    conf = context['dag_run'].conf if context.get('dag_run') else {}
    env_vars = {
        'KAFKA_BOOTSTRAP_SERVERS': 'kafka:29092',
        'DB_HOST': 'projetformation_postgres',
        'DB_PORT': '5432',
        'DB_NAME': 'projetformationdb',
        'DB_USER': 'postgres',
        'DB_PASSWORD': 'secret',
        'GOOGLE_CLIENT_ID': os.getenv('GOOGLE_CLIENT_ID'),
        'GOOGLE_CLIENT_SECRET': os.getenv('GOOGLE_CLIENT_SECRET')
    }
    if conf.get('email'):
        env_vars['USER_EMAIL'] = conf['email']
    if conf.get('refrechtoken'):
        env_vars['REFRECHTOKEN'] = conf['refrechtoken']
    return env_vars

with DAG(
    dag_id='email_intelligence_pipeline',  
    default_args=default_args,
    description='🚀 Complete Email Intelligence Pipeline: Extract → Validate → Enrich → Quality Check → Aggregate',
    start_date=datetime(2025, 1, 1),
    schedule_interval='0 2 * * *',         # ⏰ Chaque jour à 2h du matin
    catchup=False,
    tags=['email', 'intelligence', 'batch-processing', 'data-quality']
) as dag:
    # 📬 Task 1: Gmail Extraction & Ingestion
    extract_emails = PythonOperator(
        task_id='gmail_extraction',
        python_callable=run_gmail_extractor,
        provide_context=True,
        execution_timeout=timedelta(minutes=10),
        retries=0
    )
    
    # ✅ Task 2: Email Validation & Filtering
    validate_emails = BashOperator(
        task_id='email_validation',                          
        bash_command=f'python {EMAIL_VALIDATOR_PATH}',
        env={'KAFKA_BOOTSTRAP_SERVERS': 'kafka:29092'},
        trigger_rule='all_done'
    )
    
    # 🔄 Task 3: Real-time Enrichment Stream (Bronze + Silver Layers)
    # Lance le streaming en arrière-plan (il continuera après cette tâche)
    enrich_stream = BashOperator(
        task_id='enrich_bronze_silver',                     
        bash_command='''
        nohup /opt/spark/bin/spark-submit \
            --master spark://spark-master:7077 \
            --deploy-mode client \
            --packages org.apache.hadoop:hadoop-aws:3.3.4,org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 \
            --name EmailEnrichmentStream \
            /opt/spark/jobs/email_processor.py > /tmp/streaming.log 2>&1 &
        
        # Attendre que le streaming soit prêt (vérifier les logs)
        sleep 15
        echo "✅ Streaming lancé en arrière-plan"
        ''',
        execution_timeout=timedelta(minutes=5),
        retries=0
    )
    
    # 🔐 Task 4: Quality Assurance Gate (≥ 95% threshold)
    # quality_gate = BashOperator(
    #     task_id='quality_assurance_gate',                    
    #     bash_command='''
    #     export REPORT_PATH="/opt/airflow/logs/quality_report.json"
    #     
    #     /opt/spark/bin/spark-submit --master local[2] --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    #         --name QualityAssuranceGate /opt/spark/jobs/data_quality_check.py "$REPORT_PATH"
    #     
    #     # Vérifier le résultat du quality check
    #     QUALITY_EXIT_CODE=$?
    #     
    #     if [ $QUALITY_EXIT_CODE -ne 0 ]; then
    #         echo "❌ Quality check FAILED (score < 95%)"
    #         echo "⛔ Downstream tasks will be SKIPPED"
    #         # Créer un fichier vide pour indiquer l'échec
    #         echo '{"overall_score": 0, "status": "FAILED"}' > "$REPORT_PATH"
    #         exit 0
    #     else
    #         echo "✅ Quality check PASSED (score >= 95%)"
    #         echo "✅ Proceeding to daily aggregation..."
    #         exit 0
    #     fi
    #     ''',
    #     execution_timeout=timedelta(minutes=20),
    #     retries=0
    # )
    
    # 📊 Task 5: Daily Insights Aggregation (Gold Layer)
    # aggregate_insights = BashOperator(
    #     task_id='daily_insights_aggregation',               
    #     bash_command='''
    #     # Vérifier si quality check a vraiment passé
    #     QUALITY_REPORT="/opt/airflow/logs/quality_report.json"
    #     
    #     if [ ! -f "$QUALITY_REPORT" ]; then
    #         echo "❌ Quality report not found at $QUALITY_REPORT!"
    #         echo "⛔ Skipping aggregation"
    #         exit 0
    #     fi
    #     
    #     # Extraire le score du rapport JSON
    #     QUALITY_SCORE=$(grep -o '"overall_score": [0-9.]*' "$QUALITY_REPORT" | grep -o '[0-9.]*' | head -1)
    #     THRESHOLD=95
    #     
    #     if [ -z "$QUALITY_SCORE" ]; then
    #         echo "❌ Could not extract quality score from report"
    #         exit 0
    #     fi
    #     
    #     if (( $(echo "$QUALITY_SCORE < $THRESHOLD" | bc -l) )); then
    #         echo "❌ Quality score ($QUALITY_SCORE%) is below threshold ($THRESHOLD%)"
    #         echo "⛔ Skipping daily aggregation"
    #         exit 0
    #     fi
    #     
    #     echo "✅ Quality score ($QUALITY_SCORE%) is above threshold - Running aggregation"
    #     /opt/spark/bin/spark-submit --master local[2] --packages org.apache.hadoop:hadoop-aws:3.3.4 \
    #         --name DailyInsightsAggregation /opt/spark/jobs/daily_aggregation.py
    #     ''',
    #     execution_timeout=timedelta(minutes=20),
    #     retries=0
    # )
    
    # 🗄️ Task 6: Export to PostgreSQL
    # export_postgres = BashOperator(
    #     task_id='export_to_postgres',                        
    #     bash_command='/opt/spark/bin/spark-submit --master local[2] --packages org.apache.hadoop:hadoop-aws:3.3.4,org.postgresql:postgresql:42.7.1 --name GoldToPostgres /opt/spark/jobs/gold_to_postgres.py',
    #     execution_timeout=timedelta(minutes=15),
    #     retries=0
    # )
    
    # 🔗 Define Pipeline Flow

   
    extract_emails >> validate_emails >> enrich_stream
    # extract_emails >> validate_emails >> enrich_stream >> quality_gate >> aggregate_insights >> export_postgres