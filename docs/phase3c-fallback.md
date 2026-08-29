# Phase 3C transport fallback

Desired priority:

```text
Wi-Fi Direct -> Bluetooth RFCOMM -> Room QUEUED
```

The existing retry policy is bounded and persisted for outbound messages. Full cross-transport selection and automatic retry from a Wi-Fi Direct disconnect through Bluetooth are **NOT YET IMPLEMENTED** in `TransportManager`; this is a remaining production blocker, not a simulated capability.

A socket disconnect must leave the Room message queued/retrying rather than delete it. ACK remains transport/application receipt only and never means TTS playback or user acknowledgement.
