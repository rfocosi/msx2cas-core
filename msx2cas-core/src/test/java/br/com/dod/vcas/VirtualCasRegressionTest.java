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
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.wav.WavEncoder;
import br.com.dod.vcas.wav.WavOutput;

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
        assertEquals(1969309, wavReset.length);
        assertEquals("08a0ff73bd3cc15ccd313cfb4ce6476e6b0aefc79f8a498fce814bcdb220613f", sha256(wavReset));

        byte[] rom16kHead80 = rom16k.clone();
        rom16kHead80[3] = (byte) 0x80;
        File file16kHead80 = tempFolder.newFile("rom16h.rom");
        Files.write(file16kHead80.toPath(), rom16kHead80);

        byte[] wavHead80 = new VirtualCas(SampleRate.sr11025).convert(file16kHead80.getAbsolutePath()).toBytes();
        assertEquals(1940709, wavHead80.length);
        assertEquals("7309e3e452c93af8e9fd2523af4ee73ac55f3ab04075e3634bbe68b7480be520", sha256(wavHead80));
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
        assertEquals(3936764, normalWav.length);
        assertEquals("c06316b4dde2ab80730a2e1c78225d4a9732a4d2136470dc879e85b6469dc58d", sha256(normalWav));

        byte[] resetWav = new VirtualCas(SampleRate.sr11025).resetRom(true).convert(file32k.getAbsolutePath()).toBytes();
        assertEquals(3950074, resetWav.length);
        assertEquals("6824e643f7b42b6581c039ee58b9f979cf60e64bcd09da123688192b05706e40", sha256(resetWav));
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
        assertEquals(5903119, normalWav.length);
        assertEquals("30e66d0a340c1ebf06c70297df6648fe0de80024952b17de45eaf8b11cb637ad", sha256(normalWav));

        byte[] resetWav = new VirtualCas(SampleRate.sr11025).resetRom(true).convert(file49k.getAbsolutePath()).toBytes();
        assertEquals(5917309, resetWav.length);
        assertEquals("682b751301a689890a0d810d28937e036fc0f573473ed4f405d480b3732f0e5c", sha256(resetWav));
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
        assertEquals(256509, wavFromFile.length);
        assertEquals("c28a8afca98ff82d06f7ea6b18a02508fbb00ff457ec589445978e4ca85e85fa", sha256(wavFromFile));

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
