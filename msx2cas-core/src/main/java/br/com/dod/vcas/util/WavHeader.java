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
    private static final DWORD FORMAT_SIZE = new DWORD(16);
    private static final WORD FORMAT_TAG = new WORD(1); // PCM
    private static final WORD NUM_CHANNELS = new WORD(1); // Mono
    private static final WORD BITS_PER_SAMPLE = new WORD(8);

    public DWORD SampleLength;
    public DWORD SamplesPerSec;
    public DWORD PureSampleLength;

    private List<IntegerType> getHeader() {
        final List<IntegerType> charList = new LinkedList<>();

        charList.add(WAV_HEADER_ID);
        charList.add(SampleLength);
        charList.add(MM_FILE_TYPE);
        charList.add(FORMAT_ID);
        charList.add(FORMAT_SIZE);
        charList.add(FORMAT_TAG);
        charList.add(NUM_CHANNELS);
        charList.add(SamplesPerSec);
        charList.add(getBytesPerSec());
        charList.add(getBlkAllign());
        charList.add(BITS_PER_SAMPLE);
        charList.add(DATA_ID);
        charList.add(PureSampleLength);

        return charList;
    }

    private WORD getBlkAllign() {
        return new WORD((BITS_PER_SAMPLE.longValue() * NUM_CHANNELS.longValue()) / 8);
    }

    private DWORD getBytesPerSec() {
        return new DWORD((SamplesPerSec.longValue() * BITS_PER_SAMPLE.longValue() * NUM_CHANNELS.longValue()) / 8);
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

