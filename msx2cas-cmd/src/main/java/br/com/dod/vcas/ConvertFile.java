package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;

record ConvertFile(String inputName, String outputName, SampleRate sampleRate, boolean write, boolean writeCas) {

    ConvertFile(String inputName, String outputName, SampleRate sampleRate, boolean write) {
        this(inputName, outputName, sampleRate, write, false);
    }
}
