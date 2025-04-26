package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for ROM files
 */
public class RomConverter extends AbstractFileConverter {
    
    private static final int ROM_32K_SIZE = 32768;
    private static final int ROM_49K_OFFSET = 16384;
    
    private boolean resetRom;
    
    /**
     * Constructor for RomConverter
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public RomConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        this.resetRom = false;
    }
    
    /**
     * Set whether to reset the ROM during conversion
     * 
     * @param resetRom True to reset the ROM, false otherwise
     * @return This RomConverter instance
     */
    public RomConverter resetRom(boolean resetRom) {
        this.resetRom = resetRom;
        return this;
    }
    
    @Override
    public WavOutput convert() throws FlowException {
        // Determine if this is a 32K or 49K ROM
        if (isRom49K()) {
            return convertRom49K();
        } else {
            return convertRom32K();
        }
    }
    
    /**
     * Check if this is a 49K ROM
     * 
     * @return True if this is a 49K ROM, false otherwise
     */
    private boolean isRom49K() {
        return getFileSize() > ROM_32K_SIZE;
    }
    
    /**
     * Convert a 32K ROM
     * 
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    private WavOutput convertRom32K() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        
        // Encode ROM header and content
        encodeRomHeader(encoder);
        encodeRom32KContent(encoder);
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
    
    /**
     * Convert a 49K ROM
     * 
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    private WavOutput convertRom49K() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);
        
        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        
        // Encode ROM header and content
        encodeRomHeader(encoder);
        encodeRom49KContent(encoder);
        
        // Finalize the WAV encoding
        encoder.finalize();
        
        return encoder;
    }
    
    /**
     * Encode the ROM header
     * 
     * @param encoder WAV encoder to use
     */
    private void encodeRomHeader(WavEncoder encoder) {
        encoder.encodeLongHeader();
        
        // Write ROM loader
        char[] loader = getRomLoader();
        encoder.encodeData(loader);
        
        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
    }
    
    /**
     * Encode a 32K ROM's content
     * 
     * @param encoder WAV encoder to use
     */
    private void encodeRom32KContent(WavEncoder encoder) {
        encoder.encodeShortHeader();
        
        // Write ROM content
        for (int i = 0; i < Math.min(ROM_32K_SIZE, inputMemPointer.length); i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
        
        // Write reset code if needed
        if (resetRom) {
            encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
            encoder.encodeShortHeader();
            encoder.encodeData(getRomResetCode());
        }
    }
    
    /**
     * Encode a 49K ROM's content
     * 
     * @param encoder WAV encoder to use
     */
    private void encodeRom49KContent(WavEncoder encoder) {
        // First part (32K)
        encoder.encodeShortHeader();
        
        for (int i = 0; i < ROM_32K_SIZE; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
        
        // Second part (remaining data)
        encoder.encodePause(WavEncoder.SEPARATOR_PAUSE_LENGTH);
        encoder.encodeShortHeader();
        
        for (int i = ROM_49K_OFFSET; i < inputMemPointer.length; i++) {
            encoder.writeDataByte((char) inputMemPointer[i]);
        }
        
        // Write reset code if needed
        if (resetRom) {
            encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
            encoder.encodeShortHeader();
            encoder.encodeData(getRomResetCode());
        }
    }
    
    /**
     * Get the ROM loader code
     * 
     * @return ROM loader code
     */
    private char[] getRomLoader() {
        return new char[] {
            // ROM loader code would go here
            // This is a placeholder - the actual code would be copied from the original implementation
            0xC3, 0x10, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0xF3, 0x3E, 0x00, 0x32, 0x00, 0x40, 0x21, 0x01, 0x40, 0x11,
            0x00, 0x80, 0x01, 0xFF, 0x7F, 0xED, 0xB0, 0x21, 0x00, 0x40, 0x11, 0x00, 0x00,
            0x01, 0x00, 0x40, 0xED, 0xB0, 0xC3, 0x00, 0x00
        };
    }
    
    /**
     * Get the ROM reset code
     * 
     * @return ROM reset code
     */
    private char[] getRomResetCode() {
        return new char[] {
            // ROM reset code would go here
            // This is a placeholder - the actual code would be copied from the original implementation
            0xF3, 0x3E, 0x00, 0x32, 0x00, 0x40, 0xC3, 0x00, 0x00
        };
    }
    
    /**
     * Factory method to create a RomConverter instance
     * 
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @return RomConverter instance
     * @throws FlowException If there's an error creating the converter
     */
    public static RomConverter build(String inputFileName, SampleRate sampleRate) throws FlowException {
        return new RomConverter(inputFileName, sampleRate);
    }
}
