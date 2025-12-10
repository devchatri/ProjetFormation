import json
import logging
import os
from kafka import KafkaConsumer, KafkaProducer
from datetime import datetime

# Configuration

BROKER = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:29092')
CONSUMER_TOPIC = 'emails-topic'
PRODUCER_TOPIC = 'processed-emails-topic'

# Logger setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Required fields for validation
REQUIRED_FIELDS = ['subject', 'sender', 'recipient', 'timestamp', 'body']

def validate_email(email):
    """
    Validate if email has all required fields and they're not empty.
    
    Args:
        email (dict): Email message from Kafka
        
    Returns:
        tuple: (is_valid, error_message)
    """
    try:
        for field in REQUIRED_FIELDS:
            if field not in email or email[field] is None or email[field] == '':
                return False, f"Missing or empty field: {field}"
        
        return True, None
    except Exception as e:
        return False, f"Validation error: {str(e)}"

def main():
    # Consumer: read from emails-topic
    logger.info(f"🔄 Connecting to Kafka broker: {BROKER}")
    
    consumer = KafkaConsumer(
        CONSUMER_TOPIC,
        bootstrap_servers=[BROKER],
        auto_offset_reset='earliest',
        group_id='validator-group',
        value_deserializer=lambda m: json.loads(m.decode('utf-8')),
        consumer_timeout_ms=5000
    )
    
    # Producer: send to processed-emails-topic
    producer = KafkaProducer(
        bootstrap_servers=[BROKER],
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )
    
    logger.info(f"✅ Connected to Kafka")
    logger.info(f"📥 Consuming from: {CONSUMER_TOPIC}")
    logger.info(f"📤 Producing to: {PRODUCER_TOPIC}")
    logger.info(f"📋 Validating fields: {', '.join(REQUIRED_FIELDS)}")
    logger.info("=" * 80)
    
    valid_count = 0
    invalid_count = 0
    
    try:
        for message in consumer:
            email = message.value
            
            # Validate email
            is_valid, error_msg = validate_email(email)
            
            if is_valid:
                # Send to processed-emails-topic
                producer.send(PRODUCER_TOPIC, value=email)
                valid_count += 1
                logger.info(f"✅ [{valid_count}] Valid email: '{email['subject'][:50]}...' from {email['sender']}")
            else:
                # Log validation error
                invalid_count += 1
                logger.warning(f"❌ [{invalid_count}] Invalid email: {error_msg} - Subject: {email.get('subject', 'N/A')}")
    
    except KeyboardInterrupt:
        logger.info("🛑 Consumer stopped by user")
    except Exception as e:
        logger.error(f"❌ Error in validator: {str(e)}")
    finally:
        logger.info("=" * 80)
        logger.info(f"📊 VALIDATION SUMMARY:")
        logger.info(f"   ✅ Valid emails: {valid_count}")
        logger.info(f"   ❌ Invalid emails: {invalid_count}")
        logger.info(f"   📈 Total processed: {valid_count + invalid_count}")
        
        consumer.close()
        producer.close()
        logger.info("🔌 Kafka connections closed")

if __name__ == '__main__':
    main()
