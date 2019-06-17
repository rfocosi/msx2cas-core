package br.com.dod.vcas.wav;

import java.util.LinkedList;
import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;

public class Ascii extends Wav {

    private static final char[] asciiFileHeader = {0xea, 0xea, 0xea, 0xea, 0xea, 0xea, 0xea, 0xea, 0xea, 0xea};

    public Ascii(String inputFileName, SampleRate sampleRate) throws FlowException {
        super(inputFileName, sampleRate, new DWORD(0), asciiFileHeader);
    }

    @Override
    protected void validate() throws FlowException {
        if (this.fileLength < MIN_ENC_INPUT_FILE_LENGTH) throw FlowException.error("file_size_invalid");
    }

    @Override
    protected void setup() throws FlowException {

        int b = 1;
        if (this.fileLength > 256) {
            long inputFileLengthTmp = this.fileLength;
            for (b = 2; ; b++) {
                inputFileLengthTmp = inputFileLengthTmp - 256;
                if (inputFileLengthTmp <= 256) break;
            }
        }

        this.pureSampleShortHeaderLength = SHORT_HEADER_LENGTH * b;

        this.extraBytes = new DWORD(256);
        this.moreExtraBytes = new DWORD(0);

        fixFileNewLines();

        for (int i = 0; i < this.fileLength; i++) {
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

        long fileLength = inputMemPointer.length;

        long b = 256;
        if (fileLength <= b) b = fileLength;

        // Encode 256 bytes + header chunks of data
        for (int i = fileOffset.intValue(); ; i += 256) {
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
        final byte[] filec = inputMemPointer.clone();

        for (int i =0 ; i < filec.length; i++) {
            if (inputMemPointer[i] == 0xa && inputMemPointer[i-1] != 0xd) {
                convertedContent.add(new Byte("13"));
                convertedContent.add(new Byte("10"));
            } else {
                convertedContent.add(filec[i]);
            }
        }

        inputMemPointer = new byte[convertedContent.size()];

        for (int idx = 0 ; idx < convertedContent.size() ; idx++ ) {
            inputMemPointer[idx] = convertedContent.get(idx);
        }
    }
}
