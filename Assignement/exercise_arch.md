# Mini-Mindy: System Architecture & Team Distribution

## 🏗️ System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER                       │
│  ┌──────────────────┐         ┌──────────────────────────────┐  │
│  │   Chat UI        │         │   Analytics Dashboard        │  │
│  │   (Next.js)      │────────▶│   (Charts, Metrics, Logs)    │  │
│  └──────────────────┘         └──────────────────────────────┘  │
└────────────────────┬──────────────────────────────────────────┬──┘
                     │                                          │
                     ▼                                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                       APPLICATION LAYER                          │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              FastAPI Backend (Python)                     │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐  │   │
│  │  │ Chat Router │  │ Query Engine │  │  AI Service     │  │   │
│  │  │ (WebSocket) │  │ (SQL + Text) │  │  (OpenAI/Claude)│  │   │
│  │  └─────────────┘  └──────────────┘  └─────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      STREAMING LAYER                             │
│  ┌──────────────┐         ┌─────────────────────────────────┐   │
│  │    KAFKA     │────────▶│    SPARK STREAMING              │   │
│  │              │         │  ┌──────────────────────────┐   │   │
│  │ • emails     │         │  │ Email Processor          │   │   │
│  │ • calendar   │         │  │ (enrichment, sentiment)  │   │   │
│  │ • processed  │         │  └──────────────────────────┘   │   │
│  └──────────────┘         └─────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┬┘
                                                                  │
                                                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATION LAYER                           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    AIRFLOW                                │   │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────┐  │   │
│  │  │ Data Quality   │  │ Daily Batch    │  │ Analytics  │  │   │
│  │  │ Validation     │  │ Aggregation    │  │ Generation │  │   │
│  │  └────────────────┘  └────────────────┘  └────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┬┘
                                                                  │
                                                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        DATA LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │  DELTA LAKE  │  │  PostgreSQL  │  │      Redis         │    │
│  │  (Local FS / │  │  (Structured │  │      (Cache)       │    │
│  │   Azure DL)  │  │   Analytics) │  │                    │    │
│  │  Bronze/     │  │              │  │                    │    │
│  │  Silver/Gold │  │              │  │                    │    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
│                                                                   │
│  Storage Options:                                                │
│  • Development: ./datalake (local volume)                        │
│  • Production: abfss://container@account.dfs.core.windows.net   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 👥 Team Structure & Responsibilities

### **Tak Role** 
**Focus**: Streaming & Batch Processing (Kafka, Spark, Airflow)

### **Task Role 2**
**Focus**: Application Logic & AI Integration (FastAPI, OpenAI, Database)

### **Task Role 3**
**Focus**: Frontend & Infrastructure (Next.js, Docker, DevOps)

---

## 📋 Work Distribution by Day

### **DAY 1: Infrastructure & Setup**

#### Developer 
**Task 1.1: Kafka Setup** (3 hours)
- Create `docker-compose.yml` sections for Kafka + Zookeeper
- Configure Kafka topics with retention policies
- Write initialization script for topic creation
- Test message production/consumption

**Task 1.2: Spark Cluster Setup** (3 hours)
- Configure Spark master + 2 workers in docker-compose
- Setup Delta Lake dependencies
- Create shared volume for `/datalake` (bronze/silver/gold)
- Test Spark job submission

**Task 1.3: Airflow Setup** (2 hours)
- Configure Airflow webserver, scheduler, worker
- Initialize Airflow database
- Create sample DAG to verify setup
- Setup connections to Spark and PostgreSQL

**Deliverables**:
- ✅ Kafka cluster running with topics created
- ✅ Spark cluster operational
- ✅ Airflow UI accessible with test DAG

---

#### Developer 
**Task 1.1: PostgreSQL & Redis Setup** (2 hours)
- Configure PostgreSQL in docker-compose with pgvector
- Create database schema (`emails`, `calendar_events`, `daily_insights`)
- Setup Redis for caching
- Write database initialization script

**Task 1.2: FastAPI Application Bootstrap** (4 hours)
- Create FastAPI project structure
- Setup environment configuration
- Implement health check endpoint
- Configure database connection with SQLAlchemy
- Setup Redis client
- Create Pydantic models for Email, CalendarEvent

**Task 1.3: Gmail API Integration** (2 hours)
- Setup OAuth2 credentials
- Create Gmail service client
- Test email fetching (basic script)
- Document API setup instructions

**Deliverables**:
- ✅ PostgreSQL database with schema
- ✅ FastAPI app running with /health endpoint
- ✅ Gmail API authenticated and tested

