package br.com.dod.vcas;

import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;

public class CasFileTest {
	
	private static List<CasFile> casList;
	
	private static String filename = AllTests.PROJECT_FOLDER + "/resources/flapbird.cas";
	
	@BeforeClass
	public static void setup() {
		try {
			CasUtil casUtil = new CasUtil(filename);
			casList = casUtil.list();

		} catch (Exception e) {
			e.printStackTrace();
		} catch (FlowException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testCasTracks() {
		try {
			
			CasFile track01 = casList.get(0);
			Assert.assertEquals("flapbi", track01.getName());
			Assert.assertEquals(8208, track01.getSize());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
