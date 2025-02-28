package util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StreamUtils {
    public static byte[] toBytes(DataOutputStreamConsumer writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            writer.accept(dos);
            dos.flush();
        } catch (IOException e) {
            // Handle the exception as needed
            throw new RuntimeException("Error writing to stream", e);
        }
        return baos.toByteArray();
    }
}