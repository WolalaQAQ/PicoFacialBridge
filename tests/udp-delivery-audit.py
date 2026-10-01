"""Compare complete, ordered UDP delivery against the APK's sender ledger (BridgeSplit protocol)."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import socket
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--headset', required=True, help='Headset IPv4 shown in the APK')
parser.add_argument('--seconds', type=int, default=60)
parser.add_argument('--wait-seconds', type=int, default=150)
parser.add_argument('--output', default='evidence/video-analysis/delivery-audit.json')
parser.add_argument('--adb', default='adb', help='ADB command or absolute executable path')
args = parser.parse_args()
target = (args.headset, 9030)
SUBSCRIBE = b'PXR_SUB id=' + os.urandom(8).hex().encode() + b' mask=3'
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 9030))
sock.settimeout(0.1)
buffer_size = sock.getsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF)
# End the previous VRCFT session explicitly. Same IP/port must not reuse its existing hash ledger.
sock.sendto(b'STOP', target)
drain_until = time.monotonic() + 1
while time.monotonic() < drain_until:
    try: sock.recvfrom(4096)
    except socket.timeout: pass

start = time.monotonic()
first_data = last_data = stopping_at = None
last_rx = next_discovery = next_report = start - 3
digest = hashlib.sha256()
packets = eye_packets = face_packets = malformed = pings = modes = eye_changes = face_changes = 0
previous_eye = previous_face = None
print('AUDIT_READY: waiting for worn headset; first data packet starts the timed capture.', flush=True)
try:
    while True:
        now = time.monotonic()
        if first_data is None and now - start > args.wait_seconds:
            break
        if first_data is not None and stopping_at is None and now - first_data >= args.seconds:
            sock.sendto(b'STOP', target)
            stopping_at = now
        if stopping_at is not None and now - stopping_at >= 1:
            break  # Drain the final in-flight packets after STOP; do not introduce a cutoff-count mismatch.
        if stopping_at is None and now - last_rx >= 2 and now >= next_discovery:
            sock.sendto(b'DISCOVER_DAEMON', target)
            next_discovery = now + 1
        try:
            data, sender = sock.recvfrom(4096)
        except socket.timeout:
            continue
        if sender != target:
            continue
        last_rx = time.monotonic()
        if data == b'MARCO\0':
            pings += 1
            if stopping_at is None: sock.sendto(b'POLO', target)
            continue
        if data[:8] == b'PXR_MODE':
            modes += 1
            if stopping_at is None: sock.sendto(SUBSCRIBE, target)  # No data before a subscription.
            continue
        if data[:11] == b'PXR_SUB_ACK':
            continue
        is_eye = len(data) == 83 and data[0:1] == b'E'
        is_face = len(data) == 227 and data[0:1] == b'F'
        if not (is_eye or is_face):
            malformed += 1
            continue
        if first_data is None:
            first_data = last_rx
            print(f'CAPTURE_STARTED: {args.seconds}s of raw eye/face forwarding.', flush=True)
        last_data = last_rx
        digest.update(data)
        packets += 1
        if is_eye:
            eye_packets += 1
            if previous_eye is not None and data != previous_eye: eye_changes += 1
            previous_eye = data
        else:
            face_packets += 1
            if previous_face is not None and data != previous_face: face_changes += 1
            previous_face = data
        if last_rx >= next_report:
            print(f'packets={packets} ({eye_packets}E/{face_packets}F) elapsed={last_rx-first_data:.1f}s raw_eye_changes={eye_changes} face_changes={face_changes}', flush=True)
            next_report = last_rx + 5
finally:
    sock.sendto(b'STOP', target)
    sock.close()

log = subprocess.check_output([args.adb, 'logcat', '-d', '-v', 'threadtime', '-s', 'PicoFacialBridge:I'], text=True, encoding='utf-8', errors='replace')
output = pathlib.Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.with_suffix('.sender.log').write_text(log, encoding='utf-8')
matches = re.findall(r'DELIVERY_END reason=STOP packets=(\d+) sha256=([a-f0-9]{64}) ringOverruns=(\d+)', log)
sender = dict(packets=int(matches[-1][0]), sha256=matches[-1][1], ring_overruns=int(matches[-1][2])) if matches else None
result = dict(received_packets=packets, eye_packets=eye_packets, face_packets=face_packets, receiver_sha256=digest.hexdigest(),
              malformed=malformed, keepalives=pings, mode_messages=modes,
              receive_buffer_bytes=buffer_size, raw_eye_changes=eye_changes, face_changes=face_changes,
              seconds=0 if first_data is None else last_data-first_data,
              sender=sender)
result['ordered_delivery_matches'] = sender is not None and sender['packets'] == packets and sender['sha256'] == result['receiver_sha256']
output.write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result, indent=2), flush=True)
assert packets > 100 and eye_changes > 3 and face_changes > 3, 'No sufficient live eye/face capture'
assert malformed == 0 and result['ordered_delivery_matches'], 'Sender/receiver data differ: do not claim zero observed packet loss'
assert sender['ring_overruns'] == 0, 'Source history overrun observed: do not claim all retained updates were captured'
print('PASS: complete sender/receiver counts + ordered SHA-256 match; zero reported source-history overruns.', flush=True)
