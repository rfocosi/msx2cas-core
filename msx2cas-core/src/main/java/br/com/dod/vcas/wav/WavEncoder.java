package br.com.dod.vcas.wav;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.util.WavHeader;

/**
 * Class responsible for encoding data into WAV format
 */
public class WavEncoder implements WavOutput {
    
    private static final int START_BIT = 0;
    private static final int STOP_BIT = 1;
    private static final int BIT_SAMPLES = 10;
    private static final int TURBO_BOOT_3600_BIT_SAMPLES = 16;
    static final int SAMPLES_PER_BYTE = 11 * BIT_SAMPLES;
    static final int SAMPLES_PER_BYTE_16 = 11 * TURBO_BOOT_3600_BIT_SAMPLES;
    static final char SILENCE = 0x80;
    static final byte SILENCE_BYTE = (byte) SILENCE;
    private static final byte HIGH_AMPLITUDE_BYTE = (byte) 0xFF;
    private static final byte LOW_AMPLITUDE_BYTE = 0;
    
    static final long LENGTH_CORRECTION = 25;
    
    static final double LONG_HEADER_LENGTH = 20d/3d;
    static final double SHORT_HEADER_LENGTH = 5d/3d;
    static final int TURBO_PILOT_CYCLES = 512;
    static final int TURBO_SYNC_BYTE = 0xA5;
    static final int TURBO_PAUSE_DIVISOR = 4;
    
    public static final char SEPARATOR_PAUSE_LENGTH = 4;  // Seconds
    public static final char FIRST_PAUSE_LENGTH = 2;      // Seconds
    public static final char DEFAULT_PAUSE_LENGTH = 1;    // Second
    
    private static final byte[] ZERO_BIT_BYTES = {LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, 
                                           HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE};
    private static final byte[] SET_BIT_BYTES = {LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, 
                                          LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE};
    
    private static final byte[] ZERO_BIT_I_BYTES = {HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, 
                                             LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE};
    private static final byte[] SET_BIT_I_BYTES = {HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, 
                                            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE};

    // Symmetric 16-sample BIOS patterns (~2067 bps) for Stage-1 bootstrap at 33075 Hz (3600 bps)
    // so MSX1 BIOS TAPION/TAPIN (~238 T-state loop) loads Stage 1 before switching to 33075 Hz Stage 2.
    private static final byte[] ZERO_BIT_16_BYTES = {
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE
    };
    private static final byte[] SET_BIT_16_BYTES = {
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE
    };
    private static final byte[] ZERO_BIT_16_I_BYTES = {
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE
    };
    private static final byte[] SET_BIT_16_I_BYTES = {
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE
    };

    private static final byte[] TURBO_ZERO_BIT_BYTES = {
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE
    };
    private static final byte[] TURBO_ONE_BIT_BYTES = {
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE,
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE
    };
    private static final byte[] TURBO_ZERO_BIT_I_BYTES = {
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE
    };
    private static final byte[] TURBO_ONE_BIT_I_BYTES = {
            HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE, HIGH_AMPLITUDE_BYTE,
            LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE, LOW_AMPLITUDE_BYTE
    };

    private static final byte[][] BYTE_SAMPLES_NORMAL = buildByteSampleTable(BIT_SAMPLES, ZERO_BIT_BYTES, SET_BIT_BYTES);
    private static final byte[][] BYTE_SAMPLES_INVERTED = buildByteSampleTable(BIT_SAMPLES, ZERO_BIT_I_BYTES, SET_BIT_I_BYTES);
    private static final byte[][] BYTE_SAMPLES_16_NORMAL = buildByteSampleTable(TURBO_BOOT_3600_BIT_SAMPLES, ZERO_BIT_16_BYTES, SET_BIT_16_BYTES);
    private static final byte[][] BYTE_SAMPLES_16_INVERTED = buildByteSampleTable(TURBO_BOOT_3600_BIT_SAMPLES, ZERO_BIT_16_I_BYTES, SET_BIT_16_I_BYTES);
    private static final byte[][] TURBO_BYTE_SAMPLES_NORMAL = buildTurboByteSampleTable(false);
    private static final byte[][] TURBO_BYTE_SAMPLES_INVERTED = buildTurboByteSampleTable(true);
    
