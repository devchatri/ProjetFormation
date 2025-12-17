import json
import re
import logging
import os
import html
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict
import base64

from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from kafka import KafkaProducer

# =========================
# LOGGING
# =========================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# =========================
# CONFIG
# =========================
SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
KAFKA_TOPIC = "emails-topic"



GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET")


USER_EMAIL = os.getenv("USER_EMAIL")
REFRECHTOKEN = os.getenv("REFRECHTOKEN")

CHECKPOINT_DIR = "/opt/airflow"
os.makedirs(CHECKPOINT_DIR, exist_ok=True)

# =========================
# UTILS
# =========================
def authenticate_gmail(refresh_token: str):
    creds = Credentials(
        token=None,
        refresh_token=refresh_token,
        token_uri="https://oauth2.googleapis.com/token",
        client_id=GOOGLE_CLIENT_ID,
        client_secret=GOOGLE_CLIENT_SECRET,
        scopes=SCOPES,
    )
    return build("gmail", "v1", credentials=creds)


def extract_body(payload: dict) -> str:
    try:
        if "parts" in payload:
            for part in payload["parts"]:
                if part["mimeType"] == "text/plain":
                    data = part["body"].get("data")
                    if data:
                        return base64.urlsafe_b64decode(data).decode("utf-8")
        data = payload.get("body", {}).get("data")
        if data:
            return base64.urlsafe_b64decode(data).decode("utf-8")
    except Exception:
        pass
    return ""


def load_checkpoint(email: str) -> Dict:
    path = f"{CHECKPOINT_DIR}/checkpoint_{email}.json"
    if os.path.exists(path):
        with open(path, "r") as f:
            checkpoint = json.load(f)
            # Ajoute les champs si absents (rétrocompatibilité)
            if "processed_ids" not in checkpoint:
                checkpoint["processed_ids"] = []
            if "last_run" not in checkpoint:
                checkpoint["last_run"] = None
            if "total_processed" not in checkpoint:
                checkpoint["total_processed"] = len(checkpoint["processed_ids"])
            return checkpoint
    return {"processed_ids": [], "last_run": None, "total_processed": 0}


def save_checkpoint(email: str, checkpoint: Dict):
    path = f"{CHECKPOINT_DIR}/checkpoint_{email}.json"
    # Met à jour les champs last_run et total_processed
    checkpoint["last_run"] = datetime.utcnow().isoformat()
    checkpoint["total_processed"] = len(checkpoint["processed_ids"])
    with open(path, "w") as f:
        json.dump(checkpoint, f, indent=2)


# =========================
# MAIN
# =========================
def main():
    logger.info("🚀 Gmail → Kafka Producer START")


    if not GOOGLE_CLIENT_ID or not GOOGLE_CLIENT_SECRET:
        raise RuntimeError("❌ GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET manquants")
    if not USER_EMAIL or not REFRECHTOKEN:
        raise RuntimeError("❌ USER_EMAIL ou REFRECHTOKEN manquant dans l'environnement")

    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    )

    # Nettoie le nom pour le fichier checkpoint (remplace tout caractère non alphanumérique par _)
    safe_email = re.sub(r'[^a-zA-Z0-9._-]', '_', USER_EMAIL)
    print(f"\n==============================\nTraitement de l'utilisateur : {USER_EMAIL}\n==============================")
    logger.info(f"📧 Traitement : {USER_EMAIL}")

    checkpoint = load_checkpoint(safe_email)
    service = authenticate_gmail(REFRECHTOKEN)

    # Si c'est un nouvel utilisateur (aucun checkpoint), on récupère les 50 derniers emails sans filtre de date
    if not checkpoint["processed_ids"]:
        results = service.users().messages().list(
            userId="me", maxResults=50
        ).execute()
    else:
        since = int((datetime.utcnow() - timedelta(hours=24)).timestamp())
        results = service.users().messages().list(
            userId="me", q=f"after:{since}", maxResults=50
        ).execute()

    messages = results.get("messages", [])

    for msg in messages:
        msg_id = msg["id"]
        print(f"Email extrait pour {USER_EMAIL} : message_id={msg_id}")
        if msg_id in checkpoint["processed_ids"]:
            continue

        msg_data = service.users().messages().get(
            userId="me", id=msg_id, format="full"
        ).execute()

        headers = msg_data["payload"].get("headers", [])
        email_data = {
            "message_id": msg_id,
            "user_email": USER_EMAIL,
            "subject": "",
            "sender": "",
            "recipient": "",
            "timestamp": datetime.utcnow().isoformat(),
            "body": "",
        }

        for h in headers:
            name = h["name"].lower()
            value = h["value"]
            if name == "subject":
                email_data["subject"] = value
            elif name == "from":
                email_data["sender"] = value
            elif name == "to":
                email_data["recipient"] = value
            elif name == "date":
                # Parser la date de l'email et la stocker en UTC
                try:
                    from email.utils import parsedate_to_datetime
                    dt = parsedate_to_datetime(value)
                    # Si l'email a une timezone, la convertir en UTC
                    # Sinon, on assume que l'heure est déjà en UTC
                    if dt.tzinfo is None:
                        dt = dt.replace(tzinfo=timezone.utc)
                    else:
                        dt = dt.astimezone(timezone.utc)
                    # Formater en ISO 8601 sans millisecondes pour simplifier le parsing Spark
                    email_data["timestamp"] = dt.strftime("%Y-%m-%dT%H:%M:%S") + "Z"
                except Exception as e:
                    logger.warning(f"Erreur lors du parsing de la date '{value}': {e}")
                    dt_now = datetime.now(timezone.utc)
                    email_data["timestamp"] = dt_now.strftime("%Y-%m-%dT%H:%M:%S") + "Z"

        email_data["body"] = html.unescape(
            extract_body(msg_data["payload"]).replace("\n", " ")
        )

        producer.send(KAFKA_TOPIC, email_data)
        checkpoint["processed_ids"].append(msg_id)

    save_checkpoint(safe_email, checkpoint)
    logger.info(f"✅ {len(checkpoint['processed_ids'])} emails traités pour {USER_EMAIL}")

    producer.flush()
    producer.close()
    logger.info("🏁 Producer terminé")


if __name__ == "__main__":
    main()

