package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

/**
 * Abstract base class for file converters
 */
public abstract class AbstractFileConverter implements Converter {
    
    protected static final long MIN_ENC_INPUT_FILE_LENGTH = 5L;
    
    protected final String inputFileName;
    protected final SampleRate sampleRate;
    protected byte[] inputMemPointer;
    
    /**
     * Constructor for AbstractFileConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    protected AbstractFileConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        this.inputFileName = inputFileName;
        this.sampleRate = sampleRate;
        this.inputMemPointer = FileCommons.readFile(inputFileName);
        validateMinFileSize();
    }
    
    /**
     * Get the size of the input file
     * 
     * @return Size of the input file
     */
    protected int getFileSize() {
        return inputMemPointer.length;
    }
    
    /**
     * Validate that the file meets the minimum size requirements
     * 
     * @throws FlowException If the file is too small
     */
    protected void validateMinFileSize() throws FlowException {
        if (getFileSize() < MIN_ENC_INPUT_FILE_LENGTH) {
            throw FlowException.error("file_size_invalid");
        }
    }
    
    /**
     * Get the CAS name for the file
     * 
     * @return CAS format name
     */
    public String casName() {
        return FileCommons.getCasName(inputFileName);
    }
    
    /**
     * Get a formatted name buffer with the specified length
     * 
     * @param length Length of the name buffer
     * @return Formatted name buffer as a char array
     */
    protected char[] getNameBuffer(int length) {
        return formatNameBuffer("", length);
    }
    
    /**
     * Get a formatted name buffer with the specified number and length
     * 
     * @param number Number to append to the name
     * @param length Length of the name buffer
     * @return Formatted name buffer as a char array
     */
    protected char[] getNameBuffer(final String number, final int length) {
        return formatNameBuffer(number, length - number.length());
    }
    
    /**
     * Format the name buffer with the specified number and name size
     * 
     * @param number Number to append to the name
     * @param nameSize Size of the name portion
     * @return Formatted name buffer as a char array
     */
    private char[] formatNameBuffer(final String number, final int nameSize) {
        return String.format("%." + nameSize + "s%s", casName(), number).toCharArray();
    }
    
    /**
     * Calculate the size of a char array
     * 
     * @param charArray Char array to measure
     * @return Size of the char array
     */
    protected static int sizeof(char[] charArray) {
        return (charArray == null ? 0 : charArray.length);
    }
}
