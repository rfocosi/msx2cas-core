package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;

class ConvertFile {
    private String inputName;
    private String outputName;
    private SampleRate sampleRate;
    private boolean write;

    ConvertFile(String inputName, String outputName, SampleRate sampleRate, boolean write) {
        this.inputName = inputName;
        this.outputName = outputName;
        this.sampleRate = sampleRate;
        this.write = write;
    }

    String getInputName() {
        return inputName;
    }
    String getOutputName() {
        return outputName;
    }

    SampleRate getSampleRate() {
        return sampleRate;
    }

    boolean isWrite() {
        return write;
    }
}
