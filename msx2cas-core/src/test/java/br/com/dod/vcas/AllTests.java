package br.com.dod.vcas;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import static java.lang.System.getProperty;

@RunWith(Suite.class)
@SuiteClasses({
        CasFileTest.class,
        DetectFileTypeTest.class,
        TsxFormatTest.class,
        TapePcmInputStreamTest.class,
        VirtualCasRegressionTest.class
})
public class AllTests {

    static String PROJECT_FOLDER = getProperty("user.dir");

}
