# ACK registry

## Status: BLOCKED

Bluetooth retains its existing ACK tracker. A single manager-owned pending registry keyed by `messageId + sequenceNumber`, with transport ownership, timeout, disconnect cancellation, and both Wi-Fi/Bluetooth correlation, is not implemented.

Bytes written to a socket are not treated as acknowledgement. ACK remains receipt-only, not PLAYED or HEARD.
