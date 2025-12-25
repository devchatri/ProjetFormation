#!/usr/bin/env python3
"""
Chat intelligent Emails avec :
- Routeur LLM-based (robuste)
- Gestion today / yesterday / last week (TIMESTAMP SAFE)
- RAG hybride SQL + pgvector
"""

from typing import List, Dict, Tuple
import psycopg2
import datetime
import json
from openai import OpenAI
import re
from collections import Counter, defaultdict
import os

# ---------------- CONFIGURATION ----------------

PG_HOST = "localhost"
PG_PORT = "5432"
PG_DB = "projetformationdb"
PG_USER = "postgres"
PG_PASSWORD = "secret"

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY") # 🔐 mets ta vraie clé OpenAI ici
CHAT_MODEL = "gpt-4"
EMBED_MODEL = "text-embedding-3-small"

USER_EMAIL = "alaehaddad205@gmail.com"

client = OpenAI(api_key=OPENAI_API_KEY)

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

# ---------------- SENTIMENT ANALYSIS ----------------

def analyze_sentiment(text: str) -> Dict:
    """
    Analyse le sentiment d'un email en utilisant OpenAI.
    Retourne: {"sentiment": "positive|negative|neutral", "score": float, "emoji": str}
    """
    try:
        prompt = f"""Analyse le sentiment de cet email et retourne UNIQUEMENT un JSON valide:
{{
  "sentiment": "positive" ou "negative" ou "neutral",
  "score": score de 0 à 1,
  "emoji": emoji approprié (😊 pour positif, 😟 pour négatif, 😐 pour neutre)
}}

Email: {text[:500]}"""
        
        res = client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.3
        )
        
        return json.loads(res.choices[0].message.content)
    except Exception as e:
        print(f"⚠️ Sentiment analysis error: {e}")
        return {"sentiment": "neutral", "score": 0.5, "emoji": "😐"}

def batch_analyze_sentiments(emails: List[Dict]) -> List[Dict]:
    """
    Analyse les sentiments de plusieurs emails et les ajoute aux objets email.
    """
    for email in emails:
        sentiment_data = analyze_sentiment(email.get('body', '') or email.get('subject', ''))
        email['sentiment'] = sentiment_data['sentiment']
        email['sentiment_score'] = sentiment_data['score']
        email['sentiment_emoji'] = sentiment_data['emoji']
    return emails

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

# ---------------- EMAIL STATISTICS ----------------

def get_email_statistics(user_id: str, period: str = "today") -> Dict:
    """
    Génère des statistiques complètes sur les emails:
    - Nombre total d'emails
    - Emails urgents/importants
    - Top senders
    - Analyse de sentiment globale
    - Actions requises
    """
    start, end = get_date_range(period)
    if not start:
        return {}
    
    conn = get_db_connection()
    cur = conn.cursor()
    
    # Requête pour récupérer tous les emails de la période
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
    
    # Conversion en liste de dictionnaires
    emails = [
        {
            "id": r[0],
            "subject": r[1],
            "body": r[2],
            "sender": r[3],
            "receiver": r[4],
            "date": r[5],
            "is_important": r[6],
            "sender_domain": r[7]
        }
        for r in rows
    ]
    
    # Calcul des statistiques
    total_emails = len(emails)
    urgent_emails = sum(1 for e in emails if e['is_important'])
    
    # Top senders (comptage des emails par expéditeur)
    sender_counter = Counter(e['sender'] for e in emails)
    top_senders = sender_counter.most_common(3)
    
    # Analyse de sentiment sur un échantillon
    sample_size = min(20, len(emails))  # Analyser max 20 emails pour performance
    sampled_emails = emails[:sample_size]
    sentiments = {'positive': 0, 'negative': 0, 'neutral': 0}
    
    for email in sampled_emails:
        sentiment = analyze_sentiment(email['body'] or email['subject'])
        sentiments[sentiment['sentiment']] += 1
    
    # Détection des actions requises (emails importants récents)
    action_required = [
        {
            'sender': e['sender'],
            'subject': e['subject'],
            'reason': 'Marked as important',
            'date': e['date']
        }
        for e in emails[:5] if e['is_important']
    ]
    
    return {
        'total_emails': total_emails,
        'urgent_emails': urgent_emails,
        'top_senders': top_senders,
        'sentiments': sentiments,
        'action_required': action_required,
        'period': period
    }

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

