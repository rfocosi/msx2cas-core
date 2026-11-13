package br.com.dod.vcas.converter.impl;

import java.io.ByteArrayOutputStream;

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

    private static final int BLOCK_SIZE = 256;
    private static final char EOF_BYTE = 0x1A;

    /**
     * Constructor for AsciiConverter
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading or validating the file
     */
    public AsciiConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        setup();
    }

    private void setup() throws FlowException {
        fixFileNewLines();

        for (int i = 0; i < getFileSize(); i++) {
            if (inputMemPointer[i] == 0x0A && (i == 0 || inputMemPointer[i - 1] != 0x0D)) {
                throw FlowException.error("wrong_char_sequence");
            }

            if (!((char) inputMemPointer[i] >= ' '
                    || inputMemPointer[i] <= 2
                    || inputMemPointer[i] == 0x0A
                    || inputMemPointer[i] == 0x0D
                    || inputMemPointer[i] == EOF_BYTE)) {
                throw FlowException.error("invalid_file_format");
            }
        }
    }

    private void fixFileNewLines() {
        ByteArrayOutputStream converted = new ByteArrayOutputStream(inputMemPointer.length);
        for (int i = 0; i < inputMemPointer.length; i++) {
            if (inputMemPointer[i] == 0x0A && (i == 0 || inputMemPointer[i - 1] != 0x0D)) {
                converted.write(0x0D);
                converted.write(0x0A);
            } else {
                converted.write(inputMemPointer[i]);
            }
        }
        inputMemPointer = converted.toByteArray();
    }

    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);

        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
        encoder.encodeLongHeader();

        encoder.encodeData(FileType.ASCII.getHeader());
        encoder.encodeData(getNameBuffer());

        encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);

        for (int offset = 0; offset < inputMemPointer.length; offset += BLOCK_SIZE) {
            encoder.encodeShortHeader();
            int chunkSize = Math.min(BLOCK_SIZE, inputMemPointer.length - offset);
            for (int i = 0; i < chunkSize; i++) {
                encoder.writeDataByte((char) inputMemPointer[offset + i]);
            }
        }

        // Write 256 EOF bytes at the end of ASCII program
        for (int i = 0; i < BLOCK_SIZE; i++) {
            encoder.writeDataByte(EOF_BYTE);
        }

        encoder.finish();

        return encoder;
    }
}
