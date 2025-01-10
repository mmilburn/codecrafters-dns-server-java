import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class DNSHeader implements Cloneable {
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
                labels.add(new String(label, StandardCharsets.UTF_8));
            }
        }
        //whoops! restore the pointer *outside* of the while loop.
        if (isCompressed) {
            data.position(pos);
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
                dos.writeByte(Integer.parseInt(octet));
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
        this.header.setQdCount((short) questions.size());
        this.header.setAnCount((short) answers.size());
        this.questions = questions;
        this.answers = answers;
    }

    public DNSMessage(DNSHeader header, List<DNSQuestion> questions) {
        this.header = header;
        this.header.setQdCount((short) questions.size());
        this.questions = questions;
    }

    public DNSHeader getHeader() {
        return header;
    }

    public List<DNSQuestion> getQuestions() {
        return questions;
    }

    public List<DNSAnswer> getAnswers() {
        return answers;
    }

    public byte[] toBytes() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(header.toBytes());
            for (DNSQuestion question : questions) {
                dos.write(question.toBytes());
            }
            if (answers != null && !answers.isEmpty()) {
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
        List<DNSAnswer> answers = new ArrayList<>();
        for (int i = 0; i < header.getAnCount(); i++) {
            answers.add(DNSAnswer.fromByteBuffer(data));
        }
        return new DNSMessage(header, questions, answers);
    }
}

class DNSServer {
    private static final int DEFAULT_PORT = 2053;
    private final String resolver;
    private final Random random = new Random();

    public DNSServer(String resolver) {
        this.resolver = resolver;
    }

    public void start() {
        try (DatagramSocket serverSocket = new DatagramSocket(DEFAULT_PORT)) {
            System.out.println("DNS Server started on port " + DEFAULT_PORT);
            InetSocketAddress resolverSockAddr = null;

            if (!resolver.isEmpty()) {
                String[] resolverParts = resolver.split(":");
                InetAddress resolverAddress = InetAddress.getByName(resolverParts[0]);
                int resolverPort = Integer.parseInt(resolverParts[1]);
                resolverSockAddr = new InetSocketAddress(resolverAddress, resolverPort);
            }

            //noinspection InfiniteLoopStatement
            while (true) {
                DatagramPacket requestPacket = receiveRequestPacket(serverSocket);
                DNSMessage request = DNSMessage.fromByteBuffer(ByteBuffer.wrap(requestPacket.getData()));
                DNSMessage response = handleRequest(request, resolverSockAddr);
                sendResponse(serverSocket, response, requestPacket.getSocketAddress());
            }
        } catch (IOException e) {
            System.err.println("Error in DNSServer: " + e.getMessage());
        }
    }

    private DatagramPacket receiveRequestPacket(DatagramSocket serverSocket) throws IOException {
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        serverSocket.receive(packet);
        return packet;
    }

    private DNSMessage handleRequest(DNSMessage request, InetSocketAddress resolverSockAddr) {
        if (resolverSockAddr != null) {
            List<DNSAnswer> answers = new ArrayList<>();

            try {
                answers = forwardToResolver(request, resolverSockAddr);
            } catch (IOException e) {
                System.err.println("Error forwarding to resolver: " + e.getMessage());
            }

            if (answers.isEmpty()) {
                answers = generateDefaultResponse(request);
            }
            return new DNSMessage(getResponseHeader(request), request.getQuestions(), answers);
        }

        return new DNSMessage(getResponseHeader(request), request.getQuestions(), generateDefaultResponse(request));
    }

    private List<DNSAnswer> forwardToResolver(DNSMessage request, InetSocketAddress resolverSockAddr) throws IOException {
        try (DatagramSocket forwardSocket = new DatagramSocket()) {
            List<DNSAnswer> answers = new ArrayList<>();

            for (DNSQuestion question : request.getQuestions()) {
                DNSHeader forwardHeader = request.getHeader().clone();
                forwardHeader.setId((short) random.nextInt(Short.MAX_VALUE));

                DNSMessage forwardMessage = new DNSMessage(forwardHeader, Collections.singletonList(question));
                byte[] queryData = forwardMessage.toBytes();

                DatagramPacket queryPacket = new DatagramPacket(queryData, queryData.length, resolverSockAddr);
                forwardSocket.send(queryPacket);

                byte[] responseBuffer = new byte[512];
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                forwardSocket.receive(responsePacket);
                DNSMessage responseMessage = DNSMessage.fromByteBuffer(ByteBuffer.wrap(responsePacket.getData()));

                answers.addAll(responseMessage.getAnswers());
            }

            return answers;
        }
    }

    private List<DNSAnswer> generateDefaultResponse(DNSMessage request) {
        byte[] defaultIp = {8, 8, 8, 8};
        RData rData = RData.fromBytes(defaultIp);
        List<DNSAnswer> answers = new ArrayList<>();

        for (DNSQuestion question : request.getQuestions()) {
            DNSAnswer answer = new DNSAnswer(
                    question.name(),
                    question.type(),
                    question.clazz(),
                    1800,
                    (short) defaultIp.length,
                    rData
            );
            answers.add(answer);
        }
        return answers;
    }

    private DNSHeader getResponseHeader(DNSMessage request) {
        DNSHeader responseHeader = request.getHeader().clone();
        if (responseHeader.getOpcode() != 0) {
            responseHeader.setRCode(4);
        }
        responseHeader.setResponse();
        return responseHeader;
    }

    private void sendResponse(DatagramSocket serverSocket, DNSMessage response, SocketAddress requester) throws IOException {
        byte[] responseData = response.toBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, requester);
        serverSocket.send(responsePacket);
    }
}

class CommandLineArgs {

    @Parameter(names = "--resolver", description = "Resolver to forward queries to")
    private String resolver;

    public String getResolver() {
        return resolver;
    }
}

public class Main {
    public static void main(String[] args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        JCommander.newBuilder()
                .addObject(commandLineArgs)
                .build()
                .parse(args);

        DNSServer server = new DNSServer(commandLineArgs.getResolver());
        server.start();
    }
}
