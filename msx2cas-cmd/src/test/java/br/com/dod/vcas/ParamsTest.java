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

}