---

#### Developer
**Task 1.1: Docker Infrastructure** (3 hours)
- Create master `docker-compose.yml` coordinating all services
- Setup Docker networks (frontend-net, app-net, processing-net, data-net)
- Configure persistent volumes
- Create `.env.example` template

**Task 1.2: Next.js Frontend Bootstrap** (3 hours)
- Initialize Next.js 14 app with TypeScript
- Setup Tailwind CSS and shadcn/ui
- Create basic layout and navigation
- Implement API client utilities
- Create environment configuration

**Task 1.3: System Integration & Testing** (2 hours)
- Ensure all services start successfully
- Write `scripts/health_check.sh` to verify all services
- Create `scripts/setup.sh` for initial setup
- Document startup instructions in README

**Deliverables**:
- ✅ Complete docker-compose with all services
- ✅ Next.js app running and accessible
- ✅ All services healthy and communicating

---

### **DAY 2: Data Ingestion Pipeline**

#### Developer
**Task 2.1: Email Producer** (4 hours)
- Create `kafka/producers/email_producer.py`
- Implement Gmail → Kafka pipeline
- Handle OAuth token refresh
- Add error handling and retry logic
- Schedule to run every 5 minutes

**Task 2.2: Data Validation Consumer** (2 hours)
- Create `kafka/consumers/validator.py`
- Implement schema validation
- Filter valid messages to `processed-emails-topic`
- Log validation errors

**Task 2.3: Kafka Monitoring** (2 hours)
- Create consumer lag monitoring
- Setup topic metrics collection
- Create Kafka dashboard (simple Python script)

**Deliverables**:
- ✅ Email producer fetching from Gmail → Kafka
- ✅ Validator consuming and validating messages
- ✅ Monitoring showing message flow

---

#### Developer
**Task 2.1: Calendar API Integration** (3 hours)
- Create Calendar API client
- Implement event fetching logic
- Create `calendar_producer.py` for Kafka
- Test calendar → Kafka flow

**Task 2.2: Database Models & Repository** (3 hours)
- Create SQLAlchemy models for all entities
- Implement repository pattern for database access
- Create CRUD operations for emails and calendar
- Write database migration scripts

**Task 2.3: API Endpoints - Data Access** (2 hours)
- Create `/api/emails` endpoint (list, get, search)
- Create `/api/calendar` endpoint (list, get)
- Implement pagination and filtering
- Add API documentation with examples

**Deliverables**:
- ✅ Calendar events flowing to Kafka
- ✅ Database models and repositories complete
- ✅ REST API endpoints for data access

---

#### Developer
**Task 2.1: Frontend - Email View** (4 hours)
- Create email list component
- Implement email detail view
- Add search and filter UI
- Connect to backend API

**Task 2.2: Frontend - Calendar View** (2 hours)
- Create calendar component (weekly view)
- Display events from API
- Add event detail modal

**Task 2.3: Real-time Updates** (2 hours)
- Setup WebSocket connection preparation
- Implement polling for new data (temporary)
- Add loading states and error handling

**Deliverables**:
- ✅ Email inbox UI displaying data
- ✅ Calendar view showing events
- ✅ Real-time data updates

---

### **DAY 3: Real-time Stream Processing**

#### Developer
**Task 3.1: Spark Streaming Job** (5 hours)
- Create `spark/jobs/streaming/email_processor.py`
- Implement Kafka → Spark streaming
- Add data enrichment:
  - Extract sender domain
  - Calculate email length
  - Detect importance keywords
  - Timestamp processing
- Write to Delta Lake bronze layer

**Task 3.2: Stream Aggregations** (3 hours)
- Implement windowed aggregations (1-hour windows)
- Count emails per sender domain
- Calculate average email length
- Collect subjects list
- Write to Delta Lake silver layer

**Deliverables**:
- ✅ Streaming job processing emails in real-time
- ✅ Bronze layer receiving enriched data
- ✅ Silver layer receiving aggregated data

---

#### Developer
**Task 3.1: AI Service Implementation** (4 hours)
- Create `services/ai_service.py`
- Implement OpenAI integration
- Create email summarization function
- Implement sentiment analysis
- Add prompt templates

**Task 3.2: Delta Lake Query Engine** (4 hours)
- Create `services/delta_query.py`
- Implement Delta Lake reader
- Create query functions for bronze/silver layers
- Add caching layer with Redis
- Create API endpoints to query Delta Lake data

**Deliverables**:
- ✅ AI service generating summaries and sentiment
- ✅ Delta Lake query engine functional
- ✅ API endpoints returning processed data

