package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for MSX ROM files (16K, 32K, and 49K)
 */
public class RomConverter extends AbstractFileConverter {

    private static final int ROM_16K_MAX_SIZE = 16384;
    private static final int ROM_32K_MAX_SIZE = 32768;
    private static final int ROM_49K_MAX_SIZE = 50176;

    private boolean resetRom;

    /**
     * Constructor for RomConverter
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        this(inputFileName, sampleRate, false);
    }

    /**
     * Constructor for RomConverter with ROM reset option
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @param resetRom True to reset the MSX after loading the ROM
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, SampleRate sampleRate, boolean resetRom) throws FlowException {
        super(inputFileName, sampleRate);
        this.resetRom = resetRom;
        validate();
    }

    /**
     * Set whether to reset the ROM during conversion
     *
     * @param resetRom True to reset the ROM, false otherwise
     * @return This RomConverter instance
     */
    public RomConverter resetRom(boolean resetRom) {
        this.resetRom = resetRom;
        return this;
    }

    private void validate() throws FlowException {
        int fileSize = getFileSize();
        if (fileSize <= MIN_ENC_INPUT_FILE_LENGTH || fileSize > ROM_49K_MAX_SIZE) {
            throw FlowException.error("file_size_invalid");
        }
        if (fileSize > ROM_32K_MAX_SIZE
                && (char) inputMemPointer[3] >= 0x40
                && (char) inputMemPointer[0x4000] != 'A'
                && (char) inputMemPointer[0x4001] != 'B') {
            throw FlowException.error("mappers_not_supported");
        }
    }

    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        int fileSize = getFileSize();

        if (fileSize <= ROM_16K_MAX_SIZE) {
            convertRom16K(encoder);
        } else if (fileSize <= ROM_32K_MAX_SIZE) {
            convertRom32K(encoder);
        } else {
            convertRom49K(encoder);
        }

        encoder.finish();
        return encoder;
    }

    private char getRomTypeHeader() throws FlowException {
        char ch = (char) inputMemPointer[3];
        if ((ch & 0xF0) >= 0xD0) {
            throw FlowException.error("type_32k_not_supported");
        }
        return ch;
    }

    private void convertRom16K(WavEncoder encoder) throws FlowException {
        char headId = getRomTypeHeader();
        encodeBlockHeader(encoder, getNameBuffer());

        if (headId < 0x80) {
            encodeRomBlock(encoder, headId, 0, inputMemPointer.length, RomLoaders.rom16K(resetRom));
        } else {
            encoder.encodeShortHeader();
            char startAddr = (char) ((headId & 0xF0) << 8);
            char endAddr = (char) (startAddr + inputMemPointer.length - 1);
            char[] addressBuffer = {
                    0,
                    (char) (startAddr >> 8),
                    endAddr,
                    (char) (endAddr >> 8),
                    (char) inputMemPointer[2],
                    (char) inputMemPointer[3]
            };
            encoder.encodeData(addressBuffer);
            for (byte b : inputMemPointer) {
                encoder.writeDataByte((char) b);
            }
        }
    }

    private void convertRom32K(WavEncoder encoder) throws FlowException {
        char headId = getRomTypeHeader();
        if (headId > 0x80) {
            headId = 0x40;
        } else if (headId < 0x40) {
            headId = 0;
        }

        encodeBlockHeader(encoder, getNameBuffer());
        encodeRomBlock(encoder, headId, 0, ROM_16K_MAX_SIZE, RomLoaders.blockLoader());

        encodeBlockHeader(encoder, getNumberedNameBuffer(2));
        encodeRomBlock(encoder, headId, ROM_16K_MAX_SIZE, inputMemPointer.length, RomLoaders.rom32K(resetRom));
    }

    private void convertRom49K(WavEncoder encoder) throws FlowException {
        char headId = 0;

        encodeBlockHeader(encoder, getNameBuffer());
        encodeRomBlock(encoder, headId, 0, ROM_16K_MAX_SIZE, RomLoaders.blockLoader());

        encodeBlockHeader(encoder, getNumberedNameBuffer(2));
        encodeRomBlock(encoder, headId, ROM_16K_MAX_SIZE, ROM_32K_MAX_SIZE, RomLoaders.blockLoader());

        encodeBlockHeader(encoder, getNumberedNameBuffer(3));
        encodeRomBlock(encoder, headId, ROM_32K_MAX_SIZE, inputMemPointer.length, RomLoaders.rom49K(resetRom));
    }

    private void encodeBlockHeader(WavEncoder encoder, char[] nameBuffer) {
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        encoder.encodeLongHeader();
        encoder.encodeData(FileType.ROM.getHeader());
        encoder.encodeData(nameBuffer);
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
    }

    private void encodeRomBlock(WavEncoder encoder, char headId, int blockStart, int blockEnd, char[] loader)
            throws FlowException {
        char romCRC = calculateCRC(blockStart, blockEnd);

        encoder.encodeShortHeader();
        encoder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + blockEnd - blockStart));

        char addr = (char) (((headId & 0xF0) << 8) + blockStart);
        loader[3] = 0;
        loader[4] = (char) (addr >> 8);

        addr = (char) (addr + blockEnd - blockStart);
        loader[5] = addr;
        loader[6] = (char) (addr >> 8);

        if ((char) inputMemPointer[0] == 'A' && (char) inputMemPointer[1] == 'B') {
            loader[7] = (char) inputMemPointer[2];
            loader[8] = (char) inputMemPointer[3];
        } else if (inputMemPointer.length > 0x4003
                && (char) inputMemPointer[0x4000] == 'A'
                && (char) inputMemPointer[0x4001] == 'B') {
            loader[7] = (char) inputMemPointer[0x4002];
            loader[8] = (char) inputMemPointer[0x4003];
        }
        loader[9] = romCRC;

        encoder.encodeData(loader);

        for (int i = blockStart; i < blockEnd; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
    }
}
