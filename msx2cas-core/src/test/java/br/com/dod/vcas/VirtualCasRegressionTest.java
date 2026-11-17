package br.com.dod.vcas;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.CasPcmInputStream;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VirtualCasRegressionTest {

    private static final byte[] CAS_MAGIC = new byte[] {
            0x1F, (byte) 0xA6, (byte) 0xDE, (byte) 0xBA, (byte) 0xCC, 0x13, 0x7D, 0x74
    };

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void wavEncoderCasNameTruncatesToSixWordChars() {
        WavEncoder encoder = new WavEncoder(SampleRate.sr11025, "/tmp/my-game_file.rom");
        assertEquals("mygame", encoder.casName());

        WavEncoder nullEncoder = new WavEncoder(SampleRate.sr11025, null);
        assertEquals("", nullEncoder.casName());
    }

    @Test
    public void repeatedSingleFileConvertUsesLatestFile() throws Exception, FlowException {
        File first = tempFolder.newFile("first1.bas");
        File second = tempFolder.newFile("secon2.bas");
        Files.write(first.toPath(), new byte[] { (byte) 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50 });
        Files.write(second.toPath(), new byte[] { (byte) 0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77 });

        VirtualCas vcas = new VirtualCas(SampleRate.sr11025);
        WavOutput firstOut = vcas.convert(first.getAbsolutePath());
        WavOutput secondOut = vcas.convert(second.getAbsolutePath());

        WavOutput freshSecondOut = new VirtualCas(SampleRate.sr11025).convert(second.getAbsolutePath());
        assertEquals("secon2", secondOut.casName());
        assertArrayEquals(freshSecondOut.toBytes(), secondOut.toBytes());
        assertEquals("first1", firstOut.casName());
    }

    @Test
    public void rom16kWithResetAndHead80MatchLegacyOutput() throws Exception, FlowException {
        byte[] rom16k = createRom16K((byte) 0x40);
        File file16k = tempFolder.newFile("rom16a.rom");
        Files.write(file16k.toPath(), rom16k);

        byte[] wavReset = new VirtualCas(SampleRate.sr11025).resetRom(true).convert(file16k.getAbsolutePath()).toBytes();
        assertEquals(2020459, wavReset.length);
        assertEquals("40eaef1cccee77bca5656a3ccd376c53d4747a79771b9f95551c75b3fe570c6d", sha256(wavReset));

        byte[] rom16kHead80 = rom16k.clone();
        rom16kHead80[3] = (byte) 0x80;
        File file16kHead80 = tempFolder.newFile("rom16h.rom");
        Files.write(file16kHead80.toPath(), rom16kHead80);

        byte[] wavHead80 = new VirtualCas(SampleRate.sr11025).convert(file16kHead80.getAbsolutePath()).toBytes();
        assertEquals(1941369, wavHead80.length);
        assertEquals("2bcb6a92db214cbbd57f2ab88ed6716f71b9cbe6ef915fe564f411b9bb63a5a2", sha256(wavHead80));
    }

    @Test
    public void rom32kNormalAndResetMatchLegacyOutput() throws Exception, FlowException {
        byte[] rom32k = new byte[32768];
        rom32k[0] = 'A';
        rom32k[1] = 'B';
        rom32k[2] = 0x10;
        rom32k[3] = 0x40;
        for (int i = 4; i < rom32k.length; i++) {
            rom32k[i] = (byte) ((i * 3) & 0xFF);
        }

        File file32k = tempFolder.newFile("rom32a.rom");
        Files.write(file32k.toPath(), rom32k);

        byte[] normalWav = new VirtualCas(SampleRate.sr11025).convert(file32k.getAbsolutePath()).toBytes();
        assertEquals(4047424, normalWav.length);
        assertEquals("fd7104b516893dcdb0d4249e759317ab1dc4579459359708ef097077dd6f205c", sha256(normalWav));

        byte[] resetWav = new VirtualCas(SampleRate.sr11025).resetRom(true).convert(file32k.getAbsolutePath()).toBytes();
        assertEquals(4051934, resetWav.length);
        assertEquals("598ab37c24d92d6286d837db131344c60091028f9e0558f6f3af638d853cc85c", sha256(resetWav));
    }

    @Test
    public void rom49kNormalAndResetMatchLegacyOutput() throws Exception, FlowException {
        byte[] rom49k = new byte[49152];
        rom49k[0] = 0;
        rom49k[1] = 0;
        rom49k[2] = 0;
        rom49k[3] = 0x00;
        rom49k[0x4000] = 'A';
        rom49k[0x4001] = 'B';
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        for (int i = 4; i < rom49k.length; i++) {
            if (i < 0x4000 || i > 0x4003) {
                rom49k[i] = (byte) ((i * 7) & 0xFF);
            }
        }

        File file49k = tempFolder.newFile("rom49a.rom");
        Files.write(file49k.toPath(), rom49k);

        byte[] normalWav = new VirtualCas(SampleRate.sr11025).convert(file49k.getAbsolutePath()).toBytes();
        assertEquals(6063609, normalWav.length);
        assertEquals("0da6f580e58613d657d34a2d137599d61758bb56a67340f04117995c22e0c00f", sha256(normalWav));

        byte[] resetWav = new VirtualCas(SampleRate.sr11025).resetRom(true).convert(file49k.getAbsolutePath()).toBytes();
        assertEquals(6064489, resetWav.length);
        assertEquals("0ca92101b8faf49719469418ed72787ce24cb8427c42ae08d4397d754306f884", sha256(resetWav));
    }

    @Test
    public void romValidationRejectsOversizedTypeD0AndMappers() throws Exception {
        byte[] oversized = new byte[50177];
        oversized[0] = 'A';
        oversized[1] = 'B';
        assertFlowError(oversized, "oversz.rom", "file_size_invalid");

        byte[] typeD0 = createRom16K((byte) 0xD0);
        assertFlowError(typeD0, "typed0.rom", "type_32k_not_supported");

        byte[] mapper49k = new byte[49152];
        mapper49k[0] = 'A';
        mapper49k[1] = 'B';
        mapper49k[3] = 0x40;
        assertFlowError(mapper49k, "mapper.rom", "mappers_not_supported");
    }

    @Test
    public void multiTrackCasWithDataBlockAndCasListMatchLegacyOutput() throws Exception, FlowException {
        ByteArrayOutputStream casBytes = new ByteArrayOutputStream();
        casBytes.write(CAS_MAGIC);
        byte[] binHeader = new byte[10];
        Arrays.fill(binHeader, (byte) 0xD0);
        casBytes.write(binHeader);
        casBytes.write("GAME01".getBytes(StandardCharsets.US_ASCII));
        casBytes.write(CAS_MAGIC);
        casBytes.write(new byte[] { 1, 2, 3, 4, 5, 6 });
        casBytes.write(CAS_MAGIC);
        casBytes.write(new byte[] { 0x10, 0x20, 0x30, 0x40 });

        File casFile = tempFolder.newFile("multi1.cas");
        Files.write(casFile.toPath(), casBytes.toByteArray());

        byte[] wavFromFile = new VirtualCas(SampleRate.sr11025).convert(casFile.getAbsolutePath()).toBytes();
        assertEquals(234459, wavFromFile.length);
        assertEquals("68b098fe5ce7a8afae4f3f91b3b64f41178be56bdb34b5a159c5b0b5f10fea6c", sha256(wavFromFile));

        List<CasFile> casList = new CasUtil(casFile.getAbsolutePath()).list();
        byte[] wavFromList = new VirtualCas(SampleRate.sr11025).convert(casList).toBytes();
        assertArrayEquals(wavFromFile, wavFromList);
    }

    @Test
    public void shortFilenamePadsNameBuffersToSixChars() throws Exception, FlowException {
        File shortRom = tempFolder.newFile("adv.rom");
        byte[] rom49k = new byte[49152];
        rom49k[0x4000] = 'A';
        rom49k[0x4001] = 'B';
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        Files.write(shortRom.toPath(), rom49k);

        class TestRomConverter extends br.com.dod.vcas.converter.impl.RomConverter {
            TestRomConverter(String path) throws FlowException {
                super(path, SampleRate.sr11025);
            }
            char[] nameBuf() { return getNameBuffer(); }
            char[] numberedBuf(int n) { return getNumberedNameBuffer(n); }
        }

        TestRomConverter converter = new TestRomConverter(shortRom.getAbsolutePath());
        assertArrayEquals("adv   ".toCharArray(), converter.nameBuf());
        assertArrayEquals("adv2  ".toCharArray(), converter.numberedBuf(2));
        assertArrayEquals("adv3  ".toCharArray(), converter.numberedBuf(3));
        assertArrayEquals("adv10 ".toCharArray(), converter.numberedBuf(10));
        assertArrayEquals("adv100".toCharArray(), converter.numberedBuf(100));
    }

    @Test
    public void megaRamConverts128kRomAndHandlesMultiDigitNames() throws Exception, FlowException {
        File romFile = tempFolder.newFile("gradius.rom");
        byte[] rom128k = new byte[128 * 1024];
        rom128k[0] = 'A';
        rom128k[1] = 'B';
        rom128k[2] = 0x10;
        rom128k[3] = 0x40;
        Files.write(romFile.toPath(), rom128k);

        assertFlowError(rom128k, "gradius_std.rom", "file_size_invalid");

        byte[] wavBytes = new VirtualCas(SampleRate.sr33075)
                .megaRam(true)
                .resetRom(true)
                .convert(romFile.getAbsolutePath())
                .toBytes();
        assertTrue(wavBytes.length > 44);
    }

    @Test
    public void directCasConversionAndRoundTripParityForAllFormats() throws Exception, FlowException {
        File multiBlockAscii = tempFolder.newFile("ascii_multi.bas");
        StringBuilder asciiText = new StringBuilder();
        for (int line = 10; line <= 350; line += 10) {
            asciiText.append(line).append(" PRINT \"LINE ").append(line).append("\"\n");
        }
        Files.writeString(multiBlockAscii.toPath(), asciiText.toString(), StandardCharsets.US_ASCII);

        File rom16kFile = tempFolder.newFile("r16cas.rom");
        Files.write(rom16kFile.toPath(), createRom16K((byte) 0x40));

        File rom16kPg2File = tempFolder.newFile("r16p2c.rom");
        Files.write(rom16kPg2File.toPath(), createRom16K((byte) 0x80));

        byte[] rom32k = new byte[32768];
        rom32k[0] = 'A';
        rom32k[1] = 'B';
        rom32k[2] = 0x10;
        rom32k[3] = 0x40;
        for (int i = 4; i < rom32k.length; i++) {
            rom32k[i] = (byte) ((i * 5) & 0xFF);
        }
        File rom32kFile = tempFolder.newFile("r32cas.rom");
        Files.write(rom32kFile.toPath(), rom32k);

        byte[] rom49k = new byte[49152];
        rom49k[0x4000] = 'A';
        rom49k[0x4001] = 'B';
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        for (int i = 4; i < rom49k.length; i++) {
            if (i < 0x4000 || i > 0x4003) {
                rom49k[i] = (byte) ((i * 11) & 0xFF);
            }
        }
        File rom49kFile = tempFolder.newFile("r49cas.rom");
        Files.write(rom49kFile.toPath(), rom49k);

        File megaRomFile = tempFolder.newFile("megcas.rom");
        byte[] megaRom = new byte[128 * 1024];
        megaRom[0] = 'A';
        megaRom[1] = 'B';
        megaRom[2] = 0x10;
        megaRom[3] = 0x40;
        Files.write(megaRomFile.toPath(), megaRom);

        assertCasRoundTrip(PROJECT_FOLDER + "/resources/token.bas", false, false, FileType.BAS, 1);
        assertCasRoundTrip(PROJECT_FOLDER + "/resources/flabin-flapbird.bin", false, false, FileType.BIN, 1);
        assertCasRoundTrip(PROJECT_FOLDER + "/resources/asciib.bas", false, false, FileType.ASCII, 1);
        assertCasRoundTrip(PROJECT_FOLDER + "/resources/ascunix.bas", false, false, FileType.ASCII, 1);
        assertCasRoundTrip(multiBlockAscii.getAbsolutePath(), false, false, FileType.ASCII, 3);
        assertCasRoundTrip(rom16kFile.getAbsolutePath(), false, false, FileType.BIN, 1);
        assertCasRoundTrip(rom16kFile.getAbsolutePath(), true, false, FileType.BIN, 1);
        assertCasRoundTrip(rom16kPg2File.getAbsolutePath(), false, false, FileType.BIN, 1);
        assertCasRoundTrip(rom32kFile.getAbsolutePath(), false, false, FileType.BIN, 2);
        assertCasRoundTrip(rom32kFile.getAbsolutePath(), true, false, FileType.BIN, 2);
        assertCasRoundTrip(rom49kFile.getAbsolutePath(), false, false, FileType.BIN, 3);
        assertCasRoundTrip(rom49kFile.getAbsolutePath(), true, false, FileType.BIN, 3);
        assertCasRoundTrip(megaRomFile.getAbsolutePath(), true, true, FileType.BIN, 8);
    }

    @Test
    public void virtualCasWithoutSampleRateConvertsToCasAndRejectsWav() throws Exception, FlowException {
        File basFile = tempFolder.newFile("nosr01.bas");
        Files.write(basFile.toPath(), new byte[] { (byte) 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50 });

        VirtualCas vcas = new VirtualCas().addFile(basFile.getAbsolutePath());
        List<CasOutput> batchCas = vcas.convertToCas();
        assertEquals(1, batchCas.size());
        assertEquals("nosr01", batchCas.get(0).casName());
        assertTrue(batchCas.get(0).toBytes().length > CAS_MAGIC.length);

        try {
            vcas.convert(basFile.getAbsolutePath());
            fail("Expected FlowException: sample_rate_required");
        } catch (FlowException e) {
            assertEquals("sample_rate_required", e.getMessage());
        }
    }

    @Test
    public void pcmStreamMatchesWavPayloadAndSupportsReset() throws Exception, FlowException {
        File multiBlockAscii = tempFolder.newFile("ascii_stream.bas");
        StringBuilder asciiText = new StringBuilder();
        for (int line = 10; line <= 350; line += 10) {
            asciiText.append(line).append(" PRINT \"STREAM ").append(line).append("\"\n");
        }
        Files.writeString(multiBlockAscii.toPath(), asciiText.toString(), StandardCharsets.US_ASCII);

        File rom16kFile = tempFolder.newFile("s16cas.rom");
        Files.write(rom16kFile.toPath(), createRom16K((byte) 0x40));

        byte[] rom32k = new byte[32768];
        rom32k[0] = 'A';
        rom32k[1] = 'B';
        rom32k[2] = 0x10;
        rom32k[3] = 0x40;
        for (int i = 4; i < rom32k.length; i++) {
            rom32k[i] = (byte) ((i * 5) & 0xFF);
        }
        File rom32kFile = tempFolder.newFile("s32cas.rom");
        Files.write(rom32kFile.toPath(), rom32k);

        byte[] rom49k = new byte[49152];
        rom49k[0x4000] = 'A';
        rom49k[0x4001] = 'B';
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        for (int i = 4; i < rom49k.length; i++) {
            if (i < 0x4000 || i > 0x4003) {
                rom49k[i] = (byte) ((i * 11) & 0xFF);
            }
        }
        File rom49kFile = tempFolder.newFile("s49cas.rom");
        Files.write(rom49kFile.toPath(), rom49k);

        File megaRomFile = tempFolder.newFile("smegac.rom");
        byte[] megaRom = new byte[128 * 1024];
        megaRom[0] = 'A';
        megaRom[1] = 'B';
        megaRom[2] = 0x10;
        megaRom[3] = 0x40;
        Files.write(megaRomFile.toPath(), megaRom);

        ByteArrayOutputStream multiCasBytes = new ByteArrayOutputStream();
        multiCasBytes.write(CAS_MAGIC);
        byte[] binHeader = new byte[10];
        Arrays.fill(binHeader, (byte) 0xD0);
        multiCasBytes.write(binHeader);
        multiCasBytes.write("GAME01".getBytes(StandardCharsets.US_ASCII));
        multiCasBytes.write(CAS_MAGIC);
        multiCasBytes.write(new byte[] { 1, 2, 3, 4, 5, 6 });
        multiCasBytes.write(CAS_MAGIC);
        multiCasBytes.write(new byte[] { 0x10, 0x20, 0x30, 0x40 });
        File multiCasFile = tempFolder.newFile("smulti.cas");
        Files.write(multiCasFile.toPath(), multiCasBytes.toByteArray());

        try {
            assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/token.bas", false, false,
                    SampleRate.sr11025.invertWaveForm(false));
            assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/flabin-flapbird.bin", false, false,
                    SampleRate.sr11025.invertWaveForm(true));
            assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/asciib.bas", false, false,
                    SampleRate.sr27563.invertWaveForm(false));
            assertPcmStreamMatchesWav(multiBlockAscii.getAbsolutePath(), false, false,
                    SampleRate.sr27563.invertWaveForm(true));
            assertPcmStreamMatchesWav(rom16kFile.getAbsolutePath(), true, false,
                    SampleRate.sr22050.invertWaveForm(false));
            assertPcmStreamMatchesWav(rom32kFile.getAbsolutePath(), false, false,
                    SampleRate.sr22050.invertWaveForm(true));
            assertPcmStreamMatchesWav(rom49kFile.getAbsolutePath(), true, false,
                    SampleRate.sr11025.invertWaveForm(false));
            assertPcmStreamMatchesWav(megaRomFile.getAbsolutePath(), true, true,
                    SampleRate.sr33075.invertWaveForm(false));
            assertPcmStreamMatchesWav(multiCasFile.getAbsolutePath(), false, false,
                    SampleRate.sr11025.invertWaveForm(true));
        } finally {
            for (SampleRate rate : SampleRate.values()) {
                rate.invertWaveForm(false);
            }
        }

        // Reset mid-stream, reset at EOF, and compare single-byte read() vs chunked read()
        VirtualCas vcas = new VirtualCas(SampleRate.sr11025);
        CasOutput basCas = vcas.convertToCas(PROJECT_FOLDER + "/resources/token.bas");
        byte[] expectedPayload = extractWavPayload(vcas.convert(basCas).toBytes());

        try (CasPcmInputStream stream = vcas.openPcmStream(basCas)) {
            assertTrue(stream.markSupported());
            assertEquals("token", stream.casName());
            assertEquals(expectedPayload.length, stream.getTotalSamples());

            byte[] midRead = new byte[12345];
            int n = stream.read(midRead, 0, midRead.length);
            assertEquals(midRead.length, n);
            assertEquals(midRead.length, stream.getPosition());
            assertArrayEquals(Arrays.copyOf(expectedPayload, midRead.length), midRead);

            stream.reset();
            assertEquals(0L, stream.getPosition());
            assertEquals(expectedPayload.length, stream.available());

            ByteArrayOutputStream singleByteOut = new ByteArrayOutputStream(expectedPayload.length);
            int sample;
            while ((sample = stream.read()) != -1) {
                singleByteOut.write(sample);
            }
            assertEquals(expectedPayload.length, stream.getPosition());
            assertEquals(0, stream.available());
            assertArrayEquals(expectedPayload, singleByteOut.toByteArray());

            stream.reset();
            assertEquals(0L, stream.getPosition());
            byte[] replayed = readAllChunked(stream, 137);
            assertArrayEquals(expectedPayload, replayed);
        }

        // Validation checks for missing sampleRate and empty/null CAS input
        VirtualCas noRateVcas = new VirtualCas();
        try {
            noRateVcas.openPcmStream(basCas);
            fail("Expected FlowException: sample_rate_required");
        } catch (FlowException e) {
            assertEquals("sample_rate_required", e.getMessage());
        }
        try {
            noRateVcas.openPcmStream(PROJECT_FOLDER + "/resources/token.bas");
            fail("Expected FlowException: sample_rate_required");
        } catch (FlowException e) {
            assertEquals("sample_rate_required", e.getMessage());
        }
        try {
            vcas.convert((CasOutput) null);
            fail("Expected FlowException: cas_list_empty");
        } catch (FlowException e) {
            assertEquals("cas_list_empty", e.getMessage());
        }
        try {
            vcas.openPcmStream((CasOutput) null);
            fail("Expected FlowException: cas_list_empty");
        } catch (FlowException e) {
            assertEquals("cas_list_empty", e.getMessage());
        }
        try {
            new CasPcmInputStream(List.of(), SampleRate.sr11025);
            fail("Expected FlowException: cas_list_empty");
        } catch (FlowException e) {
            assertEquals("cas_list_empty", e.getMessage());
        }
    }

    private void assertPcmStreamMatchesWav(
            String sourcePath,
            boolean reset,
            boolean megaRam,
            SampleRate sampleRate) throws Exception, FlowException {
        VirtualCas vcas = new VirtualCas(sampleRate)
                .resetRom(reset)
                .megaRam(megaRam);
        CasOutput casOutput = vcas.convertToCas(sourcePath);
        byte[] expectedPayload = extractWavPayload(vcas.convert(casOutput).toBytes());

        try (CasPcmInputStream stream = vcas.openPcmStream(casOutput)) {
            assertEquals(casOutput.casName(), stream.casName());
            assertEquals(expectedPayload.length, stream.getTotalSamples());
            byte[] actualPayload = readAllChunked(stream, 137);
            assertEquals(stream.getTotalSamples(), stream.getPosition());
            assertEquals(0, stream.available());
            assertArrayEquals("PCM mismatch for " + sourcePath + " at " + sampleRate.bps(), expectedPayload, actualPayload);
        }

        try (CasPcmInputStream fromPath = vcas.openPcmStream(sourcePath)) {
            byte[] actualFromPath = readAllChunked(fromPath, 4096);
            assertArrayEquals("PCM mismatch from path for " + sourcePath, expectedPayload, actualFromPath);
        }
    }

    private static byte[] extractWavPayload(byte[] wavBytes) {
        return Arrays.copyOfRange(wavBytes, 44, wavBytes.length);
    }

    private static byte[] readAllChunked(CasPcmInputStream stream, int chunkSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) stream.getTotalSamples());
        byte[] buffer = new byte[chunkSize];
        int read;
        while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void assertCasRoundTrip(
            String sourcePath,
            boolean reset,
            boolean megaRam,
            FileType expectedParsedFirstTrackType,
            int expectedTrackCount) throws Exception, FlowException {
        CasOutput casOutput = new VirtualCas()
                .resetRom(reset)
                .megaRam(megaRam)
                .convertToCas(sourcePath);

        byte[] casBytes = casOutput.toBytes();
        assertTrue(casBytes.length > CAS_MAGIC.length);
        assertArrayEquals(CAS_MAGIC, Arrays.copyOf(casBytes, CAS_MAGIC.length));
        assertEquals(expectedTrackCount, casOutput.getTracks().size());

        File casFile = tempFolder.newFile("roundtrip_" + System.nanoTime() + ".cas");
        Files.write(casFile.toPath(), casBytes);

        List<CasFile> parsedTracks = new CasUtil(casFile.getAbsolutePath()).list();
        assertEquals(expectedTrackCount, parsedTracks.size());
        assertEquals(expectedParsedFirstTrackType, parsedTracks.get(0).getFileType());

        byte[] wavDirect = new VirtualCas(SampleRate.sr22050)
                .resetRom(reset)
                .megaRam(megaRam)
                .convert(sourcePath)
                .toBytes();
        byte[] wavFromCas = new VirtualCas(SampleRate.sr22050)
                .convert(casFile.getAbsolutePath())
                .toBytes();
        assertArrayEquals("WAV mismatch after CAS round-trip for " + sourcePath, wavDirect, wavFromCas);
    }

    private void assertFlowError(byte[] data, String name, String expectedMessage) throws Exception {
        File file = tempFolder.newFile(name);
        Files.write(file.toPath(), data);
        try {
            new VirtualCas(SampleRate.sr11025).convert(file.getAbsolutePath());
            fail("Expected FlowException: " + expectedMessage);
        } catch (FlowException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private static byte[] createRom16K(byte headId) {
        byte[] rom = new byte[16384];
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = 0x10;
        rom[3] = headId;
        for (int i = 4; i < rom.length; i++) {
            rom[i] = (byte) (i & 0xFF);
        }
        return rom;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
