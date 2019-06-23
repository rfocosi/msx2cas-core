package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

public class Bas extends Wav {

    public Bas(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, new DWORD(1), FileType.BAS.getHeader());
    }

    @Override
    protected void validate() throws FlowException {
        if (this.fileLength < MIN_ENC_INPUT_FILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    @Override
    void setup() {
    }

    @Override
    protected void encodeFileContent() {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer);

        encodePause(DEFAULT_PAUSE_LENGTH);

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
