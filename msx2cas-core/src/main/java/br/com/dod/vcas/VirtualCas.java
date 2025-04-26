package br.com.dod.vcas;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.converter.Converter;
import br.com.dod.vcas.converter.DefaultFileConverterFactory;
import br.com.dod.vcas.converter.FileConverterFactory;
import br.com.dod.vcas.converter.impl.CasConverter;
import br.com.dod.vcas.converter.impl.RomConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Main class for the MSX2CAS core library
 * Provides functionality to convert MSX files to WAV format
 */
public class VirtualCas {

    private final List<String> fileList = new ArrayList<>();
    private final SampleRate sampleRate;
    private final FileConverterFactory converterFactory;
    private boolean reset;

    /**
     * Constructor for VirtualCas
     * 
     * @param sampleRate Sample rate to use for conversion
     */
    public VirtualCas(SampleRate sampleRate) {
        this(sampleRate, new DefaultFileConverterFactory());
    }
    
    /**
     * Constructor for VirtualCas with a custom converter factory
     * 
     * @param sampleRate Sample rate to use for conversion
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
     * Convert a single file to WAV format
     * 
     * @param filePath Path to the file
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    public WavOutput convert(String filePath) throws FlowException {
        return addFile(filePath).convert().get(0);
    }

    /**
     * Convert a list of CAS files to WAV format
     * 
     * @param casList List of CAS files
     * @return WAV output
     * @throws FlowException If there's an error during conversion
     */
    public WavOutput convert(List<CasFile> casList) throws FlowException {
        Converter converter = new CasConverter(casList, sampleRate);
        return converter.convert();
    }

    /**
     * Convert all added files to WAV format
     * 
     * @return List of WAV outputs
     * @throws FlowException If there's an error during conversion
     */
    public List<WavOutput> convert() throws FlowException {
        final List<WavOutput> wavList = new LinkedList<>();

        for (String fileName : fileList) {
            Converter converter = converterFactory.createConverter(fileName, sampleRate);
            
            // Apply ROM reset if this is a ROM converter
            if (converter instanceof RomConverter) {
                ((RomConverter) converter).resetRom(reset);
            }
            
            wavList.add(converter.convert());
        }
        return wavList;
    }
}
