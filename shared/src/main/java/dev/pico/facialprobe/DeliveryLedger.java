package dev.pico.facialprobe;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Per-peer ordered payload digest, so tests can compare actual sends/receives without changing the wire format. */
final class DeliveryLedger {
    private final MessageDigest digest;
    private long count;
    DeliveryLedger() {
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    void add(byte[] packet){digest.update(packet);count++;}
    long count(){return count;}
    byte[] finish(){return digest.digest();}
    String finishHex(){StringBuilder text=new StringBuilder();for(byte b:finish())text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return text.toString();}
}
