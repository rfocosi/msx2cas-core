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
    static final int SAMPLES_PER_BYTE = 11 * BIT_SAMPLES;
    static final char SILENCE = 0x80;
    static final byte SILENCE_BYTE = (byte) SILENCE;
    private static final byte HIGH_AMPLITUDE_BYTE = (byte) 0xFF;
    private static final byte LOW_AMPLITUDE_BYTE = 0;
    
    static final long LENGTH_CORRECTION = 25;
    
    static final double LONG_HEADER_LENGTH = 20d/3d;
    static final double SHORT_HEADER_LENGTH = 5d/3d;
    
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

    private static final byte[][] BYTE_SAMPLES_NORMAL = buildByteSampleTable(false);
    private static final byte[][] BYTE_SAMPLES_INVERTED = buildByteSampleTable(true);
    
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
        return inverted ? SET_BIT_I_BYTES : SET_BIT_BYTES;
    }

    static byte[] dataByteSlice(int unsignedByte, boolean inverted) {
        byte[][] table = inverted ? BYTE_SAMPLES_INVERTED : BYTE_SAMPLES_NORMAL;
        return table[unsignedByte & 0xFF];
    }

    private static byte[][] buildByteSampleTable(boolean inverted) {
        byte[][] table = new byte[256][SAMPLES_PER_BYTE];
        byte[] zeroBit = inverted ? ZERO_BIT_I_BYTES : ZERO_BIT_BYTES;
        byte[] setBit = inverted ? SET_BIT_I_BYTES : SET_BIT_BYTES;

        for (int value = 0; value < 256; value++) {
            byte[] samples = table[value];
            System.arraycopy(START_BIT == 0 ? zeroBit : setBit, 0, samples, 0, BIT_SAMPLES);

            int bitMask = 1;
            for (int bitIndex = 1; bitIndex < 9; bitIndex++) {
                byte[] pattern = (value & bitMask) != 0 ? setBit : zeroBit;
                System.arraycopy(pattern, 0, samples, bitIndex * BIT_SAMPLES, BIT_SAMPLES);
                bitMask <<= 1;
            }

            byte[] stopPattern = STOP_BIT == 0 ? zeroBit : setBit;
            System.arraycopy(stopPattern, 0, samples, 9 * BIT_SAMPLES, BIT_SAMPLES);
            System.arraycopy(stopPattern, 0, samples, 10 * BIT_SAMPLES, BIT_SAMPLES);
        }
        return table;
    }
    
    /**
     * Encode a long header
     */
    public void encodeLongHeader() {
        encodeHeader(LONG_HEADER_LENGTH);
    }
    
    /**
     * Encode a short header
     */
    public void encodeShortHeader() {
        encodeHeader(SHORT_HEADER_LENGTH);
    }
    
    /**
     * Encode a header of the specified length
     * 
     * @param length Length of the header
     */
    private void encodeHeader(double length) {
        byte[] setBit = setBitSlice(sampleRate.isInverted());
        int iterations = headerIterations(sampleRate, length);
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
        if (data == null) {
            return;
        }
        for (char datum : data) {
            writeDataByte(datum);
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
     * Write a data byte to the output buffer
     * 
     * @param ch Byte to write
     */
    public void writeDataByte(char ch) {
        writeSampleBytes(dataByteSlice(ch & 0xFF, sampleRate.isInverted()));
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
