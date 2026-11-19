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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
 * End-to-end integration tests that convert MSX ROMs to 2400 bps WAV via {@link VirtualCas}
 * and execute them headless inside {@code openmsx} across fragmented-RAM (Toshiba HX-20E),
 * expanded-slot (Toshiba HX-21, Toshiba HX-34, Yamaha AX200, Yamaha CX5MII-128),
 * memory-mapper MSX1 (National CF-2000 + ram512k), 64KB external-RAM MSX1 (Sony HB-10 + ram64k),
 * and standard primary-slot (Toshiba HX-10, Gradiente Expert GPC-1) machines.
 *
 * <p>Requires {@code openmsx} on {@code PATH} (or {@code -Dopenmsx.bin=...}) and host MSX1 BIOS
 * system ROMs under {@code $OPENMSX_SYSTEM_ROMS}, {@code $OPENMSX_USER_DATA/systemroms}, or
 * {@code ~/.openMSX/share/systemroms}. Missing binaries or ROMs skip the corresponding tests cleanly.
 */
public class OpenMsxRomLoaderIT {

    private static final int EMULATED_LOAD_WAIT_SEC = 500;
    private static final long WALL_TIMEOUT_SEC = 90L;
    private static final Pattern WORD_PROCESSOR_ROM_PATTERN = Pattern.compile(
            "<ROM\\s+id=\"Word Processor ROM\">[\\s\\S]*?</ROM>");
    private static final Pattern BIOS_ROM_PATTERN = Pattern.compile(
            "<ROM\\s+id=\"MSX BIOS with BASIC ROM\">[\\s\\S]*?</ROM>");

    private static String openMsxBin;
    private static Map<String, Path> availableRoms;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void detectEnvironment() throws IOException {
        openMsxBin = findOpenMsxExecutable();
        Assume.assumeTrue("openmsx binary not found; skipping emulator integration tests", openMsxBin != null);
        availableRoms = discoverSystemRoms();
    }

    @Test
    public void hx20eFragmentedRamLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Path machineXml = findSystemMachineXml("Toshiba_HX-20E.xml");
        Assume.assumeTrue("Toshiba_HX-20E.xml not found in openMSX system data", machineXml != null);

        Path biosRom = findAnyRom("hx-20E_basic-bios.rom", "hx-10_basic-bios1.rom", "expert_1.1_basic-bios1.rom");
        Assume.assumeTrue("No MSX1 BASIC BIOS ROM found for HX-20E test", biosRom != null);

