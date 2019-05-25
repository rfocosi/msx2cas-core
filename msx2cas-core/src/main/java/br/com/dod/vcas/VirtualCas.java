package br.com.dod.vcas;

import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.wav.Ascii;
import br.com.dod.vcas.wav.Bas;
import br.com.dod.vcas.wav.Bin;
import br.com.dod.vcas.wav.Cas;
import br.com.dod.vcas.wav.Rom;
import br.com.dod.vcas.wav.Wav;

public class VirtualCas {

    private List<String> fileList;

    private SampleRate sampleRate;

    public VirtualCas(SampleRate sampleRate) {
        this.fileList = new LinkedList<>();

        this.sampleRate = sampleRate;
    }

    public VirtualCas addFile(String filePath) {
        this.fileList.add(filePath);
        return this;
    }

    public Wav convert(String filePath) throws FlowException, Exception {
        return addFile(filePath).convert().get(0);
    }

    public Wav convert(List<CasFile> casList) throws FlowException {
        if (casList == null || casList.size() < 1) throw FlowException.error("cas_list_empty");
        Wav wav = new Cas(casList.get(0).getName(), sampleRate, casList);
        return wav.convert();
    }

    public List<Wav> convert() throws FlowException, Exception {
        final List<Wav> wavList = new LinkedList<>();

        for (String fileName : fileList) {
            switch (FileCommons.detectFile(fileName)) {
                case BAS:
                    wavList.add(new Bas(fileName, sampleRate).convert());
                    break;
                case BIN:
                    wavList.add(new Bin(fileName, sampleRate).convert());
                    break;
                case CAS:
                    wavList.add(new Cas(fileName, sampleRate).convert());
                    break;
                case ROM:
                    wavList.add(new Rom(fileName, sampleRate).convert());
                    break;
                default:
                    wavList.add(new Ascii(fileName, sampleRate).convert());
            }
        }
        return wavList;
    }
}
