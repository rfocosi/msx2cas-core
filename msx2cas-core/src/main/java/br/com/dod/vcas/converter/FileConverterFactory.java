package br.com.dod.vcas.converter;

import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;

/**
 * Factory for creating appropriate file converters based on file type.
 */
public interface FileConverterFactory {

    /**
     * Create a converter for the specified file using default options.
     *
     * @param filePath Path to the file
     * @return Appropriate converter for the file type
     * @throws FlowException If there is an error creating the converter
     */
    default Converter createConverter(String filePath) throws FlowException {
        return createConverter(filePath, ConversionOptions.defaults());
    }

    /**
     * Create a converter for the specified file with conversion options.
     *
     * @param filePath Path to the file
     * @param options Conversion options
     * @return Appropriate converter for the file type
     * @throws FlowException If there is an error creating the converter
     */
    Converter createConverter(String filePath, ConversionOptions options) throws FlowException;

    /**
     * Create a converter for pre-loaded CAS entries at the specified {@link TapeSpeed}.
     *
     * @param casFiles List of CAS tracks
     * @param speed Target {@link TapeSpeed}
     * @return CAS converter
     * @throws FlowException If there is an error creating the converter
     */
    Converter createConverter(List<CasFile> casFiles, TapeSpeed speed) throws FlowException;

    /**
     * Create a converter for pre-loaded CAS entries with retained input file name.
     *
     * @param casFiles List of CAS tracks
     * @param speed Target {@link TapeSpeed}
     * @param inputFileName Original input file name
     * @return CAS converter
     * @throws FlowException If there is an error creating the converter
     */
    default Converter createConverter(List<CasFile> casFiles, TapeSpeed speed, String inputFileName)
            throws FlowException {
        return createConverter(casFiles, speed);
    }

    /**
     * Detect the file type for the given file path.
     *
     * @param filePath Path to the file
     * @return Detected {@link FileType}
     * @throws FlowException If there is an error detecting the file type
     */
    FileType detectFileType(String filePath) throws FlowException;
}
