# spring7-reactive

Spring Framework v7+ / Spring Boot v4+

Spring WebFlux, Spring Data R2DBC, Spring WebClient, Project Reactor

## Streaming endpoint

- SSE endpoint: `GET /api/accounts/{accountId}/transactions/stream` (produces `text/event-stream`)
- Sample usage:
  ```bash
  curl -N http://localhost:8080/api/accounts/{accountId}/transactions/stream
  ```
- Backed by `TransactionService.streamTransactions`, which polls the latest ledger entries and emits them as Server-Sent Events (SSEs).

## FX conversion endpoint

- HTTP endpoint: `POST /api/fx/convert?from=EUR&to=USD&amount=100`
- Powered by `FxClient`/`FxService` using a dedicated `WebClient` plus Reactor `timeout`/`retryWhen`/`onStatus` for resilience.
- Set `FX_ACCESS_KEY` in `.env` (auto-loaded via `spring.config.import`) so requests include the exchange rate provider access key.
- Responses include an `FxQuote` record (source/target currency, requested amount, rate—even derived if the upstream omits it—converted amount, timestamp).
