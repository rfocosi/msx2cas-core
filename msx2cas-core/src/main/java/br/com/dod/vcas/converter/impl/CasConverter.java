package br.com.dod.vcas.converter.impl;

import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for CAS files
 */
public class CasConverter extends AbstractFileConverter {
    
    private List<CasFile> casFiles;
    
    /**
     * Constructor for CasConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public CasConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        CasUtil casUtil = new CasUtil(inputFileName);
        this.casFiles = casUtil.list();
    }
    
    /**
     * Constructor for CasConverter with pre-loaded CAS files
     * 
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error with the CAS files
     */
    public CasConverter(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException {
        super(null, sampleRate);
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        this.casFiles = new ArrayList<>(casFiles);
    }
    
    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        
        boolean firstFile = true;
        
        for (CasFile casFile : casFiles) {
            if (!firstFile) {
                encoder.encodePause(WavEncoder.SEPARATOR_PAUSE_LENGTH);
            }
            
            encodeFile(encoder, casFile);
            firstFile = false;
        }
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
    
    /**
     * Encode a single CAS file
     * 
     * @param encoder WAV encoder to use
     * @param casFile CAS file to encode
     */
    private void encodeFile(WavEncoder encoder, CasFile casFile) {
        encoder.encodeLongHeader();
        
        // Write header and filename
        if (casFile.getHeader() != null && casFile.getHeader().length > 0) {
            encoder.encodeData(casFile.getHeader());
        }
        
        // Create name buffer from CAS file name
        char[] nameBuffer = new char[6];
        String name = casFile.getName() != null ? casFile.getName() : "------";
        for (int i = 0; i < Math.min(name.length(), 6); i++) {
            nameBuffer[i] = name.charAt(i);
        }
        encoder.encodeData(nameBuffer);
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        // Write file content
        Byte[] content = casFile.getContent();
        for (Byte b : content) {
            encoder.writeDataByte((char) (b & 0xFF));
        }
    }
}
