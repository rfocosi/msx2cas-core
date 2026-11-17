package br.com.dod.vcas;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.converter.ConversionOptions;
import br.com.dod.vcas.converter.Converter;
import br.com.dod.vcas.converter.DefaultFileConverterFactory;
import br.com.dod.vcas.converter.FileConverterFactory;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Main class for the MSX2CAS core library
 * Provides functionality to convert MSX files to CAS format (primary) and optionally to WAV format
 */
public class VirtualCas {

    private final List<String> fileList = new ArrayList<>();
    private final SampleRate sampleRate;
    private final FileConverterFactory converterFactory;
    private boolean reset;
    private boolean megaRam;
    private MapperType mapperType = MapperType.AUTO;

    /**
     * Constructor for VirtualCas when targeting CAS output only
     */
    public VirtualCas() {
        this(null, new DefaultFileConverterFactory());
    }

    /**
     * Constructor for VirtualCas with a custom converter factory when targeting CAS output only
     *
     * @param converterFactory Factory to create file converters
     */
    public VirtualCas(FileConverterFactory converterFactory) {
        this(null, converterFactory);
    }

    /**
     * Constructor for VirtualCas
     *
     * @param sampleRate Sample rate to use for optional WAV conversion
     */
    public VirtualCas(SampleRate sampleRate) {
        this(sampleRate, new DefaultFileConverterFactory());
    }

    /**
     * Constructor for VirtualCas with a custom converter factory
     *
     * @param sampleRate Sample rate to use for optional WAV conversion
     * @param converterFactory Factory to create file converters
     */
    public VirtualCas(SampleRate sampleRate, FileConverterFactory converterFactory) {
        this.sampleRate = sampleRate;
        this.converterFactory = converterFactory;
    }

    /**
     * Add a file to the conversion list
     *
     * @param filePath Path to the file
     * @return This VirtualCas instance
     */
    public VirtualCas addFile(String filePath) {
        this.fileList.add(filePath);
        return this;
    }

    /**
     * Set whether to reset ROMs during conversion
     *
     * @param reset True to reset ROMs, false otherwise
     * @return This VirtualCas instance
     */
    public VirtualCas resetRom(boolean reset) {
        this.reset = reset;
        return this;
    }

    /**
     * Set whether to convert ROMs for MegaRAM cartridges (port 0x8E)
     *
     * @param megaRam True to enable MegaRAM mode, false otherwise
     * @return This VirtualCas instance
     */
    public VirtualCas megaRam(boolean megaRam) {
        this.megaRam = megaRam;
        return this;
    }

    /**
     * Set the source MegaROM mapper type for MegaRAM conversion/patching
     *
     * @param mapperType Mapper type ({@link MapperType#AUTO} by default)
     * @return This VirtualCas instance
     */
    public VirtualCas mapperType(MapperType mapperType) {
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
        return this;
    }

    /**
     * Convert a single file to CAS format
     *
     * @param filePath Path to the file
     * @return CAS output
     * @throws FlowException If there's an error during conversion
     */
    public CasOutput convertToCas(String filePath) throws FlowException {
        Converter converter = converterFactory.createConverter(
                filePath, new ConversionOptions(reset, megaRam, mapperType));
        return converter.convertToCas();
    }

    /**
     * Convert all added files to CAS format
     *
     * @return List of CAS outputs
     * @throws FlowException If there's an error during conversion
     */
    public List<CasOutput> convertToCas() throws FlowException {
        final List<CasOutput> casList = new LinkedList<>();
        ConversionOptions options = new ConversionOptions(reset, megaRam, mapperType);

        for (String fileName : fileList) {
            Converter converter = converterFactory.createConverter(fileName, options);
            casList.add(converter.convertToCas());
        }
        return casList;
    }

    /**
     * Convert a single file to WAV format (via CAS conversion)
     *
     * @param filePath Path to the file
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    public WavOutput convert(String filePath) throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        CasOutput casOutput = convertToCas(filePath);
        return converterFactory.createConverter(casOutput.getTracks(), sampleRate, filePath).convert();
    }

    /**
     * Convert a list of CAS files to WAV format
     *
     * @param casList List of CAS files
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    public WavOutput convert(List<CasFile> casList) throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        Converter converter = converterFactory.createConverter(casList, sampleRate);
        return converter.convert();
    }

    /**
     * Convert all added files to WAV format (via CAS conversion)
     *
     * @return List of WAV outputs
     * @throws FlowException If there's an error during conversion
     */
    public List<WavOutput> convert() throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        final List<WavOutput> wavList = new LinkedList<>();
        for (String fileName : fileList) {
            wavList.add(convert(fileName));
        }
        return wavList;
    }
}
