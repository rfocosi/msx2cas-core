package br.com.dod.vcas.wav;

import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;

public class Ascii extends Wav {

    public Ascii(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate);
        setup();
    }

    private void setup() throws FlowException {

        fixFileNewLines();

        for (int i = 0; i < getFileSize(); i++) {
            if (inputMemPointer[i] == 0xa && inputMemPointer[i-1] != 0xd) {
                throw FlowException.error("wrong_char_sequence");
            }

            if (!((char) inputMemPointer[i] >= ' ' ||
                    inputMemPointer[i] <= 2 ||
                    inputMemPointer[i] == 0xa ||
                    inputMemPointer[i] == 0xd ||
                    inputMemPointer[i] == 0x1a)
            ) {
                throw FlowException.error("invalid_file_format");
            }
        }
    }

    @Override
    protected void encodeFileContent() {

        encodePause(FIRST_PAUSE_LENGTH);

        encodeLongHeader();

        encodeData(FileType.ASCII.getHeader());
        encodeData(getNameBuffer());

        encodePause(DEFAULT_PAUSE_LENGTH);

        long fileLength = inputMemPointer.length;

        long b = 256;
        if (fileLength <= b) b = fileLength;

        // Encode 256 bytes + header chunks of data
        for (int i = 0; ; i += 256) {
            encodeShortHeader();

            // Encode 256 (or less) bytes
            for (int a = 0; a < b; a++)	{
                writeDataByte((char) inputMemPointer[i + a]);
            }

            // Check if whole file is written
            if (fileLength <= 256) break;
            fileLength = fileLength - 256;
            if (fileLength <= 256) b = fileLength;
        }

        encodeFinalize();
    }

    private void encodeFinalize() {
        for (int i = 0; i < 256; i++) {
            writeDataByte((char) 0x1a);	// Encode 256 bytes at the end of ascii program
        }
    }

    private void fixFileNewLines() {
        final List<Byte> convertedContent = new LinkedList<>();
        final byte[] inputClone = inputMemPointer.clone();

        for (int i =0 ; i < inputClone.length; i++) {
            if (inputMemPointer[i] == 0xa && inputMemPointer[i-1] != 0xd) {
                convertedContent.add(Byte.valueOf("13"));
                convertedContent.add(Byte.valueOf("10"));
            } else {
                convertedContent.add(inputClone[i]);
            }
        }

        inputMemPointer = new byte[convertedContent.size()];

        for (int idx = 0 ; idx < convertedContent.size() ; idx++ ) {
            inputMemPointer[idx] = convertedContent.get(idx);
        }
    }
}
