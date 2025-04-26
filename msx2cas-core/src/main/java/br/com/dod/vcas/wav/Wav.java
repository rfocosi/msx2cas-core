package br.com.dod.vcas.wav;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.util.WavHeader;
import br.com.dod.vcas.exception.FlowException;

import java.util.LinkedList;
import java.util.List;

public abstract class Wav implements WavOutput {

    static final long MIN_ENC_INPUT_FILE_LENGTH = 5L;

    public static final int CAS_FILENAME_LENGTH = 6;

    private static final int START_BIT = 0;
    private static final int STOP_BIT = 1;
    private static final char SILENCE = 0x80;
    private static final char HIGH_AMPLITUDE = 0xFF;
    private static final char LOW_AMPLITUDE = 0;

    private static final long LENGTH_CORRECTION = 25;

    private static final double LONG_HEADER_LENGTH = 20d/3d;
    private static final double SHORT_HEADER_LENGTH = 5d/3d;

    static final char SEPARATOR_PAUSE_LENGTH = 4;	// Seconds
    static final char FIRST_PAUSE_LENGTH = 2;	// Seconds
    static final char DEFAULT_PAUSE_LENGTH = 1; // Second

    private static final char[] ZERO_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};
    private static final char[] SET_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};

    private static final char[] ZERO_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};
    private static final char[] SET_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};
    private String inputFileName;

    final private SampleRate sampleRate;

    final private StringBuilder outputBuffer = new StringBuilder();
    byte[] inputMemPointer;

    protected Wav(SampleRate sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Wav(String inputFileName, SampleRate sampleRate) throws FlowException {
        this(sampleRate);

        this.inputMemPointer = FileCommons.readFile(inputFileName);
        this.inputFileName = inputFileName;

        validateMinFileSize();
    }

    public Wav convert() throws FlowException {
        encodeFileContent();

        encodePause(DEFAULT_PAUSE_LENGTH, LENGTH_CORRECTION);

        return this;
    }

    public byte[] toBytes() {
        final WavHeader wavHeader = new WavHeader(sampleRate, outputBuffer.length());

        byte[] wavHeaderBytes = wavHeader.toBytes();
        int offset = wavHeaderBytes.length;
        byte[] outBytes = new byte[offset + outputBuffer.length()];

        System.arraycopy(wavHeaderBytes, 0, outBytes, 0, offset);

        for (int i=0; i < outputBuffer.length(); i++ ){
            outBytes[i+offset] = (byte) outputBuffer.charAt(i);
        }

        return outBytes;
    }

    void encodeLongHeader() {
        encodeHeader(LONG_HEADER_LENGTH);
    }

    void encodeShortHeader() {
        encodeHeader(SHORT_HEADER_LENGTH);
    }

    private char[] setBit() {
        return sampleRate.isInverted() ? SET_BIT_I : SET_BIT;
    }

    private char[] zeroBit() {
        return sampleRate.isInverted() ? ZERO_BIT_I : ZERO_BIT;
    }

    private void encodeHeader(double length) {
        final List<Character> chars = new LinkedList<>();

        for (int j = 0; j < sampleRate.headerEncodingLength(length); j++)	{
            for (char b : setBit()) {
                chars.add(b);
            }
        }
        writeByteChars(chars);
    }

    void encodeData(char[] data) {
        for (int i = 0; i < sizeof(data); i++) {
            writeDataByte(data[i]);
        }
    }

    void encodePause(int pauseLength) {
        encodePause(pauseLength, 0);
    }

    private void encodePause(int pauseLength, long lengthCorrection) {
        int charLength = (int) (sampleRate.intValue() * pauseLength + lengthCorrection);
        final List<Character> chars = new LinkedList<>();

        for (int j = 0; j < charLength; j++) {
            chars.add(SILENCE);
        }
        writeByteChars(chars);
    }

    void writeDataByte(char ch) {
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

    private void writeByteChars(List<Character> ch) {
        for (char c : ch) {
            outputBuffer.append(c);
        }
    }

    char calculateCRC(int blockStart, int blockEnd) throws FlowException {
        if (blockStart > blockEnd) throw FlowException.error("header_conflicting_information");
        char crc = 0;
        for (int i = blockStart; i < blockEnd; i++) crc += (char) inputMemPointer[i];
        return crc;
    }

    char[] buildBinaryAddressBuffer(final long binarySize) {
        char a = (char) (binarySize + 0x9000 - 1);

        char[] addressBuffer = new char[6];

        addressBuffer[0] = 0;
        addressBuffer[1] = 0x90;
        addressBuffer[2] = a;
        addressBuffer[3] = (char)(a >> 8);
        addressBuffer[4] = 0;
        addressBuffer[5] = 0x90;

        return addressBuffer;
    }

    public String casName() {
        return FileCommons.getCasName(inputFileName);
    }

    public char[] getNameBuffer() {
        return formatNameBuffer("", CAS_FILENAME_LENGTH);
    }

    char[] getNameBuffer(final int number) {
        return formatNameBuffer(String.valueOf(number), CAS_FILENAME_LENGTH - (String.valueOf(number).length()));
    }

    private char[] formatNameBuffer(final String number, final int nameSize) {
        return String.format("%." + nameSize + "s%s", casName(), number).toCharArray();
    }

    int getFileSize() {
        return inputMemPointer.length;
    }

    private void validateMinFileSize() throws FlowException {
        if (getFileSize() < MIN_ENC_INPUT_FILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    static int sizeof(char[] charArray) {
        return (charArray == null ? 0 : charArray.length);
    }

    abstract void encodeFileContent() throws FlowException;
}
