package br.com.dod.vcas.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;

public class FileCommons {

    public static final int CAS_FILENAME_LENGTH = 6;

    public static String getCasName(String fileName) {
        fileName = fileName.replaceFirst(".+/", "").replaceAll("[^.\\w]", "");
        return fileName.replaceFirst("(?:.*/)?(\\w{1," + CAS_FILENAME_LENGTH + "}).*", "$1");
    }

    public static char[] getNameBuffer(String fileName) {
        return String.format("%1$-" + CAS_FILENAME_LENGTH + "s", getCasName(fileName)).toCharArray();
    }

    public static byte[] readFile(String inputFileName) throws FlowException {
        return readFile(new File(inputFileName));
    }

    public static byte[] readFile(File inputFile) throws FlowException {
        byte[] inputMemPointer;
        try (final FileInputStream fis = new FileInputStream(inputFile)) {
            if (!inputFile.exists()) throw FlowException.error("file_not_found");
            if (!inputFile.isFile()) throw FlowException.error("not_a_file");
            if (!inputFile.canRead()) throw FlowException.error("file_access_denied");

            inputMemPointer = new byte[(int) inputFile.length()];

            for (int i = 0; i < inputMemPointer.length; i++) {
                inputMemPointer[i] = (byte) fis.read();
            }
        } catch (IOException e) {
            throw FlowException.error("file_not_found");
        }
        return inputMemPointer;
    }

    public static FileType detectFile(String fileName) throws IOException {
        FileType fileType = FileType.ASCII;

        if (FileType.CAS.equals(fileName)) {
            fileType = FileType.CAS;
        } else if (FileType.BAS.equals(fileName)) {
            fileType = FileType.BAS;
        } else if (FileType.BIN.equals(fileName)) {
            fileType = FileType.BIN;
        } else if (fileName.toLowerCase().endsWith(".rom")) {
            fileType = FileType.ROM;
        }
        return fileType;

    }

    public static FileType detectFile(byte[] inputHandler) {
        FileType fileType = FileType.ASCII;

        if (FileType.CAS.equals(inputHandler)) {
            fileType = FileType.CAS;
        } else if (FileType.BAS.equals(inputHandler)) {
            fileType = FileType.BAS;
        } else if (FileType.BIN.equals(inputHandler)) {
            fileType = FileType.BIN;
        }

        return fileType;
    }
}
