package br.com.dod.vcas.cas;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBuilder;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.util.FileCommons;

import static br.com.dod.vcas.util.FileCommons.CAS_FILENAME_LENGTH;

/**
 * Utilities for reading, writing, and converting MSX CAS containers to and from {@link TapeImage}.
 */
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
     * Convert parsed {@link CasFile} tracks into a {@link TapeImage} using {@code speed}.
     *
     * @param casFiles Ordered CAS tracks
     * @param speed Target {@link TapeSpeed} (defaults to {@link TapeSpeed#BPS_2400} if {@code null})
     * @param inputFileName Original filename for display name derivation
     * @return Assembled {@link TapeImage}
     * @throws FlowException If {@code casFiles} is null or empty
     */
    public static TapeImage toTape(List<CasFile> casFiles, TapeSpeed speed, String inputFileName) throws FlowException {
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        TapeBuilder builder = new TapeBuilder(inputFileName, speed);
        for (CasFile casFile : casFiles) {
            builder.addCasTrack(
                    casFile.getFileType(),
                    casFile.getName(),
                    casFile.getContentBytes(),
                    casFile.isAsciiContinuation());
        }
        return builder.build();
    }

    /**
     * Export a {@link TapeImage} into raw CAS container bytes.
     *
     * @param tapeImage Source tape image
     * @return Raw CAS bytes
     * @throws FlowException If the tape is empty or contains non-MSX-KCS blocks (e.g. {@code #10-#15}, {@code #19}, loops, turbo)
     */
    public static byte[] fromTapeToBytes(TapeImage tapeImage) throws FlowException {
        if (tapeImage == null || tapeImage.getBlocks().isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int kcsCount = 0;
        for (TapeBlock block : tapeImage.getBlocks()) {
            if (block instanceof KcsBlock kcs) {
                if (!kcs.isMsxStandardFraming()) {
                    throw FlowException.error("cas_incompatible_blocks");
                }
                writeChars(out, HEADER);
                out.writeBytes(kcs.data());
                kcsCount++;
            } else if (!TapeImage.isNonAudioStructuralBlock(block)) {
                throw FlowException.error("cas_incompatible_blocks");
            }
        }
        if (kcsCount == 0) {
            throw FlowException.error("cas_list_empty");
        }
        return out.toByteArray();
    }

    /**
     * Export a {@link TapeImage} into parsed {@link CasFile} tracks.
     *
     * @param tapeImage Source tape image
     * @return Parsed CAS track list
     * @throws FlowException If the tape is empty or incompatible with CAS format
     */
    public static List<CasFile> fromTape(TapeImage tapeImage) throws FlowException {
        byte[] casBytes = fromTapeToBytes(tapeImage);
        List<CasFile> list = new CasUtil(casBytes).list();
        if (list.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        return list;
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
        for (int h = curPos; h < inputHandler.length - HEADER.length; h++) {
            boolean headerFound = true;
            for (int c = 0; c < HEADER.length; c++) {
                if (Byte.toUnsignedInt(inputHandler[h + c]) != HEADER[c]) {
                    headerFound = false;
                    break;
                }
            }
            if (headerFound) {
                return h;
            }
        }
        return -1;
    }

    private boolean findType(char[] header, byte[] inputFile, int idx) {
        if (idx + header.length > inputFile.length) {
            return false;
        }
        for (int c = 0; c < header.length; c++) {
            if (Byte.toUnsignedInt(inputFile[idx + c]) != header[c]) {
                return false;
            }
        }
        return true;
    }
}
