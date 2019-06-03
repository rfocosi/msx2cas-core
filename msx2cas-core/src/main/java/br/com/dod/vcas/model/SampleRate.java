package br.com.dod.vcas.model;

public enum SampleRate {
    sr11025(11025),
    sr22050(22050),
    sr27563(27563),
    sr33075(33075);
//    ,sr44100(44100), sr55125(55125), sr66150(66150);
//    sr77175(77175), sr88200(88200), sr99225(99225),
//    sr110250(110250), sr121275(121275), sr132300(132300);

    private int sampleRate;
    private boolean inverted;

    private static final double BIT_ENCODING_BASE_LENGTH = 10.0;

    SampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public SampleRate invertWaveForm() {
        this.inverted = !this.inverted;
        return this;
    }

    public SampleRate invertWaveForm(boolean invert) {
        this.inverted = invert;
        return this;
    }

    public boolean isInverted() {
        return inverted;
    }

    public double bitEncodingLength() {
        return BIT_ENCODING_BASE_LENGTH / ((double) (intValue() / SampleRate.sr11025.intValue()));
    }

    public double headerEncodingLength(double length) {
        return (intValue() * length / BIT_ENCODING_BASE_LENGTH);
    }

    public long sampleScale() {
        return intValue() / SampleRate.sr11025.intValue();
    }

    public int intValue(){
        return this.sampleRate;
    }

    public int bps() {
        return (int) (intValue() / 9.1875);
    }

    public static SampleRate fromBps(final Object bps) {
        int bpsi = Integer.parseInt(bps.toString());
        for (SampleRate sampleRate : SampleRate.values()) {
            if (sampleRate.bps() == bpsi) return sampleRate;
        }
        return null;
    }

    public static SampleRate fromInt(int sampleRate) {
        return SampleRate.valueOf("sr"+sampleRate);
    }
}