---

#### Developer
**Task 3.1: Data Pipeline Dashboard** (4 hours)
- Create pipeline monitoring dashboard
- Display Kafka metrics (messages, lag)
- Show Spark job status
- Display Delta Lake statistics (record counts)
- Real-time updates with polling

**Task 3.2: WebSocket Implementation** (4 hours)
- Implement WebSocket server in FastAPI
- Create WebSocket client in frontend
- Push real-time email notifications
- Update UI when new data arrives

**Deliverables**:
- ✅ Pipeline dashboard showing metrics
- ✅ WebSocket real-time notifications
- ✅ UI updating automatically

---

### **DAY 4: Batch Processing & Orchestration**

#### Developer
**Task 4.1: Airflow DAG - Data Quality** (3 hours)
- Create `dags/daily_batch_dag.py`
- Implement data quality validation task
- Check completeness, freshness, accuracy
- Fail DAG if quality < 95%

**Task 4.2: Airflow DAG - Daily Aggregation** (3 hours)
- Create Spark batch job `batch/daily_aggregation.py`
- Aggregate daily email statistics
- Calculate top senders, busiest hours
- Write to Delta Lake gold layer

**Task 4.3: Airflow DAG - PostgreSQL Export** (2 hours)
- Read gold layer data
- Transform for relational database
- Write to PostgreSQL `daily_insights` table
- Create indexes for query performance

**Deliverables**:
- ✅ Airflow DAG running successfully
- ✅ Daily aggregations in gold layer
- ✅ Data exported to PostgreSQL

---

#### Developer 2 (Backend Engineer) - 8 hours
**Task 4.1: Analytics API** (3 hours)
- Create `/api/analytics/daily` endpoint
- Query PostgreSQL insights table
- Implement aggregation queries
- Add date range filtering

**Task 4.2: Pattern Detection Service** (3 hours)
- Create `services/pattern_service.py`
- Analyze 30-day email patterns
- Detect peak times, top senders
- Calculate productivity metrics
- Generate recommendations

**Task 4.3: Batch Job Monitoring** (2 hours)
- Create endpoint to check Airflow DAG status
- Implement job execution history
- Add alerting for failed jobs

**Deliverables**:
- ✅ Analytics API returning insights
- ✅ Pattern detection generating recommendations
- ✅ Job monitoring endpoints

---

#### Developer
**Task 4.1: Analytics Dashboard UI** (4 hours)
- Create analytics dashboard page
- Display daily insights (charts and metrics)
- Show email volume trends
- Display top senders and subjects
- Add date range selector

**Task 4.2: Pattern Insights UI** (2 hours)
- Create insights component
- Display 30-day patterns
- Show recommendations
- Add visualization (charts)

**Task 4.3: Airflow Integration UI** (2 hours)
- Embed Airflow DAG view (iframe)
- Show job execution status
- Display recent DAG runs
- Add manual trigger button

**Deliverables**:
- ✅ Analytics dashboard with charts
- ✅ Pattern insights displayed
- ✅ Airflow monitoring integrated

---

### **DAY 5: AI Chat Interface & Demo Polish**

#### Developer
**Task 5.1: Complex Query Support** (3 hours)
- Create stored aggregation views in Delta Lake
- Implement join queries (email + calendar)
- Optimize query performance
- Create materialized views in PostgreSQL

**Task 5.2: Calendar Stream Processing** (3 hours)
- Create Spark streaming job for calendar events
- Detect scheduling conflicts
- Analyze meeting patterns
- Write to Delta Lake

**Task 5.3: Demo Data & Testing** (2 hours)
- Create realistic test data generator
- Seed database with demo scenario data
- Test all pipeline components
- Performance tuning

**Deliverables**:
- ✅ Optimized queries for chat interface
- ✅ Calendar data in Delta Lake
- ✅ Demo data loaded and tested

---

#### Developer
**Task 5.1: Text-to-SQL Engine** (4 hours)
- Create `services/text_to_sql.py`
- Implement natural language → SQL using OpenAI
- Add SQL validation and sanitization
- Test with demo queries

**Task 5.2: Chat API Implementation** (4 hours)
- Create `/api/chat` WebSocket endpoint
- Implement conversation flow:
  1. Classify query type
  2. Generate SQL or retrieve context
  3. Execute query
  4. Generate natural language response
- Add conversation history
- Implement streaming responses

**Deliverables**:
- ✅ Text-to-SQL working for demo queries
- ✅ Chat API fully functional
- ✅ All demo scenarios tested

---

