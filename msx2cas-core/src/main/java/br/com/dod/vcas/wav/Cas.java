package br.com.dod.vcas.wav;

import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;

public class Cas extends Wav { 

	public Cas(String inputFileName, SampleRate sampleRate) throws FlowException {
		super(inputFileName, sampleRate, new DWORD(7), null, null);
	}

	public Cas(String inputFileName, SampleRate sampleRate, List<CasFile> casList) throws FlowException {
		super(inputFileName, sampleRate, new DWORD(7), null, casList);
	}

	@Override
	protected void validate() {
		if (casList == null || casList.isEmpty()) this.casList = new CasUtil(this.inputMemPointer).list();
	}
	
	@Override
	protected void setup() {

		CasFile firstFile = casList.get(0);

		this.fileHeader = firstFile.getHeader();
		this.nameBuffer[0] = firstFile.getName();
		this.extraBytes = new DWORD(0);

		int remainingFiles = casList.size()-1; // The first file PAUSES are calculated on setDefaultHeader

		int mExtraBytes = new DWORD(Math.round(wavSampleRate * SHORT_HEADER_LENGTH)).intValue();
		
		for (int f=1; f <= remainingFiles; f++) {
			mExtraBytes += new DWORD(Math.round(wavSampleRate * (LONG_HEADER_LENGTH + SEPARATOR_PAUSE_LENGTH))).intValue();
			CasFile file = casList.get(f);
			if (!"".equals(file.getName())) {
				mExtraBytes += new DWORD(Math.round(wavSampleRate * (DEFAULT_PAUSE_LENGTH + SHORT_HEADER_LENGTH))).intValue();
			}
		}
		this.moreExtraBytes = new DWORD(mExtraBytes);

	}

	@Override
	protected void encodeFileContent() {

		CasFile firstFile = casList.get(0);

		encodeShortHeader();		
		Byte[] content = firstFile.getContent();
		for (Byte value : content) {
			writeDataByte((char) value.byteValue());
		}

		for (int j=1; j < casList.size(); j++) {
			CasFile file = casList.get(j);
			encodePause(SEPARATOR_PAUSE_LENGTH);
			encodeLongHeader();
			if (!"".equals(file.getName())) {
				encodeData(file.getHeader());
				encodeData(file.getName().toCharArray());
				encodePause(DEFAULT_PAUSE_LENGTH);
				encodeShortHeader();
			}

			content = file.getContent();
			for (Byte aByte : content) {
				writeDataByte((char) aByte.byteValue());
			}
		}
	}
}
