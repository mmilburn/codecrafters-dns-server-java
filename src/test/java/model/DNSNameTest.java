package model;

import org.testng.annotations.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.testng.Assert.*;

public class DNSNameTest {

    @Test
    public void testConstructor() {
        DNSName name = new DNSName("example.com");
        assertEquals(name.name(), "example.com", "Name should match");
    }

    @Test
    public void testToBytes() {
        // Test simple domain
        DNSName name = new DNSName("example.com");
        byte[] bytes = name.toBytes();

        // Expected format: 7example3com0
        // First byte is length of "example" (7)
        // Then "example" bytes
        // Then length of "com" (3)
        // Then "com" bytes
        // Finally a zero byte
        assertEquals(bytes.length, 13, "Byte array length should be correct");
        assertEquals(bytes[0], 7, "First byte should be length of 'example'");
        assertEquals(bytes[8], 3, "Ninth byte should be length of 'com'");
        assertEquals(bytes[12], 0, "Last byte should be zero");
    }

    @Test
    public void testFromByteBuffer() {
        // Create test data
        byte[] nameBytes = {
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                3, 'c', 'o', 'm',
                0
        };

        DNSName name = DNSName.fromByteBuffer(ByteBuffer.wrap(nameBytes));
        assertEquals(name.name(), "example.com", "Parsed name should match");
    }

    @Test
    public void testNameCompression() {
        // Test name compression (pointer to previous name)
        // Create a buffer with a compressed name
        // Format: first a full name, then a compressed reference to it
        byte[] compressedNameBytes = {
                // First the full name: example.com
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                3, 'c', 'o', 'm',
                0,
                // Then some other data
                0, 1, 0, 1,
                // Then a compressed reference to the name at offset 0
                (byte) 0xC0, 0
        };

        ByteBuffer buffer = ByteBuffer.wrap(compressedNameBytes);

        // Skip over the first name and other data
        buffer.position(17);

        // Now parse the compressed name
        DNSName name = DNSName.fromByteBuffer(buffer);
        assertEquals(name.name(), "example.com", "Decompressed name should match");

        // The buffer position should be advanced past the compression pointer (2 bytes)
        assertEquals(buffer.position(), 19, "Buffer position should be advanced past compression pointer");
    }

    @Test
    public void testMultiLevelDomain() {
        // Test with subdomain
        DNSName name = new DNSName("www.example.com");
        byte[] bytes = name.toBytes();

        // Expected format: 3www7example3com0
        assertEquals(bytes.length, 17, "Byte array length should be correct");
        assertEquals(bytes[0], 3, "First byte should be length of 'www'");
        assertEquals(bytes[4], 7, "Fifth byte should be length of 'example'");
        assertEquals(bytes[12], 3, "13th byte should be length of 'com'");
        assertEquals(bytes[16], 0, "Last byte should be zero");

        // Round trip test
        DNSName parsedName = DNSName.fromByteBuffer(ByteBuffer.wrap(bytes));
        assertEquals(parsedName.name(), "www.example.com", "Round-trip name should match");
    }

    @Test
    public void testEdgeCases() {
        // Test root domain
        DNSName rootName = new DNSName("");
        byte[] rootBytes = rootName.toBytes();
        System.err.println(Arrays.toString(rootBytes));
        assertEquals(rootBytes.length, 1, "Root domain should be just a zero byte with length");
        assertEquals(rootBytes[0], 0, "Root domain byte should be zero");

        // Test very long label (63 chars is max for a DNS label)
        StringBuilder longLabel = new StringBuilder();
        for (int i = 0; i < 63; i++) {
            longLabel.append('a');
        }
        longLabel.append(".com");

        DNSName longName = new DNSName(longLabel.toString());
        byte[] longBytes = longName.toBytes();
        assertEquals(longBytes[0], 63, "First byte should be length of long label (63)");
    }
}
