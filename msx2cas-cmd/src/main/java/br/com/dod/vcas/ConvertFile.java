package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;

record ConvertFile(String inputName, String outputName, SampleRate sampleRate, boolean write) {
}