# ---------------- MORNING BRIEFING ----------------

def generate_morning_briefing(user_id: str) -> str:
    """
    Génère un briefing matinal complet avec:
    - Statistiques des emails
    - Analyse de sentiment
    - Actions requises
    - Suggestions
    """
    stats = get_email_statistics(user_id, "today")
    
    if not stats or stats['total_emails'] == 0:
        return "☀️ Good morning! No new emails today yet."
    
    # Construction du briefing formaté
    briefing = f"""☀️ Good morning! Here's your briefing for {datetime.datetime.now().strftime('%B %d, %Y')}:

📧 EMAIL INSIGHTS (last 24 hours):
• {stats['total_emails']} new emails received
• {stats['urgent_emails']} require urgent response (flagged as important)
"""
    
    # Top senders
    if stats['top_senders']:
        top_senders_str = ", ".join([f"{sender} ({count})" for sender, count in stats['top_senders']])
        briefing += f"• Top senders: {top_senders_str}\n"
    
    # Sentiment analysis
    sentiments = stats['sentiments']
    briefing += f"• Sentiment analysis: {sentiments['positive']} positive, {sentiments['negative']} negative, {sentiments['neutral']} neutral\n"
    
    # Actions requises
    if stats['action_required']:
        briefing += "\n⚡ ACTION REQUIRED:\n"
        for action in stats['action_required'][:3]:  # Top 3 actions
            briefing += f"• {action['sender']}: {action['subject'][:60]}...\n"
    
    # Suggestions intelligentes
    briefing += "\n💡 SUGGESTIONS:\n"
    if stats['urgent_emails'] > 5:
        briefing += "• You have many urgent emails. Consider prioritizing responses.\n"
    if sentiments['negative'] > 2:
        briefing += f"• {sentiments['negative']} emails show negative sentiment. Review for potential issues.\n"
    if stats['total_emails'] > 30:
        briefing += "• High email volume today. Consider batch processing similar emails.\n"
    
    return briefing

# ---------------- SMART SUGGESTIONS ----------------

def generate_smart_suggestions(emails: List[Dict]) -> List[str]:
    """
    Génère des suggestions intelligentes basées sur l'analyse des emails.
    """
    suggestions = []
    
    if not emails:
        return suggestions
    
    # Détecter les threads multiples du même expéditeur
    sender_counter = Counter(e['sender'] for e in emails)
    for sender, count in sender_counter.items():
        if count >= 3:
            suggestions.append(f"⚠️ {sender} sent {count} emails. This might be escalating.")
    
    # Détecter les emails urgents
    urgent_count = sum(1 for e in emails if e.get('is_important'))
    if urgent_count > 0:
        suggestions.append(f"🚨 {urgent_count} urgent email(s) require immediate attention.")
    
    # Détecter les mots-clés critiques dans les sujets
    critical_keywords = ['urgent', 'asap', 'emergency', 'critical', 'deadline', 'issue', 'problem']
    for email in emails[:5]:  # Check top 5 emails
        subject_lower = (email.get('subject') or '').lower()
        for keyword in critical_keywords:
            if keyword in subject_lower:
                suggestions.append(f"⚡ Email contains '{keyword}': {email['subject'][:50]}...")
                break
    
    return suggestions[:5]  # Limiter à 5 suggestions max

# ---------------- FOLLOW-UP QUESTIONS ----------------

def generate_followup_questions(emails: List[Dict], current_query: str) -> List[str]:
    """
    Génère des questions de follow-up pertinentes basées sur le contexte.
    """
    questions = []
    
    if not emails:
        return [
            "Would you like to check emails from a different time period?",
            "Should I search for emails from a specific sender?"
        ]
    
    # Questions basées sur le contexte
    if len(emails) > 1:
        questions.append(f"Would you like me to summarize all {len(emails)} emails?")
        questions.append("Should I draft a response to any specific email?")
    
    # Si emails importants détectés
    urgent_count = sum(1 for e in emails if e.get('is_important'))
    if urgent_count > 0:
        questions.append(f"Would you like to prioritize the {urgent_count} urgent email(s)?")
    
    # Si même expéditeur multiple fois
    sender_counter = Counter(e['sender'] for e in emails)
    for sender, count in sender_counter.most_common(1):
        if count > 1:
            questions.append(f"Should I consolidate all {count} emails from {sender}?")
    
    # Questions d'action
    questions.append("Would you like me to schedule a follow-up for any of these?")
    questions.append("Should I create a summary report for these emails?")
    
    return questions[:3]  # Limiter à 3 questions

