package br.com.dod.vcas;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ParamsTest {

    @Test
    public void allParams() {
        String[] args = { "-i", "-r", "-m", "-w", "out.wav", "-b", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertTrue(params.megaRam());
        assertFalse(params.turbo());
        assertTrue(params.inverted());
        assertEquals(TapeSpeed.BPS_2400, params.tapeSpeed());
        assertEquals(SampleRate.sr44100, params.sampleRate());
        assertEquals(OutputMode.WAV, params.getFiles().get(0).outputMode());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.wav", params.getFiles().get(0).outputName());
    }

    @Test
    public void generateOutput() {
        String[] args = { "-r", "-w", "-b", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("my.wav", params.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr44100, params.getFiles().get(0).sampleRate());
    }

    @Test
    public void defaultsTo2400BpsWhenBpsOmitted() {
        Params wavDefault = new Params(new String[] { "-r", "-w", "my.rom" });
        assertTrue(wavDefault.resetRom());
        assertEquals(1, wavDefault.getFiles().size());
        assertEquals(OutputMode.WAV, wavDefault.getFiles().get(0).outputMode());
        assertEquals("my.wav", wavDefault.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_2400, wavDefault.getFiles().get(0).tapeSpeed());
        assertFalse(wavDefault.getFiles().get(0).turbo());

        Params wavCustomOut = new Params(new String[] { "-w", "out.wav", "my.rom" });
        assertEquals(1, wavCustomOut.getFiles().size());
        assertEquals(OutputMode.WAV, wavCustomOut.getFiles().get(0).outputMode());
        assertEquals("out.wav", wavCustomOut.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_2400, wavCustomOut.getFiles().get(0).tapeSpeed());

        Params playSingle = new Params(new String[] { "my.rom" });
        assertEquals(1, playSingle.getFiles().size());
        assertEquals(OutputMode.PLAY, playSingle.getFiles().get(0).outputMode());
        assertEquals(TapeSpeed.BPS_2400, playSingle.getFiles().get(0).tapeSpeed());
        assertEquals(SampleRate.sr44100, playSingle.getFiles().get(0).sampleRate());
        assertFalse(playSingle.getFiles().get(0).turbo());

        Params playMulti = new Params(new String[] { "-i", "my.rom", "another.rom" });
        assertEquals(2, playMulti.getFiles().size());
        assertEquals(OutputMode.PLAY, playMulti.getFiles().get(0).outputMode());
        assertEquals(TapeSpeed.BPS_2400, playMulti.getFiles().get(0).tapeSpeed());
        assertEquals(TapeSpeed.BPS_2400, playMulti.getFiles().get(1).tapeSpeed());
        assertTrue(playMulti.getFiles().get(0).inverted());
        assertTrue(playMulti.getFiles().get(1).inverted());
    }

    @Test
    public void multiFiles() {
        String[] args = { "-i", "-r", "-w", "-b", "2400", "my.rom", "another.rom", "more.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("another.rom", params.getFiles().get(1).inputName());
        assertEquals("more.rom", params.getFiles().get(2).inputName());
        assertEquals("my.wav", params.getFiles().get(0).outputName());
        assertEquals("anothe.wav", params.getFiles().get(1).outputName());
        assertEquals("more.wav", params.getFiles().get(2).outputName());
    }

    @Test
    public void multiFilesWithOutputName() {
        String[] args = { "-i", "-r", "-w", "out.wav", "-b", "2400", "my.rom", "another.rom", "more.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("another.rom", params.getFiles().get(1).inputName());
        assertEquals("more.rom", params.getFiles().get(2).inputName());
        assertEquals("my.wav", params.getFiles().get(0).outputName());
        assertEquals("anothe.wav", params.getFiles().get(1).outputName());
        assertEquals("more.wav", params.getFiles().get(2).outputName());
    }

    @Test
    public void megaRamDefaultsToAutoAndAcceptsExplicitMapper() {
        Params autoDefault = new Params(new String[] { "-m", "-r", "-w", "out.wav", "-b", "2400", "my.rom" });
        assertTrue(autoDefault.megaRam());
        assertEquals(MapperType.AUTO, autoDefault.mapperType());
        assertEquals(1, autoDefault.getFiles().size());

        Params autoBeforeSpeed = new Params(new String[] { "-m", "-b", "2400", "my.rom" });
        assertTrue(autoBeforeSpeed.megaRam());
        assertEquals(MapperType.AUTO, autoBeforeSpeed.mapperType());
        assertEquals(1, autoBeforeSpeed.getFiles().size());

        Params forcedMapper = new Params(new String[] { "-m", "ascii8", "-r", "-w", "out.wav", "-b", "2400", "my.rom" });
        assertTrue(forcedMapper.megaRam());
        assertTrue(forcedMapper.resetRom());
        assertEquals(MapperType.ASCII8, forcedMapper.mapperType());
        assertEquals("my.rom", forcedMapper.getFiles().get(0).inputName());
        assertEquals("out.wav", forcedMapper.getFiles().get(0).outputName());

        Params forcedMapperTurbo = new Params(new String[] { "-m", "konami5", "--turbo", "my.rom" });
        assertTrue(forcedMapperTurbo.megaRam());
        assertTrue(forcedMapperTurbo.turbo());
        assertEquals(MapperType.KONAMI5, forcedMapperTurbo.mapperType());
        assertEquals(TapeSpeed.BPS_2400, forcedMapperTurbo.getFiles().get(0).tapeSpeed());
        assertTrue(forcedMapperTurbo.getFiles().get(0).turbo());
        assertEquals(SampleRate.sr44100, forcedMapperTurbo.getFiles().get(0).sampleRate());

        Params casAuto = new Params(new String[] { "-c", "-m", "my.rom" });
        assertTrue(casAuto.megaRam());
        assertEquals(MapperType.AUTO, casAuto.mapperType());
        assertEquals(1, casAuto.getFiles().size());
        assertEquals("my.cas", casAuto.getFiles().get(0).outputName());

        Params casForced = new Params(new String[] { "-c", "-m", "ascii16", "my.rom" });
        assertTrue(casForced.megaRam());
        assertEquals(MapperType.ASCII16, casForced.mapperType());
        assertEquals(1, casForced.getFiles().size());
        assertEquals("my.cas", casForced.getFiles().get(0).outputName());
    }

    @Test
    public void invalidMapperUnknownFlagsAndPositionalBpsFallBackToUsage() {
        Params invalidMapper = new Params(new String[] { "-m", "unknown", "-b", "2400", "my.rom" });
        assertEquals(0, invalidMapper.getFiles().size());

        Params unknownFlag = new Params(new String[] { "-z", "-b", "2400", "my.rom" });
        assertEquals(0, unknownFlag.getFiles().size());

        Params unknownLongFlag = new Params(new String[] { "--unknown", "my.rom" });
        assertEquals(0, unknownLongFlag.getFiles().size());

        Params missingBpsValue = new Params(new String[] { "-b", "my.rom" });
        assertEquals(0, missingBpsValue.getFiles().size());

        Params positionalBps = new Params(new String[] { "2400", "my.rom" });
        assertEquals(0, positionalBps.getFiles().size());

        Params wavPositionalBps = new Params(new String[] { "-w", "2400", "my.rom" });
        assertEquals(0, wavPositionalBps.getFiles().size());
    }

    @Test
    public void writeCasSingleFileDefaultNameWithoutBps() {
        String[] args = { "-r", "-c", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals(1, params.getFiles().size());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("my.cas", params.getFiles().get(0).outputName());
        assertEquals(OutputMode.CAS, params.getFiles().get(0).outputMode());
        assertNull(params.getFiles().get(0).tapeSpeed());
    }

    @Test
    public void writeCasSingleFileCustomNameWithoutBps() {
        String[] args = { "-r", "-m", "-c", "out.cas", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertTrue(params.megaRam());
        assertEquals(1, params.getFiles().size());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.cas", params.getFiles().get(0).outputName());
        assertEquals(OutputMode.CAS, params.getFiles().get(0).outputMode());
    }

    @Test
    public void writeCasMultipleFilesWithoutBps() {
        String[] args = { "-r", "-c", "my.rom", "another.rom", "more.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals(3, params.getFiles().size());
        assertEquals("my.cas", params.getFiles().get(0).outputName());
        assertEquals("anothe.cas", params.getFiles().get(1).outputName());
        assertEquals("more.cas", params.getFiles().get(2).outputName());
    }

    @Test
    public void writeCasMultipleFilesIgnoresCustomOutputName() {
        String[] args = { "-c", "out.cas", "my.rom", "another.rom" };

        Params params = new Params(args);

        assertEquals(2, params.getFiles().size());
        assertEquals("my.cas", params.getFiles().get(0).outputName());
        assertEquals("anothe.cas", params.getFiles().get(1).outputName());
    }

    @Test
    public void writeCasSkipsCasInputsAndAcceptsTsxInputs() {
        Params casSkip = new Params(new String[] { "-c", "game.cas" });
        assertEquals(0, casSkip.getFiles().size());

        Params tsxToCas = new Params(new String[] { "-c", "game.tsx" });
        assertEquals(1, tsxToCas.getFiles().size());
        assertEquals(OutputMode.CAS, tsxToCas.getFiles().get(0).outputMode());
        assertEquals("game.cas", tsxToCas.getFiles().get(0).outputName());
        assertNull(tsxToCas.getFiles().get(0).tapeSpeed());
    }

    @Test
    public void writeTsxDefaultsTo2400BpsAndHandlesExplicitSpeedAndTsxReTiming() {
        Params defaultTsx = new Params(new String[] { "-r", "-t", "my.rom" });
        assertTrue(defaultTsx.resetRom());
        assertEquals(1, defaultTsx.getFiles().size());
        assertEquals(OutputMode.TSX, defaultTsx.getFiles().get(0).outputMode());
        assertEquals(TapeSpeed.BPS_2400, defaultTsx.getFiles().get(0).tapeSpeed());
        assertEquals("my.tsx", defaultTsx.getFiles().get(0).outputName());

        Params customTsx = new Params(new String[] { "-t", "out.tsx", "my.rom" });
        assertEquals(1, customTsx.getFiles().size());
        assertEquals("out.tsx", customTsx.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_2400, customTsx.getFiles().get(0).tapeSpeed());

        Params turboTsx = new Params(new String[] { "-t", "--turbo", "my.rom" });
        assertEquals(1, turboTsx.getFiles().size());
        assertEquals(TapeSpeed.BPS_2400, turboTsx.getFiles().get(0).tapeSpeed());
        assertTrue(turboTsx.getFiles().get(0).turbo());
        assertEquals("my.tsx", turboTsx.getFiles().get(0).outputName());

        Params turboTsx1200 = new Params(new String[] { "-t", "--turbo", "-b", "1200", "my.rom" });
        assertEquals(1, turboTsx1200.getFiles().size());
        assertEquals(TapeSpeed.BPS_1200, turboTsx1200.getFiles().get(0).tapeSpeed());
        assertTrue(turboTsx1200.getFiles().get(0).turbo());
        assertEquals("my.tsx", turboTsx1200.getFiles().get(0).outputName());

        Params skipTsxNoBps = new Params(new String[] { "-t", "game.tsx" });
        assertEquals(0, skipTsxNoBps.getFiles().size());

        Params retimeTsx = new Params(new String[] { "-t", "-b", "1200", "game.tsx" });
        assertEquals(1, retimeTsx.getFiles().size());
        assertEquals(TapeSpeed.BPS_1200, retimeTsx.getFiles().get(0).tapeSpeed());
        assertEquals("game.tsx", retimeTsx.getFiles().get(0).outputName());

        Params combined = new Params(new String[] { "-t", "-w", "-b", "2400", "my.rom" });
        assertEquals(0, combined.getFiles().size());
    }

    @Test
    public void sampleRateOverrideAndTsxPlaybackWithoutBps() {
        Params customSr = new Params(new String[] { "-s", "48000", "-w", "-b", "2400", "my.rom" });
        assertEquals(1, customSr.getFiles().size());
        assertEquals(SampleRate.sr48000, customSr.getFiles().get(0).sampleRate());

        Params invalidSr = new Params(new String[] { "-s", "96000", "-w", "-b", "2400", "my.rom" });
        assertEquals(0, invalidSr.getFiles().size());

        Params tsxPlay = new Params(new String[] { "game.tsx" });
        assertEquals(1, tsxPlay.getFiles().size());
        assertEquals(OutputMode.PLAY, tsxPlay.getFiles().get(0).outputMode());
        assertNull(tsxPlay.getFiles().get(0).tapeSpeed());
        assertEquals(SampleRate.sr44100, tsxPlay.getFiles().get(0).sampleRate());

        Params tsxWav = new Params(new String[] { "-i", "-s", "22050", "-w", "out.wav", "game.tsx" });
        assertEquals(1, tsxWav.getFiles().size());
        assertEquals(OutputMode.WAV, tsxWav.getFiles().get(0).outputMode());
        assertEquals("out.wav", tsxWav.getFiles().get(0).outputName());
        assertNull(tsxWav.getFiles().get(0).tapeSpeed());
        assertEquals(SampleRate.sr22050, tsxWav.getFiles().get(0).sampleRate());
        assertTrue(tsxWav.getFiles().get(0).inverted());
    }

    @Test
    public void turboFlagAndBpsValidation() {
        Params directPlay = new Params(new String[] { "-i", "-m", "--turbo", "my.rom" });
        assertTrue(directPlay.turbo());
        assertTrue(directPlay.megaRam());
        assertEquals(1, directPlay.getFiles().size());
        assertEquals(TapeSpeed.BPS_2400, directPlay.getFiles().get(0).tapeSpeed());
        assertTrue(directPlay.getFiles().get(0).turbo());
        assertTrue(directPlay.getFiles().get(0).inverted());
        assertEquals(OutputMode.PLAY, directPlay.getFiles().get(0).outputMode());

        Params shortFastFlag = new Params(new String[] { "-f", "-w", "out.wav", "-b", "3000", "my.rom" });
        assertTrue(shortFastFlag.turbo());
        assertEquals(1, shortFastFlag.getFiles().size());
        assertEquals(OutputMode.WAV, shortFastFlag.getFiles().get(0).outputMode());
        assertEquals("out.wav", shortFastFlag.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_3000, shortFastFlag.getFiles().get(0).tapeSpeed());
        assertTrue(shortFastFlag.getFiles().get(0).turbo());

        Params fastLoadFlag = new Params(new String[] { "--fast-load", "--wav", "my.rom" });
        assertTrue(fastLoadFlag.turbo());
        assertEquals(OutputMode.WAV, fastLoadFlag.getFiles().get(0).outputMode());
        assertEquals("my.wav", fastLoadFlag.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_2400, fastLoadFlag.getFiles().get(0).tapeSpeed());
        assertTrue(fastLoadFlag.getFiles().get(0).turbo());

        Params at3000 = new Params(new String[] { "-w", "-b", "3000", "my.rom" });
        assertEquals(1, at3000.getFiles().size());
        assertEquals(TapeSpeed.BPS_3000, at3000.getFiles().get(0).tapeSpeed());
        assertFalse(at3000.getFiles().get(0).turbo());

        Params legacyPositionalTurbo = new Params(new String[] { "turbo", "my.rom" });
        assertEquals(0, legacyPositionalTurbo.getFiles().size());

        Params legacyPositionalTurboWav = new Params(new String[] { "-w", "out.wav", "turbo", "my.rom" });
        assertEquals(0, legacyPositionalTurboWav.getFiles().size());

        Params at3600 = new Params(new String[] { "-w", "-b", "3600", "my.rom" });
        assertEquals(0, at3600.getFiles().size());

        Params casTurbo = new Params(new String[] { "-c", "--turbo", "my.rom" });
        assertEquals(0, casTurbo.getFiles().size());

        Params casFastShort = new Params(new String[] { "--cas", "-f", "my.rom" });
        assertEquals(0, casFastShort.getFiles().size());
    }

    @Test
    public void doubleDashAliasesWorkAcrossModesAndOrdering() {
        Params wavLongAll = new Params(new String[] {
                "--invert-waveform", "--reset", "--megarom", "ascii8",
                "--sr", "48000", "--bps", "1200", "--fast-load", "--wav", "out.wav", "my.rom"
        });
        assertTrue(wavLongAll.inverted());
        assertTrue(wavLongAll.resetRom());
        assertTrue(wavLongAll.megaRam());
        assertEquals(MapperType.ASCII8, wavLongAll.mapperType());
        assertTrue(wavLongAll.turbo());
        assertEquals(SampleRate.sr48000, wavLongAll.sampleRate());
        assertEquals(TapeSpeed.BPS_1200, wavLongAll.tapeSpeed());
        assertEquals(1, wavLongAll.getFiles().size());
        assertEquals(OutputMode.WAV, wavLongAll.getFiles().get(0).outputMode());
        assertEquals("out.wav", wavLongAll.getFiles().get(0).outputName());
        assertEquals("my.rom", wavLongAll.getFiles().get(0).inputName());

        Params tsxLongRetime = new Params(new String[] { "--tsx", "custom.tsx", "--bps", "1200", "game.tsx" });
        assertEquals(1, tsxLongRetime.getFiles().size());
        assertEquals(OutputMode.TSX, tsxLongRetime.getFiles().get(0).outputMode());
        assertEquals("custom.tsx", tsxLongRetime.getFiles().get(0).outputName());
        assertEquals(TapeSpeed.BPS_1200, tsxLongRetime.getFiles().get(0).tapeSpeed());

        Params casLong = new Params(new String[] { "--megarom", "--cas", "out.cas", "my.rom" });
        assertTrue(casLong.megaRam());
        assertEquals(MapperType.AUTO, casLong.mapperType());
        assertEquals(1, casLong.getFiles().size());
        assertEquals(OutputMode.CAS, casLong.getFiles().get(0).outputMode());
        assertEquals("out.cas", casLong.getFiles().get(0).outputName());
    }

    @Test
    public void usageHelpListsSampleRateAndTurboOptions() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            new Params(new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        String usage = captured.toString(StandardCharsets.UTF_8);
        assertTrue(usage.contains("-i, --invert-waveform"));
        assertTrue(usage.contains("-r, --reset"));
        assertTrue(usage.contains("-m, --megarom"));
        assertTrue(usage.contains("-f, --turbo, --fast-load"));
        assertTrue(usage.contains("-b, --bps <rate>"));
        assertTrue(usage.contains("1200, 2400, 3000; default: 2400"));
        assertTrue(usage.contains("-s, --sr <rate>"));
        assertTrue(usage.contains("11025, 22050, 44100, 48000; default: 44100"));
        assertTrue(usage.contains("-c, --cas [<output-file>]"));
        assertTrue(usage.contains("-t, --tsx [<output-file>]"));
        assertTrue(usage.contains("-w, --wav [<output-file>]"));
    }
}
