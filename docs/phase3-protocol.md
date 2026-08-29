# Phase 3 transport protocol status

## Current status

The existing transport-independent `VokieProtocol` is retained as the current text framing implementation. It validates magic, version, language, IDs, payload size, and ACK frames. Bluetooth uses length-prefixed protocol frames, so packet bytes are independent of the underlying stream.

## Required Packet v2 work

Before production approval, add and test explicit fields for message ID, sequence number, language code, payload length, payload, CRC, fragmentation, ordering, duplicate/replay protection, TTL, ACK, and retry state. These changes require a protocol-versioned migration and interoperability tests; they are not silently claimed by the current v1 implementation.

The packet must remain transport-independent:

```text
Packet v2 -> Wi-Fi Direct TCP or Bluetooth RFCOMM framing
```

No cloud relay or internet dependency is permitted. A message must remain queued in Room when a transport fails, and `Delivered` must only be emitted after an actual acknowledgement.
