package model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSHeader implements Cloneable {
    private short id;
    private short flags;
    private short qdCount;
    private short anCount;
    private final short nsCount;
    private final short arCount;

    public DNSHeader(short id, short flags, short qdCount, short anCount, short nsCount, short arCount) {
        this.id = id;
        this.flags = flags;
        this.qdCount = qdCount;
        this.anCount = anCount;
        this.nsCount = nsCount;
        this.arCount = arCount;
    }

    public void setId(short id) {
        this.id = id;
    }

    public void setResponse() {
        flags = (short) (flags | 0x8000);
    }

    public int getOpcode() {
        return (flags >> 11) & 0b1111;
    }

    public void setRCode(int code) {
        if (code < 0 || code > 15) {
            throw new IllegalArgumentException("RCODE must be a 4-bit value (0 to 15).");
        }
        flags &= ~0xF;
        flags |= (short) code;
    }

    public short getQdCount() {
        return qdCount;
    }

    public void setQdCount(short qdCount) {
        this.qdCount = qdCount;
    }

    public short getAnCount() {
        return anCount;
    }

    public void setAnCount(short anCount) {
        this.anCount = anCount;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeShort(id);
            dos.writeShort(flags);
            dos.writeShort(qdCount);
            dos.writeShort(anCount);
            dos.writeShort(nsCount);
            dos.writeShort(arCount);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static DNSHeader fromByteBuffer(ByteBuffer data) {
        return new DNSHeader(data.getShort(), data.getShort(), data.getShort(), data.getShort(), data.getShort(), data.getShort());
    }

    @Override
    public DNSHeader clone() {
        try {
            return (DNSHeader) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
