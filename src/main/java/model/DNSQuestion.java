package model;

import util.StreamUtils;

import java.nio.ByteBuffer;

public record DNSQuestion(DNSName name, short type, short clazz) {

    public byte[] toBytes() {
        return StreamUtils.toBytes(dos -> {
            dos.write(this.name.toBytes());
            dos.writeShort(this.type);
            dos.writeShort(this.clazz);
        });
    }

    public static DNSQuestion fromByteBuffer(ByteBuffer data) {
        return new DNSQuestion(DNSName.fromByteBuffer(data), data.getShort(), data.getShort());
    }
}
