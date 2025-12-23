#!/usr/bin/env python3
"""
Chat intelligent Emails avec :
- Routeur LLM-based (robuste)
- Gestion today / yesterday / last week (TIMESTAMP SAFE)
- RAG hybride SQL + pgvector
"""

from typing import List, Dict
import psycopg2
import datetime
import json
from openai import OpenAI
import re
import os

# ---------------- CONFIGURATION ----------------

PG_HOST = "localhost"
PG_PORT = "5432"
PG_DB = "projetformationdb"
PG_USER = "postgres"
PG_PASSWORD = "secret"

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
CHAT_MODEL = "gpt-4"
EMBED_MODEL = "text-embedding-3-small"

USER_EMAIL = "alae.haddad@etu.uae.ac.ma"



# ---------------- CONVERSATION CONTEXT ----------------

class ConversationContext:
    """Mémoire simple des derniers emails retournés et email sélectionné."""

    def __init__(self):
        self.last_emails: List[Dict] = []
        self.selected_email: Dict = None

    def set_emails(self, emails: List[Dict]):
        self.last_emails = emails or []
        self.selected_email = None

    def select(self, idx: int) -> Dict:
        if 1 <= idx <= len(self.last_emails):
            self.selected_email = self.last_emails[idx - 1]
            return self.selected_email
        return None

    def get_selected(self) -> Dict:
        return self.selected_email

# ---------------- DATABASE ----------------

def get_db_connection():
    return psycopg2.connect(
        host=PG_HOST,
        port=PG_PORT,
        dbname=PG_DB,
        user=PG_USER,
        password=PG_PASSWORD
    )

# ---------------- EMBEDDING ----------------

def embed_question(text: str) -> list:
    res = client.embeddings.create(
        model=EMBED_MODEL,
        input=text
    )
    return res.data[0].embedding

# ---------------- ROUTEUR LLM ----------------

def route_intent(question: str) -> Dict:
    system_prompt = """
Tu es un routeur intelligent qui analyse les questions sur les emails.
Retourne UNIQUEMENT un JSON valide.

Format strict :
{
  "intent": "TEMPORAL | SEMANTIC | HYBRID | SPECIFIC_DATE | IMPORTANT",
  "period": "today | yesterday | last_week | null",
  "specific_date": "YYYY-MM-DD | null"
}

Règles :
- "Good morning" / "today" / "yesterday" => TEMPORAL avec period approprié
- "emails du 17 décembre" / "le 19 décembre" => SPECIFIC_DATE avec specific_date: "2025-12-17"
- "emails importants" / "important emails" / "quels sont mes emails importants" => IMPORTANT
- Question vague sur contenu => SEMANTIC
- Date + sujet => HYBRID

Exemples :
- "emails reçus le 17 décembre" => {"intent": "SPECIFIC_DATE", "period": null, "specific_date": "2025-12-17"}
- "today" => {"intent": "TEMPORAL", "period": "today", "specific_date": null}
- "emails importants" => {"intent": "IMPORTANT", "period": null, "specific_date": null}
- "emails about invoices last week" => {"intent": "HYBRID", "period": "last_week", "specific_date": null}

IMPORTANT : Pour les dates en français, utilise l'année 2025 par défaut.
"""

    try:
        res = client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": question}
            ],
            temperature=0  # ✅ Pour plus de cohérence
        )

        data = json.loads(res.choices[0].message.content)
        if "intent" not in data:
            raise ValueError("Missing intent field")

        return data

    except Exception as e:
        print(f"⚠️ Router error: {e}")
        # Fallback sûr
        return {"intent": "SEMANTIC", "period": None, "specific_date": None}

# ---------------- DATE RANGE (CORRIGÉ) ----------------

def fetch_emails_by_specific_date(user_id: str, date_str: str) -> List[Dict]:
    """
    Récupère les emails d'une date spécifique au format YYYY-MM-DD
    Exemple: "2025-12-17"
    """
    try:
        # Parser la date
        target_date = datetime.datetime.strptime(date_str, "%Y-%m-%d").date()
        
        # Créer le range pour toute la journée
        start = datetime.datetime.combine(target_date, datetime.time.min)
        end = datetime.datetime.combine(target_date, datetime.time.max)
        
        conn = get_db_connection()
        cur = conn.cursor()

        query = """
            SELECT email_id, subject, body, sender, receiver, date,
                   is_important, sender_domain
            FROM email_embeddings
            WHERE receiver = %s
              AND date >= %s
              AND date <= %s
            ORDER BY date DESC
        """

        cur.execute(query, (user_id, start, end))
        rows = cur.fetchall()

        cur.close()
        conn.close()

        return [
            {
                "id": r[0],
                "subject": r[1],
                "body": r[2],
                "sender": r[3],
                "receiver": r[4],
                "date": r[5],
                "is_important": r[6],
                "sender_domain": r[7],
                "similarity": 1.0
            }
            for r in rows
        ]
    
    except Exception as e:
        print(f"❌ Error fetching emails for date {date_str}: {e}")
        return []

