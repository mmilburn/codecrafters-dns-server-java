package model;

import util.StreamUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

record DNSName(String name) {

    public byte[] toBytes() {
        return StreamUtils.toBytes(dos -> {
            if (!name.isEmpty()) {
                for (String label : name.split("\\.")) {
                    byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
                    dos.writeByte(labelBytes.length);
                    dos.write(labelBytes);
                }
            }
            dos.writeByte(0);
        });
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
