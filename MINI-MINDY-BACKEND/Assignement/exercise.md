## 🎯 Exercise Overview

**Objective**: Build a simplified version of Mindy that demonstrates enterprise data processing using Apache Spark, Kafka, and Airflow.

**Duration**: 5 days

**Team Size**: 2-3 developers

---

## 📋 What You'll Build

A data pipeline system that:
1. Ingests email data from Gmail API into Kafka
2. Processes data in real-time using Spark Streaming
3. Orchestrates batch jobs with Airflow
4. Provides an AI-powered chat interface to query the processed data

---

## 🏗️ Architecture

```
Gmail API → Kafka → Spark Streaming → Delta Lake → PostgreSQL → FastAPI → Chat UI
                          ↓
                      Airflow (orchestration)
```

---

## 📦 Required Components

### Infrastructure Stack
- **Kafka**: Message broker for data ingestion
- **Spark**: Data processing (streaming + batch)
- **Airflow**: Workflow orchestration
- **Delta Lake**: Data lake with ACID transactions (local filesystem)
- **PostgreSQL**: Structured data storage
- **FastAPI**: Backend API
- **Next.js**: Frontend chat interface

### Development Environment
- Docker Compose for all services
- Python 3.10+
- Node.js 18+
- PySpark 3.5.0

---

## 🎬 Demo Use Case: AI Executive Assistant

### The Scenario

**Meet Sarah**, a busy VP of Sales who receives 200+ emails daily and has back-to-back meetings. She uses Mini-Mindy as her **AI Executive Assistant** that:

1. **Monitors her inbox** in real-time via Kafka streams
2. **Analyzes emails** using Spark to detect priorities, sentiment, and action items
3. **Manages her calendar** by understanding meeting context and suggesting optimizations
4. **Proactively assists** through natural language conversations

### Demo Flow (5 minutes)

#### **Scene 1: Morning Briefing** (1 min)
```
Sarah: "Good morning! What do I need to know today?"

Mini-Mindy: 
"Good morning Sarah! Here's your briefing for October 4th:

📧 EMAIL INSIGHTS (from last 24 hours):
• 47 new emails received
• 8 require urgent response (flagged by AI)
• Top 3 senders: John (Sales), Maria (Finance), Client: Acme Corp
• Sentiment analysis: 12 positive, 3 negative (2 from upset client)

📅 CALENDAR:
• 6 meetings today (4 hours total)
• Next meeting: 9:30 AM - Q4 Planning with Sales Team
• Conflict detected: 2 PM slot has overlapping invites

⚡ ACTION REQUIRED:
• Acme Corp is requesting urgent call (mentioned in 3 emails)
• Contract review deadline is today at 5 PM
• Team waiting for your approval on pricing proposal"
```

#### **Scene 2: Smart Email Management** (1.5 min)
```
Sarah: "Show me the urgent emails from Acme Corp"

Mini-Mindy:
[Displays 3 emails with AI summary]
"I found 3 emails from Acme Corp in the last 6 hours:

1. **10:47 PM (Yesterday)** - Tom Anderson (CTO)
   📌 Subject: "Production Issue - Need Immediate Support"
   🎯 Summary: Critical bug affecting 500 users, requesting emergency call
   😟 Sentiment: Frustrated but professional
   
2. **7:23 AM (Today)** - Tom Anderson
   📌 Subject: "Re: Production Issue - Status Update?"
   🎯 Summary: Following up, issue escalating, considering alternatives
   😠 Sentiment: Increasingly frustrated
   
3. **8:15 AM (Today)** - Lisa Chen (VP Ops) [NEW THREAD]
   📌 Subject: "Executive Review Meeting Request"
   🎯 Summary: Wants to discuss partnership, references the issue
   😐 Sentiment: Neutral, business-focused

💡 RECOMMENDATION: This is escalating. I suggest:
1. Immediate response acknowledging the issue
2. Schedule emergency call within 2 hours
3. Loop in your technical team"

---

Sarah: "Draft a response to Tom apologizing and offering a call at 11 AM"

Mini-Mindy:
"I've drafted this email:

Subject: Re: Production Issue - Immediate Action

Hi Tom,

I sincerely apologize for the delay in response. I understand the 
critical nature of the production issue affecting your users.

I've immediately escalated this to our engineering team and would 
like to schedule an emergency call at 11:00 AM today to discuss 
the resolution plan and next steps.

I'm also looping in our CTO Mike Stevens to ensure we have the 
right technical expertise on the call.

Please confirm if 11 AM works for you.

Best regards,
Sarah

---
📎 Actions I can take:
1. Send this email now
2. Add 11 AM call to your calendar
3. Notify Mike Stevens to join
4. Create follow-up task to check resolution

Say 'send and schedule' to execute all actions."

---

Sarah: "Send and schedule"

Mini-Mindy:
"✅ Done! I've:
• Sent the email to Tom Anderson
• Created calendar event: 11 AM Emergency Call - Acme Corp
• Invited Mike Stevens to the call
• Added task: Follow-up on Acme issue resolution (Due: 5 PM today)
• Set reminder: 10:45 AM to prepare for call

Anything else you need?"
```

