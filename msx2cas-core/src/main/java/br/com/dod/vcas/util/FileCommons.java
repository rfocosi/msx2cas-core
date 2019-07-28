package br.com.dod.vcas.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;

import static br.com.dod.vcas.wav.Wav.CAS_FILENAME_LENGTH;

public class FileCommons {

    public static String getCasName(String fileName) {
        fileName = fileName.replaceFirst(".+/", "").replaceAll("[^.\\w]", "");
        return fileName.replaceFirst("(?:.*/)?(\\w{1," + CAS_FILENAME_LENGTH + "}).*", "$1");
    }

    public static char[] getNameBuffer(String fileName) {
        return getNameBuffer(fileName, 0);
    }

    public static char[] getNameBuffer(String fileName, int number) {
        return String.format("%." + (CAS_FILENAME_LENGTH - (number < 1 ? 0 : 1)) + "s%s", getCasName(fileName), (number < 1 ? "" : String.valueOf(number))).toCharArray();
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
        } else if (FileType.ROM.equals(fileName)) {
            fileType = FileType.ROM;
        }
        return fileType;
    }

    public static char[] getLoader(final String loaderName) {

        File inputFile = new File(Objects.requireNonNull(FileCommons.class.getClassLoader().getResource(loaderName)).getPath());

        char[] inputMemPointer = new char[(int) inputFile.length()];

        try (final FileInputStream fis = new FileInputStream(inputFile)) {

            for (int i = 0; i < inputMemPointer.length; i++) {
                inputMemPointer[i] = (char) fis.read();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return inputMemPointer;
    }
}
