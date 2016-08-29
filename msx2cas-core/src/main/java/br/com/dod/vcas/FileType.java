package br.com.dod.vcas;

import java.io.FileInputStream;
import java.io.IOException;

public enum FileType {
	ASCII(new char[]{}, "ASCII", new char[]{ 0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA,0xEA }),
	BAS(new char[]{ 0xff }, "BASIC", new char[]{ 0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3,0xD3 }),
	BIN(new char[]{ 0xfe }, "BINARY", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
	CAS(new char[]{ 0x1F,0xA6,0xDE,0xBA,0xCC,0x13,0x7D,0x74 }, "CAS", new char[]{}),
	ROM(new char[]{ 0x41, 0x42 }, "ROM", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
	DATA(new char[]{}, "DATA", new char[]{ 0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0,0xD0 }),
	NONE(new char[]{}, "NONE", new char[]{});

	private char[] id;
	private char[] header;
	private String name;

	private FileType(char[] id, String name, char[] header) {
		this.id = id;
		this.name = name;
		this.header = header;
	}

	public boolean equals(String fileName) throws IOException {
		byte[] fileHeader = new byte[id.length];
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(fileName);
			fileInputStream.read(fileHeader, 0, fileHeader.length);
		} catch (IOException e) {
			throw e; // To reach finally block and always close fileInputStream
		} finally {
			if (fileInputStream != null) fileInputStream.close();
		}
		return equals(fileHeader);
	}

	public boolean equals(byte[] inputHandler) throws IOException {
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
