package br.com.dod.vcas.model;

/**
 * Tape baud rate and pulse timing profiles anchored to the standard 3.5 MHz Z80 TZX clock.
 */
public enum TapeSpeed {
    BPS_1200(1200, 729, 1458, 729),
    BPS_2400(2400, 365, 729, 365),
    BPS_3000(3000, 329, 658, 329);

    /** TZX/TSX reference clock frequency in Hz (1 T-state = 1 / 3,500,000 s). */
    public static final long TSTATES_PER_SECOND = 3_500_000L;

    /** Stage-2 Z80 turbo PWM half-cycle pulse lengths in 3.5 MHz T-states. */
    public static final int TURBO_PILOT_PULSE = 318;
    public static final int TURBO_PILOT_PULSES = 1024;
    public static final int TURBO_SYNC1_PULSE = 636;
    public static final int TURBO_SYNC2_PULSE = 636;
    public static final int TURBO_ZERO_PULSE = 318;
    public static final int TURBO_ONE_PULSE = 636;
    public static final int TURBO_SYNC_BYTE = 0xA5;

    private final int bps;
    private final int pilotPulseTStates;
    private final int zeroPulseTStates;
    private final int onePulseTStates;

    TapeSpeed(int bps, int pilotPulseTStates, int zeroPulseTStates, int onePulseTStates) {
        this.bps = bps;
        this.pilotPulseTStates = pilotPulseTStates;
        this.zeroPulseTStates = zeroPulseTStates;
        this.onePulseTStates = onePulseTStates;
    }

    /**
     * Nominal transfer rate in bits per second.
     *
     * @return Speed in bps
     */
    public int bps() {
        return bps;
    }

    /**
     * Pilot half-cycle pulse length in 3.5 MHz T-states.
     *
     * @return Pilot pulse duration in T-states
     */
    public int pilotPulseTStates() {
        return pilotPulseTStates;
    }

    /**
     * Logical {@code 0} bit half-cycle pulse length in 3.5 MHz T-states.
     *
     * @return Zero-bit pulse duration in T-states
     */
    public int zeroPulseTStates() {
        return zeroPulseTStates;
    }

    /**
     * Logical {@code 1} bit half-cycle pulse length in 3.5 MHz T-states.
     *
     * @return One-bit pulse duration in T-states
     */
    public int onePulseTStates() {
        return onePulseTStates;
    }

    /**
     * Parse a CLI/API speed token ({@code 1200}, {@code 2400}, or {@code 3000}).
     *
     * @param token Speed string token
     * @return Matching {@link TapeSpeed}
     * @throws IllegalArgumentException If the token is null or unsupported
     */
    public static TapeSpeed fromToken(String token) {
        TapeSpeed parsed = tryFromToken(token);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid tape speed: " + token);
        }
        return parsed;
    }

    /**
     * Try to parse a CLI/API speed token without throwing.
     *
     * @param token Speed string token
     * @return Matching {@link TapeSpeed}, or {@code null} when unrecognized
     */
    public static TapeSpeed tryFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        if ("1200".equals(trimmed)) {
            return BPS_1200;
        }
        if ("2400".equals(trimmed)) {
            return BPS_2400;
        }
        if ("3000".equals(trimmed)) {
            return BPS_3000;
        }
        return null;
    }
}
