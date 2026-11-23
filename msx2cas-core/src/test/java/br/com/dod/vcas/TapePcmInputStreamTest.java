package br.com.dod.vcas;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.DirectRecordingBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.PilotRle;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.SymbolDef;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopEndBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopStartBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PulseSequenceBlock;
import br.com.dod.vcas.tape.TapeBlock.PureDataBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.SetSignalLevelBlock;
import br.com.dod.vcas.tape.TapeBlock.StandardSpeedBlock;
import br.com.dod.vcas.tape.TapeBlock.TextBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.wav.TapePcmInputStream;
import br.com.dod.vcas.wav.WavWriter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TapePcmInputStreamTest {

    @Test
    public void synthesizesAllBlockTypesDeterministicallyAndMatchesWavWriter() throws Exception, FlowException {
        List<TapeBlock> blocks = List.of(
                new TextBlock("PCM synthesis check"),
                new SetSignalLevelBlock(1),
                new LoopStartBlock(2),
                new PureToneBlock(700, 4),
                new LoopEndBlock(),
                new PulseSequenceBlock(new int[] { 350, 700, 1050 }),
                new StandardSpeedBlock(20, new byte[] { 0x00, 0x55 }),
                new TurboSpeedBlock(212, 423, 423, 212, 423, 16, 4, 25, new byte[] { (byte) 0xA5, (byte) 0xF0 }),
                new PureDataBlock(350, 700, 6, 15, new byte[] { (byte) 0xA4 }),
                new DirectRecordingBlock(158, 10, 5, new byte[] { (byte) 0xB8 }),
                new GeneralizedDataBlock(
                        10,
                        1L,
                        2,
                        List.of(new SymbolDef(0, new int[] { 350, 350 })),
                        List.of(new PilotRle(0, 3)),
                        4L,
                        2,
                        List.of(
                                new SymbolDef(2, new int[] { 350, 350 }),
                                new SymbolDef(3, new int[] { 700, 700 })),
                        new byte[] { (byte) 0x50 }),
                KcsBlock.msx(new byte[] { 0x12, 0x34 }, TapeSpeed.BPS_2400, false, 30)
        );
        TapeImage image = new TapeImage("pcmall", blocks);

        for (SampleRate rate : SampleRate.values()) {
            byte[] normalWav = new WavWriter(image, rate, false).toBytes();
            byte[] invertedWav = new WavWriter(image, rate, true).toBytes();
            assertEquals(normalWav.length, invertedWav.length);
            assertTrue(normalWav.length > 44);

            byte[] normalPcm = Arrays.copyOfRange(normalWav, 44, normalWav.length);
            byte[] invertedPcm = Arrays.copyOfRange(invertedWav, 44, invertedWav.length);

            for (int i = 0; i < normalPcm.length; i++) {
                byte n = normalPcm[i];
                byte inv = invertedPcm[i];
                if (n == TapePcmInputStream.SILENCE_BYTE) {
                    assertEquals(TapePcmInputStream.SILENCE_BYTE, inv);
                } else if (n == 0x00) {
                    assertEquals((byte) 0xFF, inv);
                } else {
                    assertEquals((byte) 0xFF, n);
                    assertEquals((byte) 0x00, inv);
                }
            }

            try (TapePcmInputStream stream = new TapePcmInputStream(image, rate, false)) {
                assertTrue(stream.markSupported());
                assertEquals("pcmall", stream.casName());
                assertEquals(normalPcm.length, stream.getTotalSamples());

                byte[] chunked = readChunked(stream, 113);
                assertEquals(normalPcm.length, stream.getPosition());
                assertEquals(0, stream.available());
                assertArrayEquals(normalPcm, chunked);

                stream.reset();
                assertEquals(0L, stream.getPosition());
                ByteArrayOutputStream singleBytes = new ByteArrayOutputStream(normalPcm.length);
                int b;
                while ((b = stream.read()) != -1) {
                    singleBytes.write(b);
                }
                assertArrayEquals(normalPcm, singleBytes.toByteArray());
            }
        }
    }

    @Test
    public void exposesStopTapeMarkersAndRendersFourSecondSilence() throws Exception, FlowException {
        TapeImage image = new TapeImage("stops", List.of(
                new PureToneBlock(3500, 10),
                new PauseBlock(0),
                new PureToneBlock(3500, 10),
                new PauseBlock(0)
        ));

        try (TapePcmInputStream stream = new TapePcmInputStream(image, SampleRate.sr44100, false)) {
            List<Long> stops = stream.getStopPositions();
            assertEquals(2, stops.size());
            // 10 pulses of 3500 T-states = 10ms = 441 samples at 44100 Hz
            assertEquals(441L, stops.get(0).longValue());
            // 4s stop silence (176400 samples) + 10ms = 177282 samples
            assertEquals(441L + 176400L + 441L, stops.get(1).longValue());
            assertEquals(stops.get(1) + 176400L, stream.getTotalSamples());

            byte[] all = readChunked(stream, 1024);
            for (int i = 441; i < 441 + 176400; i++) {
                assertEquals(TapePcmInputStream.SILENCE_BYTE, all[i]);
            }
        }
    }

    @Test
    public void symmetricTurboQuantizesZeroJitterWhileNonSymmetricPreservesTStates() throws Exception, FlowException {
        byte[] symData = new byte[] { (byte) 0xA5, 0x3C };
        TapeImage symmetric = new TapeImage("sym11", List.of(
                new TurboSpeedBlock(212, 424, 424, 212, 424, 16, 8, 0, symData)));

        long[] expectedZero = { 1L, 2L, 2L, 3L, 3L };
        SampleRate[] rates = SampleRate.values();
        assertEquals(expectedZero.length, rates.length);

        for (int r = 0; r < rates.length; r++) {
            long zeroSamples = expectedZero[r];
            long oneSamples = zeroSamples * 2L;
            try (TapePcmInputStream stream = new TapePcmInputStream(symmetric, rates[r], false)) {
                byte[] pcm = readChunked(stream, 256);
                List<Integer> runLengths = measureRunLengths(pcm);
                int idx = 0;
                for (int i = 0; i < 16; i++) {
                    assertEquals(zeroSamples, runLengths.get(idx++).longValue());
                }
                assertEquals(oneSamples, runLengths.get(idx++).longValue());
                assertEquals(oneSamples, runLengths.get(idx++).longValue());
                for (byte b : symData) {
                    for (int bit = 7; bit >= 0; bit--) {
                        long bitSamples = ((b >>> bit) & 1) == 0 ? zeroSamples : oneSamples;
                        assertEquals(bitSamples, runLengths.get(idx++).longValue());
                        assertEquals(bitSamples, runLengths.get(idx++).longValue());
                    }
                }
                assertEquals(idx, runLengths.size());
                assertEquals(pcm.length, stream.getTotalSamples());
            }
        }

        TapeImage nonSymmetric = new TapeImage("asym11", List.of(
                new TurboSpeedBlock(2168, 667, 735, 855, 1710, 16, 8, 0, new byte[] { 0x55 })));
        long totalTStates = 16L * 2168L + 667L + 735L + 4L * 2L * 855L + 4L * 2L * 1710L;
        for (SampleRate rate : rates) {
            try (TapePcmInputStream stream = new TapePcmInputStream(nonSymmetric, rate, false)) {
                long expectedFractional = totalTStates * rate.intValue() / TapeSpeed.TSTATES_PER_SECOND;
                assertTrue("Sample count mismatch for non-symmetric #11 at " + rate.intValue(),
                        Math.abs(stream.getTotalSamples() - expectedFractional) <= 1L);
            }
        }
    }

    @Test
    public void handlesMetadataOnlyTapeAndRejectsEmptyOrNullInputs() throws Exception, FlowException {
        TapeImage metaOnly = new TapeImage("meta", List.of(new TextBlock("Only text")));
        try (TapePcmInputStream stream = new TapePcmInputStream(metaOnly)) {
            assertEquals(0L, stream.getTotalSamples());
            assertEquals(-1, stream.read());
        }

        try {
            new TapePcmInputStream(new TapeImage("empty", List.of()));
            fail("Expected FlowException: tape_empty");
        } catch (FlowException e) {
            assertEquals("tape_empty", e.getMessage());
        }

        try {
            new TapePcmInputStream(metaOnly, null, false);
            fail("Expected FlowException: sample_rate_required");
        } catch (FlowException e) {
            assertEquals("sample_rate_required", e.getMessage());
        }
    }

    private static List<Integer> measureRunLengths(byte[] pcm) {
        List<Integer> lengths = new ArrayList<>();
        if (pcm.length == 0) {
            return lengths;
        }
        int run = 1;
        for (int i = 1; i < pcm.length; i++) {
            if (pcm[i] == pcm[i - 1]) {
                run++;
            } else {
                lengths.add(run);
                run = 1;
            }
        }
        lengths.add(run);
        return lengths;
    }

    private static byte[] readChunked(TapePcmInputStream stream, int chunkSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) stream.getTotalSamples());
        byte[] buf = new byte[chunkSize];
        int n;
        while ((n = stream.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
