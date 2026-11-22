package br.com.dod.vcas.wav;

import java.io.IOException;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.util.WavHeader;

/**
 * {@link WavOutput} implementation backed by {@link TapePcmInputStream} and {@link WavHeader}.
 */
public class WavWriter implements WavOutput {

    private final byte[] wavBytes;
    private final String casName;

    /**
     * Render a {@link TapeImage} into a complete 8-bit unsigned mono WAV file at the default sample rate.
     *
     * @param tapeImage Source tape image
     * @throws FlowException If rendering fails
     */
    public WavWriter(TapeImage tapeImage) throws FlowException {
        this(tapeImage, SampleRate.DEFAULT, false);
    }

    /**
     * Render a {@link TapeImage} into a complete 8-bit unsigned mono WAV file.
     *
     * @param tapeImage Source tape image
     * @param sampleRate Target audio sample rate
     * @param inverted {@code true} to invert square-wave polarity
     * @throws FlowException If rendering fails or the PCM length overflows WAV 32-bit RIFF bounds
     */
    public WavWriter(TapeImage tapeImage, SampleRate sampleRate, boolean inverted) throws FlowException {
        SampleRate effectiveRate = sampleRate != null ? sampleRate : SampleRate.DEFAULT;
        try (TapePcmInputStream stream = new TapePcmInputStream(tapeImage, effectiveRate, inverted)) {
            long totalSamples = stream.getTotalSamples();
            if (totalSamples > Integer.MAX_VALUE - 44L) {
                throw FlowException.error("wav_size_overflow");
            }
            byte[] header = new WavHeader(effectiveRate, totalSamples).toBytes();
            byte[] out = new byte[header.length + (int) totalSamples];
            System.arraycopy(header, 0, out, 0, header.length);
            int offset = header.length;
            int read;
            while (offset < out.length && (read = stream.read(out, offset, out.length - offset)) != -1) {
                offset += read;
            }
            this.wavBytes = out;
            this.casName = stream.casName();
        } catch (IOException e) {
            throw FlowException.error("wav_write_error");
        }
    }

    @Override
    public byte[] toBytes() {
        return wavBytes.clone();
    }

    @Override
    public String casName() {
        return casName;
    }
}
