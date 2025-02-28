package server;

import model.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class DNSServer {
    private static final int DEFAULT_PORT = 2053;
    private static final byte[] DEFAULT_IP = {8, 8, 8, 8};
    private static final int DEFAULT_TTL = 1800;
    private static final int BUFFER_SIZE = 512;

    private InetSocketAddress resolverAddress = null;

    public DNSServer(String resolver) {
        this.resolverAddress = parseResolverAddress(resolver);
    }

    public void start() {
        try (var serverSocket = new DatagramSocket(DEFAULT_PORT)) {
            System.out.println("DNS Server started on port " + DEFAULT_PORT);

            while (true) {
                try {
                    var requestPacket = receiveRequestPacket(serverSocket);
                    var request = DNSMessage.fromByteBuffer(
                            ByteBuffer.wrap(requestPacket.getData(), 0, requestPacket.getLength()));

                    var response = handleRequest(request);
                    sendResponse(serverSocket, response, requestPacket.getSocketAddress());
                } catch (IOException e) {
                    System.err.println("Error processing request: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting DNS server: " + e.getMessage());
            throw new ServerStartupException("Failed to start DNS server", e);
        }
    }

    private DatagramPacket receiveRequestPacket(DatagramSocket serverSocket) throws IOException {
        var buffer = new byte[BUFFER_SIZE];
        var packet = new DatagramPacket(buffer, buffer.length);
        serverSocket.receive(packet);
        return packet;
    }

    private DNSMessage handleRequest(DNSMessage request) {
        var responseHeader = createResponseHeader(request);
        var answers = resolverAddress != null ?
                forwardToResolver(request, resolverAddress) :
                generateDefaultResponses(request);

        return new DNSMessage(responseHeader, request.getQuestions(), answers);
    }

    private List<DNSAnswer> forwardToResolver(DNSMessage request, InetSocketAddress resolverAddr) {
        try (var forwardSocket = new DatagramSocket()) {
            return request.getQuestions().stream()
                    .flatMap(question -> {
                        try {
                            return forwardSingleQuestion(question, request.getHeader(), forwardSocket, resolverAddr).stream();
                        } catch (IOException e) {
                            System.err.println("Error forwarding question: " + e.getMessage());
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error creating forwarding socket: " + e.getMessage());
            return List.of();
        }
    }

    private List<DNSAnswer> forwardSingleQuestion(DNSQuestion question, DNSHeader originalHeader,
                                                  DatagramSocket socket, InetSocketAddress resolverAddr) throws IOException {
        // Create a new header with a random ID
        var forwardHeader = originalHeader.clone();
        forwardHeader.setId((short) ThreadLocalRandom.current().nextInt(Short.MAX_VALUE));

        // Create message with single question
        var forwardMessage = new DNSMessage(forwardHeader, List.of(question));
        var queryData = forwardMessage.toBytes();

        // Send the query
        var queryPacket = new DatagramPacket(queryData, queryData.length, resolverAddr);
        socket.send(queryPacket);

        // Receive the response
        var responseBuffer = new byte[BUFFER_SIZE];
        var responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(responsePacket);

        // Parse the response
        var responseMessage = DNSMessage.fromByteBuffer(
                ByteBuffer.wrap(responsePacket.getData(), 0, responsePacket.getLength()));

        return responseMessage.getAnswers();
    }

    private List<DNSAnswer> generateDefaultResponses(DNSMessage request) {
        var rData = RData.fromBytes(DEFAULT_IP);

        return request.getQuestions().stream()
                .map(question -> new DNSAnswer(
                        question.name(),
                        question.type(),
                        question.clazz(),
                        DEFAULT_TTL,
                        (short) DEFAULT_IP.length,
                        rData))
                .collect(Collectors.toList());
    }

    private DNSHeader createResponseHeader(DNSMessage request) {
        var responseHeader = request.getHeader().clone();
        if (responseHeader.getOpcode() != 0) {
            responseHeader.setRCode(4); // Not implemented
        }
        responseHeader.setResponse();
        return responseHeader;
    }

    private void sendResponse(DatagramSocket serverSocket, DNSMessage response,
                              SocketAddress requester) throws IOException {
        var responseData = response.toBytes();
        var responsePacket = new DatagramPacket(responseData, responseData.length, requester);
        serverSocket.send(responsePacket);
    }

    private InetSocketAddress parseResolverAddress(String resolver) {
        if (resolver == null || resolver.isEmpty()) {
            return null;
        }

        try {
            var parts = resolver.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Resolver must be in format host:port");
            }

            var address = InetAddress.getByName(parts[0]);
            var port = Integer.parseInt(parts[1]);

            return new InetSocketAddress(address, port);
        } catch (Exception e) {
            System.err.println("Invalid resolver address: " + e.getMessage());
            return null;
        }
    }

    public static final class ServerStartupException extends RuntimeException {
        public ServerStartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}