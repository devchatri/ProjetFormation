"""
MinIO → OpenAI Embeddings → PostgreSQL pgvector
Version Option B (Entreprise recommandé)
- subject_embedding + body_embedding
- autres champs en texte/SQL classique
Optimisé pour ne traiter que les emails non insérés.
"""

import os
import psycopg2
from minio import Minio
import pandas as pd
import pyarrow.parquet as pq
import io
from dotenv import load_dotenv
from openai import OpenAI
import tiktoken
import numpy as np
import re

# --- Tokenizer ---
tokenizer = tiktoken.get_encoding("cl100k_base")

# --- Chargement clé OpenAI ---
load_dotenv()
OPENAI_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_KEY:
    raise Exception("⚠ Met la clé OPENAI_API_KEY dans .env")
client = OpenAI(api_key=OPENAI_KEY)

# --- MinIO config ---
MINIO_ENDPOINT = "localhost:9000"
MINIO_ACCESS_KEY = "minioadmin"
MINIO_SECRET_KEY = "minioadmin123"
MINIO_BUCKET = "datalake"

# --- PostgreSQL config ---
PG_HOST = "localhost"
PG_PORT = 5433
PG_DB = "airflow"
PG_USER = "airflow"
PG_PASSWORD = "airflow"

# --- Connexion MinIO ---
minio_client = Minio(
    MINIO_ENDPOINT,
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=False
)

# --- Lecture Parquet depuis MinIO ---
objects = minio_client.list_objects(MINIO_BUCKET, prefix="bronze/emails/", recursive=True)
files = [o.object_name for o in objects if o.object_name.endswith(".parquet")]
if not files:
    raise Exception("❌ Aucun parquet trouvé")

dfs = []
for f in files:
    data = minio_client.get_object(MINIO_BUCKET, f).read()
    df = pq.read_table(io.BytesIO(data)).to_pandas()
    dfs.append(df)

emails = pd.concat(dfs, ignore_index=True)
print(f"⚡ {len(emails)} emails chargés depuis MinIO")

# --- Connexion PostgreSQL ---
conn = psycopg2.connect(
    host=PG_HOST, port=PG_PORT, dbname=PG_DB,
    user=PG_USER, password=PG_PASSWORD
)
cursor = conn.cursor()

# --- Fonction embeddings OpenAI ---
def embed(text):
    if not text:
        return [0.0] * 1536  # taille embedding text-embedding-3-small
    tokens = tokenizer.encode(text)
    chunks = [tokenizer.decode(tokens[i:i+2000]) for i in range(0, len(tokens), 2000)]
    vectors = []
    for c in chunks:
        e = client.embeddings.create(model="text-embedding-3-small", input=c)
        vectors.append(e.data[0].embedding)
    return np.mean(vectors, axis=0).tolist()

# --- Extraction email propre depuis "Nom" <email> ---
def extract_email(s):
    if not s:
        return ""
    match = re.search(r'<([^<>]+)>', s)
    return match.group(1) if match else s.strip()

# --- Récupération emails déjà existants ---
cursor.execute("SELECT email_id FROM email_embeddings;")
existing_ids = set(row[0] for row in cursor.fetchall())

# --- Filtrer emails à traiter ---
new_emails = emails[~emails["id"].isin(existing_ids)]
print(f"⚡ {len(new_emails)} emails à traiter sur {len(emails)} totaux")

# --- Boucle d’insertion ---
for _, email in new_emails.iterrows():
    email_id = email.get("id")
    sender = extract_email(email.get("from", ""))
    receiver = extract_email(email.get("to", ""))
    subject = email.get("subject", "")
    body = email.get("body", "")
    date = email.get("date")
    sender_domain = email.get("sender_domain", "")
    is_important = email.get("is_important", False)

    # Embeddings
    subject_emb = embed(subject)
    body_emb = embed(body)

    # INSERT dans PostgreSQL
    cursor.execute("""
        INSERT INTO email_embeddings
        (email_id, sender, receiver, date, sender_domain, is_important,
         subject, body, subject_embedding, body_embedding)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::vector, %s::vector);
    """, (email_id, sender, receiver, date, sender_domain, is_important,
          subject, body, subject_emb, body_emb))

    print(f"📩 Insert OK → {email_id}")

# --- Commit & fermeture ---
conn.commit()
cursor.close()
conn.close()
print("\n🎉 Terminé avec embeddings complets !")
