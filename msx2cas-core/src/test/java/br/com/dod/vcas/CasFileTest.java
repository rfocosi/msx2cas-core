package br.com.dod.vcas;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static org.junit.Assert.assertEquals;

public class CasFileTest {

    private static List<CasFile> casList;

    private static String filename = PROJECT_FOLDER + "/resources/flapbird.cas";

    @BeforeClass
    public static void setup() {
        try {
            CasUtil casUtil = new CasUtil(filename);
            casList = casUtil.list();

        } catch (Exception | FlowException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCasTracks() {
        try {

            CasFile track01 = casList.get(0);
            assertEquals("flapbi", track01.getName());
            assertEquals(8208, track01.getSize());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
