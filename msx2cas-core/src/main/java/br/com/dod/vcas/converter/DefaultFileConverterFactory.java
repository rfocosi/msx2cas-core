package br.com.dod.vcas.converter;

import java.io.IOException;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.converter.impl.AsciiConverter;
import br.com.dod.vcas.converter.impl.BasConverter;
import br.com.dod.vcas.converter.impl.BinConverter;
import br.com.dod.vcas.converter.impl.CasConverter;
import br.com.dod.vcas.converter.impl.RomConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

/**
 * Default implementation of FileConverterFactory
 */
public class DefaultFileConverterFactory implements FileConverterFactory {

    @Override
    public Converter createConverter(String filePath, SampleRate sampleRate, ConversionOptions options)
            throws FlowException {
        boolean resetRom = options != null && options.resetRom();
        boolean megaRam = options != null && options.megaRam();
        MapperType mapperType = options != null && options.mapperType() != null
                ? options.mapperType()
                : MapperType.AUTO;
        FileType fileType = detectFileType(filePath);
        return switch (fileType) {
            case BAS -> new BasConverter(filePath, sampleRate);
            case BIN -> new BinConverter(filePath, sampleRate);
            case CAS -> new CasConverter(filePath, sampleRate);
            case ROM -> new RomConverter(filePath, sampleRate, resetRom, megaRam, mapperType);
            default -> new AsciiConverter(filePath, sampleRate);
        };
    }

    @Override
    public Converter createConverter(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException {
        return new CasConverter(casFiles, sampleRate);
    }

    @Override
    public Converter createConverter(List<CasFile> casFiles, SampleRate sampleRate, String inputFileName)
            throws FlowException {
        return new CasConverter(casFiles, sampleRate, inputFileName);
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