    private final SampleRate sampleRate;
    private final StringBuilder outputBuffer = new StringBuilder();
    private final String inputFileName;
    
    /**
     * Constructor for WavEncoder
     * 
     * @param sampleRate Sample rate to use for encoding
     * @param inputFileName Name of the input file
     */
    public WavEncoder(SampleRate sampleRate, String inputFileName) {
        this.sampleRate = sampleRate;
        this.inputFileName = inputFileName;
    }

    /**
     * Calculate the exact number of header bit repetitions for a sample rate and header length.
     *
     * @param sampleRate Target sample rate
     * @param length Header duration in seconds
     * @return Number of 10-sample set-bit repetitions
     */
    public static int headerIterations(SampleRate sampleRate, double length) {
        return Math.max(0, (int) Math.ceil(sampleRate.headerEncodingLength(length)));
    }

    static boolean use16SampleBootstrap(SampleRate sampleRate, boolean hasTurboPayload) {
        return hasTurboPayload && sampleRate == SampleRate.sr33075;
    }

    static int headerIterations(SampleRate sampleRate, double length, boolean hasTurboPayload) {
        if (use16SampleBootstrap(sampleRate, hasTurboPayload)) {
            return Math.max(0, (int) Math.ceil(sampleRate.intValue() * length / TURBO_BOOT_3600_BIT_SAMPLES));
        }
        return headerIterations(sampleRate, length);
    }

    /**
     * Calculate the exact number of silence samples for a pause duration.
     *
     * @param sampleRate Target sample rate
     * @param pauseLength Pause duration in seconds
     * @param lengthCorrection Additional sample count correction
     * @return Number of silence PCM samples
     */
    public static int pauseSampleCount(SampleRate sampleRate, int pauseLength, long lengthCorrection) {
        return (int) (sampleRate.intValue() * pauseLength + lengthCorrection);
    }

    /**
     * Calculate the exact number of silence samples for the inter-stage pause before a Stage-2 turbo block.
     *
     * @param sampleRate Target sample rate
     * @return Number of silence PCM samples (0.25 seconds)
     */
    public static int turboPauseSampleCount(SampleRate sampleRate) {
        return sampleRate.intValue() / TURBO_PAUSE_DIVISOR;
    }

    /**
     * Get the 10-sample set-bit waveform array for the requested polarity.
     *
     * @param inverted True for inverted waveform polarity
     * @return Copy of the 10-byte set-bit waveform pattern
     */
    public static byte[] setBitPattern(boolean inverted) {
        return setBitSlice(inverted).clone();
    }

    /**
     * Get the 110-sample waveform array for an unsigned byte (start bit, 8 LSB-first data bits, 2 stop bits).
     *
     * @param unsignedByte Byte value to encode (low 8 bits used)
     * @param inverted True for inverted waveform polarity
     * @return Copy of the 110-byte PCM waveform pattern
     */
    public static byte[] encodeByteSamples(int unsignedByte, boolean inverted) {
        return dataByteSlice(unsignedByte, inverted).clone();
    }

    static byte[] setBitSlice(boolean inverted) {
        return setBitSlice(inverted, false);
    }

    static byte[] setBitSlice(boolean inverted, boolean use16Sample) {
        if (use16Sample) {
            return inverted ? SET_BIT_16_I_BYTES : SET_BIT_16_BYTES;
        }
        return inverted ? SET_BIT_I_BYTES : SET_BIT_BYTES;
    }

    static byte[] dataByteSlice(int unsignedByte, boolean inverted) {
        return dataByteSlice(unsignedByte, inverted, false);
    }

    static byte[] dataByteSlice(int unsignedByte, boolean inverted, boolean use16Sample) {
        byte[][] table;
        if (use16Sample) {
            table = inverted ? BYTE_SAMPLES_16_INVERTED : BYTE_SAMPLES_16_NORMAL;
        } else {
            table = inverted ? BYTE_SAMPLES_INVERTED : BYTE_SAMPLES_NORMAL;
        }
        return table[unsignedByte & 0xFF];
    }

