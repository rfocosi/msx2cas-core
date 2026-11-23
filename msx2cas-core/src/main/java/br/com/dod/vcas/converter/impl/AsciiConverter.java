package br.com.dod.vcas.converter.impl;

import java.io.ByteArrayOutputStream;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBuilder;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Converter for ASCII text BASIC files.
 */
public class AsciiConverter extends AbstractFileConverter {

    private static final int BLOCK_SIZE = 256;
    private static final char EOF_BYTE = 0x1A;

    /**
     * Constructor for AsciiConverter with default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If reading or validating the file fails
     */
    public AsciiConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400);
    }

    /**
     * Constructor for AsciiConverter.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If reading or validating the file fails
     */
    public AsciiConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        super(inputFileName, speed);
        setup();
    }

    private void setup() throws FlowException {
        fixFileNewLines();

        for (int i = 0; i < getFileSize(); i++) {
            if (inputMemPointer[i] == 0x0A && (i == 0 || inputMemPointer[i - 1] != 0x0D)) {
                throw FlowException.error("wrong_char_sequence");
            }

            if (!((char) inputMemPointer[i] >= ' '
                    || inputMemPointer[i] <= 2
                    || inputMemPointer[i] == 0x0A
                    || inputMemPointer[i] == 0x0D
                    || inputMemPointer[i] == EOF_BYTE)) {
                throw FlowException.error("invalid_file_format");
            }
        }
    }

    private void fixFileNewLines() {
        ByteArrayOutputStream converted = new ByteArrayOutputStream(inputMemPointer.length);
        for (int i = 0; i < inputMemPointer.length; i++) {
            if (inputMemPointer[i] == 0x0A && (i == 0 || inputMemPointer[i - 1] != 0x0D)) {
                converted.write(0x0D);
                converted.write(0x0A);
            } else {
                converted.write(inputMemPointer[i]);
            }
        }
        inputMemPointer = converted.toByteArray();
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        TapeBuilder builder = new TapeBuilder(inputFileName, effectiveSpeed());

        builder.beginTrack(FileType.ASCII, getNameBuffer());

        for (int offset = 0; offset < inputMemPointer.length; offset += BLOCK_SIZE) {
            builder.beginDataBlock();
            int chunkSize = Math.min(BLOCK_SIZE, inputMemPointer.length - offset);
            for (int i = 0; i < chunkSize; i++) {
                builder.writeDataByte((char) inputMemPointer[offset + i]);
            }
        }

        // Write 256 EOF bytes at the end of ASCII program
        for (int i = 0; i < BLOCK_SIZE; i++) {
            builder.writeDataByte(EOF_BYTE);
        }

        return builder.build();
    }
}
