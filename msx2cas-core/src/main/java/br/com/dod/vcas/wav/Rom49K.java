package br.com.dod.vcas.wav;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Rom49K extends Rom {

    static final long MAX_ENC_INPUT_FILE_LENGTH = 50176;

    private char[] preloader;
    private char[] loader1;
    private char[] loader2;
    private char[] loader3;

    private char[] nameBuffer0;
    private char[] nameBuffer1;
    private char[] nameBuffer2;
    private char[] nameBuffer3;

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

        if (!matchSize(this.fileLength)) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void setup() {

        initLoader();

        nameBuffer0 = nameBuffer;
        String fileLoaderId = String.valueOf(nameBuffer).trim();
        int fileLoaderIdCutSize = (fileLoaderId.length() >= CAS_FILENAME_LENGTH ? CAS_FILENAME_LENGTH - 1 : fileLoaderId.length());
        nameBuffer1 = FileCommons.getNameBuffer(fileLoaderId.substring(0, fileLoaderIdCutSize) +"1");
        nameBuffer2 = FileCommons.getNameBuffer(fileLoaderId.substring(0, fileLoaderIdCutSize) +"2");
        nameBuffer3 = FileCommons.getNameBuffer(fileLoaderId.substring(0, fileLoaderIdCutSize) +"3");

        System.arraycopy(nameBuffer1, 0, preloader, 14, nameBuffer1.length);
        System.arraycopy(nameBuffer2, 0, loader1, 21, nameBuffer2.length);
        System.arraycopy(nameBuffer3, 0, loader2, 21, nameBuffer3.length);
    }

    private void encodePreLoaderBLock(char[] preloader) {
        encodeShortHeader();

        encodeData(buildBinaryAddressBuffer(sizeof(preloader)));

        encodeData(preloader);
    }

    @Override
    protected void encodeFileContent() throws FlowException {

        char headId = 0;

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer0);

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodePreLoaderBLock(preloader);

        // 1st block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer1);

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, 0, (int) Rom.MAX_ENC_INPUT_FILE_LENGTH, loader1);

        // 2nd block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer2);

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, (int) Rom.MAX_ENC_INPUT_FILE_LENGTH, (int) Rom32K.MAX_ENC_INPUT_FILE_LENGTH, loader2);

        // 3rd block

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer3);

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeRomBlock(headId, (int) Rom32K.MAX_ENC_INPUT_FILE_LENGTH, inputMemPointer.length, loader3);
    }

    private void initLoader() {
        this.preloader = new char[]{
                0xC3, 0x49, 0x90, 0x1E, 0x62, 0x6C, 0x6F, 0x61, 0x64, 0x22, 0x63, 0x61, 0x73, 0x3A, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x22, 0x2C, 0x72, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x0D,
                0x00, 0x3C, 0x20, 0x4D, 0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x20, 0x3E, 0x20, 0x4C, 0x6F, 0x61,
                0x64, 0x69, 0x6E, 0x67, 0x2C, 0x20, 0x70, 0x6C, 0x65, 0x61, 0x73, 0x65, 0x20, 0x77, 0x61, 0x69,
                0x74, 0x20, 0x2E, 0x2E, 0x2E, 0x20, 0x20, 0x20, 0x00, 0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21,
                0xE9, 0xF3, 0x77, 0x3E, 0x04, 0x23, 0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00,
                0xCD, 0xCC, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x21, 0x90, 0x1A, 0xB7, 0x28, 0x0A,
                0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18, 0xF2, 0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00,
                0xCD, 0x56, 0x01, 0xF3, 0x21, 0xF0, 0xFB, 0x22, 0xF8, 0xF3, 0x22, 0xFA, 0xF3, 0x21, 0x03, 0x90,
                0x11, 0xF0, 0xFB, 0x01, 0x1D, 0x00, 0xED, 0xB0, 0x21, 0x0D, 0xFC, 0x22, 0xF8, 0xF3, 0xFB, 0xC9,
                0x00
        };

        this.loader1 = new char[]{
                0xC3, 0x76, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1E, 0x62, 0x6C, 0x6F, 0x61, 0x64,
                0x22, 0x63, 0x61, 0x73, 0x3A, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x22, 0x2C, 0x72, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x0D, 0x00, 0x3C, 0x20, 0x4D, 0x53, 0x58, 0x32, 0x43, 0x61,
                0x73, 0x20, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E, 0x67, 0x2C, 0x20, 0x70, 0x6C, 0x65,
                0x61, 0x73, 0x65, 0x20, 0x77, 0x61, 0x69, 0x74, 0x20, 0x2E, 0x2E, 0x2E, 0x20, 0x20, 0x20, 0x00,
                0x3C, 0x4D, 0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E,
                0x67, 0x20, 0x66, 0x61, 0x69, 0x6C, 0x65, 0x64, 0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52,
                0x52, 0x4F, 0x52, 0x21, 0x20, 0x00, 0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37,
                0x3F, 0xED, 0x52, 0xE5, 0xC1, 0x21, 0x79, 0x91, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79,
                0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20, 0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0xDF,
                0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23, 0x77, 0x23,
                0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6,
                0x00, 0x11, 0x50, 0x90, 0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00,
                0x18, 0xF2, 0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xC9, 0xFB,
                0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x04, 0x23, 0x77, 0x23, 0x77, 0xCD,
                0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCC, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11,
                0x28, 0x90, 0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18, 0xF2,
                0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0x56, 0x01, 0xF3, 0x21, 0xF0, 0xFB, 0x22, 0xF8, 0xF3,
                0x22, 0xFA, 0xF3, 0x21, 0x0A, 0x90, 0x11, 0xF0, 0xFB, 0x01, 0x1D, 0x00, 0xED, 0xB0, 0x21, 0x0D,
                0xFC, 0x22, 0xF8, 0xF3, 0xF3, 0x3A, 0xFF, 0xFF, 0x2F, 0xF5, 0x4F, 0xE6, 0xF0, 0x47, 0x79, 0x0F,
                0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0x32, 0xFF, 0xFF, 0xDB, 0xA8, 0xF5, 0xE6, 0xF0, 0x47, 0x0F,
                0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0xD3, 0xA8, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB,
                0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0x79, 0x91, 0xED, 0x5B, 0x03, 0x90, 0xED, 0xB0, 0xF1,
                0xD3, 0xA8, 0xF1, 0x32, 0xFF, 0xFF, 0xFB, 0xC9, 0x00
        };

        this.loader2 = new char[]{
                0xC3, 0x76, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1E, 0x62, 0x6C, 0x6F, 0x61, 0x64,
                0x22, 0x63, 0x61, 0x73, 0x3A, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x22, 0x2C, 0x72, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x0D, 0x00, 0x3C, 0x20, 0x4D, 0x53, 0x58, 0x32, 0x43, 0x61,
                0x73, 0x20, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E, 0x67, 0x2C, 0x20, 0x70, 0x6C, 0x65,
                0x61, 0x73, 0x65, 0x20, 0x77, 0x61, 0x69, 0x74, 0x20, 0x2E, 0x2E, 0x2E, 0x20, 0x20, 0x20, 0x00,
                0x3C, 0x4D, 0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E,
                0x67, 0x20, 0x66, 0x61, 0x69, 0x6C, 0x65, 0x64, 0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52,
                0x52, 0x4F, 0x52, 0x21, 0x20, 0x00, 0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37,
                0x3F, 0xED, 0x52, 0xE5, 0xC1, 0x21, 0x79, 0x91, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79,
                0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20, 0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0xDF,
                0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23, 0x77, 0x23,
                0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6,
                0x00, 0x11, 0x50, 0x90, 0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00,
                0x18, 0xF2, 0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xC9, 0xFB,
                0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x04, 0x23, 0x77, 0x23, 0x77, 0xCD,
                0x62, 0x00, 0xAF, 0xCD, 0xC3, 0x00, 0xCD, 0xCC, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11,
                0x28, 0x90, 0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18, 0xF2,
                0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0x56, 0x01, 0xF3, 0x21, 0xF0, 0xFB, 0x22, 0xF8, 0xF3,
                0x22, 0xFA, 0xF3, 0x21, 0x0A, 0x90, 0x11, 0xF0, 0xFB, 0x01, 0x1D, 0x00, 0xED, 0xB0, 0x21, 0x0D,
                0xFC, 0x22, 0xF8, 0xF3, 0xF3, 0x3A, 0xFF, 0xFF, 0x2F, 0xF5, 0x4F, 0xE6, 0xF0, 0x47, 0x79, 0x0F,
                0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0x32, 0xFF, 0xFF, 0xDB, 0xA8, 0xF5, 0xE6, 0xF0, 0x47, 0x0F,
                0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0xD3, 0xA8, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB,
                0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0x79, 0x91, 0xED, 0x5B, 0x03, 0x90, 0xED, 0xB0, 0xF1,
                0xD3, 0xA8, 0xF1, 0x32, 0xFF, 0xFF, 0xFB, 0xC9, 0x00
        };

        if (reset) {
            this.loader3 = new char[]{
                    0xC3, 0x30, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x4D, 0x53, 0x58, 0x32, 0x43,
                    0x61, 0x73, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E, 0x67, 0x20, 0x66, 0x61, 0x69, 0x6C,
                    0x65, 0x64, 0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52, 0x52, 0x4F, 0x52, 0x21, 0x20, 0x00,
                    0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0xE5, 0xC1, 0x21,
                    0x78, 0x91, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79, 0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20,
                    0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0x99, 0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E,
                    0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23, 0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD,
                    0xC3, 0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90, 0x1A, 0xB7,
                    0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18, 0xF2, 0x21, 0x03, 0x01, 0xCD,
                    0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xC9, 0xF3, 0x3A, 0xFF, 0xFF, 0x2F, 0x4F, 0xE6,
                    0xF0, 0x47, 0x79, 0x0F, 0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0x32, 0xFF, 0xFF, 0xDB, 0xA8, 0xE6,
                    0xF0, 0x47, 0x0F, 0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0xF5, 0xD3, 0xA8, 0x21, 0x78, 0x91, 0x11,
                    0x5C, 0x91, 0x7E, 0x12, 0x23, 0x11, 0x60, 0x91, 0x7E, 0x12, 0x23, 0x11, 0x64, 0x91, 0x7E, 0x12,
                    0x2A, 0x07, 0x90, 0xE5, 0x22, 0x67, 0x91, 0x7E, 0x11, 0x6B, 0x91, 0x12, 0x23, 0x7E, 0x11, 0x6F,
                    0x91, 0x12, 0x23, 0x7E, 0x11, 0x73, 0x91, 0x12, 0xE1, 0x3E, 0xCD, 0x77, 0x23, 0xE5, 0x21, 0x7C,
                    0x91, 0x11, 0x00, 0x80, 0x37, 0x3F, 0xED, 0x52, 0xED, 0x5B, 0x05, 0x90, 0x37, 0x3F, 0xED, 0x5A,
                    0xEB, 0xD5, 0x21, 0x57, 0x91, 0x01, 0x21, 0x00, 0xED, 0xB0, 0x2A, 0x05, 0x90, 0x11, 0x25, 0x00,
                    0x37, 0x3F, 0xED, 0x5A, 0x22, 0x05, 0x90, 0xD1, 0xE1, 0x7B, 0x77, 0x23, 0x7A, 0x77, 0x21, 0x35,
                    0x91, 0x11, 0x58, 0x91, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0x35, 0x91, 0x11, 0x60,
                    0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37, 0x3F, 0xED,
                    0x52, 0x44, 0x4D, 0x21, 0x78, 0x91, 0xED, 0x5B, 0x03, 0x90, 0xED, 0xB0, 0xF1, 0xE6, 0xFC, 0xD3,
                    0xA8, 0xF7, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF3, 0x21, 0x00, 0x80, 0x3E, 0x00, 0x77, 0x23, 0x3E,
                    0x00, 0x77, 0x23, 0x3E, 0x00, 0x77, 0x21, 0x00, 0x00, 0xE5, 0x3E, 0x00, 0x77, 0x23, 0x3E, 0x00,
                    0x77, 0x23, 0x3E, 0x00, 0x77, 0xE1, 0xE9, 0x00
            };
        } else {
            this.loader3 = new char[]{
                    0xC3, 0x30, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x4D, 0x53, 0x58, 0x32, 0x43,
                    0x61, 0x73, 0x3E, 0x20, 0x4C, 0x6F, 0x61, 0x64, 0x69, 0x6E, 0x67, 0x20, 0x66, 0x61, 0x69, 0x6C,
                    0x65, 0x64, 0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52, 0x52, 0x4F, 0x52, 0x21, 0x20, 0x00,
                    0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0xE5, 0xC1, 0x21,
                    0xF7, 0x90, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79, 0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20,
                    0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E, 0xB8, 0xCA, 0x99, 0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E,
                    0x0F, 0x21, 0xE9, 0xF3, 0x77, 0x3E, 0x08, 0x23, 0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD,
                    0xC3, 0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90, 0x1A, 0xB7,
                    0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18, 0xF2, 0x21, 0x03, 0x01, 0xCD,
                    0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01, 0xC9, 0xF3, 0x3A, 0xFF, 0xFF, 0x2F, 0x4F, 0xE6,
                    0xF0, 0x47, 0x79, 0x0F, 0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0x32, 0xFF, 0xFF, 0xDB, 0xA8, 0xE6,
                    0xF0, 0x47, 0x0F, 0x0F, 0x0F, 0x0F, 0xE6, 0x0F, 0xB0, 0xF5, 0xD3, 0xA8, 0x21, 0xD3, 0x90, 0x11,
                    0xF8, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xD3, 0x90, 0x11, 0x60, 0xF5, 0xD5,
                    0xED, 0xB0, 0xC9, 0x2A, 0x07, 0x90, 0xE5, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37,
                    0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xF7, 0x90, 0xED, 0x5B, 0x03, 0x90, 0xED, 0xB0, 0xE1, 0xF1,
                    0xE6, 0xFC, 0xD3, 0xA8, 0xFB, 0xE9, 0x00
            };
        }
    }
}
