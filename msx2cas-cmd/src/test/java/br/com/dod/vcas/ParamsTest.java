package br.com.dod.vcas;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParamsTest {

    @Test
    public void allParams() {
        String args[] = { "-i", "-r", "-w", "out.wav", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).getInputName());
        assertEquals("out.wav", params.getFiles().get(0).getOutputName());
    }

    @Test
    public void generateOutput() {
        String args[] = { "-r", "-w", "2400", "my.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).getInputName());
        assertEquals("my.wav", params.getFiles().get(0).getOutputName());
    }

    @Test
    public void missingBps() {
        String args[] = { "-r", "-w", "my.rom" };

        Params params = new Params(args);

        assertNull(params.getFiles());
    }

    @Test
    public void multiFiles() {
        String args[] = { "-i", "-r", "-w", "2400", "my.rom", "another.rom", "more.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).getInputName());
        assertEquals("another.rom", params.getFiles().get(1).getInputName());
        assertEquals("more.rom", params.getFiles().get(2).getInputName());
        assertEquals("my.wav", params.getFiles().get(0).getOutputName());
        assertEquals("anothe.wav", params.getFiles().get(1).getOutputName());
        assertEquals("more.wav", params.getFiles().get(2).getOutputName());
    }

    @Test
    public void multiFilesWithOutputName() {
        String args[] = { "-i", "-r", "-w", "out.wav", "2400", "my.rom", "another.rom", "more.rom" };

        Params params = new Params(args);

        assertTrue(params.resetRom());
        assertEquals("my.rom", params.getFiles().get(0).getInputName());
        assertEquals("another.rom", params.getFiles().get(1).getInputName());
        assertEquals("more.rom", params.getFiles().get(2).getInputName());
        assertEquals("my.wav", params.getFiles().get(0).getOutputName());
        assertEquals("anothe.wav", params.getFiles().get(1).getOutputName());
        assertEquals("more.wav", params.getFiles().get(2).getOutputName());
    }

}