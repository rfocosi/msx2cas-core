package br.com.dod.vcas.converter.impl;

import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for CAS files
 */
public class CasConverter extends AbstractFileConverter {

    private final List<CasFile> casFiles;

    /**
     * Constructor for CasConverter
     *
     * @param inputFileName Name of the input file
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error reading the file
     */
    public CasConverter(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        this.casFiles = new CasUtil(this.inputMemPointer).list();
        if (this.casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS files
     *
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error with the CAS files
     */
    public CasConverter(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException {
        super(sampleRate);
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        this.casFiles = new ArrayList<>(casFiles);
    }

    @Override
    public WavOutput convert() throws FlowException {
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);

        encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);

        for (int i = 0; i < casFiles.size(); i++) {
            CasFile casFile = casFiles.get(i);
            if (i > 0) {
                encoder.encodePause(WavEncoder.SEPARATOR_PAUSE_LENGTH);
            }
            encodeFile(encoder, casFile, i == 0);
        }

        encoder.finish();

        return encoder;
    }

    private void encodeFile(WavEncoder encoder, CasFile casFile, boolean isFirstTrack) {
        encoder.encodeLongHeader();

        boolean hasName = casFile.getName() != null && !casFile.getName().isEmpty();
        if (isFirstTrack || hasName) {
            encoder.encodeData(casFile.getHeader());
            encoder.encodeData(hasName ? casFile.getName().toCharArray() : new char[0]);
            encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
            encoder.encodeShortHeader();
        }

        Byte[] content = casFile.getContent();
        if (content != null) {
            for (Byte b : content) {
                encoder.writeDataByte((char) (b & 0xFF));
            }
        }
    }
}
