package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.cas.CasEncoder;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

/**
 * Converter for BASIC files
 */
public class BasConverter extends AbstractFileConverter {

    /**
     * Constructor for BasConverter without WAV sample rate.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If there's an error reading the file
     */
    public BasConverter(String inputFileName) throws FlowException {
        super(inputFileName);
    }

    /**
     * Constructor for BasConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public BasConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }

    @Override
    public CasOutput convertToCas() throws FlowException {
        CasEncoder encoder = new CasEncoder(inputFileName);

        // Encode the BASIC track
        encoder.beginTrack(FileType.BAS, getNameBuffer());
        encoder.beginDataBlock();

        // Write file content (skip leading 0xFF token marker)
        for (int i = 1; i < inputMemPointer.length; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }

        // Write 7-byte BASIC EOF trailer
        for (int i = 0; i < 7; i++) {
            encoder.writeDataByte((char) 0x00);
        }

        encoder.finish();

        return encoder;
    }
}
