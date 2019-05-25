package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;

public class Bin extends Wav {

    private static final char[] binFileHeader = {0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0, 0xd0};

    private char[] loader;

    public Bin(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, new DWORD(7), binFileHeader);
    }

    @Override
    protected void validate() throws FlowException {
        if (this.fileLength < MIN_ENC_INPUTFILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void setup() {
        initLoader();

        this.extraBytes = new DWORD(loader.length + 6);

        this.moreExtraBytes = new DWORD(((wavSampleRate * FIRST_PAUSE_LENGTH) + (wavSampleRate * DEFAULT_PAUSE_LENGTH) +
                Math.round(wavSampleRate * LONG_HEADER_LENGTH + wavSampleRate * SHORT_HEADER_LENGTH) +
                (fileHeader.length + CAS_FILENAME_LENGTH) * Math.round(sampleScale * SIZE_OF_BITSTREAM * bitEncodingLength)));

    }

    @Override
    protected void encodeFileContent() throws FlowException {

        encodeShortHeader();

        encodeBinaryStartAddress();

        encodeLoader();

        for (int i = fileOffset.intValue(); i < inputMemPointer.length; i++) {
            writeDataByte((char) inputMemPointer[i]);
        }
    }

    private void encodeLoader() throws FlowException {
        calculateCRC();

        addStartAddressToLoader();

        encodeData(loader);
    }

    private void encodeBinaryStartAddress() {
        char[] adressBuffer = new char[6];

        adressBuffer[0] = 0;
        adressBuffer[1] = 0x90;
        char a = (char) (sizeof(loader) + inputMemPointer.length + 0x9000 - fileOffset.longValue() - 1);
        adressBuffer[2] = a;
        adressBuffer[3] = (char)(a >> 8);
        adressBuffer[4] = 0;
        adressBuffer[5] = 0x90;

        encodeData(adressBuffer);
    }

    private void addStartAddressToLoader() {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }

    private void calculateCRC() throws FlowException {
        char binCRC = 0;
        char binBegin = (char) ((new DWORD(inputMemPointer[2]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[1]).getLow()).intValue());
        char binEnd = (char) ((new DWORD(inputMemPointer[4]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[3]).getLow()).intValue());
        if (binEnd <= binBegin) throw FlowException.error("header_conflicting_information");

        for (int i = fileOffset.intValue(); i < (binEnd-binBegin) + fileOffset.intValue(); i++) binCRC += (char) inputMemPointer[i];

        loader[9] = binCRC;
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
