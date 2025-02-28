package model;

import util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RData {
    private final String data;

    private RData(String data) {
        this.data = data;
    }

    public byte[] toBytes() {
        return StreamUtils.toBytes(dos -> {
            for (String octet : data.split("\\.")) {
                dos.writeByte(Integer.parseInt(octet));
            }
        });
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
