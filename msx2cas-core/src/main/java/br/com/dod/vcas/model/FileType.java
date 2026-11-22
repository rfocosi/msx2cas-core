package br.com.dod.vcas.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public enum FileType {
    ASCII(new char[]{}, "ASCII", new char[]{ 0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA }),
    BAS(new char[]{ 0xff }, "BASIC", new char[]{ 0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3 }),
    BIN(new char[]{ 0xfe }, "BINARY", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
    CAS(new char[]{ 0x1F,0xA6,0xDE,0xBA,0xCC,0x13,0x7D,0x74 }, "CAS", new char[]{}),
    TSX(new char[]{ 'Z','X','T','a','p','e','!', 0x1A }, "TSX", new char[]{}),
    ROM(new char[]{ 0x41, 0x42 }, "ROM", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
    DATA(new char[]{}, "DATA", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
    NONE(new char[]{}, "NONE", new char[]{});

    private char[] id;
    private char[] header;
    private String name;

    FileType(char[] id, String name, char[] header) {
        this.id = id;
        this.name = name;
        this.header = header;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean equals(String fileName, int offset) throws IOException {
        if (id.length == 0) {
            return true;
        }
        byte[] fileHeader = new byte[id.length];
        try (FileInputStream fileInputStream = new FileInputStream(fileName)) {
            if (offset > 0) {
                long skipped = 0;
                while (skipped < offset) {
                    long s = fileInputStream.skip(offset - skipped);
                    if (s <= 0) {
                        return false;
                    }
                    skipped += s;
                }
            }
            int totalRead = 0;
            while (totalRead < fileHeader.length) {
                int read = fileInputStream.read(fileHeader, totalRead, fileHeader.length - totalRead);
                if (read == -1) {
                    if (totalRead == 0 && offset == 0) {
                        throw new IOException("File empty!");
                    }
                    return false;
                }
                totalRead += read;
            }
        }
        return equals(fileHeader);
    }

    private boolean equalsRom49k(String fileName) throws IOException {
        return (new File(fileName).length() > 32768) && equals(fileName, 16384);
    }

    public boolean equals(String fileName) throws IOException {
        return equals(fileName, 0) || (this == ROM && equalsRom49k(fileName));
    }

    public boolean equals(byte[] inputHandler) {
        if (inputHandler == null || inputHandler.length < id.length) {
            return false;
        }
        for (int i=0; i < id.length; i++) {
            if ((byte) id[i] != inputHandler[i]) return false;
        }
        return true;
    }

    public char[] getId() {
        return id;
    }

    public char[] getHeader() {
        return header;
    }

    public String toString() {
        return name;
    }
}
