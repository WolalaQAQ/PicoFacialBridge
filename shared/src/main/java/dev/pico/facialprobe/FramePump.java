package dev.pico.facialprobe;

import java.util.List;

/** Merge source events in timestamp order; holding the other channel is NOT interpolation/filtering. */
final class FramePump {
    interface Sink { void send(byte[] packet) throws Exception; }
    private byte[] eye, face;
    byte[] eye() { return eye; }
    byte[] face() { return face; }
    void accept(List<byte[]> eyes,List<byte[]> faces,long now,Sink sink) throws Exception {
        accept(eyes, faces, now, true, true, sink);
    }
    void accept(List<byte[]> eyes,List<byte[]> faces,long now,boolean sendEye,boolean sendFace,Sink sink) throws Exception {
        int e=0,f=0;
        while(e<eyes.size() || f<faces.size()) {
            long eyeTime=e<eyes.size()?TrackingData.eyeTimestamp(eyes.get(e)):Long.MAX_VALUE;
            long faceTime=f<faces.size()?TrackingData.faceTimestamp(faces.get(f)):Long.MAX_VALUE;
            boolean eyeChanged=eyeTime<=faceTime, faceChanged=faceTime<=eyeTime;
            if(eyeChanged) eye=eyes.get(e++);
            if(faceChanged) face=faces.get(f++);
            if ((eyeChanged && sendEye && TrackingData.eyeFresh(eye, now))
                    || (faceChanged && sendFace && TrackingData.faceFresh(face, now))) {
                sink.send(TrackingData.packet(face,eye,now,sendEye,sendFace));
            }
        }
    }
}
