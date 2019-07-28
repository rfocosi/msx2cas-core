package br.com.dod.vcas.wav;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Rom32K extends Rom {

    static final long MAX_ENC_INPUT_FILE_LENGTH = 32768L;

    public Rom32K(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }

    static boolean matchSize(final long fileSize) {
        return (fileSize > Rom.MAX_ENC_INPUT_FILE_LENGTH && fileSize <= MAX_ENC_INPUT_FILE_LENGTH);
    }

    @Override
    protected void validate() throws FlowException {
        if (!matchSize(getFileSize())) throw FlowException.error("file_size_invalid");
    }

    private char getRomTypeHeader() throws FlowException {
        char ch = (char) inputMemPointer[3];
        if ((ch & 0xf0) >= 0xD0) throw FlowException.error("type_32k_not_supported");
        return ch;
    }

    @Override
    protected void encodeFileContent() throws FlowException {

        char headId = getRomTypeHeader();

        if (headId > 0x80) headId = 0x40;
        else if (headId < 0x40) headId = 0;

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, 0, 16384, getLoaderBlock());

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        // Encode binary header and second part of 32k ROM name
        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer(2));

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, 16384, inputMemPointer.length, getLoader());
    }

    private char[] getLoaderBlock() {
        return FileCommons.getLoader("ROMBLOCK.bin");
    }

    private char[] getLoader() {
        return FileCommons.getLoader((reset ? "ROM32KR.bin" : "ROM32K.bin"));
    }
}
