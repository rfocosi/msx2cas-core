package br.com.dod.vcas;

import java.io.IOException;

import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.util.FileCommons;
import org.junit.Assert;
import org.junit.Test;

public class DetectFileTypeTest {

    @Test
    public void test() {

        try {
            Assert.assertEquals(FileType.ASCII, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/asciib.bas"));
            Assert.assertEquals(FileType.ASCII, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/ascunix.bas"));
            Assert.assertEquals(FileType.BAS, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/token.bas"));
            Assert.assertEquals(FileType.BIN, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/flapbird.bin"));
            Assert.assertEquals(FileType.CAS, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/flapbird.cas"));
            Assert.assertEquals(FileType.ROM, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/flapbird (rev.A).rom"));
            Assert.assertEquals(FileType.ROM, FileCommons.detectFile(AllTests.PROJECT_FOLDER + "/resources/flapbird (rev.B).rom"));
        } catch (IOException e) {

            e.printStackTrace();
        }

    }
}
