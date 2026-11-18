package br.com.dod.vcas.converter.impl;

import java.util.Arrays;

import br.com.dod.vcas.cas.CasEncoder;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

/**
 * Converter for binary files
 */
public class BinConverter extends AbstractFileConverter {
    
    private static final int FILE_OFFSET = 7;

    private boolean turbo;

    /**
     * Constructor for BinConverter without WAV sample rate.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If there's an error reading the file
     */
    public BinConverter(String inputFileName) throws FlowException {
        this(inputFileName, null, false);
    }

    /**
     * Constructor for BinConverter without WAV sample rate.
     *
     * @param inputFileName Name of the input file
     * @param turbo True to enable the Stage-2 Z80 turbo loader
     * @throws FlowException If there's an error reading the file
     */
    public BinConverter(String inputFileName, boolean turbo) throws FlowException {
        this(inputFileName, null, turbo);
    }
    
    /**
     * Constructor for BinConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public BinConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        this(inputFileName, sampleRate, false);
    }

    /**
     * Constructor for BinConverter
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @param turbo True to enable the Stage-2 Z80 turbo loader
     * @throws FlowException If there's an error reading the file
     */
    public BinConverter(String inputFileName, SampleRate sampleRate, boolean turbo) throws FlowException {
        super(inputFileName, sampleRate);
        this.turbo = turbo;
    }

    /**
     * Set whether to use the Stage-2 Z80 turbo loader
     *
     * @param turbo True for turbo mode, false otherwise
     * @return This BinConverter instance
     */
    public BinConverter turbo(boolean turbo) {
        this.turbo = turbo;
        return this;
    }
    
    @Override
    public CasOutput convertToCas() throws FlowException {
        CasEncoder encoder = new CasEncoder(inputFileName);

        encoder.beginTrack(FileType.BIN, getNameBuffer());
        encoder.beginDataBlock();

        char[] loader = getLoader();
        calculateBinCRC(loader);
        addStartAddressToLoader(loader);

        int payloadLength = inputMemPointer.length - FILE_OFFSET;
        if (turbo && payloadLength > 0) {
            char[] turboLoader = RomLoaders.withTurboStub(loader, payloadLength);
            encoder.encodeData(buildBinaryAddressBuffer(sizeof(turboLoader)));
            encoder.encodeData(turboLoader);
            encoder.encodeTurboPayload(Arrays.copyOfRange(inputMemPointer, FILE_OFFSET, inputMemPointer.length));
        } else {
            encoder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + payloadLength));
            encoder.encodeData(loader);

            // Write file content
            for (int i = FILE_OFFSET; i < inputMemPointer.length; i++) {
                encoder.writeDataByte((char) inputMemPointer[i]);
            }
        }

        encoder.finish();

        return encoder;
    }
    
    /**
     * Add the start address to the loader
     * 
     * @param loader Loader code
     */
    private void addStartAddressToLoader(char[] loader) {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }
    
    /**
     * Calculate the CRC for the binary file
     * 
     * @param loader Loader code
     * @throws FlowException If there's an error calculating the CRC
     */
    private void calculateBinCRC(char[] loader) throws FlowException {
        char binBegin = (char) (Byte.toUnsignedInt(inputMemPointer[2]) * 0x100 +
                               Byte.toUnsignedInt(inputMemPointer[1]));
        char binEnd = (char) (Byte.toUnsignedInt(inputMemPointer[4]) * 0x100 +
                             Byte.toUnsignedInt(inputMemPointer[3]));
        
        loader[9] = calculateCRC(FILE_OFFSET, (binEnd - binBegin) + FILE_OFFSET);
    }
    
    /**
     * Get the loader code for binary files
     * 
     * @return Loader code
     */
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