        Path userData = prepareUserData(biosRom, machineXml);
        assertAllLoaderVariants(userData, "Toshiba_HX-20E_clean", "3 0", "3 0", false);
    }

    @Test
    public void hx21ExpandedSlotLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Toshiba HX-21 ROMs (HX21-IC2.BIN, HX21-IC3.BIN) not found",
                hasAllRoms("HX21-IC2.BIN", "HX21-IC3.BIN"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Toshiba_HX-21", "3 0", "3 0", false);
    }

    @Test
    public void hx10PrimarySlotLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Toshiba HX-10 BIOS ROM (hx-10_basic-bios1.rom) not found",
                hasAllRoms("hx-10_basic-bios1.rom"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Toshiba_HX-10", "2 X", "2 X", false);
    }

    @Test
    public void yamahaAx200ExpandedSlotZeroLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Yamaha AX200 system ROMs (ax200_basic-bios1.rom, ax200_arabic.rom, ax200_music.rom/sfg-05.rom) not found",
                hasAllRoms("ax200_basic-bios1.rom", "ax200_arabic.rom")
                        && (hasAllRoms("ax200_music.rom") || hasAllRoms("sfg-05.rom")));

        Path userData = prepareUserData(null, null);
        // Holding CTRL during boot skips the built-in Arabic utility in slot 0-3 to enter MSX-BASIC.
        assertAllLoaderVariants(userData, "Yamaha_AX200", "0 2", "0 2", true);
    }

    @Test
    public void nationalCf2000WithRam512kLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("National CF-2000 BIOS ROM not found",
                hasRom("cf-2000_basic-bios1.rom", "302afb5d8be26c758309ca3df611ae69cced2821"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "National_CF-2000", "1 X", "1 X", false, List.of("-ext", "ram512k"));
    }

    @Test
    public void gradienteExpertGpc1LoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Gradiente_Expert_GPC-1", "2 X", "2 X", false);
    }

    @Test
    public void gradienteExpertLoadsGeneratedCasFilesDirectly() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Gradiente_Expert_GPC-1", "2 X", "2 X", false, List.of(), 7, true);
    }

    @Test
    public void gradienteExpertTurboLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Gradiente_Expert_GPC-1", "2 X", "2 X", false,
                List.of(), 7, false, true, SampleRate.sr22050);
    }

    @Test
    public void gradienteExpertTurboLoadsAcrossRatesPolaritiesAndBin() throws Exception, FlowException {
        Assume.assumeTrue("Gradiente Expert GPC-1 BIOS ROM not found",
                hasRom("expert_1.1_basic-bios1.rom", "d6720845928ee848cfa88a86accb067397685f02"));

        Path userData = prepareUserData(null, null);
        byte[] rom16k = buildCheckRom(16384, 0x0000, 0x4010, List.of(new int[] { 0x7FFE, 0x5A }), "16", false);
        rom16k[0x3FFE] = 0x5A;
        byte[] rom16kPg2 = buildCheckRom(16384, 0x0000, 0x8010,
                List.of(new int[] { 0xBFFE, 0x99 }, new int[] { 0xFCAF, 0x01 }), "P2", false);
        rom16kPg2[0x3FFE] = (byte) 0x99;

        byte[] binFile = buildCheckBin(0xA000, 4096, "BN");

        try {
            for (SampleRate rate : SampleRate.values()) {
                assertRomBoots(userData, "Gradiente_Expert_GPC-1", "turbo_" + rate.bps() + "_norm",
                        rom16k, false, "OK16", "2 X", null, null, false, List.of(), 7, false,
                        true, rate.invertWaveForm(false));
                assertRomBoots(userData, "Gradiente_Expert_GPC-1", "turbo_" + rate.bps() + "_inv",
                        rom16kPg2, false, "OKP2", null, null, "063607", false, List.of(), 7, false,
                        true, rate.invertWaveForm(true));
            }
            assertBinBoots(userData, "Gradiente_Expert_GPC-1", "turbo_bin_2400",
                    binFile, "OKBN", SampleRate.sr22050.invertWaveForm(false), true);
            assertBinBoots(userData, "Gradiente_Expert_GPC-1", "turbo_bin_3600i",
                    binFile, "OKBN", SampleRate.sr33075.invertWaveForm(true), true);
            assertRomBoots(userData, "Gradiente_Expert_GPC-1", "std_3000_norm",
                    rom16k, false, "OK16", "2 X", null, null, false, List.of(), 7, false,
                    false, SampleRate.sr27563.invertWaveForm(false));
            assertBinBoots(userData, "Gradiente_Expert_GPC-1", "std_bin_3000i",
                    binFile, "OKBN", SampleRate.sr27563.invertWaveForm(true), false);
        } finally {
            for (SampleRate rate : SampleRate.values()) {
                rate.invertWaveForm(false);
            }
        }
    }

    @Test
    public void sonyHb10WithRam64kLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Sony HB-10 BIOS ROM not found",
                hasRom("hb-10_basic-bios1.rom", "302afb5d8be26c758309ca3df611ae69cced2821"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Sony_HB-10", "1 X", "1 X", false, List.of("-ext", "ram64k"));
    }

    @Test
    public void toshibaHx34DiskBasicLoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Toshiba HX-34 system ROMs (HX34_IC7.BIN, hx-34_disk.rom) not found",
                hasRom("HX34_IC7.BIN", "5e057526fe39d79e88e7ff1ce02ed669bd38929e")
                        && hasRom("hx-34_disk.rom", "c88ef953b21370cbaef5e82575d093d6f9047ec6"));

        Path userData = prepareUserData(null, null);
        // Built-in Disk BASIC probes the floppy controller before entering the prompt.
        assertAllLoaderVariants(userData, "Toshiba_HX-34", "3 0", "3 0", false, List.of(), 16);
    }

    @Test
    public void yamahaCx5mii128LoadsAllRomSizesAndResets() throws Exception, FlowException {
        Assume.assumeTrue("Yamaha CX5MII-128 system ROMs not found",
                hasRom("cx5mii-128_basic-bios1.rom",
                        "521928db7bc81a8db4cac1e666851e2bf01c8d53",
                        "ea4a723cf098be7d7b40f23a7ab831cf5e2190d7")
                        && hasRom("cx5mii-128_sub.rom",
                                "df503d6f5d72a2a355db53c35950348fb7c767bf",
                                "c2340313bfda751181e8a5287d60f77bc6a2f3e6")
                        && hasRom("cx5mii-128_fmvoicingprogramii.rom", "30747a56f45389be76362f7fc55d673f1bff8312")
                        && hasRom("sfg-05.rom", "6680d7118d85418813f1db9449bf3e20942b16da"));

        Path userData = prepareUserData(null, null);
        assertAllLoaderVariants(userData, "Yamaha_CX5MII-128", "3 2", "3 2", false);
    }

    private void assertAllLoaderVariants(
            Path userData,
            String machine,
            String expectedPage1Slot,
            String expectedPage0Slot,
            boolean holdCtrlOnBoot)
            throws Exception, FlowException {
        assertAllLoaderVariants(userData, machine, expectedPage1Slot, expectedPage0Slot, holdCtrlOnBoot, List.of(), 7);
    }

    private void assertAllLoaderVariants(
            Path userData,
            String machine,
            String expectedPage1Slot,
            String expectedPage0Slot,
            boolean holdCtrlOnBoot,
            List<String> extraArgs)
            throws Exception, FlowException {
        assertAllLoaderVariants(userData, machine, expectedPage1Slot, expectedPage0Slot, holdCtrlOnBoot, extraArgs, 7);
    }

    private void assertAllLoaderVariants(
            Path userData,
            String machine,
            String expectedPage1Slot,
            String expectedPage0Slot,
            boolean holdCtrlOnBoot,
            List<String> extraArgs,
            int typeDelaySec)
            throws Exception, FlowException {
        assertAllLoaderVariants(userData, machine, expectedPage1Slot, expectedPage0Slot, holdCtrlOnBoot, extraArgs, typeDelaySec, false);
    }

    private void assertAllLoaderVariants(
            Path userData,
            String machine,
            String expectedPage1Slot,
            String expectedPage0Slot,
            boolean holdCtrlOnBoot,
            List<String> extraArgs,
            int typeDelaySec,
            boolean useCas)
            throws Exception, FlowException {
        assertAllLoaderVariants(userData, machine, expectedPage1Slot, expectedPage0Slot, holdCtrlOnBoot,
                extraArgs, typeDelaySec, useCas, false, SampleRate.sr22050);
    }

    private void assertAllLoaderVariants(
            Path userData,
            String machine,
            String expectedPage1Slot,
            String expectedPage0Slot,
            boolean holdCtrlOnBoot,
            List<String> extraArgs,
            int typeDelaySec,
            boolean useCas,
            boolean turbo,
            SampleRate sampleRate)
            throws Exception, FlowException {
        // 8K at 0x4000 (ROM_16K & ROM_16K_RST)
        byte[] rom8k = buildCheckRom(8192, 0x0000, 0x4010, List.of(), "8K", false);
        assertRomBoots(userData, machine, "rom8k_norm", rom8k, false, "OK8K", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
        assertRomBoots(userData, machine, "rom8k_rst", rom8k, true, "OK8K", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 8K at 0x0000 (ROM_16K LOAD_PG0 with IM1 VDP IRQ ack at 0x0038)
        byte[] rom8kPg0 = buildCheckRom(8192, 0x0000, 0x0050, List.of(new int[] { 0x1FFE, 0x77 }), "P0", true);
        rom8kPg0[0x1FFE] = 0x77;
        assertRomBoots(userData, machine, "rom8k_pg0", rom8kPg0, false, "OKP0", null, expectedPage0Slot, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 16K at 0x4000 (ROM_16K & ROM_16K_RST)
        byte[] rom16k = buildCheckRom(16384, 0x0000, 0x4010, List.of(new int[] { 0x7FFE, 0x5A }), "16", false);
        rom16k[0x3FFE] = 0x5A;
        assertRomBoots(userData, machine, "rom16k_norm", rom16k, false, "OK16", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
        assertRomBoots(userData, machine, "rom16k_rst", rom16k, true, "OK16", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 16K at 0x8000 (Page-2 direct load + INIT32 VDP R2/R5/R6 Screen-1 defaults, e.g. Pitfall!)
        byte[] rom16kPg2 = buildCheckRom(16384, 0x0000, 0x8010,
                List.of(new int[] { 0xBFFE, 0x99 }, new int[] { 0xFCAF, 0x01 }), "P2", false);
        rom16kPg2[0x3FFE] = (byte) 0x99;
        assertRomBoots(userData, machine, "rom16k_pg2", rom16kPg2, false, "OKP2", null, null, "063607", holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 32K at 0x4000..0xBFFF with Konami-style RSLREG + ENASLT(Page1_slot, 0x80)
        // (BLOCK_LOADER + ROM_32K / ROM_32K_RST)
        byte[] konamiPage2Map = {
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
        byte[] rom32k = buildCheckRom(32768, 0x0000, 0x4010 + konamiPage2Map.length,
                List.of(new int[] { 0x7FFE, 0x3C }, new int[] { 0xBFFE, 0xA5 }), "32", false);
        rom32k[0x02] = 0x10;
        rom32k[0x03] = 0x40;
        System.arraycopy(konamiPage2Map, 0, rom32k, 0x10, konamiPage2Map.length);
        rom32k[0x3FFE] = 0x3C;
        rom32k[0x7FFE] = (byte) 0xA5;
        assertRomBoots(userData, machine, "rom32k_norm", rom32k, false, "OK32", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
        assertRomBoots(userData, machine, "rom32k_rst", rom32k, true, "OK32", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 32K at 0x0000..0x7FFF (BLOCK_LOADER LR_PG0 + ROM_32K)
        byte[] rom32kPg0 = buildCheckRom(32768, 0x0000, 0x0050,
                List.of(new int[] { 0x1000, 0x12 }, new int[] { 0x7FFE, 0x34 }), "3P", true);
        rom32kPg0[0x1000] = 0x12;
        rom32kPg0[0x7FFE] = 0x34;
        assertRomBoots(userData, machine, "rom32k_pg0", rom32kPg0, false, "OK3P", expectedPage1Slot, expectedPage0Slot, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 49K at 0x0000..0xC3FF with Konami-style RSLREG + ENASLT(Page1_slot, 0x80)
        // (BLOCK_LOADER x2 + ROM_49K / ROM_49K_RST)
        byte[] rom49k = buildCheckRom(50176, 0x4000, 0x4010 + konamiPage2Map.length,
                List.of(new int[] { 0x7FFE, 0x22 }, new int[] { 0x8100, 0x33 }, new int[] { 0xC100, 0x44 }),
                "49", false);
        rom49k[0x4002] = 0x10;
        rom49k[0x4003] = 0x40;
        System.arraycopy(konamiPage2Map, 0, rom49k, 0x4010, konamiPage2Map.length);
        rom49k[0x7FFE] = 0x22;
        rom49k[0x8100] = 0x33;
        rom49k[0xC100] = 0x44;
        assertRomBoots(userData, machine, "rom49k_norm", rom49k, false, "OK49", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
        assertRomBoots(userData, machine, "rom49k_rst", rom49k, true, "OK49", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);

        // 49K MSXB2R-style case: INIT returns via RET after hooking H.STKE (0xFEDA) and
        // reads Page 0 via LD A,(0xC01D); LD H,0x00; CALL ... (requires JP patch + GSP_CHK_PG0 on AX200)
        byte[] rom49kRet = buildCheckRom(50176, 0x4000, 0x4120,
                List.of(new int[] { 0xC01E, 0x5A }, new int[] { 0x7FFE, 0x22 }, new int[] { 0x8100, 0x33 }, new int[] { 0xC100, 0x44 }),
                "4R", false);
        rom49kRet[0x0010] = 0x5A;
        rom49kRet[0x4002] = 0x10;
        rom49kRet[0x4003] = 0x40;
        byte[] retInitStub = {
                0x26, 0x40, (byte) 0xCD, 0x60, 0x40,       // 4010: LD H,0x40; CALL getSlot
                0x32, 0x1C, (byte) 0xC0,                   // 4015: LD (0xC01C), A
                0x32, (byte) 0xDB, (byte) 0xFE,            // 4018: LD (0xFEDB), A
                0x3E, (byte) 0xF7, 0x32, (byte) 0xDA, (byte) 0xFE, // 401B: LD (0xFEDA), 0xF7
                0x21, 0x30, 0x40, 0x22, (byte) 0xDC, (byte) 0xFE,  // 4020: LD (0xFEDC), 0x4030
                0x3E, (byte) 0xC9, 0x32, (byte) 0xDE, (byte) 0xFE, // 4026: LD (0xFEDE), 0xC9
                (byte) 0xC9                                // 402B: RET
        };
        byte[] hStkeHandler = {
                0x3E, (byte) 0xC9, 0x32, (byte) 0xDA, (byte) 0xFE, // 4030: unhook H.STKE
                0x26, (byte) 0x80, (byte) 0xCD, 0x60, 0x40,        // 4035: LD H,0x80; CALL getSlot
                0x32, 0x1D, (byte) 0xC0,                           // 403A: LD (0xC01D), A
                0x3A, 0x1D, (byte) 0xC0, 0x26, 0x00, (byte) 0xCD, (byte) 0xA0, 0x40, // 403D: LD A,(0xC01D); LD H,0; CALL pg0Read
                (byte) 0xC3, 0x20, 0x41                            // 4045: JP 0x4120
        };
        byte[] getSlotHelper = {
                (byte) 0xCD, 0x38, 0x01, (byte) 0xCB, 0x7C, 0x28, 0x04, 0x0F, 0x0F, 0x0F, 0x0F,
                (byte) 0xCB, 0x74, 0x28, 0x02, 0x0F, 0x0F, (byte) 0xE6, 0x03, 0x4F, 0x06, 0x00,
                (byte) 0xEB, 0x21, (byte) 0xC1, (byte) 0xFC, 0x09, 0x4F, 0x7E, (byte) 0xE6, (byte) 0x80,
                (byte) 0xB1, 0x4F, 0x23, 0x23, 0x23, 0x23, 0x7E, (byte) 0xEB, (byte) 0xCB, 0x7C,
                0x28, 0x04, 0x0F, 0x0F, 0x0F, 0x0F, (byte) 0xCB, 0x74, 0x20, 0x02, 0x07, 0x07,
                (byte) 0xE6, 0x0C, (byte) 0xB1, (byte) 0xC9
        };
        byte[] pg0ReadHelper = {
                (byte) 0xF3, 0x5F, (byte) 0xDB, (byte) 0xA8, (byte) 0xF5, (byte) 0xCB, 0x7B, 0x28, 0x2D, 0x7B, (byte) 0xE6, 0x03,
                0x4F, 0x06, 0x00, 0x21, (byte) 0xC5, (byte) 0xFC, 0x09, 0x7E, (byte) 0xF5, 0x57, 0x7B, 0x0F,
                0x0F, (byte) 0xE6, 0x03, 0x47, 0x7A, (byte) 0xE6, (byte) 0xFC, (byte) 0xB0, 0x57, 0x79, 0x0F, 0x0F,
                (byte) 0xE6, (byte) 0xC0, 0x47, (byte) 0xDB, (byte) 0xA8, 0x4F, (byte) 0xE6, 0x3F, (byte) 0xB0, (byte) 0xD3, (byte) 0xA8, 0x7A,
                0x32, (byte) 0xFF, (byte) 0xFF, 0x79, (byte) 0xD3, (byte) 0xA8, 0x7B, (byte) 0xE6, 0x03, 0x47, (byte) 0xDB, (byte) 0xA8,
                (byte) 0xE6, (byte) 0xFC, (byte) 0xB0, (byte) 0xD3, (byte) 0xA8, 0x3A, 0x10, 0x00, 0x32, 0x1E, (byte) 0xC0, (byte) 0xCB,
                0x7B, 0x28, 0x1A, (byte) 0xD1, (byte) 0xE1, 0x7B, (byte) 0xE6, 0x03, 0x0F, 0x0F, (byte) 0xE6, (byte) 0xC0,
                0x47, (byte) 0xDB, (byte) 0xA8, (byte) 0xE6, 0x3F, (byte) 0xB0, (byte) 0xD3, (byte) 0xA8, 0x7A, 0x32, (byte) 0xFF, (byte) 0xFF,
                0x7C, (byte) 0xD3, (byte) 0xA8, (byte) 0xFB, (byte) 0xC9, (byte) 0xF1, (byte) 0xD3, (byte) 0xA8, (byte) 0xFB, (byte) 0xC9
        };
        System.arraycopy(retInitStub, 0, rom49kRet, 0x4010, retInitStub.length);
        System.arraycopy(hStkeHandler, 0, rom49kRet, 0x4030, hStkeHandler.length);
        System.arraycopy(getSlotHelper, 0, rom49kRet, 0x4060, getSlotHelper.length);
        System.arraycopy(pg0ReadHelper, 0, rom49kRet, 0x40A0, pg0ReadHelper.length);
        rom49kRet[0x7FFE] = 0x22;
        rom49kRet[0x8100] = 0x33;
        rom49kRet[0xC100] = 0x44;
        // Also use a <6-char CAS filename ("adv") to guard 6-byte filename padding in multi-block ROMs.
        assertRomBoots(userData, machine, "adv", rom49kRet, false, "OK4R", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
        assertRomBoots(userData, machine, "rom49k_rst_ret", rom49kRet, true, "OK4R", expectedPage1Slot, null, null, holdCtrlOnBoot, extraArgs, typeDelaySec, useCas, turbo, sampleRate);
    }

    private void assertRomBoots(
            Path userData,
            String machine,
            String caseName,
            byte[] romBytes,
            boolean reset,
            String expectedTag,
            String expectedPage1Slot,
            String expectedPage0Slot,
            String expectedVdp256,
            boolean holdCtrlOnBoot,
            List<String> extraArgs,
            int typeDelaySec,
            boolean useCas,
            boolean turbo,
            SampleRate sampleRate) throws Exception, FlowException {
        Path caseDir = tempFolder.newFolder(machine + "_" + caseName + (useCas ? "_cas" : (turbo ? "_turbo" : ""))).toPath();
        Path romFile = caseDir.resolve(caseName + ".rom");
        Files.write(romFile, romBytes);

        Path tapeFile;
        if (useCas) {
            byte[] casBytes = new VirtualCas()
                    .resetRom(reset)
                    .convertToCas(romFile.toAbsolutePath().toString())
                    .toBytes();
            tapeFile = caseDir.resolve(caseName + ".cas");
            Files.write(tapeFile, casBytes);
        } else {
            byte[] wavBytes = new VirtualCas(sampleRate)
                    .resetRom(reset)
                    .turbo(turbo)
                    .convert(romFile.toAbsolutePath().toString())
                    .toBytes();
            tapeFile = caseDir.resolve(caseName + ".wav");
            Files.write(tapeFile, wavBytes);
        }

        String result = runTapeInOpenMsx(userData, machine, caseName, caseDir, tapeFile, holdCtrlOnBoot, extraArgs, typeDelaySec);
        assertTrue("Expected signature '" + expectedTag + "' for " + machine + "/" + caseName + " but got: " + result,
                result.contains("TAG=" + expectedTag));
        if (expectedPage1Slot != null) {
            assertTrue("Expected Page 1 slot '" + expectedPage1Slot + "' for " + machine + "/" + caseName + " but got: " + result,
                    result.contains("P1=" + expectedPage1Slot));
        }
        if (expectedPage0Slot != null) {
            assertTrue("Expected Page 0 slot '" + expectedPage0Slot + "' for " + machine + "/" + caseName + " but got: " + result,
                    result.contains("P0=" + expectedPage0Slot));
        }
        if (expectedVdp256 != null) {
            assertTrue("Expected VDP R2/R5/R6 '" + expectedVdp256 + "' for " + machine + "/" + caseName + " but got: " + result,
                    result.contains("VDP256=" + expectedVdp256));
        }
    }

    private void assertBinBoots(
            Path userData,
            String machine,
            String caseName,
            byte[] binBytes,
            String expectedTag,
            SampleRate sampleRate,
            boolean turbo) throws Exception, FlowException {
        Path caseDir = tempFolder.newFolder(machine + "_" + caseName).toPath();
        Path binFile = caseDir.resolve(caseName + ".bin");
        Files.write(binFile, binBytes);

        byte[] wavBytes = new VirtualCas(sampleRate)
                .turbo(turbo)
                .convert(binFile.toAbsolutePath().toString())
                .toBytes();
        Path tapeFile = caseDir.resolve(caseName + ".wav");
        Files.write(tapeFile, wavBytes);

        String result = runTapeInOpenMsx(userData, machine, caseName, caseDir, tapeFile, false, List.of(), 7);
        assertTrue("Expected signature '" + expectedTag + "' for " + machine + "/" + caseName + " but got: " + result,
                result.contains("TAG=" + expectedTag));
    }

    private String runTapeInOpenMsx(
            Path userData,
            String machine,
            String caseName,
            Path caseDir,
            Path tapeFile,
            boolean holdCtrlOnBoot,
            List<String> extraArgs,
            int typeDelaySec) throws IOException, InterruptedException {
        Path outFile = caseDir.resolve("result.txt");
        Path tclFile = caseDir.resolve("run.tcl");
        Files.writeString(tclFile, buildOpenMsxTclScript(holdCtrlOnBoot, typeDelaySec), StandardCharsets.UTF_8);

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
        env.put("TEST_WAV", tapeFile.toString());
        env.put("OUT_FILE", outFile.toString());
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        boolean finished = proc.waitFor(WALL_TIMEOUT_SEC, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new AssertionError("openMSX timed out after " + WALL_TIMEOUT_SEC + "s for " + machine + "/" + caseName);
        }

        String procLog = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("openMSX exited non-zero for " + machine + "/" + caseName + ": " + procLog, 0, proc.exitValue());
        assertTrue("Missing openMSX output file for " + machine + "/" + caseName, Files.exists(outFile));
        return Files.readString(outFile, StandardCharsets.UTF_8).trim();
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

    /**
     * Generates a headless openMSX Tcl script. Registers a hard emulated-time safety quit first
     * so any Tcl error cannot leave an orphan emulator waiting indefinitely.
     */
    private static String buildOpenMsxTclScript(boolean holdCtrlOnBoot, int typeDelaySec) {
        String bootSequence = holdCtrlOnBoot
                ? "keymatrixdown 6 2\n"
                        + "after time 12 { keymatrixup 6 2 }\n"
                        + "after time " + Math.max(13, typeDelaySec) + " {\n"
                        + "    type_via_keybuf \"BLOAD\\\"CAS:\\\",R\\r\"\n"
                        + "}\n"
                : "after time " + typeDelaySec + " {\n"
                        + "    type_via_keybuf \"BLOAD\\\"CAS:\\\",R\\r\"\n"
                        + "}\n";
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
                + "    puts $fp [format \"t=%.2f TAG=%c%c%c%c HEX=%02X%02X%02X%02X PC=%04X P0=%s P1=%s P2=%s P3=%s VDP256=%02X%02X%02X\" "
                + "[machine_info time] $b0 $b1 $b2 $b3 $b0 $b1 $b2 $b3 [reg PC] "
                + "[get_selected_slot 0] [get_selected_slot 1] [get_selected_slot 2] [get_selected_slot 3] "
                + "[debug read \"VDP regs\" 2] [debug read \"VDP regs\" 5] [debug read \"VDP regs\" 6]]\n"
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
                + "    after time 5 poll_done\n"
                + "}\n"
                + "after time 20 poll_done\n";
    }

    /**
     * Stages system ROMs directly under {@code <tmp>/systemroms} and, when requested, builds a
     * Word-Processor-free {@code Toshiba_HX-20E_clean.xml} under {@code <tmp>/machines}.
     */
    private Path prepareUserData(Path hx20BiosOverride, Path hx20SourceXml) throws IOException {
        Path userData = tempFolder.newFolder("openmsx_data_" + System.nanoTime()).toPath();
        Path romsDir = Files.createDirectories(userData.resolve("systemroms"));
        Path machinesDir = Files.createDirectories(userData.resolve("machines"));

        for (Map.Entry<String, Path> entry : availableRoms.entrySet()) {
            Files.copy(entry.getValue(), romsDir.resolve(entry.getKey()), StandardCopyOption.REPLACE_EXISTING);
        }

        if (hx20SourceXml != null && hx20BiosOverride != null) {
            byte[] dummy = new byte[32768];
            Arrays.fill(dummy, (byte) 0xFF);
            Files.write(romsDir.resolve("dummy32k.rom"), dummy);
            byte[] biosBytes = Files.readAllBytes(hx20BiosOverride);
            Files.write(romsDir.resolve("msx1_basic_bios.rom"), biosBytes);

            String xml = Files.readString(hx20SourceXml, StandardCharsets.UTF_8);
            String biosBlock = "<ROM id=\"MSX BIOS with BASIC ROM\">\n"
                    + "        <rom>\n"
                    + "          <filename>msx1_basic_bios.rom</filename>\n"
                    + "          <sha1>" + sha1Hex(biosBytes) + "</sha1>\n"
                    + "        </rom>\n"
                    + "        <mem base=\"0x0000\" size=\"0x8000\"/>\n"
                    + "      </ROM>";
            String wpBlock = "<ROM id=\"Word Processor ROM\">\n"
                    + "          <rom>\n"
                    + "            <sha1>" + sha1Hex(dummy) + "</sha1>\n"
                    + "            <filename>dummy32k.rom</filename>\n"
                    + "          </rom>\n"
                    + "          <mem base=\"0x4000\" size=\"0x8000\"/>\n"
                    + "        </ROM>";
            xml = BIOS_ROM_PATTERN.matcher(xml).replaceFirst(biosBlock);
            xml = WORD_PROCESSOR_ROM_PATTERN.matcher(xml).replaceFirst(wpBlock);
            Files.writeString(machinesDir.resolve("Toshiba_HX-20E_clean.xml"), xml, StandardCharsets.UTF_8);
        }
        return userData;
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

    /**
     * Constructs a synthetic MSX ROM with an {@code AB} cartridge header, optional IM1 VDP IRQ ack
     * handler at {@code 0x0038}, and Z80 verification code writing {@code OK<tag>} or {@code ER<tag>}
     * to {@code 0xD000..0xD003}.
     */
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
            // PUSH AF; IN A,(0x99); POP AF; EI; RET (clears TMS9918/T6950 VDP interrupt flag)
            byte[] isr = new byte[] { (byte) 0xF5, (byte) 0xDB, (byte) 0x99, (byte) 0xF1, (byte) 0xFB, (byte) 0xC9 };
            System.arraycopy(isr, 0, rom, 0x0038, isr.length);
        }

        byte[] code = makeCheckCode(checks, tag);
        int baseAddr = abOffset == 0 ? (initAddr & 0xC000) : 0;
        int codeOffset = initAddr - baseAddr;
        System.arraycopy(code, 0, rom, codeOffset, code.length);
        return rom;
    }

    private static byte[] makeCheckCode(List<int[]> checks, String tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> failPatchPositions = new ArrayList<>();
        for (int[] check : checks) {
            int addr = check[0];
            int val = check[1] & 0xFF;
            out.write(0x3A); // LD A, (addr)
            out.write(addr & 0xFF);
            out.write((addr >> 8) & 0xFF);
            out.write(0xFE); // CP val
            out.write(val);
            out.write(0x20); // JR NZ, rel
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

    private static Path findAnyRom(String... preferredNames) {
        for (String name : preferredNames) {
            if (availableRoms.containsKey(name)) {
                return availableRoms.get(name);
            }
        }
        return availableRoms.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains("basic-bios1"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static Path findSystemMachineXml(String fileName) {
        List<Path> candidates = List.of(
                Paths.get(System.getProperty("openmsx.system.data", "/usr/share/openmsx"), "machines", fileName),
                Paths.get("/usr/local/share/openmsx/machines", fileName)
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
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
