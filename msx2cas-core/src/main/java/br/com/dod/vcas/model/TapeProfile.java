package br.com.dod.vcas.model;

import java.util.Locale;

/**
 * Predefined conversion profiles grouping common {@link TapeSpeed}, {@link SampleRate}, and turbo
 * defaults. Individual parameters remain overridable via advanced options.
 */
public enum TapeProfile {
    COMPATIBLE("compatible", TapeSpeed.BPS_1200, SampleRate.sr44100, false),
    DEFAULT("default", TapeSpeed.BPS_2400, SampleRate.sr44100, false),
    FASTEST("fastest", TapeSpeed.BPS_2400, SampleRate.sr48000, true);

    private final String token;
    private final TapeSpeed tapeSpeed;
    private final SampleRate sampleRate;
    private final boolean turbo;

    TapeProfile(String token, TapeSpeed tapeSpeed, SampleRate sampleRate, boolean turbo) {
        this.token = token;
        this.tapeSpeed = tapeSpeed;
        this.sampleRate = sampleRate;
        this.turbo = turbo;
    }

    /**
     * CLI/API profile identifier token.
     *
     * @return Profile token ({@code compatible}, {@code default}, or {@code fastest})
     */
    public String token() {
        return token;
    }

    /**
     * Default {@link TapeSpeed} for this profile.
     *
     * @return Profile {@link TapeSpeed}
     */
    public TapeSpeed tapeSpeed() {
        return tapeSpeed;
    }

    /**
     * Default PCM/WAV {@link SampleRate} for this profile.
     *
     * @return Profile {@link SampleRate}
     */
    public SampleRate sampleRate() {
        return sampleRate;
    }

    /**
     * Whether Stage-2 Z80 PWM turbo mode is enabled by default for ROM and BIN files.
     *
     * @return {@code true} if turbo mode is enabled
     */
    public boolean turbo() {
        return turbo;
    }

    /**
     * Parse a CLI/API profile token ({@code compatible}, {@code default}, or {@code fastest}).
     *
     * @param token Profile string token (case-insensitive)
     * @return Matching {@link TapeProfile}
     * @throws IllegalArgumentException If the token is blank or unrecognized
     */
    public static TapeProfile fromToken(String token) {
        TapeProfile parsed = tryFromToken(token);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid tape profile: " + token);
        }
        return parsed;
    }

    /**
     * Try to parse a CLI/API profile token without throwing.
     *
     * @param token Profile string token (case-insensitive)
     * @return Matching {@link TapeProfile}, or {@code null} when unrecognized
     */
    public static TapeProfile tryFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (TapeProfile profile : values()) {
            if (profile.token.equals(normalized)) {
                return profile;
            }
        }
        return null;
    }
}
