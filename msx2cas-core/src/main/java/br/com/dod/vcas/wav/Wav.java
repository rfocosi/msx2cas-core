package br.com.dod.vcas.wav;

import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.util.WavHeader;
import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;

public abstract class Wav {

    static final long MIN_ENC_INPUTFILE_LENGTH = 5L;
    static final int CAS_FILENAME_LENGTH = FileCommons.CAS_FILENAME_LENGTH;

    private static final char START_BIT = 0;
    private static final char STOP_BIT = 1;
    private static final char SILENCE = 0x80;
    private static final char HIGH_AMPLITUDE = 0xFF;
    private static final char LOW_AMPLITUDE = 0;

    private static final char WAV_HEADER_OFFSET_VALUE = 0x77;
    private static final long LENGTH_CORRECTION = 25;

    static final double LONG_HEADER_LENGTH = 20d/3d;
    static final double SHORT_HEADER_LENGTH = 5d/3d;

    static final char SEPARATOR_PAUSE_LENGTH = 4;	// Seconds
    static final char FIRST_PAUSE_LENGTH = 2;	// Seconds
    static final char DEFAULT_PAUSE_LENGTH = 1; // Second

    static final int SIZE_OF_BITSTREAM = 11;

    private static final char[] ZERO_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};
    private static final char[] SET_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};

    private static final char[] ZERO_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};
    private static final char[] SET_BIT_I = {HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE};

    SampleRate sampleRate;

    double pureSampleShortHeaderLength;

    DWORD extraBytes;
    DWORD fileOffset;
    DWORD moreExtraBytes;
    char[] fileHeader;

    char[] nameBuffer;

    private WavHeader wavHeader;
    private StringBuilder outputBuffer;

    byte[] inputMemPointer;

    int fileLength;
    List<CasFile> casList;

    public Wav(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId) throws FlowException {
        this(inputFileName, sampleRate, fileOffset, fileHeaderId, null);
    }

    public Wav(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId, List<CasFile> casList) throws FlowException {
        outputBuffer = new StringBuilder();

        initVars(inputFileName, sampleRate, fileOffset, fileHeaderId);

        if (casList == null || casList.isEmpty()) {
            this.inputMemPointer = FileCommons.readFile(inputFileName);
            this.fileLength = inputMemPointer.length;
        } else {
            this.casList = casList;
            int casFileSize = 0;
            for (CasFile casFile : casList) {
                casFileSize += casFile.getSize();
            }
            this.fileLength = casFileSize;
        }
    }

    private void initVars(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId) {

        wavHeader = new WavHeader();
        pureSampleShortHeaderLength = SHORT_HEADER_LENGTH;

        this.fileOffset = fileOffset;
        this.fileHeader = fileHeaderId;

        this.sampleRate = sampleRate;

        this.nameBuffer = FileCommons.getNameBuffer(inputFileName);
    }

    public char[] getFileId() {
        return nameBuffer;
    }

    public Wav convert() throws FlowException {
        validate();
        setup();

        setDefaultHeader();

        encodePause(FIRST_PAUSE_LENGTH);
        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(getFileId());
        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeFileContent();

        encodePause(DEFAULT_PAUSE_LENGTH, LENGTH_CORRECTION);

        return this;
    }

    public byte[] toBytes() {
        byte[] wavHeaderBytes = wavHeader.toBytes();
        int offset = wavHeaderBytes.length;
        byte[] outBytes = new byte[offset + outputBuffer.length()];

        System.arraycopy(wavHeaderBytes, 0, outBytes, 0, offset);

        for (int i=0; i < outputBuffer.length(); i++ ){
            outBytes[i+offset] = (byte) outputBuffer.charAt(i);
        }

        return outBytes;
    }

    private void setDefaultHeader() {
        wavHeader.SamplesPerSec = new DWORD(sampleRate.intValue());

        wavHeader.PureSampleLength = new DWORD((sampleRate.intValue() * (FIRST_PAUSE_LENGTH + DEFAULT_PAUSE_LENGTH + DEFAULT_PAUSE_LENGTH)) + // Length of pauses
                Math.round(sampleRate.intValue() * (LONG_HEADER_LENGTH + pureSampleShortHeaderLength)) +	// Length of headers
                ((sizeof(fileHeader) + CAS_FILENAME_LENGTH + fileLength + extraBytes.longValue() - fileOffset.longValue()) * Math.round(sampleRate.sampleScale() * SIZE_OF_BITSTREAM * sampleRate.bitEncodingLength())) + // Length of data
                moreExtraBytes.longValue());

        wavHeader.SampleLength = new DWORD(wavHeader.PureSampleLength.longValue() + WAV_HEADER_OFFSET_VALUE);
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
        for (int j = 0; j < sampleRate.headerEncodingLength(length); j++)	{
            writeByteChars(setBit());
        }
    }

    void encodeData(char[] data) {
        for (int i = 0; i < sizeof(data); i++) {
            writeDataByte(data[i]);
        }
    }

    void encodePause(int pauseLength) {
        encodePause(pauseLength, 0);
    }

    private void encodePause(int pauseLength, long lenghtCorrection) {
        int charLenght = (int) (sampleRate.intValue() * pauseLength + lenghtCorrection);
        char[] chars = new char[charLenght];

        for (int j = 0; j < charLenght; j++) {
            chars[j] = SILENCE;
        }
        writeByteChars(chars);
    }

    void writeDataByte(char ch) {
        final char[] bitStream = new char[SIZE_OF_BITSTREAM];

        char bitMask = 1;
        int bitSampleLength = zeroBit().length;
        char[] dataByte = new char[bitSampleLength * sizeof(bitStream)];

        bitStream[0] =	START_BIT;
        bitStream[9] =	STOP_BIT;
        bitStream[10] =	STOP_BIT;

        // Fill bitStream
        for (int i = 1; i < 9; i++) {
            if ((ch & bitMask) != 0) bitStream[i] = 1;
            else bitStream[i] = 0;
            bitMask = (char) (bitMask << 1);
        }

        for (int i = 0; i < sizeof(bitStream); i++) {
            char[] bit = (bitStream[i] == 0 ? zeroBit() : setBit());
            System.arraycopy(bit, 0, dataByte, i * bitSampleLength, bitSampleLength);
        }
        writeByteChars(dataByte);
    }

    private void writeByteChars(char[] ch) {
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

        char[] adressBuffer = new char[6];

        adressBuffer[0] = 0;
        adressBuffer[1] = 0x90;
        adressBuffer[2] = a;
        adressBuffer[3] = (char)(a >> 8);
        adressBuffer[4] = 0;
        adressBuffer[5] = 0x90;

        return adressBuffer;
    }

    static int sizeof(char[] charArray) {
        return (charArray == null ? 0 : charArray.length);
    }

    public WavHeader getWavHeader() {
        return this.wavHeader;
    }

    abstract void validate() throws FlowException;
    abstract void setup() throws FlowException;
    abstract void encodeFileContent() throws FlowException;
}