# ---------------- DATE RANGE (CORRIGÉ) ----------------

def get_date_range(period: str):
    now = datetime.datetime.now()

    if period == "today":
        start = datetime.datetime.combine(now.date(), datetime.time.min)
        end = datetime.datetime.combine(now.date(), datetime.time.max)

    elif period == "yesterday":
        d = now.date() - datetime.timedelta(days=1)
        start = datetime.datetime.combine(d, datetime.time.min)
        end = datetime.datetime.combine(d, datetime.time.max)

    elif period == "last_week":
        start = now - datetime.timedelta(days=7)
        end = now

    else:
        return None, None

    return start, end

# ---------------- SQL TEMPORAL ----------------

def fetch_emails_by_date(user_id: str, period: str) -> List[Dict]:
    start, end = get_date_range(period)
    if not start:
        return []

    conn = get_db_connection()
    cur = conn.cursor()

    query = """
        SELECT email_id, subject, body, sender, receiver, date,
               is_important, sender_domain
        FROM email_embeddings
        WHERE receiver = %s
          AND date >= %s
          AND date <= %s
        ORDER BY date DESC
    """

    cur.execute(query, (user_id, start, end))
    rows = cur.fetchall()

    cur.close()
    conn.close()

    return [
        {
            "id": r[0],
            "subject": r[1],
            "body": r[2],
            "sender": r[3],
            "receiver": r[4],
            "date": r[5],
            "is_important": r[6],
            "sender_domain": r[7],
            "similarity": 1.0
        }
        for r in rows
    ]

# ---------------- PGVECTOR ----------------

def search_similar_emails(question: str, user_id: str, top_k: int = 5) -> List[Dict]:
    embedding = embed_question(question)

    conn = get_db_connection()
    cur = conn.cursor()

    query = """
        SELECT email_id, subject, body, sender, receiver, date,
               is_important, sender_domain,
               1 - (body_embedding <-> %s::vector) AS similarity
        FROM email_embeddings
        WHERE receiver = %s
        ORDER BY body_embedding <-> %s::vector
        LIMIT %s
    """

    cur.execute(query, (embedding, user_id, embedding, top_k))
    rows = cur.fetchall()

    cur.close()
    conn.close()

    return [
        {
            "id": r[0],
            "subject": r[1],
            "body": r[2],
            "sender": r[3],
            "receiver": r[4],
            "date": r[5],
            "is_important": r[6],
            "sender_domain": r[7],
            "similarity": round(r[8], 3)
        }
        for r in rows
    ]

# ---------------- IMPORTANT EMAILS ----------------

def fetch_important_emails(user_id: str) -> List[Dict]:
    """Récupère uniquement les emails marqués comme importants (is_important=true)."""
    conn = get_db_connection()
    cur = conn.cursor()

    query = """
        SELECT email_id, subject, body, sender, receiver, date,
               is_important, sender_domain
        FROM email_embeddings
        WHERE receiver = %s
          AND is_important = true
        ORDER BY date DESC
    """

    cur.execute(query, (user_id,))
    rows = cur.fetchall()

    cur.close()
    conn.close()

    return [
        {
            "id": r[0],
            "subject": r[1],
            "body": r[2],
            "sender": r[3],
            "receiver": r[4],
            "date": r[5],
            "is_important": r[6],
            "sender_domain": r[7],
            "similarity": 1.0
        }
        for r in rows
    ]

# ---------------- CONTEXT ----------------

def format_context(emails: List[Dict]) -> str:
    if not emails:
        return "Aucun email trouvé."

    text = "Emails pertinents :\n\n"
    for i, e in enumerate(emails, 1):
        text += (
            f"--- Email {i} ---\n"
            f"De: {e['sender']}\n"
            f"Sujet: {e['subject']}\n"
            f"Date: {e['date']}\n"
            f"Important: {e['is_important']}\n"
            f"Contenu: {e['body'][:600]}...\n\n"
        )
    return text

