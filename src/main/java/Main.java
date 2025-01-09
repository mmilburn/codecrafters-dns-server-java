import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
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

    public static DNSHeader fromByteBuffer(ByteBuffer data) {
        return new DNSHeader(data.getShort(), data.getShort(), data.getShort(), data.getShort(), data.getShort(), data.getShort());
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

    public static DNSName fromByteBuffer(ByteBuffer data) {
        byte len;
        boolean isCompressed = false;
        StringJoiner labels = new StringJoiner(".");
        int pos = 0;

        while ((len = data.get()) != 0) {
            if ((len & 0xC0) == 0xC0) {
                //Compression applies to the rest of label list in this record.
                isCompressed = true;
                //Mask off the 2 high bits of the pointer to get the remaining 6, concatenate that with the next
                //byte (lower bits) to get our offset.
                int offset = ((len & 0x3F) << 8) | (data.get() & 0xFF);
                pos = data.position();
                data.position(offset);
            } else {
                byte[] label = new byte[len];
                data.get(label);
                labels.add(new String(label));
            }
            if (isCompressed) {
                data.position(pos);
            }
        }
        return new DNSName(labels.toString());
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

record DNSQuestion(DNSName name, short type, short clazz) {

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

class DNSAnswer {
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

class DNSMessage {

    private final DNSHeader header;
    private final List<DNSQuestion> questions;
    private List<DNSAnswer> answers;

    public DNSMessage(DNSHeader header, List<DNSQuestion> questions, List<DNSAnswer> answers) {
        this.header = header;
        this.questions = questions;
        this.answers = answers;
    }

    public DNSMessage(DNSHeader header, List<DNSQuestion> questions) {
        this.header = header;
        this.questions = questions;
    }

    public DNSHeader getHeader() {
        return header;
    }

    public List<DNSQuestion> getQuestions() {
        return questions;
    }

    public byte[] toBytes() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(header.toBytes());
            for (DNSQuestion question : questions) {
                dos.write(question.toBytes());
            }
            if (!answers.isEmpty()) {
                for (DNSAnswer answer : answers) {
                    dos.write(answer.toBytes());
                }
            }
            dos.flush();
        } catch (IOException ioNo) {
            System.err.println(Arrays.toString(ioNo.getStackTrace()));
        }
        return baos.toByteArray();
    }

    public static DNSMessage fromByteBuffer(ByteBuffer data) {
        DNSHeader header = DNSHeader.fromByteBuffer(data);
        List<DNSQuestion> questions = new ArrayList<>();
        for (int i = 0; i < header.getQdCount(); i++) {
            questions.add(DNSQuestion.fromByteBuffer(data));
        }
        if (header.getAnCount() > 0) {
            List<DNSAnswer> answers = new ArrayList<>();
            for (int i = 0; i < header.getAnCount(); i++) {
                answers.add(DNSAnswer.fromByteBuffer(data));
            }
            return new DNSMessage(header, questions, answers);
        }
        return new DNSMessage(header, questions);
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
                ByteBuffer bb = ByteBuffer.wrap(packet.getData());
                DNSMessage rxMsg = DNSMessage.fromByteBuffer(bb);
                final byte[] bufResponse = new byte[512];
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                DNSHeader txHeader = getDnsHeader(rxMsg);
                byte[] ip = new RData("8.8.8.8").toBytes();
                List<DNSAnswer> answers = new ArrayList<>();
                for (DNSQuestion question : rxMsg.getQuestions()) {
                    answers.add(new DNSAnswer(new DNSName(question.name().name()), question.type(), question.clazz(), 1800, (short) ip.length, RData.fromBytes(ip)));
                }
                DNSMessage txMsg = new DNSMessage(txHeader, rxMsg.getQuestions(), answers);
                packetResponse.setData(txMsg.toBytes());
                //System.err.println(Arrays.toString(packetResponse.getData()));
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static DNSHeader getDnsHeader(DNSMessage rxMsg) {
        DNSHeader txHeader = new DNSHeader();
        txHeader.setId(rxMsg.getHeader().getId());
        txHeader.setOpcode(rxMsg.getHeader().getOpcode());
        txHeader.setRD(rxMsg.getHeader().getRD());
        if (rxMsg.getHeader().getOpcode() != 0) {
            txHeader.setRCode(4);
        }
        txHeader.setResponse();
        txHeader.setQdCount(rxMsg.getHeader().getQdCount());
        txHeader.setAnCount(rxMsg.getHeader().getQdCount());
        return txHeader;
    }
}
