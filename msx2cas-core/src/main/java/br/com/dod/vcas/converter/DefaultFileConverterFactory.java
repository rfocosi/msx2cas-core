package br.com.dod.vcas.converter;

import java.io.IOException;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.converter.impl.AsciiConverter;
import br.com.dod.vcas.converter.impl.BasConverter;
import br.com.dod.vcas.converter.impl.BinConverter;
import br.com.dod.vcas.converter.impl.CasConverter;
import br.com.dod.vcas.converter.impl.RomConverter;
import br.com.dod.vcas.converter.impl.TsxConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.util.FileCommons;

/**
 * Default implementation of {@link FileConverterFactory}.
 */
public class DefaultFileConverterFactory implements FileConverterFactory {

    @Override
    public Converter createConverter(String filePath, ConversionOptions options) throws FlowException {
        boolean resetRom = options != null && options.resetRom();
        boolean megaRam = options != null && options.megaRam();
        boolean turbo = options != null && options.turbo();
        MapperType mapperType = options != null && options.mapperType() != null
                ? options.mapperType()
                : MapperType.AUTO;
        TapeSpeed rawSpeed = options != null ? options.speed() : null;
        TapeSpeed nonTsxSpeed = rawSpeed != null ? rawSpeed : TapeSpeed.BPS_2400;

        FileType fileType = detectFileType(filePath);
        return switch (fileType) {
            case TSX -> new TsxConverter(filePath, rawSpeed, turbo);
            case BAS -> new BasConverter(filePath, nonTsxSpeed);
            case BIN -> new BinConverter(filePath, nonTsxSpeed, turbo);
            case CAS -> new CasConverter(filePath, nonTsxSpeed);
            case ROM -> new RomConverter(filePath, nonTsxSpeed, resetRom, megaRam, mapperType, turbo);
            default -> new AsciiConverter(filePath, nonTsxSpeed);
        };
    }

    @Override
    public Converter createConverter(List<CasFile> casFiles, TapeSpeed speed) throws FlowException {
        return new CasConverter(casFiles, speed);
    }

    @Override
    public Converter createConverter(List<CasFile> casFiles, TapeSpeed speed, String inputFileName)
            throws FlowException {
        return new CasConverter(casFiles, speed, inputFileName);
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
