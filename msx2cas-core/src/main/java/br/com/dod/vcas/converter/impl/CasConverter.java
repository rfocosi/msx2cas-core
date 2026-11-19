package br.com.dod.vcas.converter.impl;

import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.cas.CasEncoder;
import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Converter for CAS files (CAS parsing pass-through and CAS to WAV encoding)
 */
public class CasConverter extends AbstractFileConverter {

    private final List<CasFile> casFiles;

    /**
     * Constructor for CasConverter without WAV sample rate.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If there's an error reading the file
     */
    public CasConverter(String inputFileName) throws FlowException {
        this(inputFileName, null);
    }

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
     * Constructor for CasConverter with pre-loaded CAS files and no WAV sample rate.
     *
     * @param casFiles List of CAS files
     * @throws FlowException If there's an error with the CAS files
     */
    public CasConverter(List<CasFile> casFiles) throws FlowException {
        this(casFiles, null, null);
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS files
     *
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @throws FlowException If there's an error with the CAS files
     */
    public CasConverter(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException {
        this(casFiles, sampleRate, null);
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS files and retained input filename.
     *
     * @param casFiles List of CAS files
     * @param sampleRate Sample rate to use for conversion
     * @param inputFileName Original input file name
     * @throws FlowException If there's an error with the CAS files
     */
    public CasConverter(List<CasFile> casFiles, SampleRate sampleRate, String inputFileName) throws FlowException {
        super(sampleRate, inputFileName);
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        this.casFiles = new ArrayList<>(casFiles);
    }

    @Override
    public CasOutput convertToCas() throws FlowException {
        return new CasEncoder(this.casFiles, this.inputFileName);
    }

    @Override
    public WavOutput convert() throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        WavEncoder encoder = new WavEncoder(sampleRate, inputFileName);

        for (int i = 0; i < casFiles.size(); i++) {
            CasFile casFile = casFiles.get(i);
            encodeFile(encoder, casFile, i == 0);
        }

        encoder.finish();

        return encoder;
    }

    private void encodeFile(WavEncoder encoder, CasFile casFile, boolean isFirstTrack) {
        boolean turboTrack = casFile.hasTurboPayload();
        if (casFile.isAsciiContinuation()) {
            encoder.encodeShortHeader(turboTrack);
        } else {
            encoder.encodePause(WavEncoder.FIRST_PAUSE_LENGTH);
            encoder.encodeLongHeader(turboTrack);

            boolean hasName = casFile.getName() != null && !casFile.getName().isEmpty();
            if (isFirstTrack || hasName) {
                encoder.encodeData(casFile.getHeader(), turboTrack);
                encoder.encodeData(hasName ? casFile.getName().toCharArray() : new char[0], turboTrack);
                encoder.encodePause(WavEncoder.DEFAULT_PAUSE_LENGTH);
                encoder.encodeShortHeader(turboTrack);
            }
        }

        byte[] content = casFile.getContentBytes();
        if (content != null) {
            for (byte b : content) {
                encoder.writeDataByte((char) (b & 0xFF), turboTrack);
            }
        }
        if (turboTrack) {
            encoder.encodeTurboBlocks(casFile.getTurboPayloads());
        }
    }
}