#### **Scene 3: Calendar Intelligence** (1 min)
```
Sarah: "What's my busiest day this week?"

Mini-Mindy:
[Queries Spark-processed calendar data]

"Wednesday October 6th is your busiest day:

📊 WEDNESDAY STATS:
• 9 meetings (6.5 hours of meetings)
• Only 1.5 hours of free time
• 3 different locations (office, lunch, client site)
• 12 participants across all meetings

⚠️ CONCERNS:
• Back-to-back meetings from 9 AM to 3 PM (no lunch break!)
• 30-min "Strategy Review" might run over (usually takes 45min)
• Travel time not accounted for client visit at 4 PM

💡 SUGGESTIONS:
1. Move "Budget Review" (low priority) to Thursday
2. Block 12-1 PM for lunch and email catch-up
3. Add 15-min buffer before client meeting for travel

Would you like me to optimize your Wednesday schedule?"

---

Sarah: "Yes, optimize it"

Mini-Mindy:
"✅ I've optimized Wednesday:

CHANGES MADE:
• Moved Budget Review to Thursday 2 PM (attendees confirmed available)
• Blocked 12:00-12:45 PM as 'Focus Time - Lunch & Email'
• Added 3:45 PM 'Travel to Client Site' buffer
• Suggested to end Strategy Review strictly at 11:30 AM

RESULT:
• Reduced meeting time from 6.5h to 5.5h
• Added 1 hour protected focus time
• Zero scheduling conflicts
• Travel time accounted for

Your Wednesday is now more manageable! 🎯"
```

#### **Scene 4: Proactive Insights** (1 min)
```
Sarah: "Any patterns I should know about?"

Mini-Mindy:
[Analyzes data from Spark batch jobs in Gold layer]

"I've analyzed your last 30 days of emails and calendar. Here are key patterns:

📈 EMAIL PATTERNS:
• You receive 40% more emails on Mondays (avg 73 vs 52/day)
• Peak email time: 8-10 AM (23% of daily volume)
• Your response time: 2.3 hours average (top 10% responders!)
• Most emails from: Sales team (34%), Clients (28%), Leadership (18%)

📅 MEETING PATTERNS:
• You spend 65% of work hours in meetings (industry avg: 50%)
• Most productive meeting days: Tuesday & Thursday
• Meetings often run 15-20% over scheduled time
• You have zero 1-on-1s with 3 direct reports this month

💼 WORKLOAD INSIGHTS:
• Your calendar is 92% booked for next 2 weeks
• Trending up: Client escalations (+40% this month)
• Trending down: Strategic planning time (-60% vs last month)

🎯 RECOMMENDATIONS:
1. Block Monday mornings for email processing
2. Limit meetings to 4 hours/day when possible
3. Schedule recurring 1-on-1s with your team
4. Reserve Friday afternoons for strategic work

Would you like me to automatically implement any of these?"
```

