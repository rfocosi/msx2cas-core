package br.com.dod.vcas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import br.com.dod.vcas.model.FileType;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static br.com.dod.vcas.util.FileCommons.detectFile;
import static org.junit.Assert.assertEquals;

public class DetectFileTypeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void test() {

        try {
            assertEquals(FileType.ASCII, detectFile(PROJECT_FOLDER + "/resources/asciib.bas"));
            assertEquals(FileType.ASCII, detectFile(PROJECT_FOLDER + "/resources/ascunix.bas"));
            assertEquals(FileType.ASCII, detectFile(PROJECT_FOLDER + "/resources/small.bas"));
            assertEquals(FileType.BAS, detectFile(PROJECT_FOLDER + "/resources/token.bas"));
            assertEquals(FileType.BIN, detectFile(PROJECT_FOLDER + "/resources/flabin-flapbird.bin"));
            assertEquals(FileType.CAS, detectFile(PROJECT_FOLDER + "/resources/flacas-flapbird.cas"));
            assertEquals(FileType.ROM, detectFile(PROJECT_FOLDER + "/resources/flaroA-Flapbird.rom"));
            assertEquals(FileType.ROM, detectFile(PROJECT_FOLDER + "/resources/flaroB-Flapbird.rom"));

            File tsxFile = tempFolder.newFile("sample.tsx");
            byte[] tsxHeader = new byte[] { 'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A, 1, 21 };
            Files.write(tsxFile.toPath(), tsxHeader);
            assertEquals(FileType.TSX, detectFile(tsxFile.getAbsolutePath()));

            File asciiTxt = tempFolder.newFile("plain.txt");
            Files.writeString(asciiTxt.toPath(), "10 PRINT \"HI\"\n", StandardCharsets.US_ASCII);
            assertEquals(FileType.ASCII, detectFile(asciiTxt.getAbsolutePath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
