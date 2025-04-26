package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for ASCII files
 */
public class AsciiConverter extends AbstractFileConverter {
    
    public static final int CAS_FILENAME_LENGTH = 6;
    private static final int BLOCK_SIZE = 256;
    
    /**
     * Constructor for AsciiConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public AsciiConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }
    
    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        
        int blockCount = (int) Math.ceil((double) inputMemPointer.length / BLOCK_SIZE);
        
        for (int i = 0; i < blockCount; i++) {
            encodeBlock(encoder, i, blockCount);
        }
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
    
    /**
     * Encode a block of the ASCII file
     * 
     * @param encoder WAV encoder to use
     * @param blockNumber Number of the current block
     * @param blockCount Total number of blocks
     * @throws FlowException If there's an error encoding the block
     */
    private void encodeBlock(WavEncoder encoder, int blockNumber, int blockCount) throws FlowException {
        encoder.encodeLongHeader();
        
        // Write header and filename with block number
        encoder.encodeData(FileType.ASCII.getHeader());
        encoder.encodeData(getNameBuffer(String.valueOf(blockNumber + 1), CAS_FILENAME_LENGTH));
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        // Calculate block size and offset
        int blockSize = Math.min(BLOCK_SIZE, inputMemPointer.length - (blockNumber * BLOCK_SIZE));
        int offset = blockNumber * BLOCK_SIZE;
        
        // Write block size
        encoder.writeDataByte((char) blockSize);
        
        // Write block data
        for (int i = 0; i < blockSize; i++) {
            encoder.writeDataByte((char) inputMemPointer[offset + i]);
        }
        
        // Write block CRC
        char crc = calculateCRC(offset, offset + blockSize);
        encoder.writeDataByte(crc);
        
        // Add separator pause between blocks
        if (blockNumber < blockCount - 1) {
            encoder.encodePause(WavEncoder.SEPARATOR_PAUSE_LENGTH);
        }
    }
    
    /**
     * Calculate the CRC for a block of data
     * 
     * @param blockStart Start of the block
     * @param blockEnd End of the block
     * @return Calculated CRC
     * @throws FlowException If there's an error calculating the CRC
     */
    private char calculateCRC(int blockStart, int blockEnd) throws FlowException {
        if (blockStart > blockEnd) throw FlowException.error("header_conflicting_information");
        char crc = 0;
        for (int i = blockStart; i < blockEnd; i++) crc += (char) inputMemPointer[i];
        return crc;
    }
}