#### **Scene 5: Complex Cross-System Query** (30 sec)
```
Sarah: "Who are the clients I'm meeting this week that also 
        emailed me in the last 3 days?"

Mini-Mindy:
[Joins calendar data + email data using Spark SQL]

"Found 4 clients meeting both criteria:

1. **Acme Corp** (Tom Anderson)
   📅 Meeting: Tomorrow 11 AM (Emergency Call)
   📧 Emails: 3 in last 24 hours (urgent issue)
   ⚠️ Status: HIGH PRIORITY - unresolved production bug

2. **TechFlow Inc** (Jennifer Martinez)
   📅 Meeting: Wednesday 4 PM (On-site visit)
   📧 Emails: 2 yesterday (contract questions)
   ✅ Status: Normal - renewal discussion

3. **Global Systems** (Robert Kim)
   📅 Meeting: Thursday 2 PM (Quarterly Review)
   📧 Emails: 1 email 2 days ago (agenda confirmation)
   ✅ Status: Routine check-in

4. **DataCorp** (Amanda Foster)
   📅 Meeting: Friday 10 AM (Product Demo)
   📧 Emails: 4 emails (feature requests, pricing)
   💡 Status: Hot lead - showing high interest

📎 I've created a briefing doc with:
• Email summaries for each client
• Meeting agendas and talking points
• Action items and follow-ups needed

Want me to send it to you?"
```

---

### Why This Demo is Compelling

✅ **Solves Real Pain Points**:
- Information overload (200+ daily emails)
- Calendar chaos and scheduling conflicts
- Missing important signals in the noise
- No time for strategic thinking

✅ **Shows Technical Capabilities**:
- Real-time stream processing (Kafka → Spark)
- Batch analytics (Airflow daily jobs)
- AI integration (email summarization, sentiment analysis)
- Cross-system data joining (emails + calendar)
- Natural language to SQL

✅ **Demonstrates Business Value**:
- Time saved: ~2 hours/day on email triage
- Risk mitigation: Early detection of client issues
- Better decisions: Data-driven calendar optimization
- Proactive assistance: Pattern recognition and recommendations

✅ **Relatable to Everyone**:
- Every executive has this problem
- Easy to understand the value proposition
- Shows immediate, tangible benefits

---

## 📝 Exercise Requirements

### Day 1: Infrastructure Setup

**Task 1.1: Docker Environment**
Create `docker-compose.yml` with:
- Kafka + Zookeeper
- Spark (1 master, 2 workers)
- Airflow (webserver, scheduler, worker)
- PostgreSQL
- Redis

**Task 1.2: Initialize Services**
- Create Kafka topics: `emails-topic`, `processed-emails-topic`
- Initialize Airflow database and create admin user
- Create PostgreSQL database schema

**Deliverable**: All services running and healthy

---

### Day 2: Data Ingestion (Kafka)

**Task 2.1: Email Producer**
Create `kafka/producers/email_producer.py`:
- Connect to Gmail API using OAuth2
- Fetch last 24 hours of emails
- Extract: subject, from, to, body, timestamp
- Produce messages to `emails-topic`
- Run every 5 minutes

**Task 2.2: Data Validation Consumer**
Create `kafka/consumers/validator.py`:
- Consume from `emails-topic`
- Validate data quality (non-null fields)
- Produce valid records to `processed-emails-topic`
- Log validation errors

**Deliverable**: 
- Producer script successfully ingesting emails to Kafka
- Consumer validating and forwarding messages

---

### Day 3: Real-time Processing (Spark Streaming)

**Task 3.1: Streaming Job**
Create `spark/jobs/streaming/email_processor.py`:
- Read from `processed-emails-topic`
- Parse JSON messages
- Add enrichment fields:
  - `processed_at` timestamp
  - `sender_domain` extracted from email
  - `email_length` (body character count)
  - `is_important` (based on keywords)
- Write to Delta Lake bronze layer: `/datalake/bronze/emails`

**Task 3.2: Aggregation Stream**
In the same job:
- Create 1-hour windowed aggregations:
  - Count of emails per sender domain
  - Average email length
  - List of subjects
- Write to Delta Lake silver layer: `/datalake/silver/email_stats`

**Deliverable**: 
- Streaming job processing emails in real-time
- Data visible in Delta Lake (bronze & silver)

---

### Day 4: Batch Orchestration (Airflow)

**Task 4.1: Daily Processing DAG**
Create `airflow/dags/daily_batch_dag.py` with tasks:

1. **Data Quality Check**
   - Read yesterday's emails from bronze layer
   - Calculate quality score (% of complete records)
   - Fail if quality < 95%

