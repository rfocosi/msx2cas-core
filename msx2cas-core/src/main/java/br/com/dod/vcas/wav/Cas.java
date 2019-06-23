package br.com.dod.vcas.wav;

import java.util.List;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;

public class Cas extends Wav {

    private List<CasFile> casList;

    public Cas(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, null);
    }

    public Cas(List<CasFile> casList, SampleRate sampleRate) {
        super(sampleRate, null);
        this.casList = casList;
    }

    @Override
    protected void validate() {
        if (casList == null || casList.isEmpty()) this.casList = new CasUtil(this.inputMemPointer).list();
    }

    @Override
    protected void setup() {
        CasFile firstFile = casList.get(0);
        this.fileHeader = firstFile.getHeader();
        this.nameBuffer = firstFile.getName().toCharArray();
    }

    @Override
    protected void encodeFileContent() {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(fileHeader);
        encodeData(nameBuffer);

        encodePause(DEFAULT_PAUSE_LENGTH);

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
