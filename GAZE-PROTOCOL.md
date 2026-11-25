# Standalone Native-Gaze Subscription Protocol

**English** | [简体中文](GAZE-PROTOCOL_zh.md) · [README](README.md)

This document specifies the direct UDP subscription protocol that lets a PC gaze consumer read native eye-gaze samples from PicoFacialBridge without VRCFaceTracking, `UnifiedPicoModule`, or a PC-side relay.

---

## Transport & Coexistence

- **Headset endpoint:** Reuses PicoFacialBridge's existing **UDP 9030** socket on `wlan0`.
- **Client endpoint:** The gaze client binds its own PC UDP port (typically an ephemeral port) and sends commands directly to `<headset-ipv4>:9030`. Acknowledgements and data packets return to that exact client IP and port. Do not bind PC port `9030` (used by `UnifiedPicoModule`).
- **Coexistence with BridgeSplit:** PicoFacialBridge serves one BridgeSplit avatar client and one standalone gaze client concurrently. Their subscription IDs, epochs, stop commands, and timeouts are completely separate.
- **Single gaze owner:** A second gaze endpoint cannot take over an active gaze subscription until the current lease expires or the active owner unsubscribes.
- **Shared local eye switch:** The headset's local **Transmit eye tracking** switch gates both avatar eye output and standalone gaze output. Remote clients cannot override it.
- **Security boundary:** Trusted-LAN protocol with no encryption or cryptographic authentication. Subscription IDs distinguish sessions but are not authentication tokens.

---

## Control Commands

All control messages use pure ASCII with **no trailing newline and no NUL byte**. Every `id` is exactly 16 lowercase hexadecimal characters (`[0-9a-f]{16}`), generated randomly by the client.

| Command | Direction | Wire Format | Meaning |
|---|---|---|---|
| **Subscribe / Renew** | PC → Headset | `PXR_GAZE_SUB id=<16-hex>` | Starts a gaze session or renews an active lease. |
| **Acknowledge** | Headset → PC | `PXR_GAZE_ACK id=<16-hex> epoch=<16-hex> enabled=<0\|1>` | Confirms active subscription, current epoch, and local eye-switch state. |
| **Unsubscribe** | PC → Headset | `PXR_GAZE_STOP id=<16-hex>` | Ends the gaze session immediately without affecting any avatar client. |

### Example Exchange

```text
PC      -> Headset : PXR_GAZE_SUB id=0123456789abcdef
Headset -> PC      : PXR_GAZE_ACK id=0123456789abcdef epoch=000000003b9aca00 enabled=1
PC      -> Headset : PXR_GAZE_STOP id=0123456789abcdef
```

---

## Lease Lifecycle & Error Handling

### Renewal & Idempotency
- Send `PXR_GAZE_SUB` every **1000 ms** to renew the **3000 ms** headset-side lease.
- Repeating `PXR_GAZE_SUB` from the same endpoint with the same `id` is idempotent: it extends the lease and replies with `PXR_GAZE_ACK` without resetting the epoch or source timestamp watermark.
- Sending `PXR_GAZE_SUB` from the same endpoint with a **new** `id` starts a new gaze session and advances the epoch.
- `PXR_GAZE_SUB` or `PXR_GAZE_STOP` from a different endpoint while a lease is active is ignored.

### Epochs & Local Switch Changes
- `epoch` is a positive signed 64-bit integer formatted as 16 lowercase hexadecimal digits.
- `enabled=0` indicates that the headset's **Transmit eye tracking** switch is off. The client may continue renewing the lease, but no `E` samples are sent until the switch is turned back on.
- Toggling the local eye switch, switching tracking mode, or restarting the underlying tracking session advances the gaze `epoch` and sends a fresh `PXR_GAZE_ACK` before any samples for the new epoch are transmitted. None of these events alter the BridgeSplit avatar session's ID or epoch.

### Client Validation & Recovery Rules
1. Verify that every incoming `PXR_GAZE_ACK` and `E` packet comes from the expected headset endpoint and matches the active `id` / `epoch`.
2. Never allow an older ACK to roll back an active `epoch`.
3. Treat duplicate ACKs as renewing control freshness only, never sample freshness.
4. If no valid `PXR_GAZE_ACK` arrives for **3000 ms**, stop forwarding gaze immediately, rotate to a brand-new random 16-hex `id`, re-subscribe, and ignore delayed ACKs carrying the old `id`. Rotating the ID allows a lower epoch to be established cleanly after a headset reboot without resurrecting a stale session.
5. On clean exit, send only `PXR_GAZE_STOP id=<active-id>`. Never send BridgeSplit commands (`DISCOVER_DAEMON`, `PXR_SUB`, `POLO`, or `STOP`) from a gaze client.

