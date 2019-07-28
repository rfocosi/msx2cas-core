package br.com.dod.vcas.wav;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Rom extends Wav {

    static final long MAX_ENC_INPUT_FILE_LENGTH = 16384;

    private char[] loader;

    public Rom(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        validate();
    }

    public static Rom build(String inputFileName, SampleRate sampleRate) throws FlowException {

        long fileSize = FileCommons.readFile(inputFileName).length;

        if (Rom.matchSize(fileSize)) {
            return new Rom(inputFileName, sampleRate);
        } else if (Rom32K.matchSize(fileSize)) {
            return new Rom32K(inputFileName, sampleRate);
        } else if (Rom49K.matchSize(fileSize)) {
            return new Rom49K(inputFileName, sampleRate);
        }

        throw FlowException.error("file_size_invalid");
    }

    public Wav convert(boolean reset) throws FlowException {
        setup(reset);
        return super.convert();
    }

    static boolean matchSize(final long fileSize) {
        return (fileSize > MIN_ENC_INPUT_FILE_LENGTH && fileSize <= MAX_ENC_INPUT_FILE_LENGTH);
    }

    protected void validate() throws FlowException {
        if (!matchSize(getFileSize())) throw FlowException.error("file_size_invalid");
    }

    protected void setup(boolean reset) {
        initLoader(reset);
        System.arraycopy(getNameBuffer(), 0, loader, 21, getNameBuffer().length);
    }

    private char getRomTypeHeader() throws FlowException {
        char ch = (char) inputMemPointer[3];
        if ((ch & 0xf0) >= 0xD0) throw FlowException.error("type_32k_not_supported");
        return ch;
    }

    @Override
    protected void encodeFileContent() throws FlowException {

        int blockSize = inputMemPointer.length;

        char headId = getRomTypeHeader();

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

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

            for (byte b : inputMemPointer) {
                writeDataByte((char) b);
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
        if ((char) inputMemPointer[0] == 'A' && (char) inputMemPointer[1] == 'B')
        {
            loader[7] = (char) inputMemPointer[2];
            loader[8] = (char) inputMemPointer[3];
        }
		else if ((char) inputMemPointer[0x4000] == 'A' && (char) inputMemPointer[0x4001] == 'B')
        {
            loader[7] = (char) inputMemPointer[0x4002];
            loader[8] = (char) inputMemPointer[0x4003];
        }
        loader[9] = romCRC;

        encodeData(loader);

        for (int i = blockStart; i < blockEnd; i++)	{
            writeDataByte((char) inputMemPointer[i]);	// Encode data byte
        }
    }

    private void initLoader(boolean reset) {
        if (reset) {
            this.loader = FileCommons.getLoader("ROM16KR.bin");
        } else {
            this.loader = FileCommons.getLoader("ROM16K.bin");
        }
    }
}
