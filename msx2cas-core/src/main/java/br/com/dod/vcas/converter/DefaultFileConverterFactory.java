package br.com.dod.vcas.converter;

import java.io.IOException;

import br.com.dod.vcas.converter.impl.AsciiConverter;
import br.com.dod.vcas.converter.impl.BasConverter;
import br.com.dod.vcas.converter.impl.BinConverter;
import br.com.dod.vcas.converter.impl.CasConverter;
import br.com.dod.vcas.converter.impl.RomConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

/**
 * Default implementation of FileConverterFactory
 */
public class DefaultFileConverterFactory implements FileConverterFactory {

    @Override
    public Converter createConverter(String filePath, SampleRate sampleRate) throws FlowException {
        FileType fileType = detectFileType(filePath);
        
        switch (fileType) {
            case BAS:
                return new BasConverter(filePath, sampleRate);
            case BIN:
                return new BinConverter(filePath, sampleRate);
            case CAS:
                return new CasConverter(filePath, sampleRate);
            case ROM:
                return new RomConverter(filePath, sampleRate);
            default:
                return new AsciiConverter(filePath, sampleRate);
        }
    }

    @Override
    public FileType detectFileType(String filePath) throws FlowException {
        try {
            return FileCommons.detectFile(filePath);
        } catch (IOException e) {
            throw FlowException.error("file_type_detection_error");
        }
    }
}