### Headset Send Failures
If sending an ACK or `E` packet throws a socket I/O error on the headset, PicoFacialBridge clears the gaze subscription immediately and records the failure in the **Gaze** tab without affecting the avatar client.

---

## Sample Wire Format (`E` Packet)

Samples use the 83-byte little-endian native `E` datagram:

| Offset | Size (bytes) | Type | Field |
|---:|---:|---|---|
| `0` | 1 | `uint8` | Tag: ASCII `'E'` (`0x45`) |
| `1` | 1 | `uint8` | Channel mask: always `1` |
| `2` | 1 | `uint8` | Morphology flags: always `0` |
| `3` | 8 | `int64` | Gaze subscription `epoch` |
| `11` | 8 | `int64` | Native eye source timestamp (headset monotonic nanoseconds) |
| `19` | 4 | `uint32` | Left eye native status word |
| `23` | 4 | `uint32` | Right eye native status word |
| `27` | 4 | `uint32` | Combined native status word |
| `31` | 12 | `float32[3]` | Left eye direction vector `(x, y, z)` |
| `43` | 12 | `float32[3]` | Right eye direction vector `(x, y, z)` |
| `55` | 12 | `float32[3]` | Combined direction vector `(x, y, z)` |
| `67` | 4 | `float32` | Left eye openness (`0..1`) |
| `71` | 4 | `float32` | Right eye openness (`0..1`) |
| `75` | 4 | `float32` | Left raw pupil value (`mm = raw / 10`) |
| `79` | 4 | `float32` | Right raw pupil value (`mm = raw / 10`) |

### Validity, Coordinates & Freshness Rules

- **Combined gaze validity:** Check bit `0x002` in the combined status word at offset `27` (not the morphology byte at offset `2`). The standalone gaze path strips per-eye gaze validity bits (`0x080` and `0x100`) so consumers use the measured combined direction rather than firmware's fixed-depth split. Non-finite source floats are sanitized to `0.0f` with their corresponding status bits cleared.
- **Coordinate system:** Head-relative right / up / back (`+X` right, `+Y` up, `+Z` back, forward along `-Z`). It is a unit-style direction vector, not a screen coordinate or convergence distance; rendering adapters must apply the headset's eye transforms/projections and avoid double-applying head pose compensation.
- **Latest-only freshness:** On each poll, PicoFacialBridge forwards only the newest eye observation whose timestamp strictly advances past the previous sent timestamp and whose age is **≤ 150 ms** (`150_000_000 ns`) in the headset's monotonic clock domain. Historical ring-buffer frames are never replayed into a new gaze session.
- **Invalidation forwarding:** A fresh observation whose combined gaze is invalid is still sent so the consumer can immediately stop holding the previous direction.
- **No extra streams:** Standalone gaze applies no smoothing or interpolation and never emits `A`, `F`, or `X` expression packets.
- **Clock domains:** Do not pass the headset monotonic timestamp (offset `11`) directly as a Windows QPC timestamp. Use it for source deduplication and ordering, and stamp consumer APIs with the local PC clock.

---

## Reference Implementation & Host Tests

| File | Responsibility |
|---|---|
| `bridge/src/main/java/dev/pico/facialprobe/GazeSubscription.java` | Independent lease, epoch state machine, and latest-sample selection |
| `bridge/src/main/java/dev/pico/facialprobe/BridgeService.java` | Namespace dispatch on UDP 9030, combined capture demand, and isolated gaze diagnostics |
| `tests/GazeSubscriptionTest.java` | Lease, local eye-switch, invalidation, freshness, and BridgeSplit peer-isolation tests |
| `tests/GazeLoopbackServer.java` | Synthetic host fixture using the real protocol/framing classes over a loopback port |

Run the full host suite or the focused gaze tests:

```sh
# Linux / macOS / Git Bash
bash tests/run-host-tests.sh

# Windows PowerShell
pwsh -File tests/run-gaze.ps1
```
