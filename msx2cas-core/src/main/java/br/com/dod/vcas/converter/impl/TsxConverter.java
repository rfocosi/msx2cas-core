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

    /**
     * Constructor preserving native embedded TSX T-state timings.
     *
     * @param inputFileName Path to the {@code .tsx} file
     * @throws FlowException If reading the file fails
     */
    public TsxConverter(String inputFileName) throws FlowException {
        this(inputFileName, null);
    }

    /**
     * Constructor with optional {@link TapeSpeed} override for {@code #4B} blocks.
     *
     * @param inputFileName Path to the {@code .tsx} file
     * @param speed Optional speed override ({@code null} preserves native TSX timings)
     * @throws FlowException If reading the file fails
     */
    public TsxConverter(String inputFileName, TapeSpeed speed) throws FlowException {
        super(inputFileName, speed);
    }

    @Override
    protected void validateMinFileSize() {
        // TsxParser performs full header and length validation.
    }

    @Override
    public TapeImage convertToTape() throws FlowException {
        TapeImage image = TsxParser.parse(inputMemPointer, inputFileName);
        return speed != null ? image.retimeKcs(speed) : image;
    }
}
