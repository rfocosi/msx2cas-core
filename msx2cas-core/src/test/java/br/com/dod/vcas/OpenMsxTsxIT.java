package br.com.dod.vcas;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeProfile;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.ArchiveInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.TextBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.tsx.TsxParser;
import br.com.dod.vcas.tsx.TsxWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration tests verifying TSX reading and writing directly inside {@code openmsx 21.0}
 * (both inserting generated {@code .tsx} tapes into {@code openmsx} and rendering {@code .tsx -> .wav}
 * back for execution).
 */
public class OpenMsxTsxIT {

    private static final int EMULATED_LOAD_WAIT_SEC = 1800;
    private static final long WALL_TIMEOUT_SEC = 90L;

    private static String openMsxBin;
    private static Map<String, Path> availableRoms;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void detectEnvironment() throws IOException {
        openMsxBin = findOpenMsxExecutable();
        Assume.assumeTrue("openmsx binary not found; skipping TSX integration tests", openMsxBin != null);
        availableRoms = discoverSystemRoms();
    }

    @Test
    public void directTsxBootsTokenizedAndAsciiBasicProgramsInOpenMsx() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData();

        // 1. Tokenized BASIC -> TSX (1200 and 2400 bps) -> CLOAD + RUN
        String tokenBas = Paths.get(System.getProperty("user.dir"), "resources", "token.bas").toString();
        for (TapeSpeed speed : List.of(TapeSpeed.BPS_1200, TapeSpeed.BPS_2400)) {
            Path caseDir = tempFolder.newFolder("tsx_bas_" + speed.bps()).toPath();
            Path tsxFile = caseDir.resolve("token.tsx");
            Files.write(tsxFile, new VirtualCas(speed).convertToTsx(tokenBas).toBytes());

            String result = runTapeInOpenMsx(
                    userData, "Gradiente_Expert_GPC-1", List.of(), tsxFile,
                    "CLOAD\\r", "RUN\\r", "BAS file encoding test", false, caseDir);
            assertTrue("Expected BAS screen text in openMSX at " + speed.bps() + " bps but got: " + result,
                    result.contains("BAS file encoding test"));
        }

        // 2. Multi-block ASCII BASIC -> TSX -> LOAD"CAS:",R
        StringBuilder ascii = new StringBuilder();
        for (int line = 10; line <= 180; line += 10) {
            ascii.append(line).append(" REM MULTI BLOCK ASCII TSX TEST LINE ").append(line).append("\n");
        }
        ascii.append("200 POKE &HD000,79:POKE &HD001,75:POKE &HD002,65:POKE &HD003,83:GOTO 200\n");
        Path asciiDir = tempFolder.newFolder("tsx_ascii_multi").toPath();
        Path asciiFile = asciiDir.resolve("mascii.bas");
        Files.writeString(asciiFile, ascii.toString(), StandardCharsets.US_ASCII);
        Path asciiTsx = asciiDir.resolve("mascii.tsx");
        Files.write(asciiTsx, new VirtualCas(TapeSpeed.BPS_2400).convertToTsx(asciiFile.toString()).toBytes());

