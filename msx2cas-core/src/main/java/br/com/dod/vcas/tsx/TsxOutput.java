package br.com.dod.vcas.tsx;

import br.com.dod.vcas.tape.TapeImage;

/**
 * Output wrapper for serialized MSX TSX (TZX 1.21) tape files.
 */
public interface TsxOutput {

    /**
     * Serialize the tape image into TSX container bytes.
     *
     * @return Byte array representation of the TSX file
     */
    byte[] toBytes();

    /**
     * Get the short CAS/TSX display name derived from the input file.
     *
     * @return Short name (up to 6 characters)
     */
    String casName();

    /**
     * Get the underlying {@link TapeImage}.
     *
     * @return Underlying tape image
     */
    TapeImage getTapeImage();
}
