package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.security.MessageDigest;

public final class CadenceTest {
    static int checks;
    static void check(boolean test, String description) { checks++; if (!test) throw new AssertionError(description); }
    static byte[] eye(long time, float x) {
        byte[] b=new byte[200]; ByteBuffer v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        v.putLong(168,time); v.putInt(8,3); v.putFloat(72,x); v.putFloat(80,-1); return b;
    }
    static byte[] face(long time, float jaw) {
        byte[] b=new byte[896]; ByteBuffer v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        v.putLong(0,time); v.putFloat(36,jaw); v.putFloat(296,1); v.putFloat(300,1); return b;
    }
    static ByteBuffer ring(int capacity) {
        ByteBuffer b=ByteBuffer.allocate(20+200*capacity).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,1);b.putInt(4,200);b.putInt(8,capacity);b.putInt(12,-1);b.putInt(16,20);return b;
    }
    static void publish(ByteBuffer ring,int n,long timestamp) {
        int i=n%ring.getInt(8);ring.position(20+200*i);ring.put(eye(timestamp,n));ring.putInt(12,i);
    }
    static void uniform(ByteBuffer memory,int stride,int slot,long value){for(int p=0;p<stride;p+=8)memory.putLong(20+stride*slot+p,value);}
    static void publicationRace(int stride,int capacity,int timestampOffset){
        ByteBuffer memory=ByteBuffer.allocate(20+stride*capacity).order(ByteOrder.LITTLE_ENDIAN);
        memory.putInt(0,1);memory.putInt(4,stride);memory.putInt(8,capacity);memory.putInt(12,0);memory.putInt(16,20);
        long initial=1_000_000_000L,newest=initial+capacity;
        uniform(memory,stride,0,initial);
        boolean[] once={true};
        TrackingBuffer.Cursor cursor=new TrackingBuffer.Cursor(memory,stride,timestampOffset,()->{
            if(!once[0])return;once[0]=false;
            for(int i=1;i<capacity;i++){uniform(memory,stride,i,initial+i);memory.putInt(12,i);}
            // Writer wrapped and began overwriting the previously published head. Timestamp is visible,
            // but most of its payload is still old and it is not published yet.
            memory.putLong(20+timestampOffset,newest);
        },()->{uniform(memory,stride,0,newest);memory.putInt(12,0);});
        check(cursor.read().isEmpty(),"Reject partially published wrapped head (stride="+stride+")");
        uniform(memory,stride,0,newest);memory.putInt(12,0);
        List<byte[]> retry=cursor.read();
        check(retry.size()==1,"Rejected publication must not advance cursor (stride="+stride+")");
        ByteBuffer result=ByteBuffer.wrap(retry.get(0)).order(ByteOrder.LITTLE_ENDIAN);
        for(int p=0;p<stride;p+=8)check(result.getLong(p)==newest,"Retry contains one complete frame");
        ByteBuffer changing=ByteBuffer.allocate(20+stride*capacity).order(ByteOrder.LITTLE_ENDIAN);
        changing.putInt(0,1);changing.putInt(4,stride);changing.putInt(8,capacity);changing.putInt(12,0);changing.putInt(16,20);
        uniform(changing,stride,0,initial);boolean[] armed={false};
        TrackingBuffer.Cursor active=new TrackingBuffer.Cursor(changing,stride,timestampOffset,null,()->{
            if(armed[0]){armed[0]=false;uniform(changing,stride,2,initial+2);changing.putInt(12,2);}
        });
        active.read();uniform(changing,stride,1,initial+1);changing.putInt(12,1);armed[0]=true;
        check(active.read().isEmpty(),"Changed publication during copy is retried (stride="+stride+")");
        List<byte[]> retained=active.read();check(retained.size()==2,"Retry recovers both retained frames");
        check(TrackingData.bytes(retained.get(0)).getLong(timestampOffset)==initial+1
                &&TrackingData.bytes(retained.get(1)).getLong(timestampOffset)==initial+2,"Retry preserves retained ordering");
    }
    public static void main(String[] args) throws Exception {
        long base=1_000_000_000L, step=11_110_625L;
        FramePump pump=new FramePump();List<byte[]> packets=new ArrayList<>();
        byte[] initialFace=face(base,0.2f);
        pump.accept(Collections.singletonList(eye(base,0)),Collections.singletonList(initialFace),base,packets::add);
        pump.accept(Arrays.asList(eye(base+step,0.3f),eye(base+2*step,-0.4f),eye(base+3*step,0.7f)),Collections.emptyList(),base+3*step,packets::add);
        check(packets.size()==5,"One eye and one facial datagram per source update");
        check(packets.get(0)[0]=='E'&&packets.get(1)[0]=='F',"Both streams are forwarded as separate datagrams");
        check(TrackingData.bytes(packets.get(2)).getFloat(55)==0.3f,"No interpolation or smoothing of gaze");
        check(TrackingData.bytes(packets.get(3)).getFloat(55)==-0.4f,"Every intermediate raw gaze is forwarded");
        check(TrackingData.bytes(packets.get(4)).getFloat(55)==0.7f,"Every eye frame in the batch is forwarded in order");
        pump.accept(Collections.emptyList(),Collections.singletonList(face(base+4*step,0.9f)),base+4*step,packets::add);
        check(packets.size()==6&&packets.get(5)[0]=='F',"Face-only update is forwarded too");
        check(TrackingData.bytes(packets.get(5)).getFloat(47)==0.9f,"Raw jaw value preserved");
        pump.accept(Collections.emptyList(),Collections.emptyList(),base+5*step,packets::add);
        check(packets.size()==6,"No synthetic duplicate events on empty poll");
        FramePump burst=new FramePump();List<byte[]> burstPackets=new ArrayList<>();
        List<byte[]> eyes=new ArrayList<>(),faces=new ArrayList<>();
        for(int i=0;i<90;i++){eyes.add(eye(base+i*step,i/100f));if(i%4==0)faces.add(face(base+i*step,i/100f));}
        burst.accept(eyes,faces,base+90*step,burstPackets::add);
        check(burstPackets.size()==113,"90 eye + 23 facial frames survive a batched mixed-rate stream");
        int eyePackets=0;
        for(byte[] burstPacket:burstPackets)if(burstPacket[0]=='E'){
            check(TrackingData.bytes(burstPacket).getFloat(55)==eyePackets/100f,"Burst preserves order/raw values");eyePackets++;
        }
        check(eyePackets==90,"Every eye frame is forwarded individually");
        ByteBuffer memory=ring(8);TrackingBuffer.Cursor cursor=new TrackingBuffer.Cursor(memory,200,168);
        check(cursor.read().isEmpty(),"Empty ring has no frames");
        publish(memory,0,base);check(cursor.read().size()==1,"Initial latest frame");
        for(int i=1;i<=4;i++)publish(memory,i,base+i*step);
        List<byte[]> batch=cursor.read();check(batch.size()==4,"Read all retained frames after scheduler delay");
        for(int i=0;i<4;i++)check(TrackingData.eyeTimestamp(batch.get(i))==base+(i+1)*step,"Retained frames chronological");
        check(cursor.read().isEmpty(),"No reread of old timestamps");
        for(int i=5;i<=10;i++)publish(memory,i,base+i*step);
        check(cursor.read().size()==6,"Ring wrap does not discard frames");
        check(cursor.overruns()==0,"No false overrun during bounded delay");
        for(int i=11;i<=25;i++)publish(memory,i,base+i*step);
        check(!cursor.read().isEmpty()&&cursor.overruns()==1,"Unrecoverable ring overwrite must be reported");
        TrackingBuffer.Cursor startup=new TrackingBuffer.Cursor(memory,200,168);
        check(startup.read().size()==1,"New session does not replay historical sensor data");
        DeliveryLedger ledger=new DeliveryLedger();ledger.add(new byte[]{1,2});ledger.add(new byte[]{3});
        check(ledger.count()==2,"Sender counts successful data datagrams");
        check(Arrays.equals(ledger.finish(),MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3})),"Digest detects loss/reordering without changing UDP payload");
        publicationRace(200,50,168);
        publicationRace(896,5,0);
        System.out.println("PASS: "+checks+" cadence/history/no-smoothing checks");
    }
}
