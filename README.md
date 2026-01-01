# spring7-reactive

Spring Framework v7+ / Spring Boot v4+

Spring WebFlux, Spring Data R2DBC, Spring WebClient, Project Reactor

## Streaming endpoint

- SSE endpoint: `GET /api/accounts/{accountId}/transactions/stream` (produces `text/event-stream`)
- Sample usage:
  ```bash
  curl -N http://localhost:8080/api/accounts/{accountId}/transactions/stream
  ```
- Backed by `TransactionService.streamTransactions`, which polls the latest ledger entries and emits them as Server-Sent Events.
