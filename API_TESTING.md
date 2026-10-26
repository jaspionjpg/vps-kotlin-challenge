# Guia de Testes da API

Passo a passo para testar todos os endpoints da plataforma de creditos.

## Pré-requisitos

```bash
# Subir todos os servicos
docker compose up -d

# Aguardar o credit-service ficar healthy (~30s)
docker compose ps

# Verificar health
curl http://localhost:7200/actuator/health
```

A API key default em dev é `changeme-dev-only`. Todos os curls abaixo usam essa key.

> Swagger UI disponivel em http://localhost:7200/swagger-ui.html

---

## 1. Criar parceiro

```bash
curl -s -X POST http://localhost:7200/partners \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -d '{"externalId": "PARTNER-001", "name": "Empresa Teste"}' | jq .
```

Resposta esperada (201):
```json
{
  "id": "uuid-gerado",
  "externalId": "PARTNER-001",
  "name": "Empresa Teste",
  "balance": 0,
  "createdAt": "..."
}
```

**Salve o `id` retornado** — ele sera usado em todos os proximos comandos:

```bash
PARTNER_ID="cole-o-uuid-aqui"
```

---

## 2. Consultar parceiro

```bash
curl -s http://localhost:7200/partners/$PARTNER_ID \
  -H "X-API-Key: changeme-dev-only" | jq .
```

---

## 3. Adicionar credito

```bash
curl -s -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: credit-001" \
  -d '{"amount": 1000.00, "description": "Carga inicial de creditos"}' | jq .
```

Resposta esperada (201):
```json
{
  "id": "uuid-transacao",
  "partnerId": "uuid-parceiro",
  "type": "CREDIT",
  "amount": 1000.00,
  "description": "Carga inicial de creditos",
  "status": "COMPLETED",
  "createdAt": "..."
}
```

---

## 4. Consultar saldo

```bash
curl -s http://localhost:7200/partners/$PARTNER_ID/balance \
  -H "X-API-Key: changeme-dev-only" | jq .
```

Resposta esperada: `{"balance": 1000.0000}`

---

## 5. Consumir credito (debito)

```bash
curl -s -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/debit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: debit-001" \
  -d '{"amount": 350.00, "description": "Consumo de servico X"}' | jq .
```

Saldo apos: `650.00`

---

## 6. Consultar historico de transacoes

```bash
curl -s "http://localhost:7200/partners/$PARTNER_ID/transactions?page=0&size=10" \
  -H "X-API-Key: changeme-dev-only" | jq .
```

---

## 7. Consultar transacao especifica

```bash
TX_ID="cole-o-uuid-da-transacao"

curl -s http://localhost:7200/partners/$PARTNER_ID/transactions/$TX_ID \
  -H "X-API-Key: changeme-dev-only" | jq .
```

---

## 8. Testar idempotencia (mesma key = 409)

```bash
# Repetir o mesmo credit com a mesma Idempotency-Key
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: credit-001" \
  -d '{"amount": 1000.00, "description": "Tentativa duplicada"}' | jq .
```

Resposta esperada: **409 Conflict** — saldo nao muda.

---

## 9. Testar saldo insuficiente

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/debit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: debit-big" \
  -d '{"amount": 99999.00, "description": "Debito maior que saldo"}' | jq .
```

Resposta esperada: **422** com status `FAILED` e razao "Insufficient balance". Saldo nao muda.

---

## 10. Testar validacoes de input

### Amount zero/negativo (400)

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: val-001" \
  -d '{"amount": 0, "description": "Valor invalido"}' | jq .
```

### Description vazia (400)

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: val-002" \
  -d '{"amount": 10.00, "description": ""}' | jq .
```

### Body malformado (400)

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -H "Idempotency-Key: val-003" \
  -d '{invalid json}' | jq .
```

---

## 11. Testar autenticacao (401)

### Sem API key

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:7200/partners/$PARTNER_ID | jq .
```

### API key errada

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:7200/partners/$PARTNER_ID \
  -H "X-API-Key: chave-errada" | jq .
```

