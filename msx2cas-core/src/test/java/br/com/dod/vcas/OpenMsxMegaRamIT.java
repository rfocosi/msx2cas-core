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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration tests verifying MegaRAM (port 0x8E, Konami4) cassette loading
 * headless inside {@code openmsx}.
 */
public class OpenMsxMegaRamIT {

    private static final int EMULATED_LOAD_WAIT_SEC = 1800;
    private static final long WALL_TIMEOUT_SEC = 90L;

    private static String openMsxBin;
    private static Map<String, Path> availableRoms;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void detectEnvironment() throws IOException {
        openMsxBin = findOpenMsxExecutable();
        Assume.assumeTrue("openmsx binary not found; skipping MegaRAM integration tests", openMsxBin != null);
        availableRoms = discoverSystemRoms();
    }

    @Test
    public void gradienteExpertWithMegaRam512kBoots128kKonami4WithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MR");
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "mr128r", rom128k, true, false, false);
        assertTrue("Expected OKMR signature but got: " + result, result.contains("TAG=OKMR"));
    }

    @Test
    public void gradienteExpertWithMegaRam512kBoots128kKonami4Direct() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MR");
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "mr128d", rom128k, false, false, false);
        assertTrue("Expected OKMR signature but got: " + result, result.contains("TAG=OKMR"));
    }

    @Test
    public void yamahaAx200WithMegaRam512kBoots256kKonami4WithReset() throws Exception, FlowException {
        Assume.assumeTrue("Yamaha AX200 system ROMs not found",
                hasAllRoms("ax200_basic-bios1.rom", "ax200_arabic.rom")
                        && (hasAllRoms("ax200_music.rom") || hasAllRoms("sfg-05.rom")));

        byte[] rom256k = buildKonami4CheckRom(256 * 1024, "25");
        String result = runCase("Yamaha_AX200", List.of("-ext", "megaram"),
                "ax256r", rom256k, true, true, false);
        assertTrue("Expected OK25 signature but got: " + result, result.contains("TAG=OK25"));
    }

    @Test
    public void gradienteExpertWithoutMegaRamReportsError() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MR");
        String result = runCase("Gradiente_Expert_GPC-1", List.of(),
                "nomram", rom128k, true, false, true);
        assertTrue("Expected NO MEGARAM error on screen but got: " + result,
                result.contains("Fail: NO MEGARAM!"));
    }

    @Test
    public void nationalCf2000WithRam512kDoesNotFalselyDetectMegaRam() throws Exception, FlowException {
        Assume.assumeTrue("National CF-2000 BIOS ROM not found",
                hasRom("cf-2000_basic-bios1.rom", "302afb5d8be26c758309ca3df611ae69cced2821"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MR");
        String result = runCase("National_CF-2000", List.of("-ext", "ram512k"),
                "cf512", rom128k, true, false, true);
        assertTrue("Expected NO MEGARAM error with standard 512K RAM mapper but got: " + result,
                result.contains("Fail: NO MEGARAM!"));
    }

    @Test
    public void gradienteExpertAutoPatchesAndBoots128kAscii8WithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] romAscii8 = buildPatchedMapperCheckRom(128 * 1024, "A8",
                0x6800, 0x7000, 0x7800, false, false);
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "ascii8r", romAscii8, true, false, false);
        assertTrue("Expected OKA8 signature for patched ASCII8 ROM but got: " + result, result.contains("TAG=OKA8"));
    }

    @Test
    public void gradienteExpertAutoPatchesAndBoots128kKonami5WithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] romK5 = buildPatchedMapperCheckRom(128 * 1024, "K5",
                0x7000, 0x9000, 0xB000, true, false);
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "k5rst", romK5, true, false, false);
        assertTrue("Expected OKK5 signature for patched Konami5 ROM but got: " + result, result.contains("TAG=OKK5"));
    }

    @Test
    public void gradienteExpertAutoPatchesAndBoots128kAscii16WithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] romA16 = buildAscii16CheckRom(128 * 1024, "16");
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "a16rst", romA16, true, false, false);
        assertTrue("Expected OK16 signature for patched ASCII16 ROM but got: " + result, result.contains("TAG=OK16"));
    }

    @Test
    public void gradienteExpertWithMegaRam512kBoots128kKonami4FromCasWithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MR");
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "mr128c", rom128k, true, false, false, true);
        assertTrue("Expected OKMR signature from CAS but got: " + result, result.contains("TAG=OKMR"));
    }

    @Test
    public void gradienteExpertAutoPatchesAndBoots128kAscii16FromCasWithReset() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] romA16 = buildAscii16CheckRom(128 * 1024, "16");
        String result = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                "a16cas", romA16, true, false, false, true);
        assertTrue("Expected OK16 signature from CAS for patched ASCII16 ROM but got: " + result,
                result.contains("TAG=OK16"));
    }

    @Test
    public void gradienteExpertWithMegaRam512kTurboBoots128kKonami4WithResetAndDirect()
            throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        byte[] rom128k = buildKonami4CheckRom(128 * 1024, "MT");
        try {
            String resultReset = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                    "mt128r", rom128k, true, false, false, false, true, SampleRate.sr22050.invertWaveForm(false));
            assertTrue("Expected OKMT signature in turbo reset mode but got: " + resultReset,
                    resultReset.contains("TAG=OKMT"));

            String resultDirect = runCase("Gradiente_Expert_GPC-1", List.of("-ext", "megaram"),
                    "mt128d", rom128k, false, false, false, false, true, SampleRate.sr33075.invertWaveForm(true));
            assertTrue("Expected OKMT signature in turbo direct 3600i mode but got: " + resultDirect,
                    resultDirect.contains("TAG=OKMT"));
        } finally {
            for (SampleRate rate : SampleRate.values()) {
                rate.invertWaveForm(false);
            }
        }
    }

    private String runCase(
            String machine,
            List<String> extraArgs,
            String caseName,
            byte[] romBytes,
            boolean reset,
            boolean holdCtrlOnBoot,
            boolean expectErrorScreen) throws Exception, FlowException {
        return runCase(machine, extraArgs, caseName, romBytes, reset, holdCtrlOnBoot, expectErrorScreen, false);
    }

    private String runCase(
            String machine,
            List<String> extraArgs,
            String caseName,
            byte[] romBytes,
            boolean reset,
            boolean holdCtrlOnBoot,
            boolean expectErrorScreen,
            boolean useCas) throws Exception, FlowException {
        return runCase(machine, extraArgs, caseName, romBytes, reset, holdCtrlOnBoot, expectErrorScreen, useCas,
                false, SampleRate.sr22050);
    }

    private String runCase(
            String machine,
            List<String> extraArgs,
            String caseName,
            byte[] romBytes,
            boolean reset,
            boolean holdCtrlOnBoot,
            boolean expectErrorScreen,
            boolean useCas,
            boolean turbo,
            SampleRate sampleRate) throws Exception, FlowException {
        Path userData = prepareUserData();
        File romFile = tempFolder.newFile(caseName + ".rom");
        Files.write(romFile.toPath(), romBytes);

        Path tapePath;
        if (useCas) {
            byte[] cas = new VirtualCas()
                    .resetRom(reset)
                    .megaRam(true)
                    .convertToCas(romFile.getAbsolutePath())
                    .toBytes();
            tapePath = tempFolder.newFile(caseName + ".cas").toPath();
            Files.write(tapePath, cas);
        } else {
            byte[] wav = new VirtualCas(sampleRate)
                    .resetRom(reset)
                    .megaRam(true)
                    .turbo(turbo)
                    .convert(romFile.getAbsolutePath())
                    .toBytes();
            tapePath = tempFolder.newFile(caseName + ".wav").toPath();
            Files.write(tapePath, wav);
        }

        Path outFile = tempFolder.getRoot().toPath().resolve(caseName + ".out");
        Path tclFile = tempFolder.getRoot().toPath().resolve(caseName + ".tcl");
        int waitAfterSec = expectErrorScreen ? 150 : EMULATED_LOAD_WAIT_SEC;
        Files.writeString(tclFile, buildOpenMsxTclScript(holdCtrlOnBoot, waitAfterSec, expectErrorScreen),
                StandardCharsets.UTF_8);

        List<String> cmd = new ArrayList<>();
        cmd.add(openMsxBin);
        cmd.add("-machine");
        cmd.add(machine);
        cmd.addAll(extraArgs);
        cmd.add("-script");
        cmd.add(tclFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("OPENMSX_USER_DATA", userData.toAbsolutePath().toString());
        env.put("TEST_WAV", tapePath.toAbsolutePath().toString());
        env.put("OUT_FILE", outFile.toAbsolutePath().toString());

        Process proc = pb.start();
        if (!proc.waitFor(WALL_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new AssertionError("openMSX timed out after " + WALL_TIMEOUT_SEC + "s for " + machine + "/" + caseName);
        }

        String procLog = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("openMSX exited non-zero for " + machine + "/" + caseName + ": " + procLog, 0, proc.exitValue());
        assertTrue("Missing openMSX output file for " + machine + "/" + caseName, Files.exists(outFile));
        return Files.readString(outFile, StandardCharsets.UTF_8).trim();
    }

    /**
     * Builds a synthetic Konami4 MegaROM ({@code totalBanks * 8KB}): writes a unique tag byte at
     * offset 0 of every 8 KB bank (except Bank 0 which starts with {@code AB}), switches banks at
     * {@code 0x6000}, {@code 0x8000}, and {@code 0xA000} including the last bank, verifies Bank 0
     * stays read-only, and writes {@code OK<tag>} or {@code ER<tag>} to {@code 0xD000..0xD003}.
     */
    private static byte[] buildKonami4CheckRom(int sizeBytes, String tag2Chars) {
        return buildPatchedMapperCheckRom(sizeBytes, tag2Chars, 0x6000, 0x8000, 0xA000, false, false);
    }

    /**
     * Builds a synthetic 8KB-banked MegaROM whose bank registers live at {@code regW1}, {@code regW2},
     * and {@code regW3} (while reading windows {@code 0x6000}, {@code 0x8000}, {@code 0xA000}).
     */
    private static byte[] buildPatchedMapperCheckRom(
            int sizeBytes,
            String tag2Chars,
            int regW1,
            int regW2,
            int regW3,
            boolean includeSccWrites,
            boolean padWithZero) {
        byte[] rom = new byte[sizeBytes];
        if (!padWithZero) {
            for (int i = 0x100; i < rom.length; i++) {
                rom[i] = (byte) 0xFF;
            }
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

        int bankA = Math.min(4, totalBanks - 1);
        int bankB = Math.min(9, totalBanks - 1);
        int bankLast = totalBanks - 1;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] mapPage2ToCartSlot = {
                (byte) 0xCD, 0x38, 0x01,                   // CALL 0x0138 (RSLREG)
                0x0F, 0x0F,                                // RRCA; RRCA
                (byte) 0xE6, 0x03,                         // AND 0x03
                0x4F, 0x06, 0x00,                          // LD C, A; LD B, 0
                0x21, (byte) 0xC1, (byte) 0xFC,            // LD HL, 0xFCC1 (EXPTBL)
                0x09,                                      // ADD HL, BC
                0x7E, (byte) 0xE6, (byte) 0x80, (byte) 0xB1, // LD A, (HL); AND 0x80; OR C
                0x4F,                                      // LD C, A
                0x23, 0x23, 0x23, 0x23,                    // INC HL x4 (SLTTBL)
                0x7E, (byte) 0xE6, 0x0C, (byte) 0xB1,      // LD A, (HL); AND 0x0C; OR C
                0x26, (byte) 0x80,                         // LD H, 0x80
                (byte) 0xCD, 0x24, 0x00                    // CALL 0x0024 (ENASLT)
        };
        out.writeBytes(mapPage2ToCartSlot);
        List<Integer> failPatchPositions = new ArrayList<>();

        emitCheckRegWindow(out, regW1, 0x6000, bankA, (bankA * 17 + 0x11) & 0xFF, failPatchPositions);
        emitCheckRegWindow(out, regW2, 0x8000, bankB, (bankB * 17 + 0x11) & 0xFF, failPatchPositions);
        emitCheckRegWindow(out, regW3, 0xA000, bankLast, (bankLast * 17 + 0x11) & 0xFF, failPatchPositions);

        if (includeSccWrites) {
            // Write dummy SCC register bytes to 0x988F/0x9800 and confirm Window 2 (0x8000) stays at bankB!
            out.write(0x3E); // LD A, 0x0F
            out.write(0x0F);
            out.write(0x32); // LD (0x988F), A
            out.write(0x8F);
            out.write(0x98);
            out.write(0x3A); // LD A, (0x8000)
            out.write(0x00);
            out.write(0x80);
            out.write(0xFE); // CP expected
            out.write((bankB * 17 + 0x11) & 0xFF);
            out.write(0x20);
            failPatchPositions.add(out.size());
            out.write(0x00);
        }

        // Verify Bank 0 at 0x4000 still holds 'A'
        out.write(0x3A);
        out.write(0x00);
        out.write(0x40);
        out.write(0xFE);
        out.write('A');
        out.write(0x20);
        failPatchPositions.add(out.size());
        out.write(0x00);

        writeSignatureLoop(out, "OK" + tag2Chars);
        int failPos = out.size();
        writeSignatureLoop(out, "ER" + tag2Chars);

        byte[] code = out.toByteArray();
        for (int idx : failPatchPositions) {
            code[idx] = (byte) (failPos - (idx + 1));
        }
        System.arraycopy(code, 0, rom, initAddr - 0x4000, code.length);
        return rom;
    }

    private static byte[] buildAscii16CheckRom(int sizeBytes, String tag2Chars) {
        byte[] rom = new byte[sizeBytes];
        for (int i = 0x100; i < rom.length; i++) {
            rom[i] = (byte) 0xFF;
        }
        int total8kBanks = sizeBytes / 8192;
        for (int b = 1; b < total8kBanks; b++) {
            rom[b * 8192] = (byte) ((b * 17 + 0x11) & 0xFF);
        }

        int initAddr = 0x4020;
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = (byte) (initAddr & 0xFF);
        rom[3] = (byte) ((initAddr >> 8) & 0xFF);

        // 16KB bank 2 -> 8KB banks 4 (0x4000) & 5 (0x6000) via 0x6000;
        // 16KB bank 7 -> 8KB banks 14 (0x8000) & 15 (0xA000) via 0x77FF!
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] mapPage2ToCartSlot = {
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
        out.writeBytes(mapPage2ToCartSlot);
        List<Integer> failPatchPositions = new ArrayList<>();

        // Switch Page 1 to 16KB bank 0 via 0x6000 (tests CALL 0xF560 trampoline without swapping code out!)
        // and verify (0x6000) holds 8KB bank 1's marker.
        out.write(0x3E); out.write(0x00);
        out.write(0x32); out.write(0x00); out.write(0x60); // LD (0x6000), A
        out.write(0x3A); out.write(0x00); out.write(0x60);
        out.write(0xFE); out.write((1 * 17 + 0x11) & 0xFF);
        out.write(0x20); failPatchPositions.add(out.size()); out.write(0x00);

        // Switch Page 2 to 16KB bank 7 via 0x77FF and check both 0x8000 (bank 14) and 0xA000 (bank 15)
        out.write(0x3E); out.write(0x07);
        out.write(0x32); out.write(0xFF); out.write(0x77); // LD (0x77FF), A
        out.write(0x3A); out.write(0x00); out.write(0x80);
        out.write(0xFE); out.write((14 * 17 + 0x11) & 0xFF);
        out.write(0x20); failPatchPositions.add(out.size()); out.write(0x00);
        out.write(0x3A); out.write(0x00); out.write(0xA0);
        out.write(0xFE); out.write((15 * 17 + 0x11) & 0xFF);
        out.write(0x20); failPatchPositions.add(out.size()); out.write(0x00);

        // Switch Page 2 to 16KB bank 2 via 0x7000 and check both 0x8000 (bank 4) and 0xA000 (bank 5)
        out.write(0x3E); out.write(0x02);
        out.write(0x32); out.write(0x00); out.write(0x70); // LD (0x7000), A
        out.write(0x3A); out.write(0x00); out.write(0x80);
        out.write(0xFE); out.write((4 * 17 + 0x11) & 0xFF);
        out.write(0x20); failPatchPositions.add(out.size()); out.write(0x00);
        out.write(0x3A); out.write(0x00); out.write(0xA0);
        out.write(0xFE); out.write((5 * 17 + 0x11) & 0xFF);
        out.write(0x20); failPatchPositions.add(out.size()); out.write(0x00);

        writeSignatureLoop(out, "OK" + tag2Chars);
        int failPos = out.size();
        writeSignatureLoop(out, "ER" + tag2Chars);

        byte[] code = out.toByteArray();
        for (int idx : failPatchPositions) {
            code[idx] = (byte) (failPos - (idx + 1));
        }
        System.arraycopy(code, 0, rom, initAddr - 0x4000, code.length);
        return rom;
    }

    private static void emitCheckRegWindow(
            ByteArrayOutputStream out, int regAddr, int readAddr, int bankIdx, int expectedVal,
            List<Integer> failPatches) {
        out.write(0x3E); // LD A, bankIdx
        out.write(bankIdx & 0xFF);
        out.write(0x32); // LD (regAddr), A
        out.write(regAddr & 0xFF);
        out.write((regAddr >> 8) & 0xFF);
        out.write(0x3A); // LD A, (readAddr)
        out.write(readAddr & 0xFF);
        out.write((readAddr >> 8) & 0xFF);
        out.write(0xFE); // CP expectedVal
        out.write(expectedVal & 0xFF);
        out.write(0x20); // JR NZ, fail
        failPatches.add(out.size());
        out.write(0x00);
    }

    private static void writeSignatureLoop(ByteArrayOutputStream out, String sig) {
        byte[] bytes = sig.getBytes(StandardCharsets.US_ASCII);
        out.write(0x21); // LD HL, 0xD000
        out.write(0x00);
        out.write(0xD0);
        for (int i = 0; i < 4; i++) {
            out.write(0x36); // LD (HL), byte
            out.write(bytes[i]);
            if (i < 3) {
                out.write(0x23); // INC HL
            }
        }
        out.write(0xFB); // EI
        out.write(0x18); // JR $
        out.write(0xFE);
    }

    private static String buildOpenMsxTclScript(boolean holdCtrlOnBoot, int maxWaitSec, boolean expectErrorScreen) {
        String bootSequence = holdCtrlOnBoot
                ? "keymatrixdown 6 2\n"
                        + "after time 12 { keymatrixup 6 2 }\n"
                        + "after time 13 {\n"
                        + "    type_via_keybuf \"BLOAD\\\"CAS:\\\",R\\r\"\n"
                        + "}\n"
                : "after time 10 {\n"
                        + "    type_via_keybuf \"BLOAD\\\"CAS:\\\",R\\r\"\n"
                        + "}\n";
        return "set renderer none\n"
                + "set throttle off\n"
                + "set max_wait " + maxWaitSec + "\n"
                + "proc write_result {} {\n"
                + "    global env\n"
                + "    set b0 [debug read \"memory\" 0xD000]\n"
                + "    set b1 [debug read \"memory\" 0xD001]\n"
                + "    set b2 [debug read \"memory\" 0xD002]\n"
                + "    set b3 [debug read \"memory\" 0xD003]\n"
                + "    set fp [open $env(OUT_FILE) \"w\"]\n"
                + "    puts $fp [format \"t=%.2f TAG=%c%c%c%c HEX=%02X%02X%02X%02X PC=%04X\" "
                + "[machine_info time] $b0 $b1 $b2 $b3 $b0 $b1 $b2 $b3 [reg PC]]\n"
                + "    catch { get_screen } scr\n"
                + "    puts $fp \"SCREEN:\\n$scr\"\n"
                + "    close $fp\n"
                + "}\n"
                + "after time $max_wait {\n"
                + "    write_result\n"
                + "    quit\n"
                + "}\n"
                + "cassetteplayer insert $env(TEST_WAV)\n"
                + bootSequence
                + "proc poll_done {} {\n"
                + "    set b0 [debug read \"memory\" 0xD000]\n"
                + "    set b1 [debug read \"memory\" 0xD001]\n"
                + "    if {($b0 == 0x4F && $b1 == 0x4B) || ($b0 == 0x45 && $b1 == 0x52)} {\n"
                + "        write_result\n"
                + "        quit\n"
                + "    }\n"
                + (expectErrorScreen
                        ? "    catch { get_screen } scr\n"
                                + "    if {[string first \"Fail:\" $scr] >= 0} {\n"
                                + "        write_result\n"
                                + "        quit\n"
                                + "    }\n"
                        : "")
                + "    after time 5 poll_done\n"
                + "}\n"
                + "after time 20 poll_done\n";
    }

    private Path prepareUserData() throws IOException {
        Path userData = tempFolder.newFolder("openmsx_data_" + System.nanoTime()).toPath();
        Path romsDir = Files.createDirectories(userData.resolve("systemroms"));
        for (Map.Entry<String, Path> entry : availableRoms.entrySet()) {
            Files.copy(entry.getValue(), romsDir.resolve(entry.getKey()), StandardCopyOption.REPLACE_EXISTING);
        }
        return userData;
    }

    private static boolean hasAllRoms(String... names) {
        for (String name : names) {
            if (!availableRoms.containsKey(name)) {
                return false;
            }
        }
        return true;
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
