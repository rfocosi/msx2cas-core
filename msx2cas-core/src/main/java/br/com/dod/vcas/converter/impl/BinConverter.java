package br.com.dod.vcas.converter.impl;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for binary files
 */
public class BinConverter extends AbstractFileConverter {
    
    private static final int FILE_OFFSET = 7;
    public static final int CAS_FILENAME_LENGTH = 6;
    
    /**
     * Constructor for BinConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public BinConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }
    
    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        encoder.encodeLongHeader();
        
        // Write header and filename
        encoder.encodeData(FileType.BIN.getHeader());
        encoder.encodeData(getNameBuffer(CAS_FILENAME_LENGTH));
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        // Encode the loader
        encodeLoader(encoder, getLoader());
        
        // Write file content
        for (int i = FILE_OFFSET; i < inputMemPointer.length; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
    
    /**
     * Encode the loader for the binary file
     * 
     * @param encoder WAV encoder to use
     * @param loader Loader code
     * @throws FlowException If there's an error calculating the CRC
     */
    private void encodeLoader(WavEncoder encoder, char[] loader) throws FlowException {
        encoder.encodeData(buildBinaryAddressBuffer(sizeof(loader) + inputMemPointer.length - FILE_OFFSET));
        
        calculateBinCRC(loader);
        
        addStartAddressToLoader(loader);
        
        encoder.encodeData(loader);
    }
    
    /**
     * Add the start address to the loader
     * 
     * @param loader Loader code
     */
    private void addStartAddressToLoader(char[] loader) {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }
    
    /**
     * Calculate the CRC for the binary file
     * 
     * @param loader Loader code
     * @throws FlowException If there's an error calculating the CRC
     */
    private void calculateBinCRC(char[] loader) throws FlowException {
        char binBegin = (char) ((new DWORD(inputMemPointer[2]).getLow()).intValue() * 0x100 + 
                               (new DWORD(inputMemPointer[1]).getLow()).intValue());
        char binEnd = (char) ((new DWORD(inputMemPointer[4]).getLow()).intValue() * 0x100 + 
                             (new DWORD(inputMemPointer[3]).getLow()).intValue());
        
        loader[9] = calculateCRC(FILE_OFFSET, (binEnd-binBegin) + FILE_OFFSET);
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
    
    /**
     * Build a binary address buffer
     * 
     * @param binarySize Size of the binary data
     * @return Address buffer
     */
    private char[] buildBinaryAddressBuffer(final long binarySize) {
        char a = (char) (binarySize + 0x9000 - 1);
        
        char[] addressBuffer = new char[6];
        
        addressBuffer[0] = 0;
        addressBuffer[1] = 0x90;
        addressBuffer[2] = a;
        addressBuffer[3] = (char)(a >> 8);
        addressBuffer[4] = 0;
        addressBuffer[5] = 0x90;
        
        return addressBuffer;
    }
    
    /**
     * Get the loader code for binary files
     * 
     * @return Loader code
     */
    private char[] getLoader() {
        return new char[] {
                0xC3, 0x27, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x20, 0x4D,
                0x53, 0x58, 0x32, 0x43, 0x61, 0x73, 0x20, 0x3E, 0x20, 0x46, 0x61, 0x69, 0x6C,
                0x3A, 0x20, 0x43, 0x52, 0x43, 0x20, 0x45, 0x52, 0x52, 0x4F, 0x52, 0x21, 0x00,
                0xF3, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52,
                0xE5, 0xC1, 0x21, 0x13, 0x91, 0xAF, 0xF5, 0xF1, 0x86, 0x23, 0x0B, 0xF5, 0x79,
                0xB7, 0x20, 0xF7, 0x78, 0xB7, 0x20, 0xF3, 0xF1, 0x47, 0x21, 0x09, 0x90, 0x7E,
                0xB8, 0xCA, 0x91, 0x90, 0xFB, 0xCD, 0x6C, 0x00, 0x3E, 0x0F, 0x21, 0xE9, 0xF3,
                0x77, 0x3E, 0x08, 0x23, 0x77, 0x23, 0x77, 0xCD, 0x62, 0x00, 0xAF, 0xCD, 0xC3,
                0x00, 0xCD, 0xCF, 0x00, 0x21, 0x01, 0x01, 0xCD, 0xC6, 0x00, 0x11, 0x0A, 0x90,
                0x1A, 0xB7, 0x28, 0x0A, 0x13, 0xCD, 0xA2, 0x00, 0x24, 0xCD, 0xC6, 0x00, 0x18,
                0xF2, 0x21, 0x03, 0x01, 0xCD, 0xC6, 0x00, 0xCD, 0xC0, 0x00, 0xCD, 0x56, 0x01,
                0xFB, 0xC9, 0xF3, 0x2A, 0x07, 0x90, 0xE5, 0x21, 0x13, 0x91, 0xED, 0x5B, 0x03,
                0x90, 0x7C, 0xBA, 0x38, 0x1D, 0x20, 0x04, 0x7D, 0xBB, 0x38, 0x17, 0x21, 0xD5,
                0x90, 0x11, 0xF1, 0x90, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xD5,
                0x90, 0x11, 0x60, 0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x21, 0xF0, 0x90, 0x11, 0x14,
                0x91, 0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0xF0, 0x90, 0x11, 0x60,
                0xF5, 0xD5, 0xED, 0xB0, 0xC9, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90, 0xEB,
                0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x21, 0x13, 0x91, 0xED, 0x5B, 0x03, 0x90,
                0x03, 0xED, 0xB0, 0xFB, 0xC9, 0x00, 0x2A, 0x03, 0x90, 0xED, 0x5B, 0x05, 0x90,
                0xEB, 0x37, 0x3F, 0xED, 0x52, 0x44, 0x4D, 0x2A, 0x03, 0x90, 0x37, 0x3F, 0xED,
                0x4A, 0xEB, 0x21, 0x13, 0x91, 0x37, 0x3F, 0xED, 0x4A, 0x03, 0xED, 0xB8, 0xFB,
                0xC9, 0x00
        };
    }
}
