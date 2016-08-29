package br.com.dod.vcas;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class DetectFileTypeTest {

	@Test
	public void test() {
		
		try {
			Assert.assertEquals(FileType.ASCII, FileCommons.detectFile("./resources/asciib.bas"));
			Assert.assertEquals(FileType.ASCII, FileCommons.detectFile("./resources/ascunix.bas"));
			Assert.assertEquals(FileType.BAS, FileCommons.detectFile("./resources/token.bas"));
			Assert.assertEquals(FileType.BIN, FileCommons.detectFile("./resources/flapbird.bin"));
			Assert.assertEquals(FileType.CAS, FileCommons.detectFile("./resources/flapbird.cas"));
			Assert.assertEquals(FileType.ROM, FileCommons.detectFile("./resources/flapbird (rev.A).rom"));
			Assert.assertEquals(FileType.ROM, FileCommons.detectFile("./resources/flapbird (rev.B).rom"));
		} catch (IOException e) {
		
			e.printStackTrace();
		}
		
	}
}
