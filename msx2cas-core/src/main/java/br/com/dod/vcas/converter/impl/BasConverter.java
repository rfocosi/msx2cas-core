package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for BASIC files
 */
public class BasConverter extends AbstractFileConverter {
    
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
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        // Encode the BASIC file
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        encoder.encodeLongHeader();
        
        // Write header and filename
        encoder.encodeData(FileType.BAS.getHeader());
        encoder.encodeData(getNameBuffer());
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        // Write file content (skip leading 0xFF token marker)
        for (int i = 1; i < inputMemPointer.length; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }

        // Write 7-byte BASIC EOF trailer
        for (int i = 0; i < 7; i++) {
            encoder.writeDataByte((char) 0x00);
        }
        
        // Finish the WAV encoding
        encoder.finish();
        
        return encoder;
    }
}
