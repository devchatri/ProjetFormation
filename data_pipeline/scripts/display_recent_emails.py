#!/usr/bin/env python3
"""
Script pour afficher les derniers emails de Gmail
Lit directement depuis l'API Gmail sans passer par le backend
"""

import os
import sys
import pickle
import json
from datetime import datetime

# Essayer d'importer les modules Google
try:
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from googleapiclient.discovery import build
except ImportError as e:
    print(f"❌ Erreur: Modules Google non installés")
    print(f"   {e}")
    print("📦 Installez-les avec:")
    print("   pip install --upgrade google-auth-oauthlib google-api-python-client")
    sys.exit(1)

# Configuration
SCOPES = ['https://www.googleapis.com/auth/gmail.readonly']
CREDENTIALS_FILE = 'credentials.json'
TOKEN_FILE = 'token.json'
EMAIL_LIMIT = 10  # Nombre d'emails à afficher

def authenticate_gmail():
    """
    Authentifie avec l'API Gmail
    """
    creds = None
    if os.path.exists(TOKEN_FILE):
        try:
            creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)
        except Exception as e:
            print(f"Erreur lors du chargement du token: {e}")
            creds = None
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                CREDENTIALS_FILE, SCOPES)
            creds = flow.run_local_server(port=0)
        # Sauvegarder le token au format JSON
        with open(TOKEN_FILE, 'w') as token:
            token.write(creds.to_json())
    return creds

def get_email_header(message, header_name):
    headers = message['payload']['headers']
    for header in headers:
        if header['name'] == header_name:
            return header['value']
    return 'N/A'

def get_email_body(message):
    try:
        if 'parts' in message['payload']:
            parts = message['payload']['parts']
            for part in parts:
                if part['mimeType'] == 'text/plain':
                    data = part['body'].get('data', '')
                    if data:
                        import base64
                        return base64.urlsafe_b64decode(data).decode('utf-8')
        else:
            data = message['payload']['body'].get('data', '')
            if data:
                import base64
                return base64.urlsafe_b64decode(data).decode('utf-8')
    except Exception as e:
        print(f"Erreur lors de la lecture du corps: {e}")
    return "Pas de corps"

def display_recent_emails():
    print("=" * 80)
    print("📧 AFFICHAGE DES DERNIERS EMAILS DE GMAIL")
    print("=" * 80)
    print()
    try:
        creds = authenticate_gmail()
        service = build('gmail', 'v1', credentials=creds)
        print(f"⏳ Récupération des {EMAIL_LIMIT} derniers emails...")
        results = service.users().messages().list(
            userId='me',
            maxResults=EMAIL_LIMIT
        ).execute()
        messages = results.get('messages', [])
        if not messages:
            print("❌ Aucun email trouvé!")
            return
        print(f"✅ {len(messages)} emails trouvés!\n")
        for idx, message in enumerate(messages, 1):
            msg_id = message['id']
            msg = service.users().messages().get(
                userId='me',
                id=msg_id,
                format='full'
            ).execute()
            subject = get_email_header(msg, 'Subject')
            sender = get_email_header(msg, 'From')
            date = get_email_header(msg, 'Date')
            body = get_email_body(msg)
            print(f"{'─' * 80}")
            print(f"📌 EMAIL #{idx}")
            print(f"{'─' * 80}")
            print(f"De:      {sender}")
            print(f"Sujet:   {subject}")
            print(f"Date:    {date}")
            print(f"ID:      {msg_id}")
            print()
            print("📝 Contenu:")
            print(f"{body[:300]}...")
            print()
        print("=" * 80)
        print(f"✅ Affichage de {len(messages)} emails terminé!")
        print("=" * 80)
    except Exception as e:
        print(f"❌ Erreur: {e}")

if __name__ == "__main__":
    display_recent_emails()