    static byte[] turboZeroBitSlice(boolean inverted) {
        return inverted ? TURBO_ZERO_BIT_I_BYTES : TURBO_ZERO_BIT_BYTES;
    }

    static byte[] turboOneBitSlice(boolean inverted) {
        return inverted ? TURBO_ONE_BIT_I_BYTES : TURBO_ONE_BIT_BYTES;
    }

    static byte[] turboByteSlice(int unsignedByte, boolean inverted) {
        byte[][] table = inverted ? TURBO_BYTE_SAMPLES_INVERTED : TURBO_BYTE_SAMPLES_NORMAL;
        return table[unsignedByte & 0xFF];
    }

    private static byte[][] buildTurboByteSampleTable(boolean inverted) {
        byte[][] table = new byte[256][];
        byte[] zeroBit = turboZeroBitSlice(inverted);
        byte[] oneBit = turboOneBitSlice(inverted);

        for (int value = 0; value < 256; value++) {
            int setBits = Integer.bitCount(value);
            byte[] samples = new byte[(8 - setBits) * zeroBit.length + setBits * oneBit.length];
            int offset = 0;
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                byte[] pattern = ((value >> bitIndex) & 1) != 0 ? oneBit : zeroBit;
                System.arraycopy(pattern, 0, samples, offset, pattern.length);
                offset += pattern.length;
            }
            table[value] = samples;
        }
        return table;
    }

    private static byte[][] buildByteSampleTable(int bitSamples, byte[] zeroBit, byte[] setBit) {
        byte[][] table = new byte[256][11 * bitSamples];

        for (int value = 0; value < 256; value++) {
            byte[] samples = table[value];
            System.arraycopy(START_BIT == 0 ? zeroBit : setBit, 0, samples, 0, bitSamples);

            int bitMask = 1;
            for (int bitIndex = 1; bitIndex < 9; bitIndex++) {
                byte[] pattern = (value & bitMask) != 0 ? setBit : zeroBit;
                System.arraycopy(pattern, 0, samples, bitIndex * bitSamples, bitSamples);
                bitMask <<= 1;
            }

            byte[] stopPattern = STOP_BIT == 0 ? zeroBit : setBit;
            System.arraycopy(stopPattern, 0, samples, 9 * bitSamples, bitSamples);
            System.arraycopy(stopPattern, 0, samples, 10 * bitSamples, bitSamples);
        }
        return table;
    }
    
    /**
     * Encode a long header
     */
    public void encodeLongHeader() {
        encodeLongHeader(false);
    }

    /**
     * Encode a long header, scaling Stage-1 bootstrap bits when needed for 3600 bps turbo mode.
     *
     * @param hasTurboPayload True when this track carries a Stage-2 turbo payload
     */
    public void encodeLongHeader(boolean hasTurboPayload) {
        encodeHeader(LONG_HEADER_LENGTH, hasTurboPayload);
    }
    
    /**
     * Encode a short header
     */
    public void encodeShortHeader() {
        encodeShortHeader(false);
    }

    /**
     * Encode a short header, scaling Stage-1 bootstrap bits when needed for 3600 bps turbo mode.
     *
     * @param hasTurboPayload True when this track carries a Stage-2 turbo payload
     */
    public void encodeShortHeader(boolean hasTurboPayload) {
        encodeHeader(SHORT_HEADER_LENGTH, hasTurboPayload);
    }
    
    /**
     * Encode a header of the specified length
     * 
     * @param length Length of the header
     * @param hasTurboPayload True when this track carries a Stage-2 turbo payload
     */
    private void encodeHeader(double length, boolean hasTurboPayload) {
        boolean use16Sample = use16SampleBootstrap(sampleRate, hasTurboPayload);
        byte[] setBit = setBitSlice(sampleRate.isInverted(), use16Sample);
        int iterations = headerIterations(sampleRate, length, hasTurboPayload);
        for (int j = 0; j < iterations; j++) {
            writeSampleBytes(setBit);
        }
    }
    
    /**
     * Encode data as a series of bytes
     * 
     * @param data Data to encode
     */
    public void encodeData(char[] data) {
        encodeData(data, false);
    }

    /**
     * Encode data as a series of bytes, scaling Stage-1 bootstrap bits when needed for 3600 bps turbo mode.
     *
     * @param data Data to encode
     * @param hasTurboPayload True when this track carries a Stage-2 turbo payload
     */
    public void encodeData(char[] data, boolean hasTurboPayload) {
        if (data == null) {
            return;
        }
        for (char datum : data) {
            writeDataByte(datum, hasTurboPayload);
        }
    }
    
    /**
     * Encode a pause of the specified length
     * 
     * @param pauseLength Length of the pause in seconds
     */
    public void encodePause(int pauseLength) {
        encodePause(pauseLength, 0);
    }
    
    /**
     * Encode a pause of the specified length with a length correction
     * 
     * @param pauseLength Length of the pause in seconds
     * @param lengthCorrection Correction to apply to the pause length
     */
    public void encodePause(int pauseLength, long lengthCorrection) {
        int charLength = pauseSampleCount(sampleRate, pauseLength, lengthCorrection);
        for (int j = 0; j < charLength; j++) {
            outputBuffer.append(SILENCE);
        }
    }
    
    /**
     * Encode a Stage-2 Z80 turbo payload block (0.25s pause, 512-cycle pilot, sync pulse + 0xA5,
     * LSB-first PWM payload bytes, and trailing 0-bit transition).
     *
     * @param payload Raw payload bytes to encode
     */
    public void encodeTurboBlock(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        int pauseSamples = turboPauseSampleCount(sampleRate);
        for (int i = 0; i < pauseSamples; i++) {
            outputBuffer.append(SILENCE);
        }
        boolean inverted = sampleRate.isInverted();
        byte[] pilotBit = turboZeroBitSlice(inverted);
        for (int i = 0; i < TURBO_PILOT_CYCLES; i++) {
            writeSampleBytes(pilotBit);
        }
        writeSampleBytes(turboOneBitSlice(inverted));
        writeSampleBytes(turboByteSlice(TURBO_SYNC_BYTE, inverted));
        for (byte b : payload) {
            writeSampleBytes(turboByteSlice(b & 0xFF, inverted));
        }
        writeSampleBytes(pilotBit);
    }

    /**
     * Write a data byte to the output buffer
     * 
     * @param ch Byte to write
     */
    public void writeDataByte(char ch) {
        writeDataByte(ch, false);
    }

    /**
     * Write a data byte to the output buffer, scaling Stage-1 bootstrap bits when needed for 3600 bps turbo mode.
     *
     * @param ch Byte to write
     * @param hasTurboPayload True when this track carries a Stage-2 turbo payload
     */
    public void writeDataByte(char ch, boolean hasTurboPayload) {
        boolean use16Sample = use16SampleBootstrap(sampleRate, hasTurboPayload);
        writeSampleBytes(dataByteSlice(ch & 0xFF, sampleRate.isInverted(), use16Sample));
    }

    private void writeSampleBytes(byte[] samples) {
        for (byte b : samples) {
            outputBuffer.append((char) (b & 0xFF));
        }
    }
    
    /**
     * Finish the WAV encoding by adding a default pause
     */
    public void finish() {
        encodePause(DEFAULT_PAUSE_LENGTH, LENGTH_CORRECTION);
    }
    
    @Override
    public byte[] toBytes() {
        final WavHeader wavHeader = new WavHeader(sampleRate, outputBuffer.length());
        
        byte[] wavHeaderBytes = wavHeader.toBytes();
        int offset = wavHeaderBytes.length;
        byte[] outBytes = new byte[offset + outputBuffer.length()];
        
        System.arraycopy(wavHeaderBytes, 0, outBytes, 0, offset);
        
        for (int i = 0; i < outputBuffer.length(); i++) {
            outBytes[i + offset] = (byte) outputBuffer.charAt(i);
        }
        
        return outBytes;
    }
    
    @Override
    public String casName() {
        return inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
    }
}