Resposta esperada: **401 Unauthorized**

---

## 12. Testar parceiro inexistente (404)

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:7200/partners/00000000-0000-0000-0000-000000000000 \
  -H "X-API-Key: changeme-dev-only" | jq .
```

---

## 13. Testar parceiro duplicado (409)

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:7200/partners \
  -H "Content-Type: application/json" \
  -H "X-API-Key: changeme-dev-only" \
  -d '{"externalId": "PARTNER-001", "name": "Duplicado"}' | jq .
```

---

## 14. Testar concorrencia (50 creditos simultaneos)

```bash
# Adicionar 50 creditos de R$10 em paralelo
for i in $(seq 1 50); do
  curl -s -X POST http://localhost:7200/partners/$PARTNER_ID/transactions/credit \
    -H "Content-Type: application/json" \
    -H "X-API-Key: changeme-dev-only" \
    -H "Idempotency-Key: concurrent-$i" \
    -d "{\"amount\": 10.00, \"description\": \"Credito concorrente $i\"}" &
done
wait

# Verificar saldo — deve ter aumentado exatamente R$500
curl -s http://localhost:7200/partners/$PARTNER_ID/balance \
  -H "X-API-Key: changeme-dev-only" | jq .
```

Saldo esperado: saldo anterior + 500.00 (50 x R$10.00). Nenhum credito perdido.

---

## Fluxo completo (copiar e colar)

Script para rodar o fluxo inteiro de uma vez:

```bash
API="http://localhost:7200"
KEY="changeme-dev-only"

echo "=== Criar parceiro ==="
RESPONSE=$(curl -s -X POST $API/partners \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d '{"externalId": "EVAL-TEST", "name": "Parceiro Avaliacao"}')
echo $RESPONSE | jq .
PARTNER_ID=$(echo $RESPONSE | jq -r '.id')
echo "Partner ID: $PARTNER_ID"

echo -e "\n=== Credito R\$1000 ==="
curl -s -X POST $API/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -H "Idempotency-Key: eval-credit-1" \
  -d '{"amount": 1000.00, "description": "Carga inicial"}' | jq .

echo -e "\n=== Saldo apos credito ==="
curl -s $API/partners/$PARTNER_ID/balance -H "X-API-Key: $KEY" | jq .

echo -e "\n=== Debito R\$250 ==="
curl -s -X POST $API/partners/$PARTNER_ID/transactions/debit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -H "Idempotency-Key: eval-debit-1" \
  -d '{"amount": 250.00, "description": "Consumo"}' | jq .

echo -e "\n=== Saldo apos debito (esperado: 750) ==="
curl -s $API/partners/$PARTNER_ID/balance -H "X-API-Key: $KEY" | jq .

echo -e "\n=== Idempotencia — repetir mesmo credito (esperado: 409) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST $API/partners/$PARTNER_ID/transactions/credit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -H "Idempotency-Key: eval-credit-1" \
  -d '{"amount": 1000.00, "description": "Duplicado"}' | jq .

echo -e "\n=== Saldo insuficiente (esperado: 422) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST $API/partners/$PARTNER_ID/transactions/debit \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -H "Idempotency-Key: eval-debit-big" \
  -d '{"amount": 99999.00, "description": "Debito impossivel"}' | jq .

echo -e "\n=== Saldo final (esperado: 750 — nao mudou) ==="
curl -s $API/partners/$PARTNER_ID/balance -H "X-API-Key: $KEY" | jq .

echo -e "\n=== Historico ==="
curl -s "$API/partners/$PARTNER_ID/transactions?page=0&size=10" \
  -H "X-API-Key: $KEY" | jq .

echo -e "\n=== Auth sem key (esperado: 401) ==="
curl -s -w "HTTP Status: %{http_code}\n" $API/partners/$PARTNER_ID

echo -e "\n=== Parceiro inexistente (esperado: 404) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  $API/partners/00000000-0000-0000-0000-000000000000 \
  -H "X-API-Key: $KEY" | jq .
```
