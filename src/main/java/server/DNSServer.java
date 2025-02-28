package server;

import model.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DNSServer {
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
