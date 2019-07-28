package br.com.dod.vcas.wav;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Rom49K extends Rom {

    private static final long MAX_ENC_INPUT_FILE_LENGTH = 50176;

    public Rom49K(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }

    static boolean matchSize(final long fileSize) {
        return (fileSize > Rom32K.MAX_ENC_INPUT_FILE_LENGTH && fileSize <= MAX_ENC_INPUT_FILE_LENGTH);
    }

    @Override
    protected void validate() throws FlowException {
        if ((char) inputMemPointer[3] >= 0x40 && (char) inputMemPointer[0x4000] != 'A'
                && (char) inputMemPointer[0x4001] != 'B') throw FlowException.error("mappers_not_supported");

        if (!matchSize(getFileSize())) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void encodeFileContent() throws FlowException {
        char headId = 0;

        // 1st block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, 0, (int) Rom.MAX_ENC_INPUT_FILE_LENGTH, getLoaderBlock());

        // 2nd block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer(2));

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, (int) Rom.MAX_ENC_INPUT_FILE_LENGTH, (int) Rom32K.MAX_ENC_INPUT_FILE_LENGTH, getLoaderBlock());

        // 3rd block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ROM.getHeader());
        encodeData(getNameBuffer(3));

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, (int) Rom32K.MAX_ENC_INPUT_FILE_LENGTH, inputMemPointer.length, getLoader());
    }

    private char[] getLoaderBlock() {
        return FileCommons.getLoader("ROMBLOCK.bin");
    }

    private char[] getLoader() {
        return FileCommons.getLoader((reset ? "ROM49KR.bin" : "ROM49K.bin"));
    }
}
