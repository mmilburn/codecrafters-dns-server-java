package util;

import java.io.DataOutputStream;
import java.io.IOException;

@FunctionalInterface
public interface DataOutputStreamConsumer {
    void accept(DataOutputStream dos) throws IOException;
}
