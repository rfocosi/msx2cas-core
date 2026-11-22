package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBuilder;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Converter for MSX binary ({@code BIN} / {@code LDR}) files.
 */
public class BinConverter extends AbstractFileConverter {

    private static final int FILE_OFFSET = 7;

    private boolean turbo;

    /**
     * Constructor for BinConverter with default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If reading the file fails
     */
    public BinConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, false);
    }

    /**
     * Constructor for BinConverter with optional turbo speed.
     *
     * @param inputFileName Name of the input file
     * @param turbo True to enable Stage-2 Z80 PWM turbo mode
     * @throws FlowException If reading the file fails
     */
    public BinConverter(String inputFileName, boolean turbo) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400, turbo);
    }

    /**
     * Constructor for BinConverter.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If reading the file fails
     */
    public BinConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        this(inputFileName, speed, false);
    }

    /**
     * Constructor for BinConverter with Stage-1 {@link TapeSpeed} and Stage-2 turbo toggle.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed} for Stage-1 MSX BIOS header/supervisor
     * @param turbo True to enable Stage-2 Z80 PWM turbo mode
     * @throws FlowException If reading the file fails
     */
    public BinConverter(String inputFileName, TapeSpeed speed, boolean turbo) throws FlowException {
        super(inputFileName, speed);
        this.turbo = turbo;
    }

    /**
     * Toggle Stage-2 Z80 PWM turbo mode.
     *
     * @param turbo True to enable turbo mode
     * @return This {@link BinConverter} instance
     */
    public BinConverter turbo(boolean turbo) {
        this.turbo = turbo;
        return this;
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        TapeSpeed targetSpeed = effectiveSpeed();
        TapeBuilder builder = new TapeBuilder(inputFileName, targetSpeed);

        builder.beginTrack(FileType.BIN, getNameBuffer());
        builder.beginDataBlock();

        char[] loader = getLoader();
        calculateBinCRC(loader);
        addStartAddressToLoader(loader);

        int payloadLength = inputMemPointer.length - FILE_OFFSET;
        if (turbo && payloadLength > 0) {
            char[] supervisor = RomLoaders.turboSupervisor(0x9000);
            builder.encodeData(buildBinaryAddressBuffer(sizeof(supervisor)));
            builder.encodeData(supervisor);
            builder.encodeTurboPayload(
                    RomLoaders.buildTurboFrame(loader, inputMemPointer, FILE_OFFSET, inputMemPointer.length, true));
        } else {
            builder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + payloadLength));
            builder.encodeData(loader);

            for (int i = FILE_OFFSET; i < inputMemPointer.length; i++) {
                builder.writeDataByte((char) inputMemPointer[i]);
            }
        }

        return builder.build();
    }

    private void addStartAddressToLoader(char[] loader) {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }

    private void calculateBinCRC(char[] loader) throws FlowException {
        char binBegin = (char) (Byte.toUnsignedInt(inputMemPointer[2]) * 0x100 +
                              Byte.toUnsignedInt(inputMemPointer[1]));
        char binEnd = (char) (Byte.toUnsignedInt(inputMemPointer[4]) * 0x100 +
                            Byte.toUnsignedInt(inputMemPointer[3]));

        loader[9] = calculateCRC(FILE_OFFSET, (binEnd - binBegin) + FILE_OFFSET);
    }

    private char[] getLoader() {
        return new char[] {
                0xC3, 0x27, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x20, 0x4D,
                0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x20, 0x3E, 0x20, 0x46, 0x61, 0x69, 0x6C,
                0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52, 0x52, 0x4F, 0x52, 0x21, 0x00,
                0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52,
                0xE5, 0xC1, 0x21, 0x13, 0x91, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79,
                0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20, 0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E,
                0xB8, 0xCA, 0x91, 0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3,
                0x77, 0x3E, 0x08, 0x23, 0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3,
                0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90,
                0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18,
                0xF2, 0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01,
                0xFB, 0xC9, 0xF3, 0x2A, 0x07, 0x90, 0xE5, 0x21, 0x13, 0x91, 0xED, 0x5B, 0x03,
                0x90, 0x7C, 0xBA, 0x38, 0x1D, 0x20, 0x04, 0x7D, 0xBB, 0x38, 0x17, 0x21, 0xD5,
                0x90, 0x11, 0xF1, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xD5,
                0x90, 0x11, 0x60, 0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x21, 0xF0, 0x90, 0x11, 0x14,
                0x91, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xF0, 0x90, 0x11, 0x60,
                0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB,
                0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0x13, 0x91, 0xED, 0x5B, 0x03, 0x90,
                0x03, 0xED, 0xB0, 0xFB, 0xC9, 0x00, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90,
                0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x2A, 0x03, 0x90, 0x37, 0x3F, 0xED,
                0x4A, 0xEB, 0x21, 0x13, 0x91, 0x37, 0x3F, 0xED, 0x4A, 0x03, 0xED, 0xB8, 0xFB,
                0xC9, 0x00
        };
    }
}
