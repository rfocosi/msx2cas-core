package br.com.dod.vcas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import br.com.dod.vcas.model.SampleRate;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;

public class FileEncodingTest {

    // Set to "true" to update test's WAVs
    private static boolean forceGenerate = getForceWavGenerateEnv();

    private static LinkedHashMap<String,String> files;

    @BeforeClass
    public static void generateFiles() throws FlowException, Exception {
        Files.createDirectories(new File(AllTests.PROJECT_FOLDER + "/resources/generated/").toPath());

        files = new LinkedHashMap<String,String>();
        files.put("asciib.bas", "asciib");
        files.put("ascunix.bas", "ascuni");
        files.put("token.bas", "token");
        files.put("flapbird.bin", "flap");
        files.put("flapbird.cas", "flapc");
        files.put("flapbird (rev.A).rom", "flapA");
        files.put("flapbird (rev.B).rom", "flapB");
        files.put("Arkanoid.rom", "arkano");

        System.out.println("Generating test files...");
        for (Entry<String,String> entry : files.entrySet()) {
            String file = entry.getKey();
            String casName = entry.getValue();

            generateFile(file, casName, SampleRate.sr11025);
            generateFile(file, casName, SampleRate.sr22050);
            generateFile(file, casName, SampleRate.sr33075);
        }
        System.out.println("... done");
    }

    private static boolean getForceWavGenerateEnv() {
        String forceWavGenerate = System.getenv("FORCE_WAV_GENERATE");
        return forceWavGenerate != null && System.getenv("FORCE_WAV_GENERATE").equalsIgnoreCase("true");
    }

    @Test
    public void test11025Files() {
        try {
            for (Entry<String,String> entry : files.entrySet()) {
                String file = entry.getKey();
                String casName = entry.getValue();

                fileTest(file, casName, SampleRate.sr11025);
            }
        } catch (FlowException e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    @Test
    public void test22050Files() {
        try {
            for (Entry<String,String> entry : files.entrySet()) {
                String file = entry.getKey();
                String casName = entry.getValue();

                fileTest(file, casName, SampleRate.sr22050);
            }
        } catch (FlowException e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    @Test
    public void test33075Files() {
        try {
            for (Entry<String,String> entry : files.entrySet()) {
                String file = entry.getKey();
                String casName = entry.getValue();

                fileTest(file, casName, SampleRate.sr33075);
            }
        } catch (FlowException e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    public void fileTest(String inputFileName, String fileId, SampleRate sampleRate) throws FlowException, Exception{
        byte[] wavFileBytes = new VirtualCas(sampleRate).convert(AllTests.PROJECT_FOLDER + "/resources/"+ inputFileName).toBytes();
        byte[] inputFileHandler = getFileBytes(AllTests.PROJECT_FOLDER + "/resources/generated/"+fileId+"-"+sampleRate.bps()+".wav");

        Assert.assertEquals(sampleRate.intValue() +" Error!", inputFileHandler.length, wavFileBytes.length);

        for (int i = 0; i < inputFileHandler.length; i++) {
            Assert.assertEquals("fileId:"+fileId+",sampleRate:"+sampleRate.intValue()
                    +",fileLength:"+inputFileHandler.length+",pos:"+i, inputFileHandler[i], wavFileBytes[i]);
        }
    }

    private byte[] getFileBytes(String filename) throws IOException {
        return Files.readAllBytes(new File(filename).toPath());
    }

    public static void generateFile(String inputFileName, String fileId, SampleRate sampleRate) throws FlowException, Exception{
        String finalFileName = fileId +"-"+ sampleRate.bps();
        Path finalPath = new File(AllTests.PROJECT_FOLDER + "/resources/generated/"+ finalFileName +".wav").toPath();

        if (Files.notExists(finalPath) || forceGenerate ) {
            Files.deleteIfExists(finalPath);
            System.out.println("Generating: "+ finalPath.getFileName());

            byte[] wavFile = new VirtualCas(sampleRate).convert(AllTests.PROJECT_FOLDER + "/resources/"+ inputFileName).toBytes();

            writeWav(wavFile, AllTests.PROJECT_FOLDER + "/resources/generated/"+finalPath.getFileName());

        } else {
            System.out.println("File already exists: "+ finalPath.getFileName());
        }
    }

    private static void writeWav(byte[] wavBytes, String fileOutputPathName) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(new File(fileOutputPathName));

        outputStream.write(wavBytes);

        outputStream.close();
    }
}
