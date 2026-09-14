#!/usr/bin/env bash
# Device-independent tests for Linux/macOS and Git Bash with JDK 17 on PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/host-tests
mkdir -p "$out"
javac -encoding UTF-8 -d "$out" \
  shared/src/main/java/dev/pico/facialprobe/{TrackingBuffer,TrackingData,StreamHealth,ResourceScope,FramePump,DeliveryLedger,TransmissionRates}.java \
  bridge/src/main/java/dev/pico/facialprobe/PeerProtocol.java \
  tests/{TrackingBufferTest,PeerProtocolTest,CadenceTest,ForwardingTest}.java
for test in TrackingBufferTest PeerProtocolTest CadenceTest ForwardingTest; do
  java -cp "$out" "dev.pico.facialprobe.$test"
done
