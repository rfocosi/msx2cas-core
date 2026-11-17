package br.com.dod.vcas.cas;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.vcas.util.FileCommons;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.exception.FlowException;

import static br.com.dod.vcas.util.FileCommons.CAS_FILENAME_LENGTH;

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
        boolean inAsciiTrack = false;

        int i = 0;
        while ((i = nextHeader(i, inputHandler)) > -1) {

            i += HEADER.length;
            CasFile casFile = new CasFile();

            if (findType(FileType.ASCII.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.ASCII);
                i += casFile.getHeader().length;
                inAsciiTrack = true;
            } else if (findType(FileType.BAS.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.BAS);
                i += casFile.getHeader().length;
                inAsciiTrack = false;
            } else if (findType(FileType.BIN.getHeader(), inputHandler, i)) {
                casFile.setFileType(FileType.BIN);
                i += casFile.getHeader().length;
                inAsciiTrack = false;
            } else {
                casFile.setFileType(FileType.DATA);
                if (inAsciiTrack) {
                    casFile.setAsciiContinuation(true);
                }
            }

            StringBuilder itemName = new StringBuilder();
            if (!FileType.DATA.equals(casFile.getFileType())) {
                int nameEnd = Math.min(i + CAS_FILENAME_LENGTH, inputHandler.length);
                for (int n = i; n < nameEnd; n++) {
                    itemName.append((char) inputHandler[n]);
                }
                int dataHeader = nextHeader(i, inputHandler);
                i = (dataHeader == -1 ? inputHandler.length : dataHeader + HEADER.length);
            }
            casFile.setName(itemName.toString());

            int nextHeader = nextHeader(i, inputHandler);
            nextHeader = (nextHeader == -1 ? inputHandler.length : nextHeader);

            int contentLen = Math.max(0, nextHeader - i);
            byte[] content = new byte[contentLen];
            System.arraycopy(inputHandler, i, content, 0, contentLen);
            casFile.setContentBytes(content);

            casFileList.add(casFile);
        }

        return casFileList;
    }

    /**
     * Serialize a list of {@link CasFile} tracks into raw CAS container bytes.
     *
     * @param casFiles Tracks to serialize
     * @return Serialized CAS file bytes
     */
    public static byte[] toBytes(List<CasFile> casFiles) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (casFiles == null) {
            return out.toByteArray();
        }
        for (CasFile casFile : casFiles) {
            if (!casFile.isAsciiContinuation()) {
                writeChars(out, HEADER);
                boolean hasName = casFile.getName() != null && !casFile.getName().isEmpty();
                if (hasName || !FileType.DATA.equals(casFile.getFileType())) {
                    writeChars(out, casFile.getHeader());
                    if (hasName) {
                        writeChars(out, casFile.getName().toCharArray());
                    }
                    writeChars(out, HEADER);
                }
            } else {
                writeChars(out, HEADER);
            }
            byte[] content = casFile.getContentBytes();
            if (content != null) {
                out.writeBytes(content);
            }
        }
        return out.toByteArray();
    }

    private static void writeChars(ByteArrayOutputStream out, char[] chars) {
        if (chars == null) {
            return;
        }
        for (char c : chars) {
            out.write(c & 0xFF);
        }
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
        if (idx + header.length > inputFile.length) {
            return false;
        }
        for (int c=0; c < header.length; c++) {
            if (new DWORD(inputFile[idx+c]).getLow().intValue() != header[c]) {
                return false;
            }
        }
        return true;
    }
}
