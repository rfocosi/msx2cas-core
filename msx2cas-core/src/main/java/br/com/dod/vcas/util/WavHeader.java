package br.com.dod.vcas.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import br.com.dod.vcas.model.SampleRate;

public class WavHeader {

    private static final int HEADER_SIZE = 44;
    private static final byte[] WAV_HEADER_ID = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MM_FILE_TYPE = "WAVE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FORMAT_ID = "fmt ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DATA_ID = "data".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_SIZE = 16;
    private static final short FORMAT_TAG = 1; // PCM
    private static final short NUM_CHANNELS = 1; // Mono
    private static final short BITS_PER_SAMPLE = 8;

    private final int sampleRate;
    private final long dataSize;

    public WavHeader(SampleRate sampleRate, long dataSize) {
        this.sampleRate = sampleRate.intValue();
        this.dataSize = dataSize;
    }

    public byte[] toBytes() {
        short blkAlign = (short) ((BITS_PER_SAMPLE * NUM_CHANNELS) / 8);
        int bytesPerSec = (sampleRate * BITS_PER_SAMPLE * NUM_CHANNELS) / 8;

        return ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(WAV_HEADER_ID)
                .putInt((int) (dataSize + 36))
                .put(MM_FILE_TYPE)
                .put(FORMAT_ID)
                .putInt(FORMAT_SIZE)
                .putShort(FORMAT_TAG)
                .putShort(NUM_CHANNELS)
                .putInt(sampleRate)
                .putInt(bytesPerSec)
                .putShort(blkAlign)
                .putShort(BITS_PER_SAMPLE)
                .put(DATA_ID)
                .putInt((int) dataSize)
                .array();
    }
}

