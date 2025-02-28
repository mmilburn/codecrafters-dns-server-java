package model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DNSMessage {

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
