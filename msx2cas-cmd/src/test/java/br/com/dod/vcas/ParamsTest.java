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
    public void mapperTypeImpliesMegaRam() {
        String args[] = { "-t", "ascii8", "-r", "-w", "out.wav", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.megaRam());
        assertTrue(params.resetRom());
        assertEquals(MapperType.ASCII8, params.mapperType());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.wav", params.getFiles().get(0).outputName());
    }

    @Test
    public void invalidMapperFallsBackToUsage() {
        String args[] = { "-t", "unknown", "2400", "my.rom" };

        Params params = new Params(args);

        assertEquals(0, params.getFiles().size());
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
    public void turboFlagEnablesTurboModeAndForces3600Bps() {
        String[] args = { "-f", "-r", "-w", "out.wav", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.turbo());
        assertTrue(params.resetRom());
        assertEquals(1, params.getFiles().size());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.wav", params.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr33075, params.getFiles().get(0).sampleRate());
        assertFalse(params.getFiles().get(0).sampleRate().isInverted());
    }

    @Test
    public void turboFlagWithoutBpsAutomaticallySelects3600Bps() {
        Params directPlay = new Params(new String[] { "-f", "-i", "-m", "my.rom" });
        assertTrue(directPlay.turbo());
        assertTrue(directPlay.megaRam());
        assertEquals(1, directPlay.getFiles().size());
        assertEquals("my.rom", directPlay.getFiles().get(0).inputName());
        assertEquals(SampleRate.sr33075, directPlay.getFiles().get(0).sampleRate());
        assertTrue(directPlay.getFiles().get(0).sampleRate().isInverted());
        assertFalse(directPlay.getFiles().get(0).write());

        Params defaultWav = new Params(new String[] { "-f", "-w", "my.rom" });
        assertEquals(1, defaultWav.getFiles().size());
        assertEquals("my.rom", defaultWav.getFiles().get(0).inputName());
        assertEquals("my.wav", defaultWav.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr33075, defaultWav.getFiles().get(0).sampleRate());
        assertFalse(defaultWav.getFiles().get(0).sampleRate().isInverted());
        assertTrue(defaultWav.getFiles().get(0).write());

        Params customWav = new Params(new String[] { "-f", "-w", "custom.wav", "my.rom" });
        assertEquals(1, customWav.getFiles().size());
        assertEquals("my.rom", customWav.getFiles().get(0).inputName());
        assertEquals("custom.wav", customWav.getFiles().get(0).outputName());
        assertEquals(SampleRate.sr33075, customWav.getFiles().get(0).sampleRate());
        assertTrue(customWav.getFiles().get(0).write());

        Params multiFiles = new Params(new String[] { "-f", "-w", "my.rom", "another.rom" });
        assertEquals(2, multiFiles.getFiles().size());
        assertEquals("my.wav", multiFiles.getFiles().get(0).outputName());
        assertEquals("anothe.wav", multiFiles.getFiles().get(1).outputName());
        assertEquals(SampleRate.sr33075, multiFiles.getFiles().get(0).sampleRate());
        assertEquals(SampleRate.sr33075, multiFiles.getFiles().get(1).sampleRate());
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
    public void turboFlagRejectsCasOutputCombination() {
        Params withoutBps = new Params(new String[] { "-f", "-c", "my.rom" });
        assertEquals(0, withoutBps.getFiles().size());

        Params withBps = new Params(new String[] { "-f", "-c", "2400", "my.rom" });
        assertEquals(0, withBps.getFiles().size());
    }

}
