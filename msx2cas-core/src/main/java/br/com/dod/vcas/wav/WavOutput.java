package br.com.dod.vcas.wav;

/**
 * Interface representing a WAV output that can be converted to bytes
 */
public interface WavOutput {
    /**
     * Convert the WAV output to a byte array
     * @return Byte array representation of the WAV file
     */
    byte[] toBytes();
    
    /**
     * Get the name of the file in CAS format
     * @return CAS format name
     */
    String casName();
}
