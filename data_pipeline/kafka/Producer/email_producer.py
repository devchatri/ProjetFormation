
import json
import logging
import os
import html
from datetime import datetime, timedelta
from typing import Optional, Dict, List
import base64
from threading import Thread

from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from kafka import KafkaProducer, KafkaConsumer
from google.auth.transport.requests import Request

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Gmail API scopes
SCOPES = ['https://www.googleapis.com/auth/gmail.readonly']

if os.path.exists('/opt/airflow/credentials.json'):
    CREDENTIALS_FILE = "/opt/airflow/credentials.json"
    TOKEN_FILE = "/opt/airflow/token.json"
else:
    CREDENTIALS_FILE = "credentials.json"
    TOKEN_FILE = "token.json"            

# Kafka config
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
KAFKA_TOPIC = 'emails-topic'

# Checkpoint config (JSON file for deduplication)
CHECKPOINT_DIR = '/opt/airflow' if os.path.exists('/opt/airflow/') else '.'
CHECKPOINT_FILE = os.path.join(CHECKPOINT_DIR, 'email_checkpoint.json')


def load_checkpoint() -> Dict:
    """Charger le checkpoint depuis le fichier JSON"""
    if os.path.exists(CHECKPOINT_FILE):
        try:
            with open(CHECKPOINT_FILE, 'r') as f:
                checkpoint = json.load(f)
                logger.info(f"✓ Checkpoint loaded: {len(checkpoint.get('processed_ids', []))} messages tracked")
                return checkpoint
        except Exception as e:
            logger.warning(f"⚠️ Error loading checkpoint: {e}. Starting fresh.")
    
    return {
        'processed_ids': [],
        'last_run': None,
        'total_processed': 0
    }


def save_checkpoint(checkpoint: Dict):
    """Sauvegarder le checkpoint dans le fichier JSON"""
    try:
        checkpoint['last_run'] = datetime.utcnow().isoformat()
        with open(CHECKPOINT_FILE, 'w') as f:
            json.dump(checkpoint, f, indent=2)
        logger.info(f"✓ Checkpoint saved: {checkpoint['total_processed']} total messages processed")
    except Exception as e:
        logger.error(f"❌ Error saving checkpoint: {e}")


def is_message_processed(checkpoint: Dict, message_id: str) -> bool:
    """Vérifier si un message_id a déjà été traité"""
    return message_id in checkpoint['processed_ids']


def mark_message_processed(checkpoint: Dict, message_id: str):
    """Marquer un message comme traité (ajouter à checkpoint)"""
    if message_id not in checkpoint['processed_ids']:
        checkpoint['processed_ids'].append(message_id)
        checkpoint['total_processed'] = len(checkpoint['processed_ids'])

def authenticate_gmail():
    """Authenticate with Gmail API using OAuth2"""
    creds = None

    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                CREDENTIALS_FILE, SCOPES
            )
            creds = flow.run_local_server(port=0)

        with open(TOKEN_FILE, 'w') as token:
            token.write(creds.to_json())

    return build('gmail', 'v1', credentials=creds)


def extract_email_body(payload: dict) -> str:
    """Extract email body from Gmail payload"""
    try:
        if 'parts' in payload:
            for part in payload['parts']:
                if part['mimeType'] == 'text/plain':
                    if 'data' in part['body']:
                        data = part['body']['data']
                        return base64.urlsafe_b64decode(data).decode('utf-8')
        
        if 'body' in payload and 'data' in payload['body']:
            data = payload['body']['data']
            return base64.urlsafe_b64decode(data).decode('utf-8')
        
        return ""
    except Exception as e:
        logger.warning(f"Error extracting body: {e}")
        return ""


def get_email_details(service, msg_id: str) -> Optional[dict]:
    """Fetch and parse email details from Gmail"""
    try:
        message = service.users().messages().get(
            userId='me', id=msg_id, format='full'
        ).execute()

        headers = message['payload'].get('headers', [])
        
        email_data = {
            'message_id': msg_id,
            'subject': 'Unknown',
            'sender': 'Unknown',
            'recipient': 'Unknown',
            'timestamp': datetime.utcnow().isoformat(),
            'body': '',
        }

        for h in headers:
            name = h.get('name', '').lower()
            value = h.get('value', '')
            
            if name == 'subject':
                email_data['subject'] = value
            elif name == 'from':
                email_data['sender'] = value
            elif name == 'to':
                email_data['recipient'] = value
            elif name == 'date':
                try:
                    from email.utils import parsedate_to_datetime
                    email_data['timestamp'] = parsedate_to_datetime(value).isoformat()
                except:
                    pass

        email_data['body'] = extract_email_body(message['payload'])
        return email_data

    except Exception as e:
        logger.error(f"Error fetching email {msg_id}: {e}")
        return None


