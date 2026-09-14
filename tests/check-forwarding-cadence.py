"""Check a stable, real-device log interval for discarded observed stream updates."""
import argparse
import datetime as dt
import json
import pathlib
import re

parser = argparse.ArgumentParser()
parser.add_argument('log')
parser.add_argument('--start', required=True, help='HH:MM:SS.mmm')
parser.add_argument('--end', required=True, help='HH:MM:SS.mmm')
parser.add_argument('--output')
args = parser.parse_args()
pattern = re.compile(r'^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+) .*STATE Running .*frames=(\d+)/(\d+) packets=(\d+)')
rows = []
for line in pathlib.Path(args.log).read_text(encoding='utf-8-sig', errors='replace').splitlines():
    match = pattern.search(line)
    if match and args.start <= match[1] <= args.end:
        rows.append((match[1], *(int(match[i]) for i in range(2, 5))))
assert len(rows) >= 2, 'Need at least two Running observations in the selected interval'
first, last = rows[0], rows[-1]
seconds = (dt.datetime.strptime(last[0], '%H:%M:%S.%f') - dt.datetime.strptime(first[0], '%H:%M:%S.%f')).total_seconds()
eye, face, packets = [last[i] - first[i] for i in range(1, 4)]
assert min(eye, face, packets) >= 0, 'Counter reset: select an uninterrupted worker interval'
result = dict(seconds=seconds, observed_eye_updates=eye, observed_face_updates=face,
              transmitted_packets=packets, eye_hz=eye/seconds, face_hz=face/seconds, udp_hz=packets/seconds,
              minimum_observed_eye_updates_not_individually_forwarded=max(0, eye-packets))
print(json.dumps(result, indent=2))
if args.output:
    pathlib.Path(args.output).write_text(json.dumps(result, indent=2), encoding='utf-8')
assert packets >= eye, f'RED: at least {eye-packets} already-observed eye updates were coalesced before UDP'
assert packets >= face, f'RED: at least {face-packets} already-observed face updates were coalesced before UDP'
print('PASS: no observed per-stream forwarding deficit in selected interval (not a network-loss proof)')
