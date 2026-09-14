"""Real-device acceptance gate: no success from static shared-memory contents."""
import pathlib
import re
import sys

log = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8-sig', errors='replace')
results = re.findall(r'RESULT started=(\w+) eyeFrames=(\d+) faceFrames=(\d+) eyeValueChanges=(\d+) faceValueChanges=(\d+)', log)
assert results, 'RED: no completed probe RESULT'
started, eye_frames, face_frames, eye_values, face_values = results[-1]
assert started == 'true', 'RED: algorithm did not start'
assert int(eye_values) >= 3, f'RED: eye values changed only {eye_values} times'
assert int(face_values) >= 3, f'RED: face values changed only {face_values} times'
print(f'PASS: changing samples eye={eye_frames}/{eye_values}, face={face_frames}/{face_values}')
