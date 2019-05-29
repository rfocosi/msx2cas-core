package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Rom extends Wav {

    private static final long MAX_ENC_INPUTFILE_LENGTH = 16384;

    private static final char[] romFileHeader = {0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0};

    private char[] loader;

    public Rom(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, new DWORD(0), romFileHeader);
    }

    public static Rom build(String inputFileName, SampleRate sampleRate) throws FlowException {
        return (FileCommons.readFile(inputFileName).length > 25000) ? new Rom32K(inputFileName, sampleRate) : new Rom(inputFileName, sampleRate);
    }

    @Override
    protected void validate() throws FlowException {
        if (this.fileLength < MIN_ENC_INPUTFILE_LENGTH || this.fileLength > MAX_ENC_INPUTFILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void setup() throws FlowException {

        initLoader();

        setExtraBytes();

        setMoreExtraBytes();

        if (nameBuffer.length > 1) {
            char[] nameCharArray = nameBuffer[1].trim().toCharArray();
            System.arraycopy(nameCharArray, 0, loader, 21, nameCharArray.length);
        }
    }

    private char getRomTypeHeader() throws FlowException {
        char ch = (char) inputMemPointer[3];
        if ((ch & 0xf0) >= 0xD0) throw FlowException.error("type_32k_not_supported");
        return ch;
    }

    private void setExtraBytes() throws FlowException {

        DWORD extraBytes = new DWORD(6);

        if (getRomTypeHeader() < 0x80) {
            extraBytes = new DWORD((extraBytes.longValue() + loader.length + 6));
        } else {
            extraBytes = new DWORD((extraBytes.longValue() + 6));
        }
        this.extraBytes = extraBytes;
    }

    private void setMoreExtraBytes() {

        DWORD moreExtraBytes = new DWORD(((wavSampleRate * FIRST_PAUSE_LENGTH) + (wavSampleRate * DEFAULT_PAUSE_LENGTH) +
                Math.round(wavSampleRate * LONG_HEADER_LENGTH + wavSampleRate * SHORT_HEADER_LENGTH) +
                (fileHeader.length + CAS_FILENAME_LENGTH) * Math.round(sampleScale * SIZE_OF_BITSTREAM * bitEncodingLength)));

        this.moreExtraBytes = moreExtraBytes;
    }

    @Override
    protected void encodeFileContent() throws FlowException {
        int blockSize = inputMemPointer.length;

        char headId = getRomTypeHeader();

        if (headId < 0x80) {
            encodeRomBlock(headId, 0, blockSize, loader);
        } else {
            encodeShortHeader();

            char[] addressBuffer = new char[6];

            // Encode 6 bytes of addresses
            char a = (char) ((headId & 0xf0) << 8);
            addressBuffer[0] = 0;
            addressBuffer[1] = (char)(a >> 8);
            a = (char) (a + blockSize - 1);
            addressBuffer[2] = a;
            addressBuffer[3] = (char)(a >> 8);
            addressBuffer[4] = (char)inputMemPointer[2];
            addressBuffer[5] = (char)inputMemPointer[3];

            encodeData(addressBuffer);

            for (int i = 0; i < blockSize; i++)	{
                writeDataByte((char) inputMemPointer[i]);
            }
        }
    }

    void encodeRomBlock(char headId, int blockStart, int blockEnd, char[] loader) throws FlowException {
        char romCRC = calculateCRC(blockStart, blockEnd);

        encodeShortHeader();

        encodeData(buildBinaryAddressBuffer(sizeof(loader) + blockEnd - blockStart));

        char a = (char) ((headId & 0xf0) << 8);
        a = (char) (a  + blockStart);
        loader[3] = 0;
        loader[4] = (char)(a >> 8);
        a = (char) (a + blockEnd - blockStart);
        loader[5] = a;
        loader[6] = (char)(a >> 8);
        loader[7] = (char)inputMemPointer[2];
        loader[8] = (char)inputMemPointer[3];
        loader[9] = romCRC;

        encodeData(loader);

        for (int i = blockStart; i < blockEnd; i++)	{
            writeDataByte((char) inputMemPointer[i]);	// Encode data byte
        }
    }

    private void initLoader() {

        this.loader = new char[]{
                0xC3, 0x30, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x20, 0x4D,
                0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x20, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64,
                0x69, 0x6E, 0x67, 0x20, 0x66, 0x69, 0x6C, 0x65, 0x2C, 0x20, 0x77, 0x61, 0x69,
                0x74, 0X20, 0x2E, 0x2E, 0x2E, 0x20, 0x20, 0x20, 0x00, 0xF3, 0x2A, 0x03, 0x90,
                0xED, 0x5B, 0x05, 0x90, 0xEB, 0xED, 0x52, 0xE5, 0xC1, 0x21, 0xE3, 0x90, 0xAF,
                0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79, 0xB7, 0xC2, 0x42, 0x90, 0x78, 0xB7,
                0xC2, 0x42, 0x90, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0x9C, 0x90,
                0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23,
                0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCF, 0x00,
                0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90, 0x1A, 0xB7, 0xCA, 0x8E,
                0x90, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0xC3, 0x7E, 0x90, 0x21,
                0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xFB, 0xC9,
                0xF3, 0x2A, 0x07, 0x90, 0xE5, 0x7C, 0xE6, 0xC0, 0x11, 0x00, 0x00, 0x21, 0x0F,
                0xF0, 0x28, 0x06, 0x11, 0x00, 0x40, 0x21, 0x0C, 0xF3, 0x3A, 0xFF, 0xFF, 0x2F,
                0xA4, 0x4F, 0x0F, 0x0F, 0x0F, 0x0F, 0xA5, 0xB1, 0x32, 0xFF, 0xFF, 0xDB, 0xA8,
                0xA4, 0x47, 0x0F, 0x0F, 0x0F, 0x0F, 0xA5, 0xB0, 0xD3, 0xA8, 0xD5, 0x2A, 0x03,
                0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xE3, 0x90,
                0xD1, 0xED, 0xB0, 0xFB, 0xC9, 0x00
        };
    }



}
