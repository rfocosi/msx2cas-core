package br.com.dod.vcas.wav;

import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;

public class Cas extends Wav {

    private List<CasFile> casList;

    public Cas(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        this.casList = new CasUtil(this.inputMemPointer).list();
    }

    public Cas(List<CasFile> casList, SampleRate sampleRate) {
        super(sampleRate);
        this.casList = casList;
    }

    @Override
    protected void encodeFileContent() {
        CasFile firstFile = casList.get(0);

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(firstFile.getHeader());
        encodeData(firstFile.getName().toCharArray());

        encodePause(DEFAULT_PAUSE_LENGTH);

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
