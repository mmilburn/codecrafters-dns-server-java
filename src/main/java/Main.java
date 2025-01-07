import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

record DNSMessage(DNSHeader header) {

    public byte[] toBytes() {
        return this.header.toBytes();
    }

    public static DNSMessage fromBytes(byte[] data) {
        DNSHeader header = DNSHeader.fromBytes(Arrays.copyOfRange(data, 0, 12));
        return new DNSMessage(header);
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
                DNSMessage txMsg = new DNSMessage(txHeader);
                packetResponse.setData(txMsg.toBytes());
                //System.err.println(Arrays.toString(packetResponse.getData()));
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
