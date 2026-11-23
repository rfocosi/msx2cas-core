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
import br.com.dod.vcas.converter.impl.RomConverter;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeProfile;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.tsx.TsxOutput;
import br.com.dod.vcas.tsx.TsxParser;
import br.com.dod.vcas.wav.TapePcmInputStream;
import br.com.dod.vcas.wav.WavOutput;
import br.com.dod.vcas.wav.WavWriter;

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
    public void wavWriterCasNameTruncatesToSixWordChars() throws Exception, FlowException {
        TapeImage named = new TapeImage("mygame", List.of(
                KcsBlock.msx(new byte[] { 1, 2, 3 }, TapeSpeed.BPS_2400, false, 100)));
        WavOutput writer = new WavWriter(named, SampleRate.sr44100, false);
        assertEquals("mygame", writer.casName());

        TapeImage unnamed = new TapeImage(null, List.of(
                KcsBlock.msx(new byte[] { 1, 2, 3 }, TapeSpeed.BPS_2400, false, 100)));
        WavOutput nullWriter = new WavWriter(unnamed, SampleRate.sr44100, false);
        assertEquals("", nullWriter.casName());
    }

    @Test
    public void repeatedSingleFileConvertUsesLatestFile() throws Exception, FlowException {
        File first = tempFolder.newFile("first1.bas");
        File second = tempFolder.newFile("secon2.bas");
        Files.write(first.toPath(), new byte[] { (byte) 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50 });
        Files.write(second.toPath(), new byte[] { (byte) 0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77 });

        VirtualCas vcas = new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr11025);
        WavOutput firstOut = vcas.convertToWav(first.getAbsolutePath());
        WavOutput secondOut = vcas.convertToWav(second.getAbsolutePath());

        WavOutput freshSecondOut = new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr11025)
                .convertToWav(second.getAbsolutePath());
        assertEquals("secon2", secondOut.casName());
        assertArrayEquals(freshSecondOut.toBytes(), secondOut.toBytes());
        assertEquals("first1", firstOut.casName());
    }

    @Test
    public void rom16k32kAnd49kProduceDeterministicWavs() throws Exception, FlowException {
        byte[] rom16k = createRom16K((byte) 0x40);
        File file16k = tempFolder.newFile("rom16a.rom");
        Files.write(file16k.toPath(), rom16k);

        byte[] wavResetA = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100).resetRom(true)
                .convertToWav(file16k.getAbsolutePath()).toBytes();
        byte[] wavResetB = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100).resetRom(true)
                .convertToWav(file16k.getAbsolutePath()).toBytes();
        assertTrue(wavResetA.length > 44);
        assertArrayEquals(wavResetA, wavResetB);

        byte[] rom16kHead80 = rom16k.clone();
        rom16kHead80[3] = (byte) 0x80;
        File file16kHead80 = tempFolder.newFile("rom16h.rom");
        Files.write(file16kHead80.toPath(), rom16kHead80);
        byte[] wavHead80 = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(file16kHead80.getAbsolutePath()).toBytes();
        assertTrue(wavHead80.length > 44);
        assertEquals(sha256(wavHead80), sha256(new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(file16kHead80.getAbsolutePath()).toBytes()));

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

        byte[] normal32 = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(file32k.getAbsolutePath()).toBytes();
        byte[] reset32 = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100).resetRom(true)
                .convertToWav(file32k.getAbsolutePath()).toBytes();
        assertTrue(normal32.length > wavResetA.length);
        assertTrue(reset32.length >= normal32.length);

        byte[] rom49k = new byte[49152];
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

        byte[] normal49 = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(file49k.getAbsolutePath()).toBytes();
        assertTrue(normal49.length > normal32.length);
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
    public void multiTrackCasWithDataBlockAndCasListMatchWaveforms() throws Exception, FlowException {
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

        VirtualCas vcas = new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr11025);
        byte[] wavFromFile = vcas.convertToWav(casFile.getAbsolutePath()).toBytes();
        assertTrue(wavFromFile.length > 44);

        List<CasFile> casList = new CasUtil(casFile.getAbsolutePath()).list();
        byte[] wavFromList = vcas.convertToWav(casList).toBytes();
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

        class TestRomConverter extends RomConverter {
            TestRomConverter(String path) throws FlowException {
                super(path, TapeSpeed.BPS_2400);
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

        byte[] wavBytes = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .megaRam(true)
                .resetRom(true)
                .convertToWav(romFile.getAbsolutePath())
                .toBytes();
        assertTrue(wavBytes.length > 44);
    }

    @Test
    public void directCasAndTsxRoundTripParityForAllFormats() throws Exception, FlowException {
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

        assertRoundTrip(PROJECT_FOLDER + "/resources/token.bas", false, false, FileType.BAS, 1);
        assertRoundTrip(PROJECT_FOLDER + "/resources/flabin-flapbird.bin", false, false, FileType.BIN, 1);
        assertRoundTrip(PROJECT_FOLDER + "/resources/asciib.bas", false, false, FileType.ASCII, 1);
        assertRoundTrip(PROJECT_FOLDER + "/resources/ascunix.bas", false, false, FileType.ASCII, 1);
        assertRoundTrip(multiBlockAscii.getAbsolutePath(), false, false, FileType.ASCII, 3);
        assertRoundTrip(rom16kFile.getAbsolutePath(), false, false, FileType.BIN, 1);
        assertRoundTrip(rom16kFile.getAbsolutePath(), true, false, FileType.BIN, 1);
        assertRoundTrip(rom16kPg2File.getAbsolutePath(), false, false, FileType.BIN, 1);
        assertRoundTrip(rom32kFile.getAbsolutePath(), false, false, FileType.BIN, 2);
        assertRoundTrip(rom32kFile.getAbsolutePath(), true, false, FileType.BIN, 2);
        assertRoundTrip(rom49kFile.getAbsolutePath(), false, false, FileType.BIN, 3);
        assertRoundTrip(rom49kFile.getAbsolutePath(), true, false, FileType.BIN, 3);
        assertRoundTrip(megaRomFile.getAbsolutePath(), true, true, FileType.BIN, 8);
    }

    @Test
    public void virtualCasDefaultsTo44100HzAndConvertsBatch() throws Exception, FlowException {
        File basFile = tempFolder.newFile("nosr01.bas");
        Files.write(basFile.toPath(), new byte[] { (byte) 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50 });

        VirtualCas vcas = new VirtualCas().addFile(basFile.getAbsolutePath());
        List<CasOutput> batchCas = vcas.convertToCas();
        assertEquals(1, batchCas.size());
        assertEquals("nosr01", batchCas.get(0).casName());
        assertTrue(batchCas.get(0).toBytes().length > CAS_MAGIC.length);

        List<TsxOutput> batchTsx = vcas.convertToTsx();
        assertEquals(1, batchTsx.size());
        assertTrue(batchTsx.get(0).toBytes().length > 10);

        List<WavOutput> batchWav = vcas.convertToWav();
        assertEquals(1, batchWav.size());
        assertTrue(batchWav.get(0).toBytes().length > 44);
    }

    @Test
    public void pcmStreamMatchesWavPayloadAndSupportsReset() throws Exception, FlowException {
        File rom16kFile = tempFolder.newFile("s16cas.rom");
        Files.write(rom16kFile.toPath(), createRom16K((byte) 0x40));

        assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/token.bas", false, false,
                TapeSpeed.BPS_1200, false, SampleRate.sr11025, false);
        assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/flabin-flapbird.bin", false, false,
                TapeSpeed.BPS_2400, false, SampleRate.sr22050, true);
        assertPcmStreamMatchesWav(PROJECT_FOLDER + "/resources/asciib.bas", false, false,
                TapeSpeed.BPS_3000, false, SampleRate.sr44100, false);
        assertPcmStreamMatchesWav(rom16kFile.getAbsolutePath(), true, false,
                TapeSpeed.BPS_2400, false, SampleRate.sr48000, true);

        VirtualCas vcas = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100);
        TapeImage basTape = vcas.convertToTape(PROJECT_FOLDER + "/resources/token.bas");
        byte[] expectedPayload = extractWavPayload(vcas.convertToWav(basTape).toBytes());

        try (TapePcmInputStream stream = vcas.openPcmStream(basTape)) {
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

            byte[] replayed = readAllChunked(stream, 137);
            assertArrayEquals(expectedPayload, replayed);
        }
    }

    @Test
    public void turboPcmStreamAndTsxRoundTripReduceSize() throws Exception, FlowException {
        byte[] rom32k = new byte[32768];
        rom32k[0] = 'A';
        rom32k[1] = 'B';
        rom32k[2] = 0x10;
        rom32k[3] = 0x40;
        for (int i = 4; i < rom32k.length; i++) {
            rom32k[i] = (byte) ((i * 5) & 0xFF);
        }
        File rom32kFile = tempFolder.newFile("t32cas.rom");
        Files.write(rom32kFile.toPath(), rom32k);

        byte[] megaRom = new byte[128 * 1024];
        megaRom[0] = 'A';
        megaRom[1] = 'B';
        megaRom[2] = 0x10;
        megaRom[3] = 0x40;
        File megaRomFile = tempFolder.newFile("tmegac.rom");
        Files.write(megaRomFile.toPath(), megaRom);

        assertPcmStreamMatchesWav(rom32kFile.getAbsolutePath(), true, false,
                TapeSpeed.BPS_2400, true, SampleRate.sr44100, false);
        assertPcmStreamMatchesWav(rom32kFile.getAbsolutePath(), true, false,
                TapeSpeed.BPS_2400, true, SampleRate.sr33075, false);
        assertPcmStreamMatchesWav(megaRomFile.getAbsolutePath(), true, true,
                TapeSpeed.BPS_2400, true, SampleRate.sr44100, true);

        byte[] standardWav = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(rom32kFile.getAbsolutePath()).toBytes();
        TapeImage turboTape32 = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .turbo(true)
                .convertToTape(rom32kFile.getAbsolutePath());
        long turboBlocks32 = turboTape32.getBlocks().stream().filter(b -> b instanceof TurboSpeedBlock).count();
        assertEquals(2L, turboBlocks32);

        TapeImage turboTape1200Stage1 = new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr44100)
                .turbo(true)
                .convertToTape(rom32kFile.getAbsolutePath());
        List<KcsBlock> stage1Kcs = turboTape1200Stage1.getBlocks().stream()
                .filter(b -> b instanceof KcsBlock)
                .map(b -> (KcsBlock) b)
                .toList();
        assertEquals(2, stage1Kcs.size());
        assertEquals(TapeSpeed.BPS_1200.pilotPulseTStates(), stage1Kcs.get(0).pilotPulseLen());
        assertEquals(TapeSpeed.BPS_1200.zeroPulseTStates(), stage1Kcs.get(0).zeroPulseLen());
        TurboSpeedBlock stage2Block = turboTape1200Stage1.getBlocks().stream()
                .filter(b -> b instanceof TurboSpeedBlock)
                .map(b -> (TurboSpeedBlock) b)
                .findFirst()
                .orElseThrow();
        assertEquals(TapeSpeed.TURBO_ZERO_PULSE, stage2Block.zeroPulseLen());
        assertEquals(TapeSpeed.TURBO_ONE_PULSE, stage2Block.onePulseLen());

        byte[] turboWav = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .turbo(true)
                .convertToWav(turboTape32).toBytes();
        assertTrue("Expected turbo WAV (" + turboWav.length + ") to be much smaller than standard WAV ("
                + standardWav.length + ")", turboWav.length < standardWav.length * 0.65);

        // TSX write + read round-trip preserves exact turbo WAV
        byte[] tsxBytes = new VirtualCas(TapeSpeed.BPS_2400).turbo(true).convertToTsx(turboTape32).toBytes();
        File tsxFile = tempFolder.newFile("t32_rt.tsx");
        Files.write(tsxFile.toPath(), tsxBytes);
        byte[] wavFromTsx = new VirtualCas(SampleRate.sr44100).convertToWav(tsxFile.getAbsolutePath()).toBytes();
        assertArrayEquals(turboWav, wavFromTsx);

        TapeImage turboTapeMega = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .turbo(true)
                .megaRam(true)
                .convertToTape(megaRomFile.getAbsolutePath());
        long turboBlocksMega = turboTapeMega.getBlocks().stream().filter(b -> b instanceof TurboSpeedBlock).count();
        assertEquals(8L, turboBlocksMega);
    }

    @Test
    public void turboStage1BannerOnlyOnMultiBlockRoms() throws Exception, FlowException {
        File rom16File = tempFolder.newFile("ban16.rom");
        Files.write(rom16File.toPath(), createRom16K((byte) 0x40));

        byte[] rom32 = new byte[32768];
        rom32[0] = 'A';
        rom32[1] = 'B';
        rom32[2] = 0x10;
        rom32[3] = 0x40;
        File rom32File = tempFolder.newFile("ban32.rom");
        Files.write(rom32File.toPath(), rom32);

        byte[] megaRom = new byte[65536];
        megaRom[0] = 'A';
        megaRom[1] = 'B';
        megaRom[2] = 0x10;
        megaRom[3] = 0x40;
        File megaFile = tempFolder.newFile("banmega.rom");
        Files.write(megaFile.toPath(), megaRom);

        String singleStage1 = extractFirstKcsDataText(new VirtualCas().turbo(true)
                .convertToTape(rom16File.getAbsolutePath()));
        String multi32Stage1 = extractFirstKcsDataText(new VirtualCas().turbo(true)
                .convertToTape(rom32File.getAbsolutePath()));
        String multiMegaStage1 = extractFirstKcsDataText(new VirtualCas().turbo(true).megaRam(true)
                .convertToTape(megaFile.getAbsolutePath()));

        assertTrue("16K single-block turbo Stage-1 must stay compact without banner",
                singleStage1.length() < 300);
        assertTrue("32K multi-block turbo Stage-1 must include ROM loading banner",
                multi32Stage1.contains("Loading, please wait..."));
        assertTrue("MegaRAM multi-block turbo Stage-1 must include MegaRAM loading banner",
                multiMegaStage1.contains("MegaRAM loading..."));
    }

    @Test
    public void profilesConfigureSpeedSampleRateAndTurboWithFluentOverrides() throws Exception, FlowException {
        assertEquals(TapeProfile.COMPATIBLE, TapeProfile.fromToken("Compatible"));
        assertEquals(TapeProfile.DEFAULT, TapeProfile.fromToken(" DEFAULT "));
        assertEquals(TapeProfile.FASTEST, TapeProfile.fromToken("fastest"));
        assertEquals(null, TapeProfile.tryFromToken("unknown"));

        File rom16File = tempFolder.newFile("prof16.rom");
        Files.write(rom16File.toPath(), createRom16K((byte) 0x40));

        assertArrayEquals(
                new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr44100).turbo(false)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes(),
                new VirtualCas(TapeProfile.COMPATIBLE)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes());
        assertArrayEquals(
                new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100).turbo(false)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes(),
                new VirtualCas(TapeProfile.DEFAULT)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes());
        assertArrayEquals(
                new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr48000).turbo(true)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes(),
                new VirtualCas(TapeProfile.FASTEST)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes());
        assertArrayEquals(
                new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr33075).turbo(true)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes(),
                new VirtualCas(TapeProfile.FASTEST).sampleRate(SampleRate.sr33075)
                        .convertToWav(rom16File.getAbsolutePath()).toBytes());
    }

    private static String extractFirstKcsDataText(TapeImage image) {
        List<KcsBlock> kcsBlocks = image.getBlocks().stream()
                .filter(b -> b instanceof KcsBlock)
                .map(b -> (KcsBlock) b)
                .toList();
        return new String(kcsBlocks.get(1).data(), StandardCharsets.ISO_8859_1);
    }

    private void assertPcmStreamMatchesWav(
            String sourcePath,
            boolean reset,
            boolean megaRam,
            TapeSpeed speed,
            boolean turbo,
            SampleRate sampleRate,
            boolean inverted) throws Exception, FlowException {
        VirtualCas vcas = new VirtualCas(speed, sampleRate)
                .turbo(turbo)
                .invertWaveform(inverted)
                .resetRom(reset)
                .megaRam(megaRam);
        TapeImage tape = vcas.convertToTape(sourcePath);
        byte[] expectedPayload = extractWavPayload(vcas.convertToWav(tape).toBytes());

        try (TapePcmInputStream stream = vcas.openPcmStream(tape)) {
            assertEquals(tape.casName(), stream.casName());
            assertEquals(expectedPayload.length, stream.getTotalSamples());
            byte[] actualPayload = readAllChunked(stream, 137);
            assertEquals(stream.getTotalSamples(), stream.getPosition());
            assertEquals(0, stream.available());
            assertArrayEquals("PCM mismatch for " + sourcePath, expectedPayload, actualPayload);
        }

        try (TapePcmInputStream fromPath = vcas.openPcmStream(sourcePath)) {
            byte[] actualFromPath = readAllChunked(fromPath, 4096);
            assertArrayEquals("PCM mismatch from path for " + sourcePath, expectedPayload, actualFromPath);
        }
    }

    private static byte[] extractWavPayload(byte[] wavBytes) {
        return Arrays.copyOfRange(wavBytes, 44, wavBytes.length);
    }

    private static byte[] readAllChunked(TapePcmInputStream stream, int chunkSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) stream.getTotalSamples());
        byte[] buffer = new byte[chunkSize];
        int read;
        while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void assertRoundTrip(
            String sourcePath,
            boolean reset,
            boolean megaRam,
            FileType expectedParsedFirstTrackType,
            int expectedTrackCount) throws Exception, FlowException {
        VirtualCas vcas = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .resetRom(reset)
                .megaRam(megaRam);

        CasOutput casOutput = vcas.convertToCas(sourcePath);
        byte[] casBytes = casOutput.toBytes();
        assertTrue(casBytes.length > CAS_MAGIC.length);
        assertArrayEquals(CAS_MAGIC, Arrays.copyOf(casBytes, CAS_MAGIC.length));
        assertEquals(expectedTrackCount, casOutput.getTracks().size());

        File casFile = tempFolder.newFile("roundtrip_" + System.nanoTime() + ".cas");
        Files.write(casFile.toPath(), casBytes);

        List<CasFile> parsedTracks = new CasUtil(casFile.getAbsolutePath()).list();
        assertEquals(expectedTrackCount, parsedTracks.size());
        assertEquals(expectedParsedFirstTrackType, parsedTracks.get(0).getFileType());

        byte[] wavDirect = vcas.convertToWav(sourcePath).toBytes();
        byte[] wavFromCas = new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(casFile.getAbsolutePath())
                .toBytes();
        assertArrayEquals("WAV mismatch after CAS round-trip for " + sourcePath, wavDirect, wavFromCas);

        byte[] tsxBytes = vcas.convertToTsx(sourcePath).toBytes();
        File tsxFile = tempFolder.newFile("roundtrip_" + System.nanoTime() + ".tsx");
        Files.write(tsxFile.toPath(), tsxBytes);
        byte[] wavFromTsx = new VirtualCas(SampleRate.sr44100).convertToWav(tsxFile.getAbsolutePath()).toBytes();
        assertArrayEquals("WAV mismatch after TSX round-trip for " + sourcePath, wavDirect, wavFromTsx);

        TapeImage parsedTsx = TsxParser.parse(tsxFile);
        assertTrue(parsedTsx.isCasCompatible());
    }

    private void assertFlowError(byte[] data, String name, String expectedMessage) throws Exception {
        File file = tempFolder.newFile(name);
        Files.write(file.toPath(), data);
        try {
            new VirtualCas(TapeSpeed.BPS_1200, SampleRate.sr11025).convertToWav(file.getAbsolutePath());
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
