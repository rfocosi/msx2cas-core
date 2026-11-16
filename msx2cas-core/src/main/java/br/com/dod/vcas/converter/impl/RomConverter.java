package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for MSX ROM files (16K, 32K, 49K, and MegaRAM up to 2MB)
 */
public class RomConverter extends AbstractFileConverter {

    private static final int BANK_8K_SIZE = 8192;
    private static final int ROM_16K_MAX_SIZE = 16384;
    private static final int ROM_32K_MAX_SIZE = 32768;
    private static final int ROM_49K_MAX_SIZE = 50176;
    private static final int MEGARAM_MAX_SIZE = 2 * 1024 * 1024;
    private static final int MSX_WORK_AREA_START = 0xF380;
    private static final int INIT32_TRAMPOLINE_SIZE = 6;

    private boolean resetRom;
    private boolean megaRam;
    private MapperType mapperType;

    /**
     * Constructor for RomConverter
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        this(inputFileName, sampleRate, false, false, MapperType.AUTO);
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
        this(inputFileName, sampleRate, resetRom, false, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with ROM reset and MegaRAM options
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @param resetRom True to reset the MSX after loading the ROM
     * @param megaRam True to target a MegaRAM cartridge (port 0x8E)
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, SampleRate sampleRate, boolean resetRom, boolean megaRam)
            throws FlowException {
        this(inputFileName, sampleRate, resetRom, megaRam, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with ROM reset, MegaRAM, and mapper type options
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @param resetRom True to reset the MSX after loading the ROM
     * @param megaRam True to target a MegaRAM cartridge (port 0x8E)
     * @param mapperType Source MegaROM mapper type ({@link MapperType#AUTO} by default)
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(
            String inputFileName,
            SampleRate sampleRate,
            boolean resetRom,
            boolean megaRam,
            MapperType mapperType) throws FlowException {
        super(inputFileName, sampleRate);
        this.resetRom = resetRom;
        this.megaRam = megaRam;
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
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

    /**
     * Set whether to convert the ROM for a MegaRAM cartridge
     *
     * @param megaRam True for MegaRAM mode, false otherwise
     * @return This RomConverter instance
     */
    public RomConverter megaRam(boolean megaRam) {
        this.megaRam = megaRam;
        return this;
    }

    /**
     * Set the source MegaROM mapper type for MegaRAM conversion
     *
     * @param mapperType Source mapper type ({@link MapperType#AUTO} by default)
     * @return This RomConverter instance
     */
    public RomConverter mapperType(MapperType mapperType) {
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
        return this;
    }

    private void validate() throws FlowException {
        int fileSize = getFileSize();
        int maxSize = megaRam ? MEGARAM_MAX_SIZE : ROM_49K_MAX_SIZE;
        if (fileSize <= MIN_ENC_INPUT_FILE_LENGTH || fileSize > maxSize) {
            throw FlowException.error("file_size_invalid");
        }
        if (megaRam) {
            if ((char) inputMemPointer[0] != 'A' || (char) inputMemPointer[1] != 'B') {
                throw FlowException.error("mappers_not_supported");
            }
            return;
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

        if (megaRam) {
            convertMegaRam(encoder);
        } else if (fileSize <= ROM_16K_MAX_SIZE) {
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
            int startAddr = (headId & 0xF0) << 8;
            int stubAddr = startAddr + inputMemPointer.length;
            boolean appendInit32Stub = stubAddr + INIT32_TRAMPOLINE_SIZE <= MSX_WORK_AREA_START;
            int endAddr = appendInit32Stub
                    ? stubAddr + INIT32_TRAMPOLINE_SIZE - 1
                    : stubAddr - 1;
            char[] addressBuffer = {
                    0,
                    (char) (startAddr >> 8),
                    (char) endAddr,
                    (char) (endAddr >> 8),
                    appendInit32Stub ? (char) stubAddr : (char) inputMemPointer[2],
                    appendInit32Stub ? (char) (stubAddr >> 8) : (char) inputMemPointer[3]
            };
            encoder.encodeData(addressBuffer);
            for (byte b : inputMemPointer) {
                encoder.writeDataByte((char) b);
            }
            if (appendInit32Stub) {
                // 40-column MSX BASIC leaves VDP R2/R5/R6 at 0x00; match BIOS cartridge boot via INIT32 (0x006F).
                char[] init32Trampoline = {
                        0xCD, 0x6F, 0x00,
                        0xC3, (char) inputMemPointer[2], (char) inputMemPointer[3]
                };
                encoder.encodeData(init32Trampoline);
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

    private void convertMegaRam(WavEncoder encoder) throws FlowException {
        inputMemPointer = MegaRomPatcher.patchToKonami4(inputMemPointer, mapperType);
        int fileSize = getFileSize();
        int total8kBanks = (fileSize + BANK_8K_SIZE - 1) / BANK_8K_SIZE;
        int maxBankIndex = total8kBanks - 1;
        int totalBlocks = (fileSize + ROM_16K_MAX_SIZE - 1) / ROM_16K_MAX_SIZE;

        for (int blockIdx = 0; blockIdx < totalBlocks; blockIdx++) {
            int blockStart = blockIdx * ROM_16K_MAX_SIZE;
            int blockEnd = Math.min(fileSize, blockStart + ROM_16K_MAX_SIZE);
            int blockSize = blockEnd - blockStart;
            boolean isLastBlock = blockIdx == totalBlocks - 1;

            char[] nameBuffer = blockIdx == 0
                    ? getNameBuffer()
                    : getNumberedNameBuffer(blockIdx + 1);
            char[] loader = isLastBlock ? RomLoaders.megaBoot() : RomLoaders.megaBlock();

            loader[3] = 0x00;
            loader[4] = 0x40;
            int endAddr = 0x4000 + blockSize;
            loader[5] = (char) endAddr;
            loader[6] = (char) (endAddr >> 8);
            loader[7] = (char) inputMemPointer[2];
            loader[8] = (char) inputMemPointer[3];
            loader[9] = calculateCRC(blockStart, blockEnd);
            loader[10] = (char) (blockIdx * 2);
            loader[11] = (char) maxBankIndex;
            if (isLastBlock) {
                loader[12] = (char) (resetRom ? 1 : 0);
            }

            encodeBlockHeader(encoder, nameBuffer);
            encoder.encodeShortHeader();
            encoder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + blockSize));
            encoder.encodeData(loader);

            for (int i = blockStart; i < blockEnd; i++) {
                encoder.writeDataByte((char) inputMemPointer[i]);
            }
        }
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
