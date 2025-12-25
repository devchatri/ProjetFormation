from flask import Flask, request, jsonify
from gemini_chat import ask_openai, fetch_important_emails, format_context, generate_morning_briefing

app = Flask(__name__)

# Helper to get user_id from request (from Spring Boot auth header)
def get_user_id():
    # Get user email from a custom header set by Spring Boot after authentication
    return request.headers.get('X-User-Email')

@app.route('/api/chat', methods=['POST'])
def chat():
    data = request.json
    question = data.get('question', '')
    user_id = get_user_id()
    if not user_id:
        return jsonify({'error': 'User not authenticated'}), 401

    # Si la question est un briefing matinal, retourne le même résumé que le terminal
    if any(kw in question.lower() for kw in ["good morning", "briefing", "what do i need to know"]):
        response = generate_morning_briefing(user_id)
        return jsonify({'response': response})

    emails = fetch_important_emails(user_id)[:3]  # Limite à 3 emails
    # Tronquer le body de chaque email pour éviter trop de tokens
    for email in emails:
        if 'body' in email and isinstance(email['body'], str):
            email['body'] = email['body'][:500]
    context = format_context(emails)
    response = ask_openai(question, context)
    return jsonify({'response': response})

if __name__ == '__main__':
    app.run(port=5001, debug=True)
