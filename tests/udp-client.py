"""VRCFT-compatible LAN receiver; no ADB or PICO Connect in the data path."""
import argparse
import json
import pathlib
import socket
import struct
import time

parser = argparse.ArgumentParser()
parser.add_argument('--headset', help='Headset IPv4 shown in the APK; required unless --multicast is used')
parser.add_argument('--local-ip')
parser.add_argument('--multicast', action='store_true')
parser.add_argument('--seconds', type=int, default=60)
parser.add_argument('--output', default='evidence/udp-result.json')
args = parser.parse_args()
if not args.multicast and not args.headset:
    parser.error('--headset is required unless --multicast is used')
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 9030))
sock.settimeout(0.2)
if args.local_ip:
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(args.local_ip))
sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
target = ('239.255.255.250' if args.multicast else args.headset, 9030)
start = time.monotonic()
next_discovery = next_report = start
peer = None
last_rx = start
packets = pings = malformed = gaze_changes = face_changes = 0
last_gaze = last_face = None
first_packet = last_packet = None
gazes = []
jaws = []
print(f'Listening UDP 9030; discovery={target}, duration={args.seconds}s', flush=True)
try:
    while time.monotonic() - start < args.seconds:
        now = time.monotonic()
        if peer is not None and now - last_rx >= 2:
            peer = None  # Same 2-second receive timeout / rediscovery behavior as VRCFT.
        if peer is None and now >= next_discovery:
            sock.sendto(b'DISCOVER_DAEMON', target)
            next_discovery = now + 1
        try:
            data, sender = sock.recvfrom(4096)
        except socket.timeout:
            continue
        if sender[0] != args.headset:
            continue
        peer = sender
        last_rx = time.monotonic()
        if data == b'MARCO\0':
            pings += 1
            sock.sendto(b'POLO', peer)
            continue
        if len(data) != 536:
            malformed += 1
            continue
        timestamp = struct.unpack_from('<Q', data, 0)[0]
        valid_eye, valid_face = struct.unpack_from('<2f', data, 296)
        left_status, right_status, combined_status = struct.unpack_from('<3I', data, 384)
        gaze = struct.unpack_from('<3f', data, 456)
        face = struct.unpack_from('<72f', data, 8)
        if first_packet is None: first_packet = data
        last_packet = data
        packets += 1
        if last_gaze is not None and gaze != last_gaze: gaze_changes += 1
        if last_face is not None and face != last_face: face_changes += 1
        last_gaze, last_face = gaze, face
        gazes.append(gaze)
        jaws.append(face[7])
        if now >= next_report:
            print(f'frames={packets} pings={pings} ts={timestamp} valid={valid_eye}/{valid_face} status={left_status}/{right_status}/{combined_status} gaze={gaze} jaw={face[7]:.4f}', flush=True)
            next_report = now + 5
finally:
    if peer:
        sock.sendto(b'STOP', peer)
    sock.close()
result = dict(mode='multicast' if args.multicast else 'unicast', peer=peer, seconds=time.monotonic()-start,
              packets=packets, pings=pings, malformed=malformed, gaze_changes=gaze_changes, face_changes=face_changes)
if gazes:
    result.update(gaze_min=[min(v[i] for v in gazes) for i in range(3)], gaze_max=[max(v[i] for v in gazes) for i in range(3)], jaw_min=min(jaws), jaw_max=max(jaws))
output = pathlib.Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2), encoding='utf-8')
if first_packet:
    output.with_suffix('.first.bin').write_bytes(first_packet)
    output.with_suffix('.last.bin').write_bytes(last_packet)
print(json.dumps(result, indent=2), flush=True)
if not (packets >= 10 and gaze_changes >= 3 and face_changes >= 3 and malformed == 0):
    raise SystemExit('FAIL: live end-to-end tracking criterion not met (inspect wear/permissions/network)')
