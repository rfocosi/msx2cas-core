package br.com.dod.vcas.wav;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

public class Bin extends Wav {

    private static final int FILE_OFFSET = 7;

    public Bin(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
    }

    @Override
    protected void encodeFileContent() throws FlowException {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.BIN.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

        encodeShortHeader();

        encodeLoader(getLoader());

        for (int i = FILE_OFFSET; i < inputMemPointer.length; i++) {
            writeDataByte((char) inputMemPointer[i]);
        }
    }

    private void encodeLoader(char[] loader) throws FlowException {
        encodeData(buildBinaryAddressBuffer(sizeof(loader) + inputMemPointer.length - FILE_OFFSET));

        calculateBinCRC(loader);

        addStartAddressToLoader(loader);

        encodeData(loader);
    }

    private void addStartAddressToLoader(char[] loader) {
        for (int j = 0; j < 6; j++) {
            loader[j + 3] = (char) inputMemPointer[j + 1];
        }
    }

    private void calculateBinCRC(char[] loader) throws FlowException {
        char binBegin = (char) ((new DWORD(inputMemPointer[2]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[1]).getLow()).intValue());
        char binEnd = (char) ((new DWORD(inputMemPointer[4]).getLow()).intValue() * 0x100 + (new DWORD(inputMemPointer[3]).getLow()).intValue());

        loader[9] = calculateCRC(FILE_OFFSET, (binEnd-binBegin) + FILE_OFFSET);
    }

    private char[] getLoader() {
        return FileCommons.getLoader("BIN.bin");
    }
}
