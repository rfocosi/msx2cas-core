package br.com.dod.vcas.cas;

import java.util.List;

/**
 * Interface representing a CAS output that holds tracks and can be serialized to bytes.
 */
public interface CasOutput {

    /**
     * Serialize the CAS tracks into a CAS byte array.
     *
     * @return Byte array representation of the CAS file
     */
    byte[] toBytes();

    /**
     * Get the name of the file in CAS format.
     *
     * @return CAS format name
     */
    String casName();

    /**
     * Get the ordered list of CAS tracks.
     *
     * @return List of {@link CasFile} tracks
     */
    List<CasFile> getTracks();
}
