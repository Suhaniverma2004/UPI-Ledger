# Eventing

Owns the transactional outbox and Kafka event contracts.

## V5 responsibilities

- Persist outbound events in PostgreSQL in the same transaction as payment/ledger state changes.
- Publish committed outbox events asynchronously to Kafka.
- Retry failed publications using the `FAILED` outbox state.
- Treat Kafka delivery as at-least-once: consumers must be idempotent because a crash after Kafka acknowledgement but before the outbox row is marked `PUBLISHED` can produce a duplicate.

PostgreSQL remains the source of truth. Kafka is the asynchronous event transport.
