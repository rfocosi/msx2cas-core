package br.com.dod.vcas.converter;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.wav.WavOutput;

/**
 * Interface for file converters that transform different file types to WAV format
 */
public interface Converter {
    /**
     * Convert the input file to WAV format
     * @return The WAV output
     * @throws FlowException If there's an error during conversion
     */
    WavOutput convert() throws FlowException;
}