#### Developer
**Task 5.1: Chat Interface** (4 hours)
- Create chat UI component
- Implement message rendering
- Add typing indicators
- Display sources and SQL queries
- Handle streaming responses

**Task 5.2: Demo Scenario Implementation** (2 hours)
- Implement all 5 demo scenes
- Create guided demo mode
- Add example prompts/suggestions
- Polish UI/UX

**Task 5.3: Final Integration & Documentation** (2 hours)
- End-to-end testing of all features
- Create demo video script
- Write user documentation
- Create architecture diagram
- Final bug fixes and polish

**Deliverables**:
- ✅ Chat interface complete
- ✅ Demo scenarios working
- ✅ Documentation and video ready

---

## 🔄 Daily Sync Points

### Morning Standup (9:00 AM - 15 min)
- What I completed yesterday
- What I'm working on today
- Any blockers or dependencies

### Integration Check (2:00 PM - 30 min)
- Test integration points
- Resolve conflicts
- Align on interfaces

### End-of-Day Demo (5:00 PM - 30 min)
- Show completed features
- Merge code to main branch
- Plan next day

---

## 🧩 Integration Points & Dependencies

### Developer 1 ↔ Developer 2
- **Day 1**: Kafka topic schemas
- **Day 2**: Message format validation
- **Day 3**: Delta Lake table schemas
- **Day 4**: Gold layer → PostgreSQL schema

### Developer 2 ↔ Developer 3
- **Day 1**: API endpoint contracts
- **Day 2**: Data models and DTOs
- **Day 4**: Analytics API response format
- **Day 5**: Chat API WebSocket protocol

### Developer 1 ↔ Developer 3
- **Day 1**: Docker compose integration
- **Day 3**: Pipeline metrics API
- **Day 5**: Query performance requirements

---

## 📊 Success Metrics by Role

### Developer 
- ✅ 100+ emails/min processing throughput
- ✅ < 5 second latency for stream processing
- ✅ 100% data quality for valid records
- ✅ Airflow DAG success rate > 95%

### Developer 2
- ✅ API response time < 200ms (95th percentile)
- ✅ Text-to-SQL accuracy > 90% for demo queries
- ✅ AI summary quality: coherent and accurate
- ✅ Zero data corruption/loss

### Developer 3
- ✅ UI loads in < 3 seconds
- ✅ Real-time updates within 1 second
- ✅ Mobile responsive design
- ✅ Zero console errors
- ✅ Demo video < 5 minutes, compelling

---

## 🚀 Deployment Checklist (End of Day 5)

### Infrastructure (Dev 3 leads)
- [ ] All services start with `docker-compose up -d`
- [ ] Health checks passing for all services
- [ ] Persistent volumes configured correctly
- [ ] Environment variables documented

### Data Pipeline (Dev 1 leads)
- [ ] Kafka topics receiving messages
- [ ] Spark streaming jobs running
- [ ] Delta Lake layers populated
- [ ] Airflow DAG executing daily

### Application (Dev 2 leads)
- [ ] All API endpoints documented
- [ ] Authentication working
- [ ] Database migrations applied
- [ ] AI integrations functional

### Frontend (Dev 3 leads)
- [ ] Chat interface working
- [ ] Dashboards displaying data
- [ ] Demo scenarios executable
- [ ] Documentation complete

---

## 📝 Submission Requirements

### Code Repository
- Well-organized folder structure
- Clean commit history with meaningful messages
- README with setup instructions
- API documentation (Swagger/OpenAPI)

### Demo Video (5 minutes)
- System architecture overview (30s)
- Data pipeline in action (1m)
- Chat interface demo - all 5 scenarios (3m)
- Technical highlights (30s)

### Documentation
- Architecture diagram (this document)
- Setup guide (step-by-step)
- API documentation
- Troubleshooting guide

### Reflection Document
- What worked well in team collaboration
- Technical challenges and solutions
- What you learned about each technology
- How you'd improve with more time

---

## 💡 Pro Tips for Success

### Communication
- Over-communicate on integration points
- Use shared Slack channel for quick questions
- Document decisions in GitHub issues
- Pair program on complex integration tasks

### Code Quality
- Code review each other's PRs
- Use consistent naming conventions
- Add logging at integration boundaries
- Write integration tests for critical paths

### Time Management
- Don't over-engineer on Day 1-2
- Focus on MVP, then polish
- Leave buffer time for debugging
- Start demo prep early (Day 4)

### Technical
- Use docker logs to debug service issues
- Test Kafka/Spark jobs in isolation first
- Mock AI responses during development
- Use Redis for all expensive queries

Good luck team! 🚀
