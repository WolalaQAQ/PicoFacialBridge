# Independent native-gaze subscription

Bridge 0.6.0 and later let a PC gaze consumer read PicoFacialBridge directly while an existing BridgeSplit avatar client remains connected. This protocol does not require VRCFaceTracking or UnifiedPicoModule. Bridge 0.5.0 APKs do not implement these commands.

## Transport and ownership

The headset listens on its existing UDP9030 socket. A gaze client binds its own PC UDP port, normally an ephemeral port, and sends the commands below directly to the headset address. Replies and samples return to that exact IP/port from the headset's UDP9030. Do not bind the avatar module's PC9030 port.

Bridge supports one avatar client and one independent gaze client at the same time. Their subscription IDs, epochs, stop commands and timeouts are separate. A second gaze endpoint cannot replace an active gaze subscriber until that lease expires. The local **Transmit eye tracking** switch controls both paths; it is not overridden by a remote client.

This is a trusted-LAN protocol, with no encryption or cryptographic authentication. A subscription ID distinguishes sessions but is not an authentication token.

## Commands

Control messages are ASCII with no trailing newline or null byte. The ID is exactly 16 lowercase hexadecimal digits, generated randomly by the client.

```text
PC → headset: PXR_GAZE_SUB id=0123456789abcdef
headset → PC: PXR_GAZE_ACK id=0123456789abcdef epoch=000000003b9aca00 enabled=1
PC → headset: PXR_GAZE_STOP id=0123456789abcdef
```

Send `PXR_GAZE_SUB` once per 1000 ms to renew the 3000 ms lease. Repeating the same endpoint/ID is idempotent: it renews the lease and returns an ACK without resetting the source watermark or epoch. A new ID on the same endpoint starts a new gaze session; another endpoint's SUB or STOP does not change the current subscription.

The ACK's epoch is a positive signed int64 represented as exactly 16 lowercase hexadecimal digits. `enabled=0` means the headset's local eye-transmission switch is off; the subscription can keep renewing, but no native samples are sent. A local eye-switch change or tracking-session restart advances the gaze epoch and sends a new ACK before new-epoch data. These changes do not replace the avatar session's ID or epoch.

A client must match the ACK ID and headset source endpoint before accepting data. It must reject samples for other epochs and stop forwarding if no valid ACK has arrived for 3000 ms. An old ACK must not roll back an active epoch. On timeout, rotate the subscription ID once and immediately re-subscribe; reject delayed ACKs for the old ID. The new ID's ACK can then establish a lower epoch after a headset reboot without resurrecting an old session. Duplicate ACKs renew only control freshness, not sample freshness.

On exit, send only `PXR_GAZE_STOP` with the owned ID. Do not send the avatar protocol's `STOP`, `DISCOVER_DAEMON`, `PXR_SUB`, `POLO` or other control messages for a gaze subscription. Stopping the gaze consumer leaves the avatar client connected, and an avatar STOP leaves gaze connected.

## Samples

Data uses the existing 83-byte native `E` layout, little-endian throughout:

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 1 | ASCII `E` |
| 1 | 1 | Channel mask, always 1 |
| 2 | 1 | Morphology flags, always 0 |
| 3 | 8 | Gaze subscription epoch |
| 11 | 8 | Native eye source timestamp in headset monotonic nanoseconds |
| 19 / 23 / 27 | 4 each | Left, right and combined native status words |
| 31 / 43 / 55 | 12 each | Left, right and combined float32 direction vectors |
| 67 / 71 | 4 each | Left/right openness |
| 75 / 79 | 4 each | Left/right raw pupil values |

Combined direction validity comes from bit `0x002` of the status word at offset 27, not the morphology byte. The gaze path clears per-eye `0x100` validity rather than claiming that firmware's fixed-depth split represents two independent measured rays. The consumer should use the valid combined direction for its initial integration. All native fields use the same sanitization as ordinary `E` packets.

Native gaze uses head-relative right/up/back axes, with forward along negative Z. It is a direction, not a screen coordinate or a fixation distance. A rendering adapter must apply the connected headset's eye transforms/projections and must not apply world/head compensation a second time. The packet does not provide a reliable convergence distance.

Only the newest available source from each poll is sent, and only if its timestamp advances and its age is at most 150 ms in the headset clock domain. Old ring history is not replayed into a fresh gaze session. A new invalid native observation is still sent, so consumers can stop using the previous direction. No smoothing, interpolation, face expressions or `A/F/X` packets are added to this path. The avatar path retains its existing independent stream cadence and packet format.

Headset timestamps must not be passed directly as Windows QPC ticks. The VRSS adapter keeps the source identity for deduplication and uses the PC clock for its provider input. Network delivery and a running client alone do not prove a correctly aligned rendered foveated region.

## Implementation and host checks

- `GazeSubscription.java`: independent lease, epoch and latest-native-sample selection.
- `BridgeService.java`: namespace dispatch and combined capture demand, with separate diagnostics.
- `tests/GazeSubscriptionTest.java`: lease, switch, invalidation, freshness and original-peer isolation checks.
- `tests/GazeLoopbackServer.java`: synthetic host fixture using the actual protocol and framing classes; it binds a system-assigned loopback port and never accesses a headset.

Run `bash tests/run-host-tests.sh` or the focused Windows `tests/run-gaze.ps1`. Android compilation and host tests do not establish device or VRSS rendering acceptance.
