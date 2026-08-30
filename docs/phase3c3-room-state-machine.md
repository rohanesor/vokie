# Phase 3C.3 Room state machine

## Status: PARTIAL

Room remains authoritative for messages and now persists sequence number, TTL, priority, checksum, retry count, transport, and last error. The inbound coordinator persists a replay identity before emitting the application message.

The target transitions are:

```text
QUEUED -> SENDING -> ACKNOWLEDGED
SENDING -> RETRYING -> SENDING
QUEUED/RETRYING/SENDING -> EXPIRED
RETRYING -> FAILED
```

Missing production work includes `nextRetryAt`, a restart-safe retry worker, a local TTL expiration worker, and explicit manager-owned ACK state transitions. At-least-once delivery remains the safe assumption.