# ---------------- CONTEXT ----------------

def format_context(emails: List[Dict], include_sentiment: bool = True) -> str:
    """
    Formate les emails en contexte lisible avec analyse de sentiment optionnelle.
    """
    if not emails:
        return "Aucun email trouvé."

    text = f"📧 {len(emails)} Email(s) pertinent(s) :\n\n"
    
    for i, e in enumerate(emails, 1):
        # Emoji pour importance
        importance_emoji = "🚨" if e.get('is_important') else "📨"
        
        # Formatage avec sentiment si demandé
        text += f"{importance_emoji} --- Email {i} ---\n"
        text += f"De: {e['sender']}\n"
        text += f"Sujet: {e['subject']}\n"
        text += f"Date: {e['date']}\n"
        
        # Ajouter sentiment si disponible
        if include_sentiment and 'sentiment' in e:
            text += f"Sentiment: {e['sentiment_emoji']} {e['sentiment'].capitalize()}\n"
        
        text += f"Contenu: {e['body'][:600]}...\n\n"
    
    # Ajouter suggestions intelligentes
    suggestions = generate_smart_suggestions(emails)
    if suggestions:
        text += "\n💡 SUGGESTIONS:\n"
        for suggestion in suggestions:
            text += f"• {suggestion}\n"
    
    return text

# ---------------- DRAFT RESPONSES ----------------

def draft_email_response(email: Dict, instruction: str = "") -> str:
    """
    Génère un draft de réponse pour un email spécifique.
    """
    prompt = f"""Génère une réponse professionnelle et concise (2-4 paragraphes) à cet email.

Email original:
De: {email['sender']}
Sujet: {email['subject']}
Contenu: {email['body'][:800]}

Instructions supplémentaires: {instruction or 'Réponse professionnelle standard'}

Format de la réponse:
Subject: Re: [sujet]

[Salutation professionnelle]

[Corps du message]

[Formule de politesse]
[Signature]
"""
    
    try:
        res = client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.7
        )
        
        draft = res.choices[0].message.content
        
        # Ajouter des actions suggérées
        actions = f"""\n---\n📎 ACTIONS DISPONIBLES:
1. 'send' - Envoyer ce draft
2. 'edit' - Modifier le draft
3. 'schedule' - Programmer l'envoi
4. 'add reminder' - Ajouter un rappel de suivi
"""
        
        return draft + actions
        
    except Exception as e:
        return f"❌ Erreur lors de la génération du draft: {e}"