        String asciiRes = runTapeInOpenMsx(
                userData, "Gradiente_Expert_GPC-1", List.of(), asciiTsx,
                "LOAD\\\"CAS:\\\",R\\r", null, null, true, asciiDir);
        assertTrue("Expected OKAS signature from multi-block ASCII TSX but got: " + asciiRes,
                asciiRes.contains("TAG=OKAS"));
    }

    @Test
    public void directTsxBootsBinCasAndRomsStandardAndTurboInOpenMsx() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData();

        // BIN (2400 bps & turbo)
        byte[] binBytes = buildCheckBin(0xA000, 2048, "BN");
        for (boolean turbo : List.of(false, true)) {
            Path dir = tempFolder.newFolder("tsx_bin_" + (turbo ? "turbo" : "2400")).toPath();
            Path src = dir.resolve("chkbin.bin");
            Files.write(src, binBytes);
            Path tsx = dir.resolve("chkbin.tsx");
            Files.write(tsx, new VirtualCas(TapeSpeed.BPS_2400).turbo(turbo).convertToTsx(src.toString()).toBytes());
            String res = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), tsx,
                    "BLOAD\\\"CAS:\\\",R\\r", null, null, true, dir);
            assertTrue("Expected OKBN from TSX (turbo=" + turbo + ") but got: " + res, res.contains("TAG=OKBN"));
        }

        // CAS -> TSX -> openMSX
        byte[] rom16k = buildCheckRom(16384, 0x0000, 0x4010, List.of(new int[] { 0x7FFE, 0x5A }), "16", false);
        rom16k[0x3FFE] = 0x5A;
        Path casDir = tempFolder.newFolder("tsx_from_cas").toPath();
        Path rom16Path = casDir.resolve("r16.rom");
        Files.write(rom16Path, rom16k);
        Path casPath = casDir.resolve("r16.cas");
        Files.write(casPath, new VirtualCas().convertToCas(rom16Path.toString()).toBytes());
        Path casTsxPath = casDir.resolve("r16cas.tsx");
        Files.write(casTsxPath, new VirtualCas(TapeSpeed.BPS_2400).convertToTsx(casPath.toString()).toBytes());
        String casRes = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), casTsxPath,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, casDir);
        assertTrue("Expected OK16 from CAS->TSX but got: " + casRes, casRes.contains("TAG=OK16"));

        // ROM 16K Page-2 (2400 & turbo)
        byte[] rom16kPg2 = buildCheckRom(16384, 0x0000, 0x8010, List.of(new int[] { 0xBFFE, 0x99 }), "P2", false);
        rom16kPg2[0x3FFE] = (byte) 0x99;
        assertRomTsxBoots(userData, "r16p2_2400", rom16kPg2, TapeSpeed.BPS_2400, false, false, false, List.of(), "OKP2");
        assertRomTsxBoots(userData, "r16p2_turbo", rom16kPg2, TapeSpeed.BPS_2400, true, false, false, List.of(), "OKP2");

        // ROM 16K via TapeProfile.FASTEST -> TSX -> openMSX
        Path fastestDir = tempFolder.newFolder("tsx_r16_fastest").toPath();
        Path fastestRomPath = fastestDir.resolve("r16f.rom");
        Files.write(fastestRomPath, rom16k);
        Path fastestTsxPath = fastestDir.resolve("r16f.tsx");
        Files.write(fastestTsxPath, new VirtualCas(TapeProfile.FASTEST).convertToTsx(fastestRomPath.toString()).toBytes());
        String fastestRes = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), fastestTsxPath,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, fastestDir);
        assertTrue("Expected OK16 from FASTEST profile TSX but got: " + fastestRes, fastestRes.contains("TAG=OK16"));

        // ROM 32K (2400 & turbo)
        byte[] konamiPage2Map = konamiPage2MapStub();
        byte[] rom32k = buildCheckRom(32768, 0x0000, 0x4010 + konamiPage2Map.length,
                List.of(new int[] { 0x7FFE, 0x3C }, new int[] { 0xBFFE, 0xA5 }), "32", false);
        rom32k[0x02] = 0x10;
        rom32k[0x03] = 0x40;
        System.arraycopy(konamiPage2Map, 0, rom32k, 0x10, konamiPage2Map.length);
        rom32k[0x3FFE] = 0x3C;
        rom32k[0x7FFE] = (byte) 0xA5;
        assertRomTsxBoots(userData, "r32_2400", rom32k, TapeSpeed.BPS_2400, false, true, false, List.of(), "OK32");
        assertRomTsxBoots(userData, "r32_turbo", rom32k, TapeSpeed.BPS_2400, true, false, false, List.of(), "OK32");

        // ROM 49K (turbo)
        byte[] rom49k = buildCheckRom(50176, 0x4000, 0x4010 + konamiPage2Map.length,
                List.of(new int[] { 0x7FFE, 0x22 }, new int[] { 0x8100, 0x33 }, new int[] { 0xC100, 0x44 }),
                "49", false);
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        System.arraycopy(konamiPage2Map, 0, rom49k, 0x4010, konamiPage2Map.length);
        rom49k[0x7FFE] = 0x22;
        rom49k[0x8100] = 0x33;
        rom49k[0xC100] = 0x44;
        assertRomTsxBoots(userData, "r49_turbo", rom49k, TapeSpeed.BPS_2400, true, true, false, List.of(), "OK49");

        // MegaRAM 128K Konami4 (2400 & turbo)
        byte[] mega128 = buildKonami4CheckRom(128 * 1024, "MR");
        assertRomTsxBoots(userData, "mega_2400", mega128, TapeSpeed.BPS_2400, false, true, true,
                List.of("-ext", "megaram"), "OKMR");
        assertRomTsxBoots(userData, "mega_turbo", mega128, TapeSpeed.BPS_2400, true, false, true,
                List.of("-ext", "megaram"), "OKMR");
    }

    @Test
    public void tsxToWavRoundTripsAndMultiBlockCustomTsxBootInOpenMsx() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData();
        byte[] rom16k = buildCheckRom(16384, 0x0000, 0x4010, List.of(new int[] { 0x7FFE, 0x5A }), "16", false);
        rom16k[0x3FFE] = 0x5A;

        Path workDir = tempFolder.newFolder("tsx_wav_rt").toPath();
        Path romPath = workDir.resolve("r16.rom");
        Files.write(romPath, rom16k);

        // 1. Standard #4B TSX -> WAV (native timing)
        Path tsx1200 = workDir.resolve("r16_1200.tsx");
        Files.write(tsx1200, new VirtualCas(TapeSpeed.BPS_1200).convertToTsx(romPath.toString()).toBytes());
        Path wavNative = workDir.resolve("r16_native.wav");
        Files.write(wavNative, new VirtualCas(SampleRate.sr44100).convertToWav(tsx1200.toString()).toBytes());
        String resNative = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), wavNative,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, workDir.resolve("run_native"));
        assertTrue("Expected OK16 from native TSX->WAV but got: " + resNative, resNative.contains("TAG=OK16"));

        // 2. Re-timed #4B TSX (1200 -> 2400) -> WAV
        Path wavRetimed = workDir.resolve("r16_retimed.wav");
        Files.write(wavRetimed, new VirtualCas(TapeSpeed.BPS_2400, SampleRate.sr44100)
                .convertToWav(tsx1200.toString()).toBytes());
        String resRetimed = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), wavRetimed,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, workDir.resolve("run_retimed"));
        assertTrue("Expected OK16 from re-timed TSX->WAV but got: " + resRetimed, resRetimed.contains("TAG=OK16"));

        // 3. Turbo #11 TSX -> WAV (normal and inverted at 44100 Hz)
        Path tsxTurbo = workDir.resolve("r16_turbo.tsx");
        Files.write(tsxTurbo, new VirtualCas(TapeSpeed.BPS_2400).turbo(true).convertToTsx(romPath.toString()).toBytes());
        Path wavTurbo = workDir.resolve("r16_turbo.wav");
        Files.write(wavTurbo, new VirtualCas(SampleRate.sr44100).convertToWav(tsxTurbo.toString()).toBytes());
        String resTurbo = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), wavTurbo,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, workDir.resolve("run_turbo"));
        assertTrue("Expected OK16 from turbo TSX->WAV but got: " + resTurbo, resTurbo.contains("TAG=OK16"));

        Path wavTurboInv = workDir.resolve("r16_turbo_inv.wav");
        Files.write(wavTurboInv, new VirtualCas(SampleRate.sr44100).invertWaveform(true)
                .convertToWav(tsxTurbo.toString()).toBytes());
        String resTurboInv = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), wavTurboInv,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, workDir.resolve("run_turbo_inv"));
        assertTrue("Expected OK16 from inverted turbo TSX->WAV but got: " + resTurboInv,
                resTurboInv.contains("TAG=OK16"));

        // 4. Synthetic multi-block TSX (#32 Archive, #30 Text, #4B Stage-1, #20(0) Stop-the-Tape, #12 PureTone, #11 Turbo) -> WAV
        TapeImage parsedTurbo = TsxParser.parse(tsxTurbo.toFile());
        List<TapeBlock> customBlocks = new ArrayList<>();
        customBlocks.add(new ArchiveInfoBlock(List.of(new ArchiveInfoBlock.Entry(0x00, "Custom Multi TSX"))));
        customBlocks.add(new TextBlock("Synthetic TSX IT fixture"));
        for (TapeBlock b : parsedTurbo.getBlocks()) {
            if (b instanceof ArchiveInfoBlock || b instanceof TextBlock) {
                continue;
            }
            if (b instanceof TurboSpeedBlock) {
                customBlocks.add(new PauseBlock(0)); // Stop-the-Tape -> 4s silence in WAV
                customBlocks.add(new PureToneBlock(TapeSpeed.TURBO_PILOT_PULSE, 64)); // Extra lead tone before #11
            }
            customBlocks.add(b);
        }
        Path customTsx = workDir.resolve("custom_multi.tsx");
        Files.write(customTsx, new TsxWriter(new TapeImage("cmulti", customBlocks)).toBytes());
        Path customWav = workDir.resolve("custom_multi.wav");
        Files.write(customWav, new VirtualCas(SampleRate.sr44100).convertToWav(customTsx.toString()).toBytes());
        String resCustom = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", List.of(), customWav,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, workDir.resolve("run_custom"));
        assertTrue("Expected OK16 from synthetic multi-block TSX->WAV but got: " + resCustom,
                resCustom.contains("TAG=OK16"));
    }

    private void assertRomTsxBoots(
            Path userData,
            String caseName,
            byte[] romBytes,
            TapeSpeed speed,
            boolean turbo,
            boolean reset,
            boolean megaRam,
            List<String> extraArgs,
            String expectedTag) throws Exception, FlowException {
        Path caseDir = tempFolder.newFolder("tsx_" + caseName).toPath();
        Path romPath = caseDir.resolve(caseName + ".rom");
        Files.write(romPath, romBytes);
        Path tsxPath = caseDir.resolve(caseName + ".tsx");
        Files.write(tsxPath, new VirtualCas(speed).turbo(turbo).resetRom(reset).megaRam(megaRam)
                .convertToTsx(romPath.toString()).toBytes());

        String result = runTapeInOpenMsx(userData, "Gradiente_Expert_GPC-1", extraArgs, tsxPath,
                "BLOAD\\\"CAS:\\\",R\\r", null, null, true, caseDir);
        assertTrue("Expected signature '" + expectedTag + "' for " + caseName + " TSX but got: " + result,
                result.contains("TAG=" + expectedTag));
    }

    private String runTapeInOpenMsx(
            Path userData,
            String machine,
            List<String> extraArgs,
            Path tapeFile,
            String basicCommand,
            String followUpCommand,
            String expectedScreenSubstring,
            boolean pollMemorySignature,
            Path workDir) throws IOException, InterruptedException {
        Files.createDirectories(workDir);
        Path outFile = workDir.resolve("result.txt");
        Path tclFile = workDir.resolve("run.tcl");
        Files.writeString(tclFile,
                buildTclScript(basicCommand, followUpCommand, expectedScreenSubstring, pollMemorySignature),
                StandardCharsets.UTF_8);

        List<String> cmd = new ArrayList<>();
        cmd.add(openMsxBin);
        cmd.add("-machine");
        cmd.add(machine);
        cmd.addAll(extraArgs);
        cmd.add("-script");
        cmd.add(tclFile.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.put("OPENMSX_USER_DATA", userData.toString());
        env.put("SDL_VIDEODRIVER", "dummy");
        env.put("SDL_AUDIODRIVER", "dummy");
        env.put("TEST_TAPE", tapeFile.toString());
        env.put("OUT_FILE", outFile.toString());
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        if (!proc.waitFor(WALL_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new AssertionError("openMSX timed out for " + tapeFile.getFileName());
        }

        String procLog = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("openMSX exited non-zero for " + tapeFile.getFileName() + ": " + procLog, 0, proc.exitValue());
        assertTrue("Missing openMSX output file for " + tapeFile.getFileName(), Files.exists(outFile));
        return Files.readString(outFile, StandardCharsets.UTF_8).trim();
    }

    private static String buildTclScript(
            String basicCommand,
            String followUpCommand,
            String expectedScreenSubstring,
            boolean pollMemorySignature) {
        String screenCheck = expectedScreenSubstring != null
                ? "    catch { get_screen } scr\n"
                        + "    if {[string first \"" + expectedScreenSubstring + "\" $scr] >= 0} {\n"
                        + "        write_result\n"
                        + "        quit\n"
                        + "    }\n"
                : "";
        String memCheck = pollMemorySignature
                ? "    set b0 [debug read \"memory\" 0xD000]\n"
                        + "    set b1 [debug read \"memory\" 0xD001]\n"
                        + "    if {($b0 == 0x4F && $b1 == 0x4B) || ($b0 == 0x45 && $b1 == 0x52)} {\n"
                        + "        write_result\n"
                        + "        quit\n"
                        + "    }\n"
                : "";
        String followUpTcl = followUpCommand != null
                ? "after time 30 {\n"
                        + "    type_via_keybuf \"" + followUpCommand + "\"\n"
                        + "}\n"
                : "";
        return "set renderer none\n"
                + "set throttle off\n"
                + "set max_wait " + EMULATED_LOAD_WAIT_SEC + "\n"
                + "proc write_result {} {\n"
                + "    global env\n"
                + "    set b0 [debug read \"memory\" 0xD000]\n"
                + "    set b1 [debug read \"memory\" 0xD001]\n"
                + "    set b2 [debug read \"memory\" 0xD002]\n"
                + "    set b3 [debug read \"memory\" 0xD003]\n"
                + "    set fp [open $env(OUT_FILE) \"w\"]\n"
                + "    puts $fp [format \"t=%.2f TAG=%c%c%c%c PC=%04X\" "
                + "[machine_info time] $b0 $b1 $b2 $b3 [reg PC]]\n"
                + "    catch { get_screen } scr\n"
                + "    puts $fp \"SCREEN:\\n$scr\"\n"
                + "    close $fp\n"
                + "}\n"
                + "after time $max_wait {\n"
                + "    write_result\n"
                + "    quit\n"
                + "}\n"
                + "catch { set autoruncassettes off }\n"
                + "cassetteplayer insert $env(TEST_TAPE)\n"
                + "after time 7 {\n"
                + "    type_via_keybuf \"" + basicCommand + "\"\n"
                + "}\n"
                + followUpTcl
                + "proc poll_done {} {\n"
                + memCheck
                + screenCheck
                + "    after time 5 poll_done\n"
                + "}\n"
                + "after time 20 poll_done\n";
    }

    private static byte[] konamiPage2MapStub() {
        return new byte[] {
                (byte) 0xCD, 0x38, 0x01,
                0x0F, 0x0F,
                (byte) 0xE6, 0x03,
                0x4F, 0x06, 0x00,
                0x21, (byte) 0xC1, (byte) 0xFC,
                0x09,
                0x7E, (byte) 0xE6, (byte) 0x80, (byte) 0xB1,
                0x4F,
                0x23, 0x23, 0x23, 0x23,
                0x7E, (byte) 0xE6, 0x0C, (byte) 0xB1,
                0x26, (byte) 0x80,
                (byte) 0xCD, 0x24, 0x00
        };
    }

    private static byte[] buildKonami4CheckRom(int sizeBytes, String tag2Chars) {
        byte[] rom = new byte[sizeBytes];
        for (int i = 0x100; i < rom.length; i++) {
            rom[i] = (byte) 0xFF;
        }
        int totalBanks = sizeBytes / 8192;
        for (int b = 1; b < totalBanks; b++) {
            rom[b * 8192] = (byte) ((b * 17 + 0x11) & 0xFF);
        }
        int initAddr = 0x4020;
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = (byte) (initAddr & 0xFF);
        rom[3] = (byte) ((initAddr >> 8) & 0xFF);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(konamiPage2MapStub());
        int bankLast = totalBanks - 1;
        out.write(0x3E);
        out.write(bankLast & 0xFF);
        out.write(0x32);
        out.write(0x00);
        out.write((byte) 0xA0);
        out.write(0x3A);
        out.write(0x00);
        out.write((byte) 0xA0);
        out.write(0xFE);
        out.write((byte) ((bankLast * 17 + 0x11) & 0xFF));
        out.write(0x20);
        int failPatch = out.size();
        out.write(0x00);

        writeSignatureLoop(out, "OK" + tag2Chars);
        int failPos = out.size();
        writeSignatureLoop(out, "ER" + tag2Chars);
        byte[] code = out.toByteArray();
        code[failPatch] = (byte) (failPos - (failPatch + 1));
        System.arraycopy(code, 0, rom, initAddr - 0x4000, code.length);
        return rom;
    }

    private static byte[] buildCheckRom(
            int sizeBytes,
            int abOffset,
            int initAddr,
            List<int[]> checks,
            String tag,
            boolean withIm1VdpAck) {
        byte[] rom = new byte[sizeBytes];
        rom[abOffset] = 'A';
        rom[abOffset + 1] = 'B';
        rom[abOffset + 2] = (byte) (initAddr & 0xFF);
        rom[abOffset + 3] = (byte) ((initAddr >> 8) & 0xFF);
        if (withIm1VdpAck) {
            byte[] isr = new byte[] { (byte) 0xF5, (byte) 0xDB, (byte) 0x99, (byte) 0xF1, (byte) 0xFB, (byte) 0xC9 };
            System.arraycopy(isr, 0, rom, 0x0038, isr.length);
        }
        byte[] code = makeCheckCode(checks, tag);
        int baseAddr = abOffset == 0 ? (initAddr & 0xC000) : 0;
        System.arraycopy(code, 0, rom, initAddr - baseAddr, code.length);
        return rom;
    }

    private static byte[] buildCheckBin(int startAddr, int payloadSize, String tag) {
        int endAddr = startAddr + payloadSize - 1;
        byte[] bin = new byte[7 + payloadSize];
        bin[0] = (byte) 0xFE;
        bin[1] = (byte) (startAddr & 0xFF);
        bin[2] = (byte) ((startAddr >> 8) & 0xFF);
        bin[3] = (byte) (endAddr & 0xFF);
        bin[4] = (byte) ((endAddr >> 8) & 0xFF);
        bin[5] = (byte) (startAddr & 0xFF);
        bin[6] = (byte) ((startAddr >> 8) & 0xFF);
        for (int i = 0; i < payloadSize; i++) {
            bin[7 + i] = (byte) ((i * 13 + 7) & 0xFF);
        }
        bin[7 + payloadSize - 1] = 0x5A;
        byte[] code = makeCheckCode(List.of(new int[] { endAddr, 0x5A }), tag);
        System.arraycopy(code, 0, bin, 7, code.length);
        return bin;
    }

    private static byte[] makeCheckCode(List<int[]> checks, String tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> failPatchPositions = new ArrayList<>();
        for (int[] check : checks) {
            int addr = check[0];
            int val = check[1] & 0xFF;
            out.write(0x3A);
            out.write(addr & 0xFF);
            out.write((addr >> 8) & 0xFF);
            out.write(0xFE);
            out.write(val);
            out.write(0x20);
            failPatchPositions.add(out.size());
            out.write(0x00);
        }
        writeSignatureLoop(out, "OK" + tag);
        int failPos = out.size();
        writeSignatureLoop(out, "ER" + tag);

        byte[] code = out.toByteArray();
        for (int idx : failPatchPositions) {
            code[idx] = (byte) (failPos - (idx + 1));
        }
        return code;
    }

    private static void writeSignatureLoop(ByteArrayOutputStream out, String sig) {
        byte[] bytes = sig.getBytes(StandardCharsets.US_ASCII);
        out.write(0x21);
        out.write(0x00);
        out.write(0xD0);
        for (int i = 0; i < 4; i++) {
            out.write(0x36);
            out.write(bytes[i]);
            if (i < 3) {
                out.write(0x23);
            }
        }
        out.write(0xFB);
        out.write(0x18);
        out.write(0xFE);
    }

    private Path prepareUserData() throws IOException {
        Path userData = tempFolder.newFolder("openmsx_tsx_data_" + System.nanoTime()).toPath();
        Path romsDir = Files.createDirectories(userData.resolve("systemroms"));
        for (Map.Entry<String, Path> entry : availableRoms.entrySet()) {
            Files.copy(entry.getValue(), romsDir.resolve(entry.getKey()), StandardCopyOption.REPLACE_EXISTING);
        }
        return userData;
    }

    private static boolean hasRom(String name, String... expectedSha1s) throws IOException {
        if (availableRoms.containsKey(name)) {
            return true;
        }
        for (Path path : availableRoms.values()) {
            String sha1 = sha1Hex(Files.readAllBytes(path));
            for (String expectedSha1 : expectedSha1s) {
                if (sha1.equals(expectedSha1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sha1Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Path> discoverSystemRoms() throws IOException {
        Map<String, Path> roms = new HashMap<>();
        List<Path> roots = new ArrayList<>();
        addIfNonBlank(roots, System.getProperty("openmsx.roms"));
        addIfNonBlank(roots, System.getenv("OPENMSX_SYSTEM_ROMS"));
        String userData = System.getenv("OPENMSX_USER_DATA");
        if (userData != null && !userData.isBlank()) {
            roots.add(Paths.get(userData, "systemroms"));
        }
        roots.add(Paths.get(System.getProperty("user.home"), ".openMSX", "share", "systemroms"));
        roots.add(Paths.get("/usr/share/openmsx/systemroms"));

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString();
                    String lower = name.toLowerCase();
                    if ((lower.endsWith(".rom") || lower.endsWith(".bin")) && !roms.containsKey(name)) {
                        roms.put(name, p);
                    }
                });
            }
        }
        return roms;
    }

    private static void addIfNonBlank(List<Path> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(Paths.get(value));
        }
    }

    private static String findOpenMsxExecutable() {
        String prop = System.getProperty("openmsx.bin");
        if (prop != null && !prop.isBlank() && Files.isExecutable(Paths.get(prop))) {
            return prop;
        }
        String env = System.getenv("OPENMSX_BIN");
        if (env != null && !env.isBlank() && Files.isExecutable(Paths.get(env))) {
            return env;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                Path candidate = Paths.get(dir, "openmsx");
                if (Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        Path usrBin = Paths.get("/usr/bin/openmsx");
        return Files.isExecutable(usrBin) ? usrBin.toString() : null;
    }
}