def fetch_recent_emails(service, checkpoint: Dict, hours: int = 24) -> list:
    """Fetch emails from last N hours avec déduplication via checkpoint"""
    try:
        time_threshold = datetime.utcnow() - timedelta(hours=hours)
        query = f'after:{int(time_threshold.timestamp())}'

        results = service.users().messages().list(
            userId='me',
            q=query,
            maxResults=50
        ).execute()

        messages = results.get('messages', [])
        logger.info(f"Found {len(messages)} emails in last {hours} hours")

        emails = []
        duplicates_skipped = 0

        for msg in messages:
            msg_id = msg['id']
            
            # ÉTAPE 1: Vérifier si déjà traité via checkpoint
            if is_message_processed(checkpoint, msg_id):
                logger.info(f"⏭️  Skipping duplicate message: {msg_id}")
                duplicates_skipped += 1
                continue
            
            # ÉTAPE 2: Récupérer les détails
            email = get_email_details(service, msg_id)
            if email:
                emails.append(email)
                # Marquer comme traité immédiatement (avant Kafka)
                mark_message_processed(checkpoint, msg_id)

        logger.info(f"📊 Deduplication: {duplicates_skipped} duplicates skipped, {len(emails)} new emails")
        return emails

    except Exception as e:
        logger.error(f"Error fetching emails: {e}")
        return []


def send_to_kafka(producer: KafkaProducer, emails: list):
    """Send emails to Kafka topic"""
    for email in emails:
        try:
            message = json.dumps(email).encode('utf-8')
            future = producer.send(KAFKA_TOPIC, value=message)
            record_metadata = future.get(timeout=10)
            logger.info(
                f"✓ Email sent to Kafka - Offset: {record_metadata.offset} - Message: {email['message_id']}"
            )
        except Exception as e:
            logger.error(f"❌ Error sending email to Kafka: {e}")


def clean_text(text):
    """Nettoyer le texte"""
    if not text:
        return ""
    text = text.replace('\r\n', ' ').replace('\r', ' ').replace('\n', ' ')
    text = html.unescape(text)
    return text.strip()


def display_email(email, count):
    """Afficher un email de manière lisible"""
    print(f"\n{'='*100}")
    print(f"📧 EMAIL #{count}")
    print(f"{'='*100}")
    
    msg_id = email.get('message_id', 'N/A')
    subject = clean_text(email.get('subject', 'N/A'))
    sender = email.get('sender', 'N/A')
    recipient = email.get('recipient', 'N/A')
    timestamp = email.get('timestamp', 'N/A')
    body = clean_text(email.get('body', ''))
    
    print(f"🆔 ID Message      : {msg_id}")
    print(f"📬 Objet           : {subject[:80]}{'...' if len(subject) > 80 else ''}")
    print(f"📤 Expéditeur      : {sender}")
    print(f"📥 Destinataire    : {recipient}")
    print(f"🕐 Timestamp       : {timestamp}")
    print(f"📝 Contenu         : {len(body)} caractères")
    print(f"\n--- APERÇU DU CONTENU ({min(300, len(body))} premiers caractères) ---")
    print(f"{body[:300]}{'...' if len(body) > 300 else ''}")
    print(f"{'='*100}\n")


def consumer_thread():
    """Afficher les emails en temps réel depuis Kafka"""
    logger.info("🎬 Démarrage du visualiseur temps réel...")
    
    consumer = KafkaConsumer(
        KAFKA_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        value_deserializer=lambda x: json.loads(x.decode('utf-8')),
        auto_offset_reset='earliest',
        group_id='unified-viewer'
    )

    count = 0
    for message in consumer:
        count += 1
        try:
            email = message.value
            display_email(email, count)
        except Exception as e:
            logger.error(f"Error displaying email: {e}")


def main():
    """Main function avec déduplication par checkpoint JSON"""
    logger.info("🚀 Starting Producer with JSON Checkpoint Deduplication...")
    logger.info(f"📁 Checkpoint file: {CHECKPOINT_FILE}")

    try:
        # ÉTAPE 1: Charger le checkpoint
        logger.info("📂 Loading checkpoint...")
        checkpoint = load_checkpoint()

        # ÉTAPE 2: Authenticate Gmail
        logger.info("🔐 Authenticating with Gmail API...")
        service = authenticate_gmail()

        # ÉTAPE 3: Initialize Kafka producer
        logger.info("📨 Connecting to Kafka...")
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda x: x,
        )

        # ÉTAPE 4: Start consumer thread only in interactive/local runs (avoid blocking Airflow)
        start_viewer = os.getenv('AIRFLOW_CTX_DAG_ID') is None
        consumer_thread_instance = None
        if start_viewer:
            consumer_thread_instance = Thread(target=consumer_thread, daemon=True)
            consumer_thread_instance.start()
            logger.info("✓ Consumer thread started")

        # ÉTAPE 5: Fetch and send emails (avec déduplication)
        logger.info("📧 Fetching recent emails with deduplication...")
        emails = fetch_recent_emails(service, checkpoint, hours=24)
        
        if emails:
            send_to_kafka(producer, emails)
            logger.info(f"✅ Successfully sent {len(emails)} NEW emails to Kafka")
            
            # ÉTAPE 6: Sauvegarder le checkpoint APRÈS succès Kafka
            logger.info("💾 Saving checkpoint...")
            save_checkpoint(checkpoint)
        else:
            logger.info("ℹ️ No new emails to process (all duplicates skipped)")

        producer.flush()
        
        # In Airflow, do not block; if viewer was started locally, wait briefly
        if consumer_thread_instance:
            consumer_thread_instance.join(timeout=5)

    except Exception as e:
        logger.error(f"❌ Error: {e}", exc_info=True)
        raise
    finally:
        if 'producer' in locals():
            producer.close()
            logger.info("📤 Kafka producer closed")


if __name__ == '__main__':
    main()
