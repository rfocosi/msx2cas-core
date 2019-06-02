package br.com.dod.vcas.model;

public enum SampleRate {
    sr11025(11025),
    sr22050(22050),
    sr33075(33075);
//    ,
//    sr44100(44100), sr55125(55125), sr66150(66150),
//    sr77175(77175), sr88200(88200), sr99225(99225),
//    sr110250(110250), sr121275(121275), sr132300(132300);

    private int sampleRate;
    private boolean inverted;

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

    public int intValue(){
        return this.sampleRate;
    }

    public int bps() {
        return (int) (this.sampleRate / 9.1875);
    }

    public static SampleRate fromBps(final Object bps) {
        int bpsi = Integer.parseInt(bps.toString());
        for (SampleRate sampleRate : SampleRate.values()) {
            if (sampleRate.bps() == bpsi) return sampleRate;
        }
        return null;
    }

    public boolean isInverted() {
        return inverted;
    }

    public static SampleRate fromInt(int sampleRate) {
        return SampleRate.valueOf("sr"+sampleRate);
    }
}