# ---------------- FINAL LLM ----------------
def ask_openai(question: str, context: str) -> str:
    """Use OpenAI chat completions with EXPLICIT instructions."""
    
    # ✅ Prompt plus directif
    system_prompt = """Tu es Mini-Mindy, un assistant email expert.

RÈGLES ABSOLUES :
1. Tu DOIS analyser les emails fournis dans le contexte
2. Si le contexte contient "Aucun email trouvé", dis-le clairement
3. Sinon, réponds UNIQUEMENT en te basant sur les emails fournis
4. JAMAIS de réponse générique du type "I don't have access to..."
5. Format : utilise des emojis (📧 ✅ ⚡) et structure claire

CONTEXTE DES EMAILS :
{context}
"""
    
    try:
        res = client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[
                {"role": "system", "content": system_prompt.format(context=context)},
                {"role": "user", "content": question}
            ],
            max_completion_tokens=800
        )
        
        content = res.choices[0].message.content
        
        if content:
            return content.strip()
        else:
            return "❌ Pas de réponse générée."
            
    except Exception as e:
        return f"❌ Erreur OpenAI: {e}"
# ---------------- CHAT LOOP ----------------

def chat_loop(user_id: str):
    print("\n💬 Chat Emails intelligent (exit pour quitter)\n")
    conv = ConversationContext()

    while True:
        question = input("Vous : ").strip()
        if question.lower() in ["exit", "quit", "q"]:
            break

        # Détecter sélection/action locale (ex: "email 3", "draft reply to email 3")
        sel = re.search(r"email\s*(?:num(?:éro|ber)?\s*)?(\d+)", question, re.I)
        wants_reply = bool(re.search(r"draft|reply|respond|r[eé]pond", question, re.I))
        wants_summarize = bool(re.search(r"resume|summariz|r[eé]sume", question, re.I))

        # Si la requête contient une sélection explicite
        if sel:
            idx = int(sel.group(1))
            selected = conv.select(idx)
            if not selected:
                print("❌ Aucun email en mémoire pour ce numéro. Faites d'abord une recherche (ex: 'show my important emails').")
                continue

            # Si demande de réponse dans la même phrase
            if wants_reply:
                prompt = "Génère une réponse professionnelle, concise (1-3 phrases) à cet email. Inclue formule d'appel et clôture :\n\n" + (selected.get('body') or '')
                context = format_context([selected])
                answer = ask_openai(prompt, context)
                print(f"\n🤖 Assistant (Réponse proposée):\n{answer}\n")
                continue

            # Si demande de résumé
            if wants_summarize:
                prompt = "Résume cet email en 2-3 phrases :\n\n" + (selected.get('body') or '')
                context = format_context([selected])
                answer = ask_openai(prompt, context)
                print(f"\n🤖 Assistant (Résumé):\n{answer}\n")
                continue

            # Sinon afficher le mail sélectionné
            print(format_context([selected]))
            continue

        # Sinon utiliser le routeur LLM
        route = route_intent(question)
        intent = route.get("intent")
        period = route.get("period")
        specific_date = route.get("specific_date")

        print(f"🧠 Intent: {intent} | Period: {period} | Specific Date: {specific_date}")

        emails = []
        if intent == "IMPORTANT":
            emails = fetch_important_emails(user_id)
            print(f"📧 Found {len(emails)} important emails")

        elif intent == "SPECIFIC_DATE" and specific_date:
            emails = fetch_emails_by_specific_date(user_id, specific_date)
            print(f"📧 Found {len(emails)} emails for {specific_date}")

        elif intent == "TEMPORAL":
            emails = fetch_emails_by_date(user_id, period)
            print(f"📧 Found {len(emails)} emails for period '{period}'")

        elif intent == "SEMANTIC":
            emails = search_similar_emails(question, user_id)
            print(f"📧 Found {len(emails)} similar emails")

        else:  # HYBRID
            temporal_emails = []
            if period:
                temporal_emails = fetch_emails_by_date(user_id, period)
            elif specific_date:
                temporal_emails = fetch_emails_by_specific_date(user_id, specific_date)
            semantic_emails = search_similar_emails(question, user_id)
            emails = temporal_emails + semantic_emails
            print(f"📧 Found {len(emails)} emails (hybrid search)")

        # Sauvegarder pour follow-ups
        conv.set_emails(emails)

        context = format_context(emails)
        answer = ask_openai(question, context)

        print(f"\n🤖 Assistant:\n{answer}\n")
# ---------------- MAIN ----------------

def main():
    try:
        conn = get_db_connection()
        conn.close()
        print("✅ PostgreSQL OK")
    except Exception as e:
        print(f"❌ PostgreSQL ERROR: {e}")
        return

    chat_loop(USER_EMAIL)

if __name__ == "__main__":
    main()