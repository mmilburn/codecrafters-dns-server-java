package model;

import util.StreamUtils;

import java.nio.ByteBuffer;

public class DNSAnswer {
    private final DNSName name;
    private final short type;
    private final short clazz;
    private final int ttl;
    private final short rdLength;
    private final RData rdata;

    public DNSAnswer(DNSName name, short type, short clazz, int ttl, short rdLength, RData rdata) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
        this.ttl = ttl;
        this.rdLength = rdLength;
        this.rdata = rdata;
    }

    public byte[] toBytes() {
        return StreamUtils.toBytes(dos -> {
            dos.write(this.name.toBytes());
            dos.writeShort(this.type);
            dos.writeShort(this.clazz);
            dos.writeInt(this.ttl);
            byte[] temp = this.rdata.toBytes();
            dos.writeShort(temp.length);
            dos.write(temp);
        });
    }

    public static DNSAnswer fromByteBuffer(ByteBuffer data) {
        DNSName name = DNSName.fromByteBuffer(data);
        short type = data.getShort();
        short clazz = data.getShort();
        int ttl = data.getInt();
        short rdLength = data.getShort();
        byte[] rdBytes = new byte[rdLength];
        data.get(rdBytes);
        return new DNSAnswer(name, type, clazz, ttl, rdLength, RData.fromBytes(rdBytes));
    }
}
