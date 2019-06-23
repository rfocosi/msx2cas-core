package br.com.dod.vcas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;
import org.junit.BeforeClass;
import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static java.lang.System.getenv;
import static java.lang.System.out;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FileEncodingTest {

    // Set to "true" to update test's WAVs
    private static boolean forceGenerate = getForceWavGenerateEnv();

    private static LinkedHashMap<String,String> files;

    @BeforeClass
    public static void generateFiles() throws FlowException, Exception {
        Files.createDirectories(new File(PROJECT_FOLDER + "/resources/generated/").toPath());

        files = new LinkedHashMap<>();
        files.put(PROJECT_FOLDER + "/resources/asciib.bas", "asciib");
        files.put(PROJECT_FOLDER + "/resources/ascunix.bas", "ascuni");
        files.put(PROJECT_FOLDER + "/resources/token.bas", "token");
        files.put(PROJECT_FOLDER + "/resources/flapbird.bin", "flap");
        files.put(PROJECT_FOLDER + "/resources/flapbird.cas", "flapc");
        files.put(PROJECT_FOLDER + "/resources/flapbird (rev.A).rom", "flapA");
        files.put(PROJECT_FOLDER + "/resources/flapbird (rev.B).rom", "flapB");

        Files.list(getExtraTestResourcesPath())
                .filter(path -> path.toString().endsWith(".rom")
                        || path.toString().endsWith(".bin")
                        || path.toString().endsWith(".bas")
                        || path.toString().endsWith(".cas"))
                .forEach( file -> files.put(file.toString(), FileCommons.getCasName(file.getFileName().toString())));

        out.println("Generating test files...");
        for (Entry<String,String> entry : files.entrySet()) {
            String file = entry.getKey();
            String casName = entry.getValue();

            generateFile(file, casName, SampleRate.sr11025);
            generateFile(file, casName, SampleRate.sr22050);
            generateFile(file, casName, SampleRate.sr33075);
            generateFile(file, casName, SampleRate.sr11025.invertWaveForm());
            generateFile(file, casName, SampleRate.sr22050.invertWaveForm());
            generateFile(file, casName, SampleRate.sr33075.invertWaveForm());
        }
        out.println("... done");
    }

    private static Path getExtraTestResourcesPath() {
        String extra_test_resources_path = getenv("EXTRA_TEST_RESOURCES_PATH");
        return Paths.get(extra_test_resources_path != null ? extra_test_resources_path : ".");
    }

    private static boolean getForceWavGenerateEnv() {
        String forceWavGenerate = getenv("FORCE_WAV_GENERATE");
        return forceWavGenerate != null && getenv("FORCE_WAV_GENERATE").equalsIgnoreCase("true");
    }

    @Test
    public void test11025Files() {
        testFiles(SampleRate.sr11025);
    }

    @Test
    public void test22050Files() {
        testFiles(SampleRate.sr22050);
    }

    @Test
    public void test33075Files() {
        testFiles(SampleRate.sr33075);
    }

    @Test
    public void test11025iFiles() {
        testFiles(SampleRate.sr11025.invertWaveForm());
    }

    @Test
    public void test22050iFiles() {
        testFiles(SampleRate.sr22050.invertWaveForm());
    }

    @Test
    public void test33075iFiles() {
        testFiles(SampleRate.sr33075.invertWaveForm());
    }

    private void testFiles(SampleRate sampleRate) {
        try {
            for (Entry<String,String> entry : files.entrySet()) {
                String file = entry.getKey();
                String casName = entry.getValue();

                fileTest(file, casName, sampleRate);
            }
        } catch (FlowException | Exception fe) {
            fe.printStackTrace();
            fail();
        }
    }

    private void fileTest(String inputFileName, String fileId, SampleRate sampleRate) throws FlowException, Exception{
        byte[] wavFileBytes = new VirtualCas(sampleRate).convert(inputFileName).toBytes();
        byte[] inputFileHandler = getFileBytes(PROJECT_FOLDER + "/resources/generated/" + getWavFileName(fileId, sampleRate));

        assertEquals(sampleRate.intValue() +" Error!", inputFileHandler.length, wavFileBytes.length);

        for (int i = 0; i < inputFileHandler.length; i++) {
            assertEquals("fileId:"+fileId+",sampleRate:"+sampleRate.intValue()
                    +",fileLength:"+inputFileHandler.length+",pos:"+i, inputFileHandler[i], wavFileBytes[i]);
        }
    }

    private byte[] getFileBytes(String filename) throws IOException {
        return Files.readAllBytes(new File(filename).toPath());
    }

    private static void generateFile(String inputFileName, String fileId, SampleRate sampleRate) throws FlowException, Exception{
        Path finalPath = new File(PROJECT_FOLDER + "/resources/generated/"+ getWavFileName(fileId, sampleRate)).toPath();

        if (Files.notExists(finalPath) || forceGenerate ) {
            Files.deleteIfExists(finalPath);
            out.println("Generating: "+ finalPath.getFileName());

            byte[] wavFile = new VirtualCas(sampleRate).convert(inputFileName).toBytes();

            writeWav(wavFile, PROJECT_FOLDER + "/resources/generated/"+finalPath.getFileName());

        } else {
            out.println("File already exists: "+ finalPath.getFileName());
        }
    }

    private static void writeWav(byte[] wavBytes, String fileOutputPathName) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(new File(fileOutputPathName));

        outputStream.write(wavBytes);

        outputStream.close();
    }

    private static String getWavFileName(String fileId, SampleRate sampleRate) {
        return fileId +"-"+ sampleRate.bps() + (sampleRate.isInverted() ? "i" : "") + ".wav";
    }
}
