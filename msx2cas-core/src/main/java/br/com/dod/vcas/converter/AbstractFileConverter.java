package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

/**
 * Abstract base class for file converters
 */
public abstract class AbstractFileConverter implements Converter {
    
    protected static final long MIN_ENC_INPUT_FILE_LENGTH = 5L;
    public static final int CAS_FILENAME_LENGTH = FileCommons.CAS_FILENAME_LENGTH;
    
    protected final String inputFileName;
    protected final SampleRate sampleRate;
    protected byte[] inputMemPointer;

    /**
     * Constructor for converters backed by in-memory data rather than a file path.
     *
     * @param sampleRate Sample rate to use for conversion
     */
    protected AbstractFileConverter(SampleRate sampleRate) {
        this.inputFileName = null;
        this.sampleRate = sampleRate;
    }
    
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
        return inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
    }
    
    /**
     * Get a formatted name buffer with the default CAS filename length
     * 
     * @return Formatted name buffer as a char array
     */
    protected char[] getNameBuffer() {
        return formatNameBuffer("", CAS_FILENAME_LENGTH);
    }

    /**
     * Get a formatted name buffer with the specified suffix number and default CAS length
     * 
     * @param number Number to append to the name
     * @return Formatted name buffer as a char array
     */
    protected char[] getNumberedNameBuffer(final int number) {
        String numStr = String.valueOf(number);
        return formatNameBuffer(numStr, CAS_FILENAME_LENGTH - numStr.length());
    }
    
    /**
     * Format the name buffer with the specified number and name size
     * 
     * @param number Number to append to the name
     * @param nameSize Size of the name portion
     * @return Formatted name buffer as a char array
     */
    private char[] formatNameBuffer(final String number, final int nameSize) {
        int clampedSize = Math.max(0, nameSize);
        String name = casName();
        String prefix = name.substring(0, Math.min(name.length(), clampedSize));
        String combined = prefix + number;
        if (combined.length() > CAS_FILENAME_LENGTH) {
            combined = combined.substring(0, CAS_FILENAME_LENGTH);
        }
        return String.format("%-" + CAS_FILENAME_LENGTH + "s", combined).toCharArray();
    }

    /**
     * Calculate the CRC for a block of data
     * 
     * @param blockStart Start of the block
     * @param blockEnd End of the block
     * @return Calculated CRC
     * @throws FlowException If block bounds are invalid
     */
    protected char calculateCRC(int blockStart, int blockEnd) throws FlowException {
        if (blockStart > blockEnd) {
            throw FlowException.error("header_conflicting_information");
        }
        char crc = 0;
        for (int i = blockStart; i < blockEnd; i++) {
            crc += (char) inputMemPointer[i];
        }
        return crc;
    }

    /**
     * Build a binary address buffer starting at 0x9000
     * 
     * @param binarySize Size of the binary data
     * @return Address buffer
     */
    protected char[] buildBinaryAddressBuffer(final long binarySize) {
        char endAddr = (char) (binarySize + 0x9000 - 1);
        return new char[] {
                0,
                0x90,
                endAddr,
                (char) (endAddr >> 8),
                0,
                0x90
        };
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
