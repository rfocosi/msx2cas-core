package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

public class Bin extends Wav {

    private char[] loader;

    private static final int FILE_OFFSET = 7;

    public Bin(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, FileType.BIN.getHeader());
    }

    @Override
    protected void validate() throws FlowException {
        if (getFileSize() < MIN_ENC_INPUT_FILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void setup() {
        initLoader();
    }

    @Override
    protected void encodeFileContent() throws FlowException {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer);

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeShortHeader();

        encodeData(buildBinaryAddressBuffer(sizeof(loader) + inputMemPointer.length - FILE_OFFSET));

        encodeLoader();

        for (int i = FILE_OFFSET; i < inputMemPointer.length; i++) {
            writeDataByte((char) inputMemPointer[i]);
        }
    }

    private void encodeLoader() throws FlowException {
        calculateBinCRC();

        addStartAddressToLoader();

        encodeData(loader);
    }

    private void addStartAddressToLoader() {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }

    private void calculateBinCRC() throws FlowException {
        char binBegin = (char) ((new DWORD(inputMemPointer[2]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[1]).getLow()).intValue());
        char binEnd = (char) ((new DWORD(inputMemPointer[4]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[3]).getLow()).intValue());

        loader[9] = calculateCRC(FILE_OFFSET, (binEnd-binBegin) + FILE_OFFSET);
    }

    private void initLoader() {

        this.loader = new char[]{
                0xC3, 0x30, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x20, 0x4D,
                0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x20, 0x3E, 0x4C, 0x6F, 0x61, 0x64, 0x69,
                0x6E, 0x67, 0x20, 0x66, 0x61, 0x69, 0x6C, 0x65, 0x64, 0x3A, 0x20, 0x43, 0x52,
                0x43, 0x20, 0x45, 0x52, 0x52, 0x4F, 0x52, 0x21, 0x00, 0xF3, 0x2A, 0x03, 0x90,
                0xED, 0x5B, 0x05, 0x90, 0xEB, 0xED, 0x52, 0xE5, 0xC1, 0x21, 0xCE, 0x90, 0xAF,
                0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79, 0xB7, 0xC2, 0x42, 0x90, 0x78, 0xB7,
                0xC2, 0x42, 0x90, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0x9C, 0x90,
                0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23,
                0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCF, 0x00,
                0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90, 0x1A, 0xB7, 0xCA, 0x8E,
                0x90, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0xC3, 0x7E, 0x90, 0x21,
                0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xFB, 0xC9,
                0xF3, 0x21, 0xB2, 0x90, 0x11, 0xCF, 0x90, 0xEB, 0xED, 0x52, 0x44, 0x4D, 0x21,
                0xB2, 0x90, 0x11, 0x60, 0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x2A, 0x07, 0x90, 0xE5,
                0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0xED, 0x52, 0x44, 0x4D, 0x21,
                0xCE, 0x90, 0xED, 0x5B, 0x03, 0x90, 0xED, 0xB0, 0xFB, 0xC9, 0x00
        };
    }
}
