# VPS Credit Management Platform

Plataforma B2B de gestao de creditos para parceiros. Operacoes de credito e debito com alta concorrencia, idempotencia, conciliacao automatica e notificacoes via mensageria.

## Stack

| Camada       | Tecnologia                          |
|-------------|--------------------------------------|
| Linguagem   | Kotlin 2.3.21 + Java 21             |
| Framework   | Spring Boot 4.1.2                    |
| Banco       | PostgreSQL 15.3 (UUID nativo, ACID)  |
| Cache       | Redis 7                              |
| Mensageria  | SNS/SQS via LocalStack               |
| Migrations  | Flyway                               |
| Docs API    | SpringDoc OpenAPI 2.8.8 (Swagger UI) |
| Build       | Gradle 8.14 (Kotlin DSL)             |
| Container   | Docker multi-stage + docker-compose   |
| Testes      | JUnit 5, Mockito-Kotlin, Testcontainers |

## Arquitetura

```
credit-service/
  application/
    config/         # OpenAPI, Redis, AWS
    controller/     # REST endpoints + request/response DTOs
      request/
      response/
  domain/
    service/        # Regras de negocio
    exception/      # Exceptions tipadas com HTTP status
  resource/
    model/          # Entidades JPA
    repositories/   # Spring Data JPA + native queries
    messaging/      # SNS publisher + event DTOs
```

Cada service acessa apenas seu proprio repository. `TransactionService` opera transacoes, `PartnerService` opera saldos. Sem acoplamento cruzado.

## Decisoes tecnicas

**Concorrencia** — Updates atomicos via SQL nativo (`UPDATE partner SET balance = balance + :amount WHERE id = :id AND balance >= :amount`). Sem pessimistic locking, sem optimistic locking. Row count valida sucesso da operacao.

**Idempotencia** — Header `Idempotency-Key` com unique constraint na coluna `idempotency_key`. Requests duplicados retornam 409.

**Ciclo de vida da transacao** — `PENDING -> COMPLETED | FAILED`. Transacao salva como PENDING antes da operacao de saldo. Em caso de falha, status atualizado para FAILED com razao.

**Conciliacao** — Job `@Scheduled` (cron configuravel) reprocessa transacoes PENDING alem do timeout. Trata erros individualmente por transacao.

**Cache** — Redis read-through no saldo do parceiro. Evict on write. Fail-safe: erros de Redis nao propagam.

**IDs** — UUID nativo do PostgreSQL com `gen_random_uuid()` como default. Entidades com `id: UUID? = null` + `@GeneratedValue` — JPA usa `persist()` para novas entidades, eliminando conflitos de merge sob concorrencia.

**Notificacoes** — SNS publish fire-and-forget para transacoes completadas. SQS queue subscrita ao topico para consumo futuro pelo `communication-service`.

## Endpoints

### Partners

| Metodo | Path                          | Descricao              |
|--------|-------------------------------|------------------------|
| POST   | `/partners`                   | Criar parceiro         |
| GET    | `/partners/{id}`              | Buscar parceiro        |
| GET    | `/partners/{id}/balance`      | Consultar saldo        |

### Transactions

| Metodo | Path                                      | Descricao                |
|--------|--------------------------------------------|--------------------------|
| POST   | `/partners/{id}/transactions/credit`       | Adicionar credito        |
| POST   | `/partners/{id}/transactions/debit`        | Consumir credito         |
| GET    | `/partners/{id}/transactions`              | Historico (paginado)     |
| GET    | `/partners/{id}/transactions/{txId}`       | Detalhe da transacao     |

Headers obrigatorios para credit/debit: `Idempotency-Key`

## Como rodar

### Requisitos

- Docker e Docker Compose
- Java 21 (para desenvolvimento local)

### Subir tudo via Docker

```bash
docker-compose up -d
```

Servicos disponiveis:
- **Credit Service**: http://localhost:7200
- **Swagger UI**: http://localhost:7200/swagger-ui.html
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **LocalStack (SNS/SQS)**: localhost:4566

### Desenvolvimento local

```bash
# Subir infra
docker-compose up -d vps-db vps-redis localstack

# Rodar o servico
cd credit-service && ./gradlew bootRun

# Testes unitarios
cd credit-service && ./gradlew test

# Testes de integracao (requer Docker para Testcontainers)
cd credit-service && ./gradlew integrationTest
```

## Testes

- **Unit tests (36)** — Services, controllers (MockMvc), exception handling. Isolados com Mockito.
- **Integration tests (3)** — Concorrencia contra PostgreSQL real via Testcontainers:
  - 20 creditos concorrentes: valida saldo final correto
  - 20 debitos concorrentes com saldo de 100: valida que saldo nunca fica negativo
  - 10 creditos + 10 debitos concorrentes: valida consistencia do saldo

## Estrutura do repositorio

```
vps-kotlin-challenge/
  docker-compose.yml
  docker-compose/
    init/
      postgres-init.sql
      localstack-init.sh
  credit-service/
    Dockerfile
    build.gradle.kts
    src/
  communication-service/    # futuro
```
