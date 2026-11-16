#!/usr/bin/env bash
# Device-independent tests for Linux/macOS and Git Bash with JDK 17 on PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/host-tests
mkdir -p "$out"
javac -encoding UTF-8 -d "$out" \
  shared/src/main/java/dev/pico/facialprobe/{TrackingBuffer,TrackingData,TrackingMode,StreamHealth,ResourceScope,FramePump,DeliveryLedger,TransmissionRates,XDeliveryStats}.java \
  bridge/src/main/java/dev/pico/facialprobe/{PeerProtocol,GazeSubscription,GazeStatus,ExpressionStatus,ExpressionCatalog,NativeEyeStatus,OutputStatus}.java \
  tests/{TrackingBufferTest,PeerProtocolTest,CadenceTest,ForwardingTest,IndependentExpressionTest,GazeSubscriptionTest,GazeStatusTest,ExpressionStatusTest,NativeEyeStatusTest,GazeLoopbackServer}.java
for test in TrackingBufferTest PeerProtocolTest CadenceTest ForwardingTest IndependentExpressionTest GazeSubscriptionTest GazeStatusTest ExpressionStatusTest NativeEyeStatusTest; do
  java -cp "$out" "dev.pico.facialprobe.$test"
done
