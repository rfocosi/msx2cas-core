package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Interface for file converters that transform input files into a unified {@link TapeImage}.
 */
public interface Converter {

    /**
     * Convert the input file into a format-agnostic {@link TapeImage}.
     *
     * @return Unified {@link TapeImage}
     * @throws FlowException If reading or converting the input fails
     */
    TapeImage convertToTape() throws FlowException;
}
