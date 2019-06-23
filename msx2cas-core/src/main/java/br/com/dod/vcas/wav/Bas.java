package br.com.dod.vcas.wav;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

public class Bas extends Wav {

    public Bas(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }

    @Override
    protected void encodeFileContent() {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.BAS.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeShortHeader();

        for (int i = 1; i < inputMemPointer.length; i++) {
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
