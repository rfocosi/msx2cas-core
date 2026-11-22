package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.util.FileCommons;

/**
 * Abstract base class for file converters targeting {@link br.com.dod.vcas.tape.TapeImage}.
 */
public abstract class AbstractFileConverter implements Converter {

    protected static final long MIN_ENC_INPUT_FILE_LENGTH = 5L;
    public static final int CAS_FILENAME_LENGTH = FileCommons.CAS_FILENAME_LENGTH;

    protected final String inputFileName;
    protected final TapeSpeed speed;
    protected byte[] inputMemPointer;

    /**
     * Constructor for converters backed by in-memory data rather than a file path.
     */
    protected AbstractFileConverter() {
        this((TapeSpeed) null, null);
    }

    /**
     * Constructor for converters backed by in-memory data rather than a file path.
     *
     * @param speed Target tape speed (optional)
     */
    protected AbstractFileConverter(TapeSpeed speed) {
        this(speed, null);
    }

    /**
     * Constructor for converters backed by in-memory data while retaining an input file name.
     *
     * @param speed Target tape speed (optional)
     * @param inputFileName Original input file name (optional)
     */
    protected AbstractFileConverter(TapeSpeed speed, String inputFileName) {
        this.inputFileName = inputFileName;
        this.speed = speed;
    }

    /**
     * Constructor reading an input file from disk with default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If there is an error reading the file
     */
    protected AbstractFileConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400);
    }

    /**
     * Constructor reading an input file from disk with explicit {@link TapeSpeed}.
     *
     * @param inputFileName Name of the input file
     * @param speed Target tape speed (may be {@code null} for native TSX timings)
     * @throws FlowException If there is an error reading the file
     */
    protected AbstractFileConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        this.inputFileName = inputFileName;
        this.speed = speed;
        this.inputMemPointer = FileCommons.readFile(inputFileName);
        validateMinFileSize();
    }

    /**
     * Effective {@link TapeSpeed} for non-TSX conversions (defaults to {@link TapeSpeed#BPS_2400}).
     *
     * @return Non-null {@link TapeSpeed}
     */
    protected TapeSpeed effectiveSpeed() {
        return speed != null ? speed : TapeSpeed.BPS_2400;
    }

    /**
     * Get the size of the input file.
     *
     * @return Size of the input file
     */
    protected int getFileSize() {
        return inputMemPointer.length;
    }

    /**
     * Validate that the file meets the minimum size requirements.
     *
     * @throws FlowException If the file is too small
     */
    protected void validateMinFileSize() throws FlowException {
        if (getFileSize() < MIN_ENC_INPUT_FILE_LENGTH) {
            throw FlowException.error("file_size_invalid");
        }
    }

    /**
     * Get the CAS name for the file.
     *
     * @return CAS format name
     */
    public String casName() {
        return inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
    }

    /**
     * Get a formatted name buffer with the default CAS filename length.
     *
     * @return Formatted name buffer as a char array
     */
    protected char[] getNameBuffer() {
        return formatNameBuffer("", CAS_FILENAME_LENGTH);
    }

    /**
     * Get a formatted name buffer with the specified suffix number and default CAS length.
     *
     * @param number Number to append to the name
     * @return Formatted name buffer as a char array
     */
    protected char[] getNumberedNameBuffer(final int number) {
        String numStr = String.valueOf(number);
        return formatNameBuffer(numStr, CAS_FILENAME_LENGTH - numStr.length());
    }

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
     * Calculate the CRC for a block of data.
     *
     * @param blockStart Start of the block
     * @param blockEnd End of the block
     * @return Calculated CRC
     * @throws FlowException If block bounds are invalid
     */
    protected char calculateCRC(int blockStart, int blockEnd) throws FlowException {
        return calculateCRC(inputMemPointer, blockStart, blockEnd);
    }

    /**
     * Calculate the CRC for a block of data in the supplied buffer.
     *
     * @param data Source buffer
     * @param blockStart Start of the block
     * @param blockEnd End of the block
     * @return Calculated CRC
     * @throws FlowException If block bounds are invalid
     */
    protected static char calculateCRC(byte[] data, int blockStart, int blockEnd) throws FlowException {
        if (blockStart > blockEnd) {
            throw FlowException.error("header_conflicting_information");
        }
        char crc = 0;
        for (int i = blockStart; i < blockEnd; i++) {
            crc += (char) data[i];
        }
        return crc;
    }

    /**
     * Build a binary address buffer starting at 0x9000.
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
     * Calculate the size of a char array.
     *
     * @param charArray Char array to measure
     * @return Size of the char array
     */
    protected static int sizeof(char[] charArray) {
        return (charArray == null ? 0 : charArray.length);
    }
}
