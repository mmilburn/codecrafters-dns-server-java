import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    public int getOpcode() {
        return (flags >> 11) & 0b1111;
    }

    public void setOpcode(int opcode) {
        if (opcode < 0 || opcode > 15) {
            throw new IllegalArgumentException("OPCODE must be a 4-bit value (0 to 15).");
        }
        flags &= ~(0b1111 << 11);
        flags |= (short) (opcode << 11);
    }

    public int getRD() {
        return (flags >> 8) & 1;
    }

    public void setRD(int rd) {
        if (rd < 0 || rd > 1) {
            throw new IllegalArgumentException("RD must be a 1-bit value.");
        }
        flags &= ~0x0100;
        flags |= (short) (rd << 8);
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

    public static DNSHeader fromBytes(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        try {
            return new DNSHeader(dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort(), dis.readShort());
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return new DNSHeader((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0);
    }
}

record DNSName(String name) {

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (String label : name.split("\\.")) {
                byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
                dos.writeByte(labelBytes.length);
                dos.write(labelBytes);
            }
            dos.writeByte(0);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
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
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return new DNSName(name.toString());
    }
}

class RData {
    private final String data;

    public RData(String data) {
        this.data = data;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (String octet : data.split("\\.")) {
                dos.writeByte(Byte.parseByte(octet));
            }
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static RData fromBytes(byte[] data) {
        ArrayList<String> rdata = new ArrayList<>();
        for (byte octet : data) {
            int val = octet & 0xFF;
            rdata.add(Integer.valueOf(val).toString());
        }
        return new RData(rdata.stream().map(String::valueOf).collect(Collectors.joining(".")));
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
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return new DNSQuestion();
    }
}

class DNSAnswer {
    private DNSName name;
    private short type;
    private short clazz;
    private int ttl;
    private short rdlength;
    private RData rdata;

    public DNSAnswer() {
    }

    public DNSAnswer(DNSName name, short type, short clazz, int ttl, short rdlength, RData rdata) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
        this.ttl = ttl;
        this.rdlength = rdlength;
        this.rdata = rdata;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(this.name.toBytes());
            dos.writeShort(this.type);
            dos.writeShort(this.clazz);
            dos.writeInt(this.ttl);
            byte[] temp = this.rdata.toBytes();
            dos.writeShort(temp.length);
            dos.write(temp);
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static DNSAnswer fromBytes(byte[] data) {
        int end = 0;
        for (byte octet : data) {
            if (octet == 0) {
                //end++;
                break;
            }
            end++;
        }
        byte[] nameBytes = new byte[end];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (DataInputStream dis = new DataInputStream(bais)) {
            dis.readFully(nameBytes);
            DNSName name = DNSName.fromBytes(nameBytes);
            short type = dis.readShort();
            short clazz = dis.readShort();
            Integer ttl = dis.readInt();
            short rdLength = dis.readShort();
            byte[] rdBytes = new byte[rdLength];
            dis.readFully(rdBytes);
            return new DNSAnswer(name, type, clazz, ttl, rdLength, RData.fromBytes(rdBytes));
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return new DNSAnswer();
    }
}

class DNSMessage {

    private DNSHeader header;
    private DNSQuestion question;
    private DNSAnswer answer;

    public DNSMessage(DNSHeader header, DNSQuestion question, DNSAnswer answer) {
        this.header = header;
        this.question = question;
        this.answer = answer;
    }

    public DNSMessage(DNSHeader header, DNSQuestion question) {
        this.header = header;
        this.question = question;
    }

    public DNSHeader getHeader() {
        return header;
    }

    public DNSQuestion getQuestion() {
        return question;
    }

    public byte[] toBytes() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(this.header.toBytes());
            dos.write(this.question.toBytes());
            if (this.answer != null) {
                dos.write(this.answer.toBytes());
            }
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static DNSMessage fromBytes(byte[] data) {
        DNSHeader header = DNSHeader.fromBytes(Arrays.copyOfRange(data, 0, 12));
        DNSQuestion question = DNSQuestion.fromBytes(Arrays.copyOfRange(data, 12, 512));
        if (header.getAnCount() > 0) {
            DNSAnswer answer = DNSAnswer.fromBytes(Arrays.copyOfRange(data, 12 + question.toBytes().length, 512));
            return new DNSMessage(header, question, answer);
        }
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
                txHeader.setId(rxMsg.getHeader().getId());
                txHeader.setOpcode(rxMsg.getHeader().getOpcode());
                txHeader.setRD(rxMsg.getHeader().getRD());
                if (rxMsg.getHeader().getOpcode() != 0) {
                    txHeader.setRCode(4);
                }
                txHeader.setResponse();
                txHeader.setQdCount((short) 1);
                txHeader.setAnCount((short) 1);
                byte[] ip = new RData("8.8.8.8").toBytes();
                DNSQuestion question = new DNSQuestion(new DNSName(rxMsg.getQuestion().getName().name()), rxMsg.getQuestion().getType(), rxMsg.getQuestion().getClazz());
                DNSAnswer answer = new DNSAnswer(new DNSName(rxMsg.getQuestion().getName().name()), rxMsg.getQuestion().getType(), rxMsg.getQuestion().getClazz(), 1800, (short) ip.length, RData.fromBytes(ip));
                DNSMessage txMsg = new DNSMessage(txHeader, question, answer);
                packetResponse.setData(txMsg.toBytes());
                //System.err.println(Arrays.toString(packetResponse.getData()));
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