# ---------------- FINAL LLM ----------------
def ask_openai(question: str, context: str, emails: List[Dict] = None) -> str:
    """Use OpenAI chat completions with EXPLICIT instructions and follow-up questions."""
    
    # ✅ Prompt plus directif avec instructions pour suggestions
    system_prompt = """Tu es Mini-Mindy, un assistant email intelligent et proactif.

RÈGLES ABSOLUES :
1. Tu DOIS analyser les emails fournis dans le contexte
2. Si le contexte contient "Aucun email trouvé", dis-le clairement
3. Sinon, réponds UNIQUEMENT en te basant sur les emails fournis
4. JAMAIS de réponse générique du type "I don't have access to..."
5. Format : utilise des emojis (📧 ✅ ⚡ 💡 🎯) et structure claire
6. Fournis des analyses approfondies : sentiment, urgence, actions suggérées
7. Termine TOUJOURS par des questions de follow-up pertinentes

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
            max_completion_tokens=1000
        )
        
        content = res.choices[0].message.content
        
        if content:
            response = content.strip()
            
            # Ajouter des follow-up questions si des emails sont fournis
            if emails:
                followup = generate_followup_questions(emails, question)
                if followup:
                    response += "\n\n🤔 FOLLOW-UP QUESTIONS:\n"
                    for i, q in enumerate(followup, 1):
                        response += f"{i}. {q}\n"
            
            return response
        else:
            return "❌ Pas de réponse générée."
            
    except Exception as e:
        return f"❌ Erreur OpenAI: {e}"
# ---------------- CHAT LOOP ----------------

def chat_loop(user_id: str):
    print("\n💬 Mini-Mindy - Chat Emails Intelligent (exit pour quitter)\n")
    print("💡 Commandes spéciales:")
    print("  • 'good morning' ou 'briefing' - Briefing matinal complet")
    print("  • 'draft reply to email N' - Générer un draft de réponse")
    print("  • 'summarize email N' - Résumer un email spécifique")
    print("  • 'important emails' - Voir les emails importants\n")
    
    conv = ConversationContext()

    while True:
        question = input("Vous : ").strip()
        if question.lower() in ["exit", "quit", "q"]:
            break

        # ==================== MORNING BRIEFING ====================
        # Détecte: "good morning", "briefing", "what do I need to know"
        if re.search(r"good morning|briefing|what.*need.*know|morning summary", question, re.I):
            print("\n" + generate_morning_briefing(user_id))
            continue

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

            # ==================== DRAFT REPLY ====================
            # Si demande de réponse dans la même phrase
            if wants_reply:
                # Extraire les instructions supplémentaires
                instruction_match = re.search(r"(?:draft|reply).*?(?:saying|about|for|at)\s+(.+)", question, re.I)
                instruction = instruction_match.group(1) if instruction_match else ""
                
                draft = draft_email_response(selected, instruction)
                print(f"\n🤖 Mini-Mindy (Draft généré):\n{draft}\n")
                continue

            # ==================== SUMMARIZE ====================
            # Si demande de résumé avec analyse de sentiment
            if wants_summarize:
                # Ajouter l'analyse de sentiment
                sentiment = analyze_sentiment(selected.get('body') or '')
                
                prompt = f"""Résume cet email en incluant:
1. Résumé principal (2-3 phrases)
2. Points clés d'action
3. Urgence/Priorité

Email: {selected.get('body') or ''}"""
                
                context = format_context([selected], include_sentiment=True)
                answer = ask_openai(prompt, context, [selected])
                
                print(f"\n🤖 Mini-Mindy (Résumé avec analyse):\n")
                print(f"Sentiment: {sentiment['emoji']} {sentiment['sentiment'].capitalize()}\n")
                print(f"{answer}\n")
                continue

            # ==================== DISPLAY EMAIL ====================
            # Sinon afficher le mail sélectionné avec sentiment
            selected_with_sentiment = batch_analyze_sentiments([selected])[0]
            print(format_context([selected_with_sentiment], include_sentiment=True))
            continue

        # Sinon utiliser le routeur LLM
        route = route_intent(question)
        intent = route.get("intent")
        period = route.get("period")
        specific_date = route.get("specific_date")

        # Suppression des prints d'intent et de nombre d'emails trouvés
        emails = []
        if intent == "IMPORTANT":
            emails = fetch_important_emails(user_id)
        elif intent == "SPECIFIC_DATE" and specific_date:
            emails = fetch_emails_by_specific_date(user_id, specific_date)
        elif intent == "TEMPORAL":
            emails = fetch_emails_by_date(user_id, period)
        elif intent == "SEMANTIC":
            emails = search_similar_emails(question, user_id)
        else:  # HYBRID
            temporal_emails = []
            if period:
                temporal_emails = fetch_emails_by_date(user_id, period)
            elif specific_date:
                temporal_emails = fetch_emails_by_specific_date(user_id, specific_date)
            semantic_emails = search_similar_emails(question, user_id)
            emails = temporal_emails + semantic_emails

        # ==================== ANALYZE SENTIMENTS ====================
        # Analyser les sentiments pour les premiers emails (pour performance)
        if emails:
            emails = batch_analyze_sentiments(emails[:10])  # Analyser max 10 emails
        
        # Sauvegarder pour follow-ups
        conv.set_emails(emails)

        # ==================== GENERATE RESPONSE ====================
        context = format_context(emails, include_sentiment=True)
        answer = ask_openai(question, context, emails)

        print(f"\n🤖 Mini-Mindy:\n{answer}\n")
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