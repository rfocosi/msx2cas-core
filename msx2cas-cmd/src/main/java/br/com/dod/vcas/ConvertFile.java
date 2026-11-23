package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;

enum OutputMode {
    PLAY,
    WAV,
    CAS,
    TSX
}

record ConvertFile(
        String inputName,
        String outputName,
        TapeSpeed tapeSpeed,
        SampleRate sampleRate,
        boolean inverted,
        boolean turbo,
        OutputMode outputMode) {
}
