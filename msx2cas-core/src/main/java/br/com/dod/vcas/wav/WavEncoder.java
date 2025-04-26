package br.com.dod.vcas.wav;

import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.WavHeader;

/**
 * Class responsible for encoding data into WAV format
 */
public class WavEncoder implements WavOutput {
    
    private static final int START_BIT = 0;
    private static final int STOP_BIT = 1;
    private static final char SILENCE = 0x80;
    private static final char HIGH_AMPLITUDE = 0xFF;
    private static final char LOW_AMPLITUDE = 0;
    
    private static final long LENGTH_CORRECTION = 25;
    
    private static final double LONG_HEADER_LENGTH = 20d/3d;
    private static final double SHORT_HEADER_LENGTH = 5d/3d;
    
    public static final char SEPARATOR_PAUSE_LENGTH = 4;  // Seconds
    public static final char FIRST_PAUSE_LENGTH = 2;      // Seconds
    public static final char DEFAULT_PAUSE_LENGTH = 1;    // Second
    
    private static final char[] ZERO_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, 
                                           HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};
    private static final char[] SET_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, 
                                          LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};
    
    private static final char[] ZERO_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, 
                                             LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};
    private static final char[] SET_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, 
                                            HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};
    
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
     * Get the appropriate bit pattern for a set bit based on wave inversion
     * 
     * @return Bit pattern for a set bit
     */
    private char[] setBit() {
        return sampleRate.isInverted() ? SET_BIT_I : SET_BIT;
    }
    
    /**
     * Get the appropriate bit pattern for a zero bit based on wave inversion
     * 
     * @return Bit pattern for a zero bit
     */
    private char[] zeroBit() {
        return sampleRate.isInverted() ? ZERO_BIT_I : ZERO_BIT;
    }
    
    /**
     * Encode a header of the specified length
     * 
     * @param length Length of the header
     */
    private void encodeHeader(double length) {
        final List<Character> chars = new LinkedList<>();
        
        for (int j = 0; j < sampleRate.headerEncodingLength(length); j++) {
            for (char b : setBit()) {
                chars.add(b);
            }
        }
        writeByteChars(chars);
    }
    
    /**
     * Encode data as a series of bytes
     * 
     * @param data Data to encode
     */
    public void encodeData(char[] data) {
        for (int i = 0; i < data.length; i++) {
            writeDataByte(data[i]);
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
        int charLength = (int) (sampleRate.intValue() * pauseLength + lengthCorrection);
        final List<Character> chars = new LinkedList<>();
        
        for (int j = 0; j < charLength; j++) {
            chars.add(SILENCE);
        }
        writeByteChars(chars);
    }
    
    /**
     * Write a data byte to the output buffer
     * 
     * @param ch Byte to write
     */
    public void writeDataByte(char ch) {
        final List<Integer> bitStream = fillBitStream(ch);
        final List<Character> dataByte = new LinkedList<>();
        
        for (Integer b : bitStream) {
            char[] bit = (b == 0 ? zeroBit() : setBit());
            for (char c : bit) {
                dataByte.add(c);
            }
        }
        
        writeByteChars(dataByte);
    }
    
    /**
     * Fill a bit stream with the bits from a character
     * 
     * @param ch Character to convert to bits
     * @return List of bits (0 or 1)
     */
    private List<Integer> fillBitStream(char ch) {
        final List<Integer> bitStream = new LinkedList<>();
        
        bitStream.add(START_BIT);
        
        char bitMask = 1;
        for (int i = 1; i < 9; i++) {
            if ((ch & bitMask) != 0) bitStream.add(1);
            else bitStream.add(0);
            bitMask = (char) (bitMask << 1);
        }
        
        bitStream.add(STOP_BIT);
        bitStream.add(STOP_BIT);
        
        return bitStream;
    }
    
    /**
     * Write a list of characters to the output buffer
     * 
     * @param ch List of characters to write
     */
    private void writeByteChars(List<Character> ch) {
        for (char c : ch) {
            outputBuffer.append(c);
        }
    }
    
    /**
     * Finalize the WAV encoding by adding a default pause
     */
    public void finalize() {
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
        return inputFileName != null ? inputFileName.replaceFirst(".+/", "").replaceAll("[^.\\w]", "") : "";
    }
}
