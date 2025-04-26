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
    
    public static final int CAS_FILENAME_LENGTH = 6;
    
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
        encoder.encodeData(getNameBuffer(CAS_FILENAME_LENGTH));
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        // Write file content
        for (int i = 0; i < inputMemPointer.length; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
}
