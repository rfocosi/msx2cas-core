package br.com.dod.vcas;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ CasFileTest.class, DetectFileTypeTest.class, 
	FileEncodingTest.class, ValidateWavHeaderTest.class })
public class AllTests {

}
