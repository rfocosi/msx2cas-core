package br.com.dod.vcas;

import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParamsTest {

    @After
    public void resetSampleRates() {
        for (SampleRate rate : SampleRate.values()) {
            rate.invertWaveForm(false);
        }
    }

    @Test
    public void allParams() {
        String args[] = { "-i", "-r", "-m", "-w", "out.wav", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertTrue(params.megaRam());
        assertFalse(params.turbo());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.wav", params.getFiles().get(0).outputName());
    }

    @Test
    public void generateOutput() {
        String args[] = { "-r", "-w", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("my.wav", params.getFiles().get(0).outputName());
    }

    @Test
    public void missingBps() {
        String args[] = { "-r", "-w", "my.rom" };

        Params params = new Params(args);

        assertEquals(0, params.getFiles().size());
    }

    @Test
    public void multiFiles() {
        String args[] = { "-i", "-r", "-w", "2400", "my.rom", "another.rom", "more.rom" };

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
        String args[] = { "-i", "-r", "-w", "out.wav", "2400", "my.rom", "another.rom", "more.rom" };

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
        Params autoDefault = new Params(new String[] { "-m", "-r", "-w", "out.wav", "2400", "my.rom" });
        assertTrue(autoDefault.megaRam());
        assertEquals(MapperType.AUTO, autoDefault.mapperType());
        assertEquals(1, autoDefault.getFiles().size());

        Params autoBeforeSpeed = new Params(new String[] { "-m", "2400", "my.rom" });
        assertTrue(autoBeforeSpeed.megaRam());
        assertEquals(MapperType.AUTO, autoBeforeSpeed.mapperType());
        assertEquals(1, autoBeforeSpeed.getFiles().size());

        Params forcedMapper = new Params(new String[] { "-m", "ascii8", "-r", "-w", "out.wav", "2400", "my.rom" });
        assertTrue(forcedMapper.megaRam());
        assertTrue(forcedMapper.resetRom());
        assertEquals(MapperType.ASCII8, forcedMapper.mapperType());
        assertEquals("my.rom", forcedMapper.getFiles().get(0).inputName());
        assertEquals("out.wav", forcedMapper.getFiles().get(0).outputName());

        Params forcedMapperTurbo = new Params(new String[] { "-m", "konami5", "turbo", "my.rom" });
        assertTrue(forcedMapperTurbo.megaRam());
        assertTrue(forcedMapperTurbo.turbo());
        assertEquals(MapperType.KONAMI5, forcedMapperTurbo.mapperType());
        assertEquals(SampleRate.sr33075, forcedMapperTurbo.getFiles().get(0).sampleRate());

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
    public void invalidMapperAndRemovedTFlagFallBackToUsage() {
        Params invalidMapper = new Params(new String[] { "-m", "unknown", "2400", "my.rom" });
        assertEquals(0, invalidMapper.getFiles().size());

        Params removedTFlag = new Params(new String[] { "-t", "ascii8", "2400", "my.rom" });
        assertEquals(0, removedTFlag.getFiles().size());
    }

    @Test
    public void writeCasSingleFileDefaultNameWithoutBps() {
        String[] args = { "-r", "-c", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals(1, params.getFiles().size());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("my.cas", params.getFiles().get(0).outputName());
        assertTrue(params.getFiles().get(0).writeCas());
        assertFalse(params.getFiles().get(0).write());
        assertNull(params.getFiles().get(0).sampleRate());
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
        assertTrue(params.getFiles().get(0).writeCas());
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
    public void writeCasSkipsCasInputFiles() {
        String[] args = { "-c", "game.cas" };

        Params params = new Params(args);

        assertEquals(0, params.getFiles().size());
    }

    @Test
    public void turboSpeedEnablesTurboModeAndSelects3600Bps() {
        Params directPlay = new Params(new String[] { "-i", "-m", "turbo", "my.rom" });
        assertTrue(directPlay.turbo());
        assertTrue(directPlay.megaRam());
        assertEquals(1, directPlay.getFiles().size());
        assertEquals("my.rom", directPlay.getFiles().get(0).inputName());
        assertEquals(SampleRate.sr33075, directPlay.getFiles().get(0).sampleRate());
        assertTrue(directPlay.getFiles().get(0).sampleRate().isInverted());
        assertFalse(directPlay.getFiles().get(0).write());

        Params defaultWav = new Params(new String[] { "-w", "turbo", "my.rom" });
        assertTrue(defaultWav.turbo());
        assertEquals(1, defaultWav.getFiles().size());
        assertEquals("my.rom", defaultWav.getFiles().get(0).inputName());
        assertEquals("my.wav", defaultWav.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr33075, defaultWav.getFiles().get(0).sampleRate());
        assertFalse(defaultWav.getFiles().get(0).sampleRate().isInverted());
        assertTrue(defaultWav.getFiles().get(0).write());

        Params customWav = new Params(new String[] { "-r", "-w", "out.wav", "TURBO", "my.rom" });
        assertTrue(customWav.turbo());
        assertTrue(customWav.resetRom());
        assertEquals(1, customWav.getFiles().size());
        assertEquals("my.rom", customWav.getFiles().get(0).inputName());
        assertEquals("out.wav", customWav.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr33075, customWav.getFiles().get(0).sampleRate());
        assertTrue(customWav.getFiles().get(0).write());

        Params multiFiles = new Params(new String[] { "-w", "turbo", "my.rom", "another.rom" });
        assertTrue(multiFiles.turbo());
        assertEquals(2, multiFiles.getFiles().size());
        assertEquals("my.wav", multiFiles.getFiles().get(0).outputName());
        assertEquals("anothe.wav", multiFiles.getFiles().get(1).outputName());
        assertEquals(SampleRate.sr33075, multiFiles.getFiles().get(0).sampleRate());
        assertEquals(SampleRate.sr33075, multiFiles.getFiles().get(1).sampleRate());
    }

    @Test
    public void removedTurboFlagIsRejected() {
        Params legacyFlagOnly = new Params(new String[] { "-f", "-w", "my.rom" });
        assertEquals(0, legacyFlagOnly.getFiles().size());

        Params legacyFlagWithBps = new Params(new String[] { "-f", "-w", "2400", "my.rom" });
        assertEquals(0, legacyFlagWithBps.getFiles().size());
    }

    @Test
    public void maxSpeedWithoutTurboIs3000Bps() {
        Params at3000 = new Params(new String[] { "-w", "3000", "my.rom" });
        assertFalse(at3000.turbo());
        assertEquals(1, at3000.getFiles().size());
        assertEquals(SampleRate.sr27563, at3000.getFiles().get(0).sampleRate());

        Params at3600 = new Params(new String[] { "-w", "3600", "my.rom" });
        assertEquals(0, at3600.getFiles().size());

        Params play3600 = new Params(new String[] { "3600", "my.rom" });
        assertEquals(0, play3600.getFiles().size());
    }

    @Test
    public void turboSpeedRejectsCasOutputCombination() {
        Params withTurbo = new Params(new String[] { "-c", "turbo", "my.rom" });
        assertEquals(0, withTurbo.getFiles().size());
    }

}
