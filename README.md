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
    config/         # Security, OpenAPI, Redis, AWS, Async
    controller/     # REST endpoints + request/response DTOs
      request/
      response/
  domain/
    service/        # Regras de negocio
    exception/      # Exceptions tipadas com HTTP status
  resource/
    model/          # Entidades JPA
    repositories/   # Spring Data JPA + native queries
    messaging/      # SNS publisher (async) + event DTOs
```

Cada service acessa apenas seu proprio repository. `TransactionService` opera transacoes, `PartnerService` opera saldos. Sem acoplamento cruzado.

## Decisoes tecnicas

**Seguranca** — Autenticacao via API key no header `X-API-Key`. Comparacao constant-time com `MessageDigest.isEqual` para prevencao de timing attacks. Filtro registrado como `@Bean` no `SecurityFilterChain` (nao como `@Component`, evitando dupla execucao). Endpoints publicos: `/actuator/health`, Swagger UI, OpenAPI docs. Credenciais externalizadas via env vars sem defaults em producao (`API_KEY` falha fast se ausente).

**Concorrencia** — Updates atomicos via SQL nativo (`UPDATE partner SET balance = balance + :amount WHERE id = :id AND balance >= :amount`). Sem pessimistic locking, sem optimistic locking. Row count valida sucesso da operacao.

**Idempotencia** — Header `Idempotency-Key` com unique constraint na coluna `idempotency_key`. Validacao de formato (nao-vazio, max 64 caracteres). Tanto transacoes COMPLETED quanto FAILED rejeitam reuso da mesma key (409 Conflict). Race conditions na constraint tratadas via `DataIntegrityViolationException` handler.

**Ciclo de vida da transacao** — `PENDING -> COMPLETED | FAILED`. Transacao salva como PENDING antes da operacao de saldo. Em caso de falha, status atualizado para FAILED com razao controlada (strings fixas, sem vazamento de dados internos).

**IDOR prevention** — Lookup de transacao por ID valida ownership do partner (`findByIdAndPartnerId`). Mensagens de erro nao expoe IDs internos.

**Conciliacao** — Job `@Scheduled` (cron configuravel) reprocessa transacoes PENDING alem do timeout. Processamento paralelo via `CompletableFuture` com thread pool dedicado. ShedLock garante execucao unica em ambiente distribuido.

**Cache** — Redis read-through no saldo do parceiro. Evict on write. Fail-safe: erros de Redis nao propagam.

**IDs** — UUID nativo do PostgreSQL com `gen_random_uuid()` como default. Entidades com `id: UUID? = null` + `@GeneratedValue` — JPA usa `persist()` para novas entidades, eliminando conflitos de merge sob concorrencia.

**Notificacoes** — SNS publish assincrono (`@Async` com thread pool dedicado) para transacoes completadas. Fire-and-forget — falhas logadas sem propagar para o request. SQS queue subscrita ao topico para consumo futuro pelo `communication-service`.

**Performance** — JVM com ZGC Generational (sub-ms GC pauses) e container memory awareness. HikariCP tuned (20 max connections). Indice composto `(status, created_at)` para query de conciliacao. `subtractBalance` usa `findBalanceById` para disambiguar "not found" vs "insufficient balance" em single query.

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

Headers obrigatorios para credit/debit: `X-API-Key`, `Idempotency-Key`

### Validacao de input

- `amount`: minimo 0.01
- `description`: obrigatorio, max 500 caracteres
- `Idempotency-Key`: obrigatorio, nao-vazio, max 64 caracteres

## Como rodar

### Requisitos

- Docker e Docker Compose
- Java 21 (para desenvolvimento local)

### Subir tudo via Docker

```bash
docker-compose up -d
```

### Subir com dados de demonstracao

```bash
SPRING_PROFILES_ACTIVE=demo docker-compose up -d
```

O profile `demo` popula o banco com 3 parceiros e 12 transacoes de exemplo:
- **Acme Corporation** — saldo saudavel, multiplas transacoes de credito e debito
- **Globex Industries** — saldo moderado, inclui uma transacao FAILED
- **Initech Solutions** — saldo zerado, credito consumido e tentativa de debito rejeitada

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

# Rodar com dados de demonstracao
cd credit-service && SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun

# Testes unitarios
cd credit-service && ./gradlew test

# Testes de integracao (requer Docker para Testcontainers)
cd credit-service && ./gradlew integrationTest
```

### Variaveis de ambiente

Veja `.env.example` na raiz do projeto. Variaveis principais:

| Variavel | Descricao | Default |
|----------|-----------|---------|
| `API_KEY` | Chave de autenticacao da API | _(obrigatorio)_ |
| `DB_USERNAME` | Usuario do PostgreSQL | `postgres` |
| `DB_PASSWORD` | Senha do PostgreSQL | `postgres` |
| `AWS_ACCESS_KEY` | Access key AWS/LocalStack | `test` |
| `AWS_SECRET_KEY` | Secret key AWS/LocalStack | `test` |
| `SPRING_PROFILES_ACTIVE` | Profiles do Spring (ex: `demo`) | _(vazio)_ |

## Testes

- **Unit tests (73)** — Services, controllers (MockMvc), security (auth filter, endpoint protection), cache, notification publisher, exception handling. Isolados com Mockito.
- **Integration tests (8)** — Concorrencia e idempotencia contra PostgreSQL real via Testcontainers:
  - 20 creditos concorrentes: valida saldo final correto
  - 20 debitos concorrentes com saldo de 100: valida que saldo nunca fica negativo
  - 10 creditos + 10 debitos concorrentes: valida consistencia do saldo
  - Idempotencia: requests duplicados retornam 409, requests concorrentes com mesma key nao geram duplicatas

## Estrutura do repositorio

```
vps-kotlin-challenge/
  docker-compose.yml
  .env.example
  docker-compose/
    init/
      postgres-init.sql
      localstack-init.sh
  credit-service/
    Dockerfile
    build.gradle.kts
    .editorconfig
    src/
      main/resources/
        application.yaml
        application-demo.yaml
        db/migration/          # Flyway migrations
        db/seed/               # Demo data (profile: demo)
  communication-service/       # futuro
```
