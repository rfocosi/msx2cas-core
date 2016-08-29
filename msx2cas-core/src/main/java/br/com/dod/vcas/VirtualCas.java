package br.com.dod.vcas;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.wav.Ascii;
import br.com.dod.vcas.wav.Bas;
import br.com.dod.vcas.wav.Bin;
import br.com.dod.vcas.wav.Cas;
import br.com.dod.vcas.wav.Rom;
import br.com.dod.vcas.wav.Wav;
import br.com.dod.vcas.wav.Wav.SampleRate;

public class VirtualCas {

	public static final Logger log = Logger.getLogger(VirtualCas.class.getSimpleName());

	private List<String> fileList;

	private SampleRate sampleRate;

	public VirtualCas(SampleRate sampleRate) {
		this.fileList = new LinkedList<String>();

		this.sampleRate = sampleRate;
	}

	public VirtualCas addFile(String filePath) {
		this.fileList.add(filePath);
		return this;
	}

	public Wav convert(String filePath) throws FlowException, Exception {		
		return addFile(filePath).convert().get(0);
	}

	public Wav convert(List<CasFile> casList) throws FlowException, Exception {
		if (casList == null || casList.size() < 1) throw FlowException.error("cas_list_empty");
		Wav wav = new Cas(casList.get(0).getName(), sampleRate, casList);
		return wav.convert();
	}
	
	public List<Wav> convert() throws FlowException, Exception {
		List<Wav> wavList = new LinkedList<Wav>();

		for (String fileName : fileList) {
			Wav wav = null;
			switch (FileCommons.detectFile(fileName)) {
			case BAS:
				wav = new Bas(fileName, sampleRate);
				break;
			case BIN:
				wav = new Bin(fileName, sampleRate);
				break;
			case CAS:
				wav = new Cas(fileName, sampleRate);
				break;
			case ROM:
				wav = new Rom(fileName, sampleRate);
				break;
			default:
				wav = new Ascii(fileName, sampleRate);
			}
			if (wav != null) wavList.add(wav.convert());
		}
		return wavList;
	}
}
