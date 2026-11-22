package br.com.dod.vcas.model;

/**
 * Immutable audio PCM sample rates supported for WAV export and live playback.
 */
public enum SampleRate {
    sr11025(11025),
    sr22050(22050),
    sr33075(33075),
    sr44100(44100),
    sr48000(48000);

    /** Default PCM/WAV output sample rate (44100 Hz). */
    public static final SampleRate DEFAULT = sr44100;

    private final int sampleRate;

    SampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Sample rate in Hz.
     *
     * @return Sample rate in samples per second
     */
    public int intValue() {
        return sampleRate;
    }

    /**
     * Resolve an exact {@link SampleRate} by Hz, throwing on unsupported values.
     *
     * @param hz Sample rate in Hz
     * @return Matching {@link SampleRate}
     * @throws IllegalArgumentException If the sample rate is unsupported
     */
    public static SampleRate fromHz(int hz) {
        SampleRate resolved = tryFromHz(hz);
        if (resolved == null) {
            throw new IllegalArgumentException("Unsupported sample rate: " + hz);
        }
        return resolved;
    }

    /**
     * Try resolving a {@link SampleRate} by Hz without throwing.
     *
     * @param hz Sample rate in Hz
     * @return Matching {@link SampleRate}, or {@code null} if unsupported
     */
    public static SampleRate tryFromHz(int hz) {
        for (SampleRate rate : values()) {
            if (rate.sampleRate == hz) {
                return rate;
            }
        }
        return null;
    }

    /**
     * Parse a string sample rate in Hz (e.g. {@code "44100"}).
     *
     * @param value Decimal sample rate string
     * @return Matching {@link SampleRate}
     * @throws IllegalArgumentException If the string is invalid or unsupported
     */
    public static SampleRate fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sample rate cannot be blank");
        }
        try {
            return fromHz(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sample rate: " + value, e);
        }
    }
}
