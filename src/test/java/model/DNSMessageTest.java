package model;

import org.testng.annotations.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

public class DNSMessageTest {

    private DNSHeader header;
    private List<DNSQuestion> questions;
    private List<DNSAnswer> answers;

    @BeforeMethod
    public void setup() throws Exception {
        // Create test data
        // Using reflection to access the private constructor of DNSHeader
        java.lang.reflect.Constructor<DNSHeader> constructor =
                DNSHeader.class.getDeclaredConstructor(short.class, short.class, short.class, short.class, short.class, short.class);
        constructor.setAccessible(true);
        header = constructor.newInstance((short) 1234, (short) 0, (short) 1, (short) 1, (short) 0, (short) 0);

        questions = new ArrayList<>();
        DNSName name = new DNSName("example.com");
        questions.add(new DNSQuestion(name, (short) 1, (short) 1)); // Type A, Class IN

        answers = new ArrayList<>();
        byte[] ip = {(byte) 192, (byte) 168, 1, 1};
        RData rdata = RData.fromBytes(ip);
        answers.add(new DNSAnswer(name, (short) 1, (short) 1, 3600, (short) ip.length, rdata));
    }

    @Test
    public void testConstructor() {
        // Test the constructor with questions and answers
        DNSMessage message = new DNSMessage(header, questions, answers);

        assertEquals(message.getHeader(), header, "Header should match");
        assertEquals(message.getQuestions(), questions, "Questions should match");
        assertEquals(message.getAnswers(), answers, "Answers should match");
        assertEquals(message.getHeader().getQdCount(), (short) 1, "QdCount should be set");
        assertEquals(message.getHeader().getAnCount(), (short) 1, "AnCount should be set");
    }

    @Test
    public void testConstructorWithoutAnswers() {
        // Test the constructor with only questions
        DNSMessage message = new DNSMessage(header, questions);

        assertEquals(message.getHeader(), header, "Header should match");
        assertEquals(message.getQuestions(), questions, "Questions should match");
        assertNull(message.getAnswers(), "Answers should be null");
        assertEquals(message.getHeader().getQdCount(), (short) 1, "QdCount should be set");
    }

    @Test
    public void testToBytes() {
        // Test serialization to bytes
        DNSMessage message = new DNSMessage(header, questions, answers);
        byte[] bytes = message.toBytes();

        assertNotNull(bytes, "Bytes should not be null");
        assertTrue(bytes.length > 0, "Bytes should not be empty");
    }

    @Test
    public void testFromByteBuffer() {
        // Test deserialization from ByteBuffer
        DNSMessage originalMessage = new DNSMessage(header, questions, answers);
        byte[] bytes = originalMessage.toBytes();

        DNSMessage deserializedMessage = DNSMessage.fromByteBuffer(ByteBuffer.wrap(bytes));

        assertNotNull(deserializedMessage, "Deserialized message should not be null");
        assertEquals(deserializedMessage.getHeader().getQdCount(), originalMessage.getHeader().getQdCount(),
                "QdCount should match");
        assertEquals(deserializedMessage.getHeader().getAnCount(), originalMessage.getHeader().getAnCount(),
                "AnCount should match");
        assertEquals(deserializedMessage.getQuestions().size(), originalMessage.getQuestions().size(),
                "Number of questions should match");
        assertEquals(deserializedMessage.getAnswers().size(), originalMessage.getAnswers().size(),
                "Number of answers should match");
    }

    @Test
    public void testRoundTrip() {
        // Test complete serialization-deserialization round trip
        DNSMessage originalMessage = new DNSMessage(header, questions, answers);
        byte[] bytes = originalMessage.toBytes();
        DNSMessage deserializedMessage = DNSMessage.fromByteBuffer(ByteBuffer.wrap(bytes));

        // Verify question data
        DNSQuestion originalQuestion = originalMessage.getQuestions().get(0);
        DNSQuestion deserializedQuestion = deserializedMessage.getQuestions().get(0);
        assertEquals(deserializedQuestion.name().name(), originalQuestion.name().name(), "Question name should match");
        assertEquals(deserializedQuestion.type(), originalQuestion.type(), "Question type should match");
        assertEquals(deserializedQuestion.clazz(), originalQuestion.clazz(), "Question class should match");

        // Verify answer data
        DNSAnswer originalAnswer = originalMessage.getAnswers().get(0);
        DNSAnswer deserializedAnswer = deserializedMessage.getAnswers().get(0);
        assertEquals(deserializedAnswer.toBytes().length, originalAnswer.toBytes().length,
                "Answer bytes length should match");
    }
}