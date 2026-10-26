# VPS Kotlin Challenge - Credit Management Platform

## Projeto
Plataforma B2B de gestão de créditos para parceiros. Arquitetura multi-serviço com alta concorrência, idempotência e conciliação de transações.

## Stack
- **Linguagem:** Kotlin 2.3.21 + Java 21
- **Framework:** Spring Boot 4.1.2
- **Banco:** PostgreSQL 15.3 (transações ACID)
- **Cache:** Redis 7
- **Mensageria:** SQS/SNS via LocalStack
- **Migrations:** Flyway
- **Docs:** SpringDoc OpenAPI 2.8.8 (Swagger)
- **Build:** Gradle 8.14 Kotlin DSL
- **Container:** Docker multi-stage + Docker Compose

## Estrutura do Repositório
```
vps-kotlin-challenge/
├── docker-compose.yml          # Infraestrutura compartilhada
├── docker-compose/
│   └── init/                   # Scripts de inicialização (postgres, localstack)
├── credit-service/             # Microserviço de créditos
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
└── communication-service/      # (futuro) Serviço de notificações email/sms
```

## Arquitetura (credit-service)
Layered architecture seguindo padrões do RichBank:
```
br.com.vps.creditservice/
├── CreditServiceApplication.kt
├── application/
│   ├── config/
│   └── controller/
│       ├── request/
│       ├── response/
│       └── RestExceptionHandler.kt
├── domain/
│   ├── service/
│   └── exception/
└── resource/
    ├── model/
    ├── repositories/
    └── messaging/
```

## Portas
- credit-service: 7200
- communication-service: 7201 (futuro)
- PostgreSQL: 5432
- Redis: 6379
- LocalStack: 4566

## Comandos
```bash
# Infra
docker compose up -d vps-db vps-redis localstack

# Build credit-service
cd credit-service && ./gradlew build

# Run local
cd credit-service && ./gradlew bootRun

# Run tudo via Docker
docker compose up -d

# Testes
cd credit-service && ./gradlew test
```
