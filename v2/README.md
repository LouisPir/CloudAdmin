# Netflix API — V2/V3 (Spring Boot + Redis)

## What changed from V1

| Component         | V1 (Python/Flask)                  | V2/V3 (Java/Spring Boot)                 |
|-------------------|------------------------------------|------------------------------------------|
| Web server        | Flask dev server (1 thread)        | Embedded Tomcat (200 threads)            |
| Connection pool   | mysql-connector pool_size=5        | HikariCP pool_size=20                    |
| DB indexes        | None (only PRIMARY KEY)            | 10 indexes (B-tree + FULLTEXT)           |
| Title search      | LIKE '%term%' (full table scan)    | MATCH() AGAINST() (FULLTEXT index)       |
| Concurrency model | Single-threaded (GIL)              | Multi-threaded (JVM)                     |
| Caching           | None                               | Redis (60s TTL on 4 aggregation endpoints)|

## Architecture

```
                    +----------------+
  k6 (100 VUs) --> |  Tomcat         |
                    |  200 threads    |
                    +-------+--------+
                            |
              +-------------+-------------+
              v                           v
    +--------------------+    +--------------------+
    |  4 cached routes    |    |  4 direct routes    |
    |  top-directors      |    |  list shows         |
    |  top-genres         |    |  single show        |
    |  stats/categories   |    |  search             |
    |  stats/yearly       |    |  filter             |
    +---------+----------+    +---------+----------+
              |                         |
              v                         |
    +----------------+                  |
    |    Redis       | <-- miss         |
    |  (60s TTL)     |     falls        |
    +-------+--------+     through -->  |
            |                           |
            v                           v
    +------------------------------------------+
    |          MySQL (HikariCP x20)            |
    |    + B-tree indexes + FULLTEXT           |
    +------------------------------------------+
```

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0 with database_netflix loaded (same as V1)
- Redis 7+ running on localhost:6379

### Installing Redis (Windows)

Option A: WSL — sudo apt install redis-server && sudo service redis-server start
Option B: Docker — docker run -d --name redis -p 6379:6379 redis:7
Option C: Memurai (native Windows Redis alternative) — https://www.memurai.com/

## Setup

### 1. Apply database indexes (run once)

    mysql -u root -proot < indexes.sql

### 2. Start Redis

    redis-server

### 3. Build the project

    cd v2-spring
    mvn clean package -DskipTests

### 4. Run the API

    java -jar target/netflix-api-2.0.0.jar

Starts on http://localhost:8000 (same port as V1).

### 5. Run the load test

    k6 run loadtest.js

### 6. Check cache hit rate after the test

    curl http://localhost:8000/netflix/cache/stats

## Project structure

```
v2-spring/
  pom.xml                              Maven config (Spring Boot + MySQL + Redis)
  indexes.sql                          DB indexes to apply before testing
  loadtest.js                          Fixed k6 script (now includes p50/p99)
  src/main/resources/
    application.properties             DB + HikariCP + Tomcat + Redis config
  src/main/java/com/netflix/
    Application.java                   Entry point
    config/
      RedisConfig.java                 Redis serialization setup
    model/
      Show.java                        Table mapping (Java record)
    repository/
      ShowRepository.java              SQL queries (JdbcTemplate)
    service/
      CacheService.java                Cache-aside logic (Redis + TTL)
    controller/
      ShowController.java              8 REST endpoints (4 cached)
```

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| HikariCP 20 connections | Handles 100+ VUs without pool exhaustion | More MySQL memory |
| B-tree indexes | Filter/group queries use index scans | Slower INSERTs (irrelevant, read-only) |
| FULLTEXT index on title | Search avoids full table scans | Does not match partial words (fallback to LIKE) |
| Redis 60s TTL | Aggregations from memory 99%+ of the time | Results can be up to 60s stale |
| Cache-aside pattern | Simple, cache failure does not break API | First request after expiry is slow |
| Tomcat 200 threads | 200x concurrency vs Flask | Higher memory per thread |
