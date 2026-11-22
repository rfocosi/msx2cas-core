package br.com.dod.vcas.converter.impl;

import br.com.dod.vcas.converter.AbstractFileConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.tsx.TsxParser;

/**
 * Converter for TSX (TZX 1.21) tape files into {@link TapeImage}, preserving native embedded
 * T-state timings when {@code speed} is {@code null} or re-timing {@code #4B} KCS blocks when specified.
 */
public class TsxConverter extends AbstractFileConverter {

    private final boolean turbo;

    /**
     * Constructor preserving native embedded TSX T-state timings.
     *
     * @param inputFileName Path to the {@code .tsx} file
     * @throws FlowException If reading the file fails
     */
    public TsxConverter(String inputFileName) throws FlowException {
        this(inputFileName, null, false);
    }

    /**
     * Constructor with optional {@link TapeSpeed} override for {@code #4B} blocks.
     *
     * @param inputFileName Path to the {@code .tsx} file
     * @param speed Optional speed override ({@code null} preserves native TSX timings)
     * @throws FlowException If reading the file fails
     */
    public TsxConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        this(inputFileName, speed, false);
    }

    /**
     * Constructor with optional {@link TapeSpeed} override and turbo check.
     *
     * @param inputFileName Path to the {@code .tsx} file
     * @param speed Optional speed override ({@code null} preserves native TSX timings)
     * @param turbo Rejects TSX conversion with {@code tsx_turbo_retime_not_supported} when {@code true}
     * @throws FlowException If reading the file fails
     */
    public TsxConverter(String inputFileName, TapeSpeed speed, boolean turbo) throws FlowException {
        super(inputFileName, speed);
        this.turbo = turbo;
    }

    @Override
    protected void validateMinFileSize() {
        // TsxParser performs full header and length validation.
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        if (turbo) {
            throw FlowException.error("tsx_turbo_retime_not_supported");
        }
        TapeImage image = TsxParser.parse(inputMemPointer, inputFileName);
        return speed != null ? image.retimeKcs(speed) : image;
    }
}
