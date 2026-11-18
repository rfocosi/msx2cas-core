package br.com.dod.vcas;

import br.com.dod.vcas.model.MapperType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParamsTest {

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
    public void turboFlagEnablesTurboMode() {
        String[] args = { "-f", "-r", "-w", "out.wav", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.turbo());
        assertTrue(params.resetRom());
        assertEquals(1, params.getFiles().size());
        assertEquals("my.rom", params.getFiles().get(0).inputName());
        assertEquals("out.wav", params.getFiles().get(0).outputName());
    }

    @Test
    public void turboFlagRejectsCasOutputCombination() {
        Params withoutBps = new Params(new String[] { "-f", "-c", "my.rom" });
        assertEquals(0, withoutBps.getFiles().size());

        Params withBps = new Params(new String[] { "-f", "-c", "2400", "my.rom" });
        assertEquals(0, withBps.getFiles().size());
    }

}
