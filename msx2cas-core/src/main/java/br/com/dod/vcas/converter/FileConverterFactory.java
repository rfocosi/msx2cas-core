package br.com.dod.vcas.converter;

import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

/**
 * Factory for creating appropriate file converters based on file type
 */
public interface FileConverterFactory {

    /**
     * Create a converter for the specified file without a WAV sample rate
     *
     * @param filePath Path to the file
     * @return Appropriate converter for the file type
     * @throws FlowException If there's an error creating the converter
     */
    default Converter createConverter(String filePath) throws FlowException {
        return createConverter(filePath, (SampleRate) null, ConversionOptions.defaults());
    }

    /**
     * Create a converter for the specified file with conversion options and no WAV sample rate
     *
     * @param filePath Path to the file
     * @param options Conversion options
     * @return Appropriate converter for the file type
     * @throws FlowException If there's an error creating the converter
     */
    default Converter createConverter(String filePath, ConversionOptions options) throws FlowException {
        return createConverter(filePath, (SampleRate) null, options);
    }

    /**
     * Create a converter for the specified file using default options
     *
     * @param filePath Path to the file
     * @param sampleRate Sample rate to use for conversion
     * @return Appropriate converter for the file type
     * @throws FlowException If there's an error creating the converter
     */
    default Converter createConverter(String filePath, SampleRate sampleRate) throws FlowException {
        return createConverter(filePath, sampleRate, ConversionOptions.defaults());
    }

    /**
     * Create a converter for the specified file with conversion options
     *
     * @param filePath Path to the file
     * @param sampleRate Sample rate to use for conversion
     * @param options Conversion options
     * @return Appropriate converter for the file type
     * @throws FlowException If there's an error creating the converter
     */
    Converter createConverter(String filePath, SampleRate sampleRate, ConversionOptions options) throws FlowException;

    /**
     * Create a converter for pre-loaded CAS entries
     *
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @return CAS converter
     * @throws FlowException If there's an error creating the converter
     */
    Converter createConverter(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException;

    /**
     * Create a converter for pre-loaded CAS entries with retained input file name
     *
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @param inputFileName Original input file name
     * @return CAS converter
     * @throws FlowException If there's an error creating the converter
     */
    default Converter createConverter(List<CasFile> casFiles, SampleRate sampleRate, String inputFileName)
            throws FlowException {
        return createConverter(casFiles, sampleRate);
    }

    /**
     * Detect the file type for the given file path
     *
     * @param filePath Path to the file
     * @return Detected FileType
     * @throws FlowException If there's an error detecting the file type
     */
    FileType detectFileType(String filePath) throws FlowException;
}
