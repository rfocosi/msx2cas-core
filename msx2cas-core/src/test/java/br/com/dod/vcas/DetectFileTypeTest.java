package br.com.dod.vcas;

import java.io.IOException;

import br.com.dod.vcas.model.FileType;
import org.junit.Test;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static br.com.dod.vcas.util.FileCommons.detectFile;
import static org.junit.Assert.assertEquals;

public class DetectFileTypeTest {

    @Test
    public void test() {

        try {
            assertEquals(FileType.ASCII, detectFile(PROJECT_FOLDER + "/resources/asciib.bas"));
            assertEquals(FileType.ASCII, detectFile(PROJECT_FOLDER + "/resources/ascunix.bas"));
            assertEquals(FileType.BAS, detectFile(PROJECT_FOLDER + "/resources/token.bas"));
            assertEquals(FileType.BIN, detectFile(PROJECT_FOLDER + "/resources/flapbird.bin"));
            assertEquals(FileType.CAS, detectFile(PROJECT_FOLDER + "/resources/flapbird.cas"));
            assertEquals(FileType.ROM, detectFile(PROJECT_FOLDER + "/resources/flapbird (rev.A).rom"));
            assertEquals(FileType.ROM, detectFile(PROJECT_FOLDER + "/resources/flapbird (rev.B).rom"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
