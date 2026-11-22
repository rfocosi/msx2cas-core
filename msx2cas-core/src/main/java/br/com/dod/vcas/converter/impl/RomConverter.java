package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBuilder;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Converter for MSX ROM files (16K, 32K, 49K, and MegaRAM up to 2MB).
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
    private boolean turbo;

    /**
     * Constructor for RomConverter with default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, false, false, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with ROM reset option.
     *
     * @param inputFileName Name of the input file
     * @param resetRom True to reset the MSX after loading the ROM
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, boolean resetRom) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, resetRom, false, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with ROM reset and MegaRAM options.
     *
     * @param inputFileName Name of the input file
     * @param resetRom True to reset the MSX after loading the ROM
     * @param megaRam True to target a MegaRAM cartridge (port 0x8E)
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, boolean resetRom, boolean megaRam) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, resetRom, megaRam, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with ROM reset, MegaRAM, and mapper type options.
     *
     * @param inputFileName Name of the input file
     * @param resetRom True to reset the MSX after loading the ROM
     * @param megaRam True to target a MegaRAM cartridge (port 0x8E)
     * @param mapperType Source MegaROM mapper type ({@link MapperType#AUTO} by default)
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(
            String inputFileName,
            boolean resetRom,
            boolean megaRam,
            MapperType mapperType) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, resetRom, megaRam, mapperType);
    }

    /**
     * Constructor for RomConverter with {@link TapeSpeed}.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        this(inputFileName, speed, false, false, MapperType.AUTO);
    }

    /**
     * Constructor for RomConverter with {@link TapeSpeed}, ROM reset, MegaRAM, and mapper type options.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @param resetRom True to reset the MSX after loading the ROM
     * @param megaRam True to target a MegaRAM cartridge (port 0x8E)
     * @param mapperType Source MegaROM mapper type ({@link MapperType#AUTO} by default)
     * @throws FlowException If reading or validating the ROM fails
     */
    public RomConverter(
            String inputFileName,
            TapeSpeed speed,
            boolean resetRom,
            boolean megaRam,
            MapperType mapperType) throws FlowException {
        super(inputFileName, speed != null ? speed : TapeSpeed.BPS_2400);
        this.resetRom = resetRom;
        this.megaRam = megaRam;
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
        this.turbo = effectiveSpeed().isTurbo();
        validate();
    }

    public RomConverter resetRom(boolean resetRom) {
        this.resetRom = resetRom;
        return this;
    }

    public RomConverter megaRam(boolean megaRam) {
        this.megaRam = megaRam;
        return this;
    }

    public RomConverter mapperType(MapperType mapperType) {
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
        return this;
    }

    public RomConverter turbo(boolean turbo) {
        this.turbo = turbo;
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
    public TapeImage convertToTape() throws FlowException {
        TapeSpeed buildSpeed = turbo
                ? TapeSpeed.TURBO
                : (effectiveSpeed().isTurbo() ? TapeSpeed.BPS_2400 : effectiveSpeed());
        TapeBuilder builder = new TapeBuilder(inputFileName, buildSpeed);
        int fileSize = getFileSize();

        if (megaRam) {
            convertMegaRam(builder);
        } else if (fileSize <= ROM_16K_MAX_SIZE) {
            convertRom16K(builder);
        } else if (fileSize <= ROM_32K_MAX_SIZE) {
            convertRom32K(builder);
        } else {
            convertRom49K(builder);
        }

        return builder.build();
    }

    private char getRomTypeHeader() throws FlowException {
        char ch = (char) inputMemPointer[3];
        if ((ch & 0xF0) >= 0xD0) {
            throw FlowException.error("type_32k_not_supported");
        }
        return ch;
    }

    private void convertRom16K(TapeBuilder builder) throws FlowException {
        char headId = getRomTypeHeader();
        encodeBlockHeader(builder, getNameBuffer());

        if (headId < 0x80) {
            if (turbo) {
                startTurboSupervisor(builder);
            }
            encodeRomBlock(builder, headId, 0, inputMemPointer.length, RomLoaders.rom16K(resetRom), true);
        } else if (turbo) {
            int startAddr = (headId & 0xF0) << 8;
            int initAddr = Byte.toUnsignedInt(inputMemPointer[2])
                    | (Byte.toUnsignedInt(inputMemPointer[3]) << 8);
            int stubAddr = startAddr + inputMemPointer.length;
            boolean appendInit32Stub = stubAddr + INIT32_TRAMPOLINE_SIZE <= MSX_WORK_AREA_START;
            char[] supervisor = RomLoaders.turboSupervisorPage2(initAddr, appendInit32Stub);
            builder.beginDataBlock();
            builder.encodeData(buildBinaryAddressBuffer(sizeof(supervisor)));
            builder.encodeData(supervisor);
            builder.encodeTurboPayload(RomLoaders.buildPage2TurboFrame(startAddr, inputMemPointer));
        } else {
            builder.beginDataBlock();
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
            builder.encodeData(addressBuffer);
            for (byte b : inputMemPointer) {
                builder.writeDataByte((char) b);
            }
            if (appendInit32Stub) {
                // 40-column MSX BASIC leaves VDP R2/R5/R6 at 0x00; match BIOS cartridge boot via INIT32 (0x006F).
                char[] init32Trampoline = {
                        0xCD, 0x6F, 0x00,
                        0xC3, (char) inputMemPointer[2], (char) inputMemPointer[3]
                };
                builder.encodeData(init32Trampoline);
            }
        }
    }

    private void convertRom32K(TapeBuilder builder) throws FlowException {
        char headId = getRomTypeHeader();
        if (headId > 0x80) {
            headId = 0x40;
        } else if (headId < 0x40) {
            headId = 0;
        }

        encodeBlockHeader(builder, getNameBuffer());
        if (turbo) {
            startTurboSupervisor(builder, true, false);
        }
        encodeRomBlock(builder, headId, 0, ROM_16K_MAX_SIZE, RomLoaders.blockLoader(turbo), false);

        if (!turbo) {
            encodeBlockHeader(builder, getNumberedNameBuffer(2));
        }
        encodeRomBlock(builder, headId, ROM_16K_MAX_SIZE, inputMemPointer.length, RomLoaders.rom32K(resetRom), true);
    }

    private void convertRom49K(TapeBuilder builder) throws FlowException {
        char headId = 0;

        encodeBlockHeader(builder, getNameBuffer());
        if (turbo) {
            startTurboSupervisor(builder, true, false);
        }
        encodeRomBlock(builder, headId, 0, ROM_16K_MAX_SIZE, RomLoaders.blockLoader(turbo), false);

        if (!turbo) {
            encodeBlockHeader(builder, getNumberedNameBuffer(2));
        }
        encodeRomBlock(builder, headId, ROM_16K_MAX_SIZE, ROM_32K_MAX_SIZE, RomLoaders.blockLoader(turbo), false);

        if (!turbo) {
            encodeBlockHeader(builder, getNumberedNameBuffer(3));
        }
        encodeRomBlock(builder, headId, ROM_32K_MAX_SIZE, inputMemPointer.length, RomLoaders.rom49K(resetRom), true);
    }

    private void convertMegaRam(TapeBuilder builder) throws FlowException {
        byte[] patchedRom = MegaRomPatcher.patchToKonami4(inputMemPointer, mapperType);
        int fileSize = patchedRom.length;
        int total8kBanks = (fileSize + BANK_8K_SIZE - 1) / BANK_8K_SIZE;
        int maxBankIndex = total8kBanks - 1;
        int totalBlocks = (fileSize + ROM_16K_MAX_SIZE - 1) / ROM_16K_MAX_SIZE;

        if (turbo) {
            encodeBlockHeader(builder, getNameBuffer());
            startTurboSupervisor(builder, totalBlocks > 1, true);
        }

        for (int blockIdx = 0; blockIdx < totalBlocks; blockIdx++) {
            int blockStart = blockIdx * ROM_16K_MAX_SIZE;
            int blockEnd = Math.min(fileSize, blockStart + ROM_16K_MAX_SIZE);
            int blockSize = blockEnd - blockStart;
            boolean isLastBlock = blockIdx == totalBlocks - 1;

            char[] loader = isLastBlock ? RomLoaders.megaBoot() : RomLoaders.megaBlock(turbo);

            loader[3] = 0x00;
            loader[4] = 0x40;
            int endAddr = 0x4000 + blockSize;
            loader[5] = (char) endAddr;
            loader[6] = (char) (endAddr >> 8);
            loader[7] = (char) patchedRom[2];
            loader[8] = (char) patchedRom[3];
            loader[9] = calculateCRC(patchedRom, blockStart, blockEnd);
            loader[10] = (char) (blockIdx * 2);
            loader[11] = (char) maxBankIndex;
            if (isLastBlock) {
                loader[12] = (char) (resetRom ? 1 : 0);
            }

            if (!turbo) {
                char[] nameBuffer = blockIdx == 0
                        ? getNameBuffer()
                        : getNumberedNameBuffer(blockIdx + 1);
                encodeBlockHeader(builder, nameBuffer);
            }
            writeBlockPayload(builder, loader, patchedRom, blockStart, blockEnd, isLastBlock);
        }
    }

    private void startTurboSupervisor(TapeBuilder builder) {
        startTurboSupervisor(builder, false, false);
    }

    private void startTurboSupervisor(TapeBuilder builder, boolean showBanner, boolean megaRamBanner) {
        char[] supervisor = showBanner
                ? RomLoaders.turboSupervisorWithBanner(0x9000, megaRamBanner)
                : RomLoaders.turboSupervisor(0x9000);
        builder.beginDataBlock();
        builder.encodeData(buildBinaryAddressBuffer(sizeof(supervisor)));
        builder.encodeData(supervisor);
    }

    private void encodeBlockHeader(TapeBuilder builder, char[] nameBuffer) {
        builder.beginTrack(FileType.ROM, nameBuffer);
    }

    private void encodeRomBlock(
            TapeBuilder builder,
            char headId,
            int blockStart,
            int blockEnd,
            char[] loader,
            boolean isLastBlock) throws FlowException {
        char romCRC = calculateCRC(blockStart, blockEnd);

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

        writeBlockPayload(builder, loader, inputMemPointer, blockStart, blockEnd, isLastBlock);
    }

    private void writeBlockPayload(
            TapeBuilder builder,
            char[] loader,
            byte[] source,
            int blockStart,
            int blockEnd,
            boolean isLastBlock) {
        int blockSize = blockEnd - blockStart;
        if (turbo) {
            builder.encodeTurboPayload(RomLoaders.buildTurboFrame(loader, source, blockStart, blockEnd, isLastBlock));
        } else {
            builder.beginDataBlock();
            builder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + blockSize));
            builder.encodeData(loader);
            for (int i = blockStart; i < blockEnd; i++) {
                builder.writeDataByte((char) source[i]);
            }
        }
    }
}
