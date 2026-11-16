package br.com.dod.vcas;

import java.util.Arrays;

import org.junit.Test;

import br.com.dod.vcas.converter.impl.MegaRomPatcher;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.MapperType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MegaRomPatcherTest {

    @Test
    public void detectsKonami4AndReturnsOriginalArray() throws Exception, FlowException {
        byte[] rom = new byte[128 * 1024];
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = 0x20;
        rom[3] = 0x40;
        putLdNnA(rom, 0x20, 0x6000);
        putLdNnA(rom, 0x30, 0x8000);
        putLdNnA(rom, 0x40, 0xA000);

        assertEquals(MapperType.KONAMI4, MegaRomPatcher.detectMapper(rom));
        assertSame(rom, MegaRomPatcher.patchToKonami4(rom, MapperType.AUTO));
    }

    @Test
    public void detectsAndPatchesAscii8PreservingLdirSourcePointers() throws Exception, FlowException {
        byte[] rom = new byte[128 * 1024];
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = 0x20;
        rom[3] = 0x40;
        putLdNnA(rom, 0x20, 0x6000);
        putLdNnA(rom, 0x24, 0x6800);
        putLdNnA(rom, 0x28, 0x7000);
        putLdNnA(rom, 0x2C, 0x7800);
        // LD HL, 0x7000; LD (HL), A (should be patched to 0x8000)
        rom[0x30] = 0x21;
        rom[0x31] = 0x00;
        rom[0x32] = 0x70;
        rom[0x33] = 0x77;
        // LD HL, 0x6000; LD DE, 0xC000; LDIR (data copy, must NOT be patched)
        rom[0x40] = 0x21;
        rom[0x41] = 0x00;
        rom[0x42] = 0x60;
        rom[0x43] = 0x11;
        rom[0x44] = 0x00;
        rom[0x45] = (byte) 0xC0;
        rom[0x46] = (byte) 0xED;
        rom[0x47] = (byte) 0xB0;

        assertEquals(MapperType.ASCII8, MegaRomPatcher.detectMapper(rom));
        byte[] patched = MegaRomPatcher.patchToKonami4(rom, MapperType.AUTO);

        assertEquals(0x40, patched[0x22] & 0xFF);
        assertEquals(0x60, patched[0x26] & 0xFF);
        assertEquals(0x80, patched[0x2A] & 0xFF);
        assertEquals(0xA0, patched[0x2E] & 0xFF);
        assertEquals(0x80, patched[0x32] & 0xFF);
        assertEquals(0x60, patched[0x42] & 0xFF);
    }

    @Test
    public void detectsAndPatchesKonami5AndNeutralizesSccWrites() throws Exception, FlowException {
        byte[] rom = new byte[128 * 1024];
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = 0x20;
        rom[3] = 0x40;
        putLdNnA(rom, 0x20, 0x5000);
        putLdNnA(rom, 0x24, 0x7000);
        putLdNnA(rom, 0x28, 0x9000);
        putLdNnA(rom, 0x2C, 0xB000);
        putLdNnA(rom, 0x30, 0x988F); // SCC channel mask register

        assertEquals(MapperType.KONAMI5, MegaRomPatcher.detectMapper(rom));
        byte[] patched = MegaRomPatcher.patchToKonami4(rom, MapperType.AUTO);

        assertEquals(0x40, patched[0x22] & 0xFF);
        assertEquals(0x60, patched[0x26] & 0xFF);
        assertEquals(0x80, patched[0x2A] & 0xFF);
        assertEquals(0xA0, patched[0x2E] & 0xFF);
        assertArrayEquals(new byte[] { 0x00, 0x00, 0x00 }, Arrays.copyOfRange(patched, 0x30, 0x33));
    }

    @Test
    public void detectsAndPatchesAscii16WithTrampolineStub() throws Exception, FlowException {
        byte[] rom = new byte[128 * 1024];
        rom[0] = 'A';
        rom[1] = 'B';
        rom[2] = 0x20;
        rom[3] = 0x40;
        putLdNnA(rom, 0x20, 0x6000);
        putLdNnA(rom, 0x24, 0x7000);
        putLdNnA(rom, 0x28, 0x77FF);

        assertEquals(MapperType.ASCII16, MegaRomPatcher.detectMapper(rom));
        byte[] patched = MegaRomPatcher.patchToKonami4(rom, MapperType.AUTO);

        // Patched calls to 0xF560 and 0xF56B
        assertArrayEquals(new byte[] { (byte) 0xCD, 0x60, (byte) 0xF5 }, Arrays.copyOfRange(patched, 0x20, 0x23));
        assertArrayEquals(new byte[] { (byte) 0xCD, 0x6B, (byte) 0xF5 }, Arrays.copyOfRange(patched, 0x24, 0x27));
        assertArrayEquals(new byte[] { (byte) 0xCD, 0x6B, (byte) 0xF5 }, Arrays.copyOfRange(patched, 0x28, 0x2B));

        // Original INIT overwritten to point at Bank-0 installer stub in 0x4040..0x5FFF
        int stubAddr = (patched[2] & 0xFF) | ((patched[3] & 0xFF) << 8);
        assertEquals(true, stubAddr > 0x4040 && stubAddr < 0x6000);
        int stubOff = stubAddr - 0x4000;
        assertEquals(0x21, patched[stubOff] & 0xFF); // LD HL, payload
        assertEquals((byte) 0xED, patched[stubOff + 9]);
        assertEquals((byte) 0xB0, patched[stubOff + 10]); // LDIR
        assertEquals((byte) 0xC3, patched[stubOff + 11]); // JP origInit (0x4020)
        assertEquals(0x20, patched[stubOff + 12] & 0xFF);
        assertEquals(0x40, patched[stubOff + 13] & 0xFF);
    }

    private static void putLdNnA(byte[] rom, int offset, int addr) {
        rom[offset] = 0x32;
        rom[offset + 1] = (byte) (addr & 0xFF);
        rom[offset + 2] = (byte) (addr >> 8);
    }
}
