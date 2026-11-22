package br.com.dod.vcas.cas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.util.FileCommons;

/**
 * {@link CasOutput} serializer exporting validated MSX-compatible {@link TapeImage} tracks
 * or {@link CasFile} lists to CAS bytes.
 */
public class CasEncoder implements CasOutput {

    private final List<CasFile> tracks;
    private final byte[] casBytes;
    private final String casName;

    /**
     * Create a {@link CasEncoder} from a {@link TapeImage}, failing fast if non-CAS blocks exist.
     *
     * @param tapeImage Source tape image
     * @throws FlowException If the tape is empty or contains non-MSX-KCS blocks
     */
    public CasEncoder(TapeImage tapeImage) throws FlowException {
        this.casBytes = CasUtil.fromTapeToBytes(tapeImage);
        this.tracks = new CasUtil(this.casBytes).list();
        this.casName = tapeImage.casName();
    }

    /**
     * Create a {@link CasEncoder} wrapping an existing list of CAS tracks.
     *
     * @param tracks Existing CAS tracks
     * @param inputFileName Name of the source file
     */
    public CasEncoder(List<CasFile> tracks, String inputFileName) {
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<>();
        this.casBytes = CasUtil.toBytes(this.tracks);
        this.casName = inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
    }

    @Override
    public List<CasFile> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public byte[] toBytes() {
        return casBytes.clone();
    }

    @Override
    public String casName() {
        return casName;
    }
}
