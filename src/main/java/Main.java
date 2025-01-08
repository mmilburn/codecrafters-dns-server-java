import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class DNSHeader {
    private short id;
    private short flags;
    private short qdCount;
    private short anCount;
    private short nsCount;
    private short arCount;

    public DNSHeader() {
    }

    public DNSHeader(short id, short flags, short qdCount, short anCount, short nsCount, short arCount) {
        this.id = id;
        this.flags = flags;
        this.qdCount = qdCount;
        this.anCount = anCount;
        this.nsCount = nsCount;
        this.arCount = arCount;
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public void setResponse() {
        flags = (short) (flags | 0x8000);
    }

    public void setQdCount(short qdCount) {
        this.qdCount = qdCount;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeShort(id);
            dos.writeShort(flags);
            dos.writeShort(qdCount);
            dos.writeShort(anCount);
            dos.writeShort(nsCount);
            dos.writeShort(arCount);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return baos.toByteArray();
    }

    public static DNSHeader fromBytes(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        try {
            return new DNSHeader(dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort());
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return new DNSHeader((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0);
    }
}

record DNSName(String name) {

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            for (String label : name.split("\\.")) {
                byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
                dos.writeByte(labelBytes.length);
                dos.write(labelBytes);
            }
            dos.writeByte(0);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return baos.toByteArray();
    }

    public static DNSName fromBytes(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        StringBuilder name = new StringBuilder();
        try {
            int len = dis.readUnsignedByte();
            while (len > 0) {
                if (name.length() > 0) {
                    name.append(".");
                }
                byte[] label = new byte[len];
                dis.readFully(label);
                name.append(new String(label));
                len = dis.readUnsignedByte();
            }
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return new DNSName(name.toString());
    }
}

class DNSQuestion {
    private DNSName name;
    private short type;
    private short clazz;

    public DNSQuestion() {
    }

    public DNSQuestion(DNSName name, short type, short clazz) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
    }

    public DNSName getName() {
        return name;
    }

    public short getType() {
        return type;
    }

    public short getClazz() {
        return clazz;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.write(this.name.toBytes());
            dos.writeShort(this.type);
            dos.writeShort(this.clazz);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return baos.toByteArray();
    }

    public static DNSQuestion fromBytes(byte[] data) {
        int end = 0;
        for (byte octet : data) {
            if (octet == 0) {
                end++;
                break;
            }
            end++;
        }
        byte[] nameBytes = new byte[end];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        try {
            dis.readFully(nameBytes);
            return new DNSQuestion(DNSName.fromBytes(nameBytes), dis.readShort(), dis.readShort());
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return new DNSQuestion();
    }
}

record DNSMessage(DNSHeader header, DNSQuestion question) {

    public byte[] toBytes() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(this.header.toBytes());
            dos.write(this.question.toBytes());
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(ioNo.getMessage());
        }
        return baos.toByteArray();
    }

    public static DNSMessage fromBytes(byte[] data) {
        DNSHeader header = DNSHeader.fromBytes(Arrays.copyOfRange(data, 0, 12));
        DNSQuestion question = DNSQuestion.fromBytes(Arrays.copyOfRange(data, 12, 512));
        return new DNSMessage(header, question);
    }
}

public class Main {
    public static void main(String[] args) {

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                //System.out.println("Received data");
                DNSMessage rxMsg = DNSMessage.fromBytes(packet.getData());
                final byte[] bufResponse = new byte[512];
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                DNSHeader txHeader = new DNSHeader();
                txHeader.setId(rxMsg.header().getId());
                txHeader.setResponse();
                txHeader.setQdCount((short) 1);
                DNSMessage txMsg = new DNSMessage(txHeader, new DNSQuestion(new DNSName(rxMsg.question().getName().name()), rxMsg.question().getType(), rxMsg.question().getClazz()));
                packetResponse.setData(txMsg.toBytes());
                //System.err.println(Arrays.toString(packetResponse.getData()));
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