2. **Daily Aggregation**
   - Spark job to aggregate daily statistics:
     - Total emails received
     - Top 10 sender domains
     - Busiest hour of the day
   - Write results to gold layer: `/datalake/gold/daily_summary`

3. **Export to PostgreSQL**
   - Read gold layer data
   - Write to PostgreSQL table `daily_insights`
   - Create indexes for fast queries

4. **Send Email Report**
   - Generate summary of pipeline execution
   - Send notification email

**Schedule**: Daily at 2 AM

**Deliverable**: 
- Working Airflow DAG
- Successful execution with all tasks passing
- Data in PostgreSQL table

---

### Day 5: AI Query Interface

**Task 5.1: Backend API**
Create `backend/app/routers/chat.py`:

**Endpoint**: `POST /api/chat`

Logic:
1. Receive user natural language query
2. Convert to SQL using OpenAI API
3. Execute SQL on PostgreSQL `daily_insights` table
4. Generate natural language response with AI
5. Return formatted answer + SQL used

**Example queries to support**:
- "How many emails did I receive yesterday?"
- "Which sender sent me the most emails this week?"
- "What was the busiest hour yesterday?"

**Task 5.2: Frontend Chat**
Create `frontend/src/app/chat/page.tsx`:
- Chat interface with message history
- Display AI responses
- Show SQL query used (collapsible)
- Real-time typing indicators

**Task 5.3: Analytics Dashboard**
Create `frontend/src/app/dashboard/page.tsx`:
- Display pipeline health metrics
- Show recent Airflow DAG runs
- Display data lake statistics (records in each layer)
- Kafka topic lag monitoring

**Deliverable**: 
- Working chat interface
- Successful query → SQL → response flow
- Dashboard showing pipeline metrics

---

## 🎓 Learning Objectives

By completing this exercise, you should understand:

1. **Kafka**: Event streaming, producers, consumers, topics
2. **Spark Streaming**: Real-time data processing, windowing, Delta Lake
3. **Airflow**: DAG creation, task dependencies, scheduling
4. **Data Lake Architecture**: Bronze/Silver/Gold layer pattern
5. **AI Integration**: Text-to-SQL, natural language responses

---

## 📊 Evaluation Criteria

### Technical Requirements (70%)
- ✅ All services running in Docker
- ✅ Kafka producing and consuming messages
- ✅ Spark streaming job writing to Delta Lake
- ✅ Airflow DAG executing successfully
- ✅ API converting natural language to SQL
- ✅ Chat interface displaying results

### Code Quality (20%)
- ✅ Proper error handling
- ✅ Logging throughout pipeline
- ✅ Environment variables for configuration
- ✅ Clear code comments
- ✅ Modular, reusable functions

### Documentation (10%)
- ✅ README with setup instructions
- ✅ Architecture diagram
- ✅ Example queries documented
- ✅ Troubleshooting guide

---

## 🚀 Getting Started

### Step 1: Fork Repository
```bash
git clone https://github.com/yourcompany/mini-mindy-exercise
cd mini-mindy-exercise
```

### Step 2: Setup Environment
```bash
# Copy environment template
cp .env.example .env

# Add your API keys:
# - GMAIL_CLIENT_ID
# - GMAIL_CLIENT_SECRET
# - OPENAI_API_KEY
```

### Step 3: Start Infrastructure
```bash
docker-compose up -d
```

### Step 4: Follow Daily Tasks
Complete requirements for each day in order.




## 💡 Hints & Tips

### Kafka
- Use `kafka-console-consumer` to debug messages
- Set retention policy for topics
- Monitor consumer lag

### Spark
- Start with small batch intervals (30 seconds)
- Use checkpointing for fault tolerance
- Monitor Spark UI at localhost:4040

### Airflow
- Test tasks individually before full DAG
- Use `airflow tasks test` command
- Check logs in Airflow UI

### Delta Lake
- Use `DESCRIBE HISTORY` to see versions
- Partition by date for better performance
- Enable auto-optimize

---

## 🎯 Bonus Challenges (Optional)

1. **Schema Evolution**: Handle Gmail API schema changes gracefully
2. **Late Data Handling**: Configure watermarking in Spark
3. **Cost Optimization**: Implement data retention policies
4. **Monitoring**: Add Prometheus + Grafana dashboards
5. **CI/CD**: Create GitHub Actions for automated testing

