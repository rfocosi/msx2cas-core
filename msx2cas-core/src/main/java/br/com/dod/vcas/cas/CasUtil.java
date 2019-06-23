package br.com.dod.vcas.cas;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.exception.FlowException;

import static br.com.dod.vcas.wav.Wav.CAS_FILENAME_LENGTH;

public class CasUtil {
    private static final char[] HEADER = FileType.CAS.getId();

    private byte[] inputHandler;

    public CasUtil(String filename) throws FlowException {
        this(new File(filename));
    }

    public CasUtil(File file) throws FlowException {
        this(FileCommons.readFile(file));
    }

    public CasUtil(byte[] inputHandler) {
        this.inputHandler = inputHandler;
    }

    public List<CasFile> list() {
        List<CasFile> casFileList = new LinkedList<>();
        new DWORD(0);

        int i = 0;
        while ((i = nextHeader(i, inputHandler)) > -1) {

            i+=HEADER.length;
            CasFile casFile = new CasFile();

            if (findType(FileType.ASCII.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.ASCII);
                i+=casFile.getHeader().length;
            } else if (findType(FileType.BAS.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.BAS);
                i+=casFile.getHeader().length;
            } else if (findType(FileType.BIN.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.BIN);
                i+=casFile.getHeader().length;
            } else {
                casFile.setFileType(FileType.DATA);
            }

            String itemName = "";
            if (!FileType.DATA.equals(casFile.getFileType())) {
                for (int n=i; n < i+CAS_FILENAME_LENGTH; n++) {
                    itemName += (char) inputHandler[n];
                }
                i = nextHeader(i, inputHandler) + HEADER.length;
                i = (i == -1 ? inputHandler.length : i);
            }
            casFile.setName(itemName);

            List<Byte> content = new LinkedList<>();

            int nextHeader = nextHeader(i, inputHandler);
            nextHeader = (nextHeader == -1 ? inputHandler.length : nextHeader);

            for (int p=i; p < nextHeader ; p++) {
                content.add(inputHandler[p]);
            }
            casFile.setContent(content.toArray(new Byte[content.size()]));

            casFileList.add(casFile);
        }

        return casFileList;
    }

    private int nextHeader(int curPos, byte[] inputHandler) {
        for (int h=curPos; h < inputHandler.length - HEADER.length; h++) {
            boolean headerFound = true;
            for (int c=0; c < HEADER.length; c++) {
                if (new DWORD(inputHandler[h+c]).getLow().intValue() != HEADER[c]) {
                    headerFound = false;
                    break;
                }
            }
            if (headerFound) return h;
        }
        return -1;
    }

    private boolean findType(char[] header, byte[] inputFile, int idx) {
        for (int c=0; c < header.length; c++) {
            if (new DWORD(inputFile[idx+c]).getLow().intValue() != header[c]) {
                return false;
            }
        }
        return true;
    }
}
