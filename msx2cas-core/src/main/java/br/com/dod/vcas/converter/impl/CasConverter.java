package br.com.dod.vcas.converter.impl;

import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Converter for MSX CAS files into unified {@link TapeImage}.
 */
public class CasConverter extends AbstractFileConverter {

    private final List<CasFile> casFiles;

    /**
     * Constructor for CasConverter using default {@link TapeSpeed#BPS_2400}.
     *
     * @param inputFileName Name of the input file
     * @throws FlowException If reading or parsing the CAS file fails
     */
    public CasConverter(String inputFileName) throws FlowException {
        this(inputFileName, TapeSpeed.BPS_2400);
    }

    /**
     * Constructor for CasConverter.
     *
     * @param inputFileName Name of the input file
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If reading or parsing the CAS file fails
     */
    public CasConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        super(inputFileName, speed);
        this.casFiles = new CasUtil(this.inputMemPointer).list();
        if (this.casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS tracks.
     *
     * @param casFiles List of CAS tracks
     * @throws FlowException If the CAS list is empty
     */
    public CasConverter(List<CasFile> casFiles) throws FlowException {
        this(casFiles, TapeSpeed.BPS_2400, null);
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS tracks and explicit {@link TapeSpeed}.
     *
     * @param casFiles List of CAS tracks
     * @param speed Target {@link TapeSpeed}
     * @throws FlowException If the CAS list is empty
     */
    public CasConverter(List<CasFile> casFiles, TapeSpeed speed) throws FlowException {
        this(casFiles, speed, null);
    }

    /**
     * Constructor for CasConverter with pre-loaded CAS tracks and retained input filename.
     *
     * @param casFiles List of CAS tracks
     * @param speed Target {@link TapeSpeed}
     * @param inputFileName Original input file name
     * @throws FlowException If the CAS list is empty
     */
    public CasConverter(List<CasFile> casFiles, TapeSpeed speed, String inputFileName) throws FlowException {
        super(speed, inputFileName);
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        this.casFiles = new ArrayList<>(casFiles);
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        return CasUtil.toTape(casFiles, effectiveSpeed(), inputFileName);
    }
}