---

## 📞 Support

- **Office Hours**: Daily at 3 PM
- **Slack Channel**: #mini-mindy-exercise
- **Documentation**: `/docs` folder in repo

---

## ✅ Submission

**Due**: End of Day 5

**Submit**:
1. GitHub repository link
2. Demo video (5 minutes) showing:
   - Data flowing through pipeline
   - Airflow DAG execution
   - Chat interface answering questions
3. Architecture diagram
4. Brief reflection (what was challenging? what did you learn?)

**Format**: Submit via Google Form (link provided separately)

---

## 🏆 Success Criteria

**Pass**: All Day 1-5 requirements completed
**Good**: Pass + code quality standards met
**Excellent**: Good + 2+ bonus challenges completed

---

## 🐳 Docker Deployment Architecture

### Overview

Mini-Mindy uses a **multi-container Docker architecture** with service isolation, network segregation, and persistent storage. All services communicate through internal Docker networks with minimal external exposure.

### Network Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    External Network (Host)                       │
│                                                                   │
│  Exposed Ports:                                                  │
│  • 3000  → Frontend                                              │
│  • 8000  → Backend API                                           │
│  • 8080  → Airflow UI                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Network                          │
│  • frontend (Next.js)                                            │
│  • nginx (reverse proxy)                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Application Network                       │
│  • backend (FastAPI)                                             │
│  • redis (cache)                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Processing Network                        │
│  • kafka + zookeeper                                             │
│  • spark-master                                                  │
│  • spark-worker-1, spark-worker-2                                │
│  • airflow-webserver, scheduler, worker                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                          Data Network                            │
│  • postgres (structured data)                                    │
└─────────────────────────────────────────────────────────────────┘
```

---


**Recommended per service**:
- **Kafka**: 2 CPUs, 4GB RAM
- **Spark Master**: 1 CPU, 2GB RAM
- **Spark Workers**: 2 CPUs, 2GB RAM each
- **Airflow**: 2 CPUs, 4GB RAM total (all components)
- **PostgreSQL**: 1 CPU, 2GB RAM
- **Backend**: 1 CPU, 1GB RAM
- **Frontend**: 0.5 CPU, 512MB RAM

**Total minimum**: 8 CPUs, 16GB RAM

---

### Storage Architecture

#### Development (Local Filesystem)
```
./datalake/
├── bronze/          # Raw ingested data
│   ├── emails/
│   └── calendar/
├── silver/          # Cleaned & enriched data
│   ├── email_aggregations/
│   └── calendar_patterns/
└── gold/            # Business-ready analytics
    ├── daily_insights/
    └── recommendations/
```

**Pros**: 
- Simple setup, no cloud dependencies
- Fast local development
- Easy to inspect files

**Cons**: 
- Not distributed
- Limited to single machine storage
- No built-in replication

#### Production (Azure Data Lake Gen2)
```
abfss://mindy-datalake@account.dfs.core.windows.net/
├── bronze/
├── silver/
└── gold/
```

**Pros**:
- Unlimited scalable storage
- Built-in replication and backup
- Integration with Azure ecosystem
- POSIX-compliant for Spark
- Fine-grained access control

**Migration Path**:
1. Develop locally using /datalake
2. Test with Azure connection
3. Update paths in code: `/datalake/` → `abfss://...`
4. Deploy to production

---


### Production Considerations

#### Security Checklist
- [ ] Change all default passwords
- [ ] Use Docker secrets for sensitive data
- [ ] Enable SSL/TLS for all services
- [ ] Implement network policies
- [ ] Enable authentication for all UIs
- [ ] Set up firewall rules
- [ ] Regular security updates

#### Scaling Strategy
- [ ] Use Docker Swarm or Kubernetes for orchestration
- [ ] Implement horizontal scaling for workers
- [ ] Set up load balancer (nginx/HAProxy)
- [ ] Configure auto-scaling policies
- [ ] Implement service mesh (Istio) for microservices

#### Backup Strategy
- [ ] Automated PostgreSQL backups
- [ ] Delta Lake snapshots and time travel
- [ ] Azure Blob replication (production)
- [ ] Configuration backup
- [ ] Disaster recovery plan

---

Good luck! 🚀