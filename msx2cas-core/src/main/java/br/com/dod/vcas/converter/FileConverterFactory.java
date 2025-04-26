package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

/**
 * Factory for creating appropriate file converters based on file type
 */
public interface FileConverterFactory {
    /**
     * Create a converter for the specified file
     * 
     * @param filePath Path to the file
     * @param sampleRate Sample rate to use for conversion
     * @return Appropriate converter for the file type
     * @throws FlowException If there's an error creating the converter
     */
    Converter createConverter(String filePath, SampleRate sampleRate) throws FlowException;
    
    /**
     * Detect the file type for the given file path
     * 
     * @param filePath Path to the file
     * @return Detected FileType
     * @throws FlowException If there's an error detecting the file type
     */
    FileType detectFileType(String filePath) throws FlowException;
}
