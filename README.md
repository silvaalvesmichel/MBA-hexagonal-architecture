# MBA Arquitetura Hexagonal & Clean — Cancelamento de Evento

Fork de [devfullcycle/MBA-hexagonal-architecture](https://github.com/devfullcycle/MBA-hexagonal-architecture),
partindo da branch `clean-arch`, com a feature de **cancelamento de evento** e a **cascata assíncrona de
cancelamento dos ingressos** guiada por evento de domínio.

## Pré-requisitos

- Java 17
- Docker (apenas para subir a aplicação; os testes usam H2 em memória)

## Como subir o projeto

```bash
docker compose up -d
```

```bash
./gradlew :infrastructure:bootRun
```

A aplicação sobe em `http://localhost:8080` (REST) e o GraphiQL fica em `http://localhost:8080/graphiql`.
O MySQL do `docker-compose.yml` sobe em `localhost:3306` com o banco `events`, e o schema é criado pelo Hibernate.

## Como rodar os testes

```bash
./gradlew test
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`. Não é necessário Docker para a suíte.

| Camada | O que cobre | Onde |
|---|---|---|
| Domínio | `Event.cancel()`, registro do `EventCancelled`, bloqueio de reserva em evento cancelado, `Ticket.cancel()` | `domain/src/test` |
| Casos de uso (in-memory) | `CancelEventUseCase`, `GetEventByIdUseCase`, `CancelEventTicketsUseCase`, inscrição em evento cancelado | `application/src/test` |
| Integração | persistência real do estado do evento, outbox, `ticketsByEventId`, REST e GraphQL | `infrastructure/src/test` |
| Ponta a ponta do fluxo assíncrono | `EventCancelled` → fila → ingressos `CANCELLED` | `infrastructure/src/test/.../gateways/EventCancelledFlowIT.java` |

## Onde a cascata de cancelamento acontece

O `CancelEventUseCase` apenas chama `event.cancel()` e persiste o agregado: o `Event` muda para `CANCELLED` e
registra o evento de domínio **`EventCancelled`** (type `event.cancelled`), sem tocar em nenhum `Ticket`. Ao
salvar, o `EventDatabaseRepository` grava esse evento na tabela `outbox` na mesma transação; o `OutboxRelay`
o publica na fila pelo `QueueGateway`; e o `ConsumerQueueGateway` roteia o type `event.cancelled` para o
**`CancelEventTicketsUseCase`**, que busca os ingressos do evento por `TicketRepository.ticketsByEventId` e
chama `ticket.cancel()` em cada um. É o mesmo caminho do fluxo `EventTicketReserved → CreateTicketForCustomerUseCase`
já existente, e a cascata é idempotente: reprocessar o mesmo evento mantém os ingressos `CANCELLED`.

## Endpoints da feature

### REST

| Operação | Requisição | Resposta |
|---|---|---|
| Cancelar evento | `POST /events/{id}/cancel` | `200` `{ "id", "status": "CANCELLED" }` · `422` com a mensagem (`Event not found`, `Event already cancelled`) |
| Consultar evento (completo) | `GET /events/{id}` | `200` `{ "id", "name", "date", "totalSpots", "status" }` · `404` |
| Consultar evento (público) | `GET /events/{id}` com header `X-Public` | `200` `{ "id", "status" }` · `404` |

Inscrever um cliente em evento cancelado (`POST /events/{id}/subscribe`) responde `422` com `Event is cancelled`.

A consulta passa pelo padrão presenter: o `EventController` escolhe entre `GetEventByIdResponseEntity`
(completo) e `PublicGetEventByIdResponseEntity` (reduzido) pelo header `X-Public` e chama
`getEventByIdUseCase.execute(input, presenter)`.

### GraphQL

```graphql
mutation {
    cancelEvent(id: "<event-id>") { id status }
}

query {
    eventOfId(id: "<event-id>") { id name date totalSpots status }
}
```
