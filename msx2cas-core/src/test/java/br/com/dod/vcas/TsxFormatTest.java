package br.com.dod.vcas;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.cas.CasUtil;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.ArchiveInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.CustomInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.DirectRecordingBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.PilotRle;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.SymbolDef;
import br.com.dod.vcas.tape.TapeBlock.GlueBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupEndBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupStartBlock;
import br.com.dod.vcas.tape.TapeBlock.HardwareTypeBlock;
import br.com.dod.vcas.tape.TapeBlock.HardwareTypeBlock.HardwareEntry;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopEndBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopStartBlock;
import br.com.dod.vcas.tape.TapeBlock.MessageBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PulseSequenceBlock;
import br.com.dod.vcas.tape.TapeBlock.PureDataBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.SetSignalLevelBlock;
import br.com.dod.vcas.tape.TapeBlock.StandardSpeedBlock;
import br.com.dod.vcas.tape.TapeBlock.Stop48kBlock;
import br.com.dod.vcas.tape.TapeBlock.TextBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.tsx.TsxOutput;
import br.com.dod.vcas.tsx.TsxParser;
import br.com.dod.vcas.tsx.TsxWriter;

import static br.com.dod.vcas.AllTests.PROJECT_FOLDER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TsxFormatTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void roundTripAllSupportedBlockTypes() throws Exception, FlowException {
        List<TapeBlock> blocks = List.of(
                new ArchiveInfoBlock(List.of(
                        new ArchiveInfoBlock.Entry(0x00, "Test Tape"),
                        new ArchiveInfoBlock.Entry(0x01, "Publisher"))),
                new TextBlock("Custom creator"),
                new MessageBlock(5, "Side A"),
                new HardwareTypeBlock(List.of(new HardwareEntry(0x00, 0x10, 0x01))),
                new CustomInfoBlock("MSX2CAS_TEST    ", new byte[] { 1, 2, 3, 4 }),
                new GroupStartBlock("Loader"),
                new StandardSpeedBlock(500, new byte[] { 0x00, 0x55, (byte) 0xAA }),
                new TurboSpeedBlock(212, 423, 423, 212, 423, 64, 5, 250, new byte[] { (byte) 0xA5, 0x1F, (byte) 0x80 }),
                new PureToneBlock(729, 32),
                new PulseSequenceBlock(new int[] { 500, 1000, 1500 }),
                new PureDataBlock(300, 600, 6, 100, new byte[] { 0x33, (byte) 0xCC }),
                new DirectRecordingBlock(79, 50, 7, new byte[] { (byte) 0xF0, 0x0F }),
                new GeneralizedDataBlock(
                        200,
                        1L,
                        2,
                        List.of(new SymbolDef(0, new int[] { 400, 400 }), new SymbolDef(3, new int[] { 800, 0 })),
                        List.of(new PilotRle(0, 4)),
                        4L,
                        2,
                        List.of(new SymbolDef(0, new int[] { 200, 200 }), new SymbolDef(0, new int[] { 400, 400 })),
                        new byte[] { 0x50 }),
                new KcsBlock(1000, 365, 7680, 729, 365, KcsBlock.MSX_BIT_CFG, KcsBlock.MSX_BYTE_CFG,
                        new byte[] { 0x11, 0x22, 0x33 }),
                new LoopStartBlock(2),
                new SetSignalLevelBlock(1),
                new PureToneBlock(212, 4),
                new LoopEndBlock(),
                new PauseBlock(0),
                new PauseBlock(1500),
                new Stop48kBlock(),
                new GroupEndBlock(),
                new GlueBlock(1, 21)
        );

        TapeImage original = new TapeImage("testtp", blocks);
        byte[] tsxBytes = TsxWriter.toBytes(original);
        assertEquals('Z', (char) tsxBytes[0]);
        assertEquals(0x1A, tsxBytes[7]);
        assertEquals(1, tsxBytes[8]);
        assertEquals(21, tsxBytes[9]);

        TapeImage parsed = TsxParser.parse(tsxBytes, "testtp.tsx");
        assertEquals(blocks.size(), parsed.getBlocks().size());

        for (int i = 0; i < blocks.size(); i++) {
            assertBlockEquals(blocks.get(i), parsed.getBlocks().get(i));
        }
    }

    @Test
    public void writerInjectsDefaultMetadataWhenMissing() throws Exception, FlowException {
        TapeImage plain = new TapeImage("mygame", List.of(
                KcsBlock.msx(new byte[] { 0x01, 0x02 }, TapeSpeed.BPS_2400, true, 1000)));
        byte[] bytes = new TsxWriter(plain).toBytes();
        TapeImage parsed = TsxParser.parse(bytes, "mygame.tsx");

        assertTrue(parsed.getBlocks().get(0) instanceof ArchiveInfoBlock);
        assertTrue(parsed.getBlocks().get(1) instanceof TextBlock);
        assertTrue(parsed.getBlocks().get(2) instanceof KcsBlock);
        assertEquals("mygame", ((ArchiveInfoBlock) parsed.getBlocks().get(0)).entries().get(0).text());
    }

    @Test
    public void validationRejectsBadHeaderTruncatedEofUnmatchedLoopAndUnsupportedBlocks() {
        assertParserError(new byte[] { 'B', 'A', 'D', 1, 2, 3 }, "tsx_invalid_header");
        assertParserError(new byte[] { 'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A, 2, 0 }, "tsx_invalid_header");

        // Truncated #4B
        assertParserError(new byte[] { 'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A, 1, 21, 0x4B, 12, 0, 0, 0 },
                "tsx_unexpected_eof");

        // #4B blockLen < 12
        byte[] bad4B = headerWith(new byte[] { 0x4B, 8, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8 });
        assertParserError(bad4B, "tsx_invalid_block_length");

        // Unmatched loop start
        assertParserError(headerWith(new byte[] { 0x24, 2, 0 }), "tsx_unmatched_loop");
        // Stray loop end
        assertParserError(headerWith(new byte[] { 0x25 }), "tsx_unmatched_loop");

        // Unsupported block IDs
        assertParserError(headerWith(new byte[] { 0x18 }), "tsx_unsupported_block_18");
        assertParserError(headerWith(new byte[] { 0x23 }), "tsx_unsupported_block_23");
        assertParserError(headerWith(new byte[] { 0x26 }), "tsx_unsupported_block_26");
        assertParserError(headerWith(new byte[] { (byte) 0xFF }), "tsx_unsupported_block_FF");
    }

    @Test
    public void tsxToCasRoundTripAndFailFastCompatibility() throws Exception, FlowException {
        VirtualCas vcas = new VirtualCas(TapeSpeed.BPS_2400);

        String[] standardFixtures = {
                PROJECT_FOLDER + "/resources/token.bas",
                PROJECT_FOLDER + "/resources/flabin-flapbird.bin",
                PROJECT_FOLDER + "/resources/asciib.bas",
                PROJECT_FOLDER + "/resources/flacas-flapbird.cas"
        };

        for (String path : standardFixtures) {
            CasOutput expectedCas = vcas.convertToCas(path);
            TsxOutput tsxOut = vcas.convertToTsx(path);

            File tsxFile = tempFolder.newFile("rt_" + System.nanoTime() + ".tsx");
            Files.write(tsxFile.toPath(), tsxOut.toBytes());

            CasOutput casFromTsx = new VirtualCas().convertToCas(tsxFile.getAbsolutePath());
            assertArrayEquals("CAS mismatch after TSX round-trip for " + path,
                    expectedCas.toBytes(), casFromTsx.toBytes());
        }

        // Turbo ROM -> TSX succeeds and includes #11 TurboSpeedBlock; TSX -> CAS must fail fast
        byte[] rom16k = new byte[16384];
        rom16k[0] = 'A';
        rom16k[1] = 'B';
        rom16k[2] = 0x10;
        rom16k[3] = 0x40;
        File romFile = tempFolder.newFile("turbo_compat.rom");
        Files.write(romFile.toPath(), rom16k);

        TsxOutput turboTsx = new VirtualCas(TapeSpeed.TURBO).convertToTsx(romFile.getAbsolutePath());
        assertTrue(turboTsx.getTapeImage().getBlocks().stream().anyMatch(b -> b instanceof TurboSpeedBlock));
        assertFalse(turboTsx.getTapeImage().isCasCompatible());

        File turboTsxFile = tempFolder.newFile("turbo_compat.tsx");
        Files.write(turboTsxFile.toPath(), turboTsx.toBytes());
        assertCasRejected(turboTsxFile.getAbsolutePath());

        // Non-standard #4B framing -> rejected by CAS export
        TapeImage oddKcs = new TapeImage("odd", List.of(
                new KcsBlock(500, 729, 1000, 1458, 729, 0x24, 0x55, new byte[] { 0x01, 0x02 })));
        assertFalse(oddKcs.isCasCompatible());
        File oddTsxFile = tempFolder.newFile("odd_kcs.tsx");
        Files.write(oddTsxFile.toPath(), new TsxWriter(oddKcs).toBytes());
        assertCasRejected(oddTsxFile.getAbsolutePath());
    }

    @Test
    public void retimeKcsUpdatesStandardBlocksAndRejectsCustomOrTurbo() throws Exception, FlowException {
        TapeImage std = new VirtualCas(TapeSpeed.BPS_1200)
                .convertToTape(PROJECT_FOLDER + "/resources/token.bas");
        File tsxFile = tempFolder.newFile("retime.tsx");
        Files.write(tsxFile.toPath(), new TsxWriter(std).toBytes());

        TapeImage retimed = new VirtualCas(TapeSpeed.BPS_2400).convertToTape(tsxFile.getAbsolutePath());
        for (TapeBlock block : retimed.getBlocks()) {
            if (block instanceof KcsBlock kcs) {
                assertEquals(TapeSpeed.BPS_2400.pilotPulseTStates(), kcs.pilotPulseLen());
                assertEquals(TapeSpeed.BPS_2400.zeroPulseTStates(), kcs.zeroPulseLen());
                assertEquals(TapeSpeed.BPS_2400.onePulseTStates(), kcs.onePulseLen());
            }
        }

        try {
            new VirtualCas(TapeSpeed.TURBO).convertToTape(tsxFile.getAbsolutePath());
            fail("Expected FlowException: tsx_turbo_retime_not_supported");
        } catch (FlowException e) {
            assertEquals("tsx_turbo_retime_not_supported", e.getMessage());
        }

        TapeImage custom = new TapeImage("cust", List.of(new PureToneBlock(500, 20)));
        File customTsx = tempFolder.newFile("custom_retime.tsx");
        Files.write(customTsx.toPath(), new TsxWriter(custom).toBytes());
        try {
            new VirtualCas(TapeSpeed.BPS_2400).convertToTape(customTsx.getAbsolutePath());
            fail("Expected FlowException: tsx_custom_blocks_cannot_retime");
        } catch (FlowException e) {
            assertEquals("tsx_custom_blocks_cannot_retime", e.getMessage());
        }
    }

    private void assertCasRejected(String tsxPath) {
        try {
            new VirtualCas().convertToCas(tsxPath);
            fail("Expected FlowException: cas_incompatible_blocks");
        } catch (FlowException e) {
            assertEquals("cas_incompatible_blocks", e.getMessage());
        }
    }

    private static void assertParserError(byte[] tsxBytes, String expectedMessage) {
        try {
            TsxParser.parse(tsxBytes, "bad.tsx");
            fail("Expected FlowException: " + expectedMessage);
        } catch (FlowException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private static byte[] headerWith(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("ZXTape!".getBytes(StandardCharsets.US_ASCII));
        out.write(0x1A);
        out.write(1);
        out.write(21);
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static void assertBlockEquals(TapeBlock expected, TapeBlock actual) {
        assertEquals(expected.getClass(), actual.getClass());
        if (expected instanceof KcsBlock e && actual instanceof KcsBlock a) {
            assertEquals(e.pauseMs(), a.pauseMs());
            assertEquals(e.pilotPulseLen(), a.pilotPulseLen());
            assertEquals(e.pilotPulses(), a.pilotPulses());
            assertEquals(e.zeroPulseLen(), a.zeroPulseLen());
            assertEquals(e.onePulseLen(), a.onePulseLen());
            assertEquals(e.bitCfg(), a.bitCfg());
            assertEquals(e.byteCfg(), a.byteCfg());
            assertArrayEquals(e.data(), a.data());
        } else if (expected instanceof StandardSpeedBlock e && actual instanceof StandardSpeedBlock a) {
            assertEquals(e.pauseMs(), a.pauseMs());
            assertArrayEquals(e.data(), a.data());
        } else if (expected instanceof TurboSpeedBlock e && actual instanceof TurboSpeedBlock a) {
            assertEquals(e.pilotPulseLen(), a.pilotPulseLen());
            assertEquals(e.sync1PulseLen(), a.sync1PulseLen());
            assertEquals(e.sync2PulseLen(), a.sync2PulseLen());
            assertEquals(e.zeroPulseLen(), a.zeroPulseLen());
            assertEquals(e.onePulseLen(), a.onePulseLen());
            assertEquals(e.pilotPulses(), a.pilotPulses());
            assertEquals(e.usedBitsInLastByte(), a.usedBitsInLastByte());
            assertEquals(e.pauseMs(), a.pauseMs());
            assertArrayEquals(e.data(), a.data());
        } else if (expected instanceof PulseSequenceBlock e && actual instanceof PulseSequenceBlock a) {
            assertArrayEquals(e.pulseLengths(), a.pulseLengths());
        } else if (expected instanceof PureDataBlock e && actual instanceof PureDataBlock a) {
            assertEquals(e.zeroPulseLen(), a.zeroPulseLen());
            assertEquals(e.onePulseLen(), a.onePulseLen());
            assertEquals(e.usedBitsInLastByte(), a.usedBitsInLastByte());
            assertEquals(e.pauseMs(), a.pauseMs());
            assertArrayEquals(e.data(), a.data());
        } else if (expected instanceof DirectRecordingBlock e && actual instanceof DirectRecordingBlock a) {
            assertEquals(e.tStatesPerSample(), a.tStatesPerSample());
            assertEquals(e.pauseMs(), a.pauseMs());
            assertEquals(e.usedBitsInLastByte(), a.usedBitsInLastByte());
            assertArrayEquals(e.samples(), a.samples());
        } else if (expected instanceof GeneralizedDataBlock e && actual instanceof GeneralizedDataBlock a) {
            assertEquals(e.pauseMs(), a.pauseMs());
            assertEquals(e.totalPilotSymbols(), a.totalPilotSymbols());
            assertEquals(e.pilotStream(), a.pilotStream());
            assertEquals(e.totalDataSymbols(), a.totalDataSymbols());
            assertArrayEquals(e.dataStream(), a.dataStream());
            assertEquals(e.pilotSymbols().size(), a.pilotSymbols().size());
            assertEquals(e.dataSymbols().size(), a.dataSymbols().size());
        } else if (expected instanceof CustomInfoBlock e && actual instanceof CustomInfoBlock a) {
            assertEquals(e.identification(), a.identification());
            assertArrayEquals(e.data(), a.data());
        } else {
            assertEquals(expected, actual);
        }
    }
}
