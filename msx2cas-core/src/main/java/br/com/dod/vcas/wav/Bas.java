package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;

public class Bas extends Wav {

	private static final char[] basicFileHeader = {0xd3, 0xd3, 0xd3, 0xd3, 0xd3, 0xd3, 0xd3, 0xd3, 0xd3, 0xd3};

	public Bas(String inputFileName, SampleRate sampleRate) throws FlowException {
		super(inputFileName, sampleRate, new DWORD(1), basicFileHeader);
	}

	@Override
	protected void validate() throws FlowException {
		if (this.fileLength < MIN_ENC_INPUTFILE_LENGTH || this.fileLength > MAX_ENC_INPUTFILE_LENGTH) throw FlowException.error("file_size_invalid");
	}
	
	@Override
	protected void setup() {
		this.extraBytes = new DWORD(7);
		this.moreExtraBytes = new DWORD(0);
	}

	@Override
	protected void encodeFileContent() {
		encodeShortHeader();
		
		for (int i = fileOffset.intValue(); i < inputMemPointer.length; i++) {
			writeDataByte((char)inputMemPointer[i]);
		}
		
		encodeFinalize();
	}

	private void encodeFinalize() {
		for (int i = 0; i < 7; i++) {
			writeDataByte((char) 0x00);
		}
	}
}
