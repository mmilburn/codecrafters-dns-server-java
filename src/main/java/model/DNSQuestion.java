package model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public record DNSQuestion(DNSName name, short type, short clazz) {

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(this.name.toBytes());
            dos.writeShort(this.type);
            dos.writeShort(this.clazz);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static DNSQuestion fromByteBuffer(ByteBuffer data) {
        return new DNSQuestion(DNSName.fromByteBuffer(data), data.getShort(), data.getShort());
    }
}
