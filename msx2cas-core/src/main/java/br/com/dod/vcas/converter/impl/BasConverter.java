package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBuilder;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Converter for tokenized MSX BASIC files.
 */
public class BasConverter extends AbstractFileConverter {

    /**
     * Constructor for BasConverter with default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If reading the file fails
     */
    public BasConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400);
    }

    /**
     * Constructor for BasConverter.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If reading the file fails
     */
    public BasConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        super(inputFileName, speed);
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        TapeBuilder builder = new TapeBuilder(inputFileName, effectiveSpeed());

        builder.beginTrack(FileType.BAS, getNameBuffer());
        builder.beginDataBlock();

        // Write file content (skip leading 0xFF token marker)
        for (int i = 1; i < inputMemPointer.length; i++) {
            builder.writeDataByte((char) inputMemPointer[i]);
        }

        // Write 7-byte BASIC EOF trailer
        for (int i = 0; i < 7; i++) {
            builder.writeDataByte((char) 0x00);
        }

        return builder.build();
    }
}
