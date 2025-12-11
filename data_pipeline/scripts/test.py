import psycopg2
import pandas as pd
import ast  # pour convertir les vecteurs stockés en liste si nécessaire

# PostgreSQL config
PG_HOST = "localhost"
PG_PORT = 5433
PG_DB = "airflow"
PG_USER = "airflow"
PG_PASSWORD = "airflow"

# email_id que tu veux récupérer
EMAIL_ID = "19b0714287a08c9b"

# Connexion à PostgreSQL
conn = psycopg2.connect(
    host=PG_HOST, port=PG_PORT, dbname=PG_DB,
    user=PG_USER, password=PG_PASSWORD
)
cursor = conn.cursor()

# Récupération des données
query = """
SELECT email_id, sender, receiver, date, sender_domain, is_important,
       subject_embedding, body_embedding
FROM email_embeddings
WHERE email_id = %s
"""
cursor.execute(query, (EMAIL_ID,))
rows = cursor.fetchall()

# Récupération des noms de colonnes
columns = [desc[0] for desc in cursor.description]

# Création du DataFrame
df = pd.DataFrame(rows, columns=columns)

# Si les embeddings sont stockés comme texte, on peut les convertir en liste Python
for col in ["subject_embedding", "body_embedding"]:
    if df[col].dtype == object:
        df[col] = df[col].apply(lambda x: ast.literal_eval(str(x)))

# Affichage complet des vecteurs
pd.set_option('display.max_columns', None)
pd.set_option('display.max_rows', None)
pd.set_option('display.max_colwidth', None)
print(df)

# Sauvegarde dans un fichier CSV
df.to_csv(f"email_{EMAIL_ID}_vectors.csv", index=False)
print(f"\n✅ Données enregistrées dans email_{EMAIL_ID}_vectors.csv")

cursor.close()
conn.close()
