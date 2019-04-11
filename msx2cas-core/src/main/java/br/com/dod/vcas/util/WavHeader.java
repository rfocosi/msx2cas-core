package br.com.dod.vcas.util;

import java.util.LinkedList;
import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.dotnet.types.WORD;
import br.com.dod.types.IntegerType;

public class WavHeader { 

	private static final DWORD WAV_HEADER_ID = new DWORD("RIFF".toCharArray());
	private static final DWORD MM_FILE_TYPE = new DWORD("WAVE".toCharArray());
	private static final DWORD FORMAT_ID = new DWORD("fmt ".toCharArray());
	private static final DWORD DATA_ID = new DWORD("data".toCharArray());

	public DWORD SampleLength;
	public DWORD FormatSize;
	public WORD FormatTag;
	public WORD NumChannels;
	public DWORD SamplesPerSec;
	public DWORD BytesPerSec;
	public WORD BlkAllign;
	public WORD BitsPerSample;
	public DWORD PureSampleLength;

	private List<IntegerType> getHeader() {
		final List<IntegerType> charList = new LinkedList<>();
		
		charList.add(WAV_HEADER_ID);
		charList.add(SampleLength);
		charList.add(MM_FILE_TYPE);
		charList.add(FORMAT_ID);
		charList.add(FormatSize);
		charList.add(FormatTag);
		charList.add(NumChannels);
		charList.add(SamplesPerSec);
		charList.add(BytesPerSec);
		charList.add(BlkAllign);
		charList.add(BitsPerSample);
		charList.add(DATA_ID);
		charList.add(PureSampleLength);
		
		return charList;
	}
	
	public byte[] toBytes() {
		final List<IntegerType> charList = getHeader();

		List<Byte> bytesList = new LinkedList<>();

		for (IntegerType integerType : charList) {
			byte[] cur = integerType.getBytes();

			for (byte b : cur) {
				bytesList.add(b);
			}
		}		
		
		byte[] bytes = new byte[bytesList.size()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = bytesList.get(i);
		}
		
		return bytes;
	}

}

