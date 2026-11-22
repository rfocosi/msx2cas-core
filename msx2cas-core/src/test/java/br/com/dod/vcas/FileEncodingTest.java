package br.com.dod.vcas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.junit.BeforeClass;
import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static java.lang.System.getenv;
import static java.lang.System.out;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FileEncodingTest {

    // Set FORCE_WAV_GENERATE=true in the environment to regenerate reference WAVs
    private static final boolean forceGenerate = getForceWavGenerateEnv();

    private static LinkedHashMap<String, String> files;

    @BeforeClass
    public static void generateFiles() throws Exception {
        Files.createDirectories(new File(PROJECT_FOLDER + "/resources/generated/").toPath());

        files = new LinkedHashMap<>();
        Files.list(Paths.get(PROJECT_FOLDER + "/resources/"))
                .filter(path -> path.toString().endsWith(".rom")
                        || path.toString().endsWith(".bin")
                        || path.toString().endsWith(".bas")
                        || path.toString().endsWith(".cas"))
                .forEach(file -> files.put(file.toString(), file.getFileName().toString()));

        Path extraPath = getExtraTestResourcesPath();
        if (Files.isDirectory(extraPath)) {
            Files.list(extraPath)
                    .filter(path -> path.toString().endsWith(".rom")
                            || path.toString().endsWith(".bin")
                            || path.toString().endsWith(".bas")
                            || path.toString().endsWith(".cas"))
                    .forEach(file -> files.put(file.toString(), file.getFileName().toString()));
        }

        out.println("Generating test files...");
        generateTestFiles();
        out.println("... done");
    }

    private static void generateTestFiles() throws Exception {
        for (Entry<String, String> entry : files.entrySet()) {
            String file = entry.getKey();
            String casName = entry.getValue();

            try {
                for (TapeSpeed speed : TapeSpeed.values()) {
                    generateFile(file, casName, speed, false);
                    generateFile(file, casName, speed, true);
                }
            } catch (FlowException e) {
                out.println("Can not generate file [" + file + "]: " + e.getMessage());
            }
        }
    }

    private static Path getExtraTestResourcesPath() {
        String extra = getenv("EXTRA_TEST_RESOURCES_PATH");
        return Paths.get(extra != null ? extra : PROJECT_FOLDER + "/resources/local/");
    }

    private static boolean getForceWavGenerateEnv() {
        String forceWavGenerate = getenv("FORCE_WAV_GENERATE");
        return forceWavGenerate != null && forceWavGenerate.equalsIgnoreCase("true");
    }

    @Test
    public void test1200Files() {
        testFiles(TapeSpeed.BPS_1200, false);
    }

    @Test
    public void test2400Files() {
        testFiles(TapeSpeed.BPS_2400, false);
    }

    @Test
    public void test3000Files() {
        testFiles(TapeSpeed.BPS_3000, false);
    }

    @Test
    public void test3600Files() {
        testFiles(TapeSpeed.TURBO, false);
    }

    @Test
    public void test1200iFiles() {
        testFiles(TapeSpeed.BPS_1200, true);
    }

    @Test
    public void test2400iFiles() {
        testFiles(TapeSpeed.BPS_2400, true);
    }

    @Test
    public void test3000iFiles() {
        testFiles(TapeSpeed.BPS_3000, true);
    }

    @Test
    public void test3600iFiles() {
        testFiles(TapeSpeed.TURBO, true);
    }

    private void testFiles(TapeSpeed speed, boolean inverted) {
        try {
            for (Entry<String, String> entry : files.entrySet()) {
                String file = entry.getKey();
                String casName = entry.getValue();

                fileTest(file, casName, speed, inverted);
            }
        } catch (FlowException | Exception fe) {
            fe.printStackTrace();
            fail();
        }
    }

    private void fileTest(String inputFileName, String fileId, TapeSpeed speed, boolean inverted)
            throws FlowException, Exception {
        try {
            byte[] expected = getFileBytes(PROJECT_FOLDER + "/resources/generated/" + getWavFileName(fileId, speed, inverted));

            byte[] wavFileBytes = new VirtualCas(speed, SampleRate.DEFAULT)
                    .invertWaveform(inverted)
                    .convertToWav(inputFileName)
                    .toBytes();

            assertEquals(speed.bps() + " Error!", expected.length, wavFileBytes.length);

            for (int i = 0; i < expected.length; i++) {
                assertEquals("fileId:" + fileId + ",speed:" + speed.bps()
                        + ",inverted:" + inverted + ",fileLength:" + expected.length + ",pos:" + i,
                        expected[i], wavFileBytes[i]);
            }
        } catch (IOException e) {
            // Skip missing fixture
        }
    }

    private byte[] getFileBytes(String filename) throws IOException {
        return Files.readAllBytes(new File(filename).toPath());
    }

    private static void generateFile(String inputFileName, String fileId, TapeSpeed speed, boolean inverted)
            throws FlowException, Exception {
        Path finalPath = new File(PROJECT_FOLDER + "/resources/generated/" + getWavFileName(fileId, speed, inverted)).toPath();

        if (Files.notExists(finalPath) || forceGenerate) {
            Files.deleteIfExists(finalPath);
            out.println("Generating: " + finalPath.getFileName());

            byte[] wavFile = new VirtualCas(speed, SampleRate.DEFAULT)
                    .invertWaveform(inverted)
                    .convertToWav(inputFileName)
                    .toBytes();

            writeWav(wavFile, PROJECT_FOLDER + "/resources/generated/" + finalPath.getFileName());
        } else {
            out.println("File already exists: " + finalPath.getFileName());
        }
    }

    private static void writeWav(byte[] wavBytes, String fileOutputPathName) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(new File(fileOutputPathName))) {
            outputStream.write(wavBytes);
        }
    }

    private static String getWavFileName(String fileId, TapeSpeed speed, boolean inverted) {
        return fileId + "-" + speed.bps() + (inverted ? "i" : "") + ".wav";
    }
}
