package br.com.dod.vcas.converter.impl;

import java.util.Arrays;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.MapperType;

/**
 * Detects MSX MegaROM mapper types and statically patches non-Konami4 bank-switch
 * writes ({@link MapperType#ASCII8}, {@link MapperType#ASCII16}, {@link MapperType#KONAMI5})
 * into Konami4-compatible register writes for MegaRAM cartridges (port {@code 0x8E}).
 */
public final class MegaRomPatcher {

    private static final int BANK_0_SIZE = 0x2000;
    private static final int ASCII16_HELPER_ADDR = 0xF560;
    private static final int ASCII16_HELPER_W2_OFFSET = 11;

    private MegaRomPatcher() {
    }

    /**
     * Detect the most likely MegaROM mapper type from static Z80 register-write opcodes.
     *
     * @param rom Raw ROM image bytes
     * @return Detected {@link MapperType} (defaults to {@link MapperType#KONAMI4})
     */
    public static MapperType detectMapper(byte[] rom) {
        int bank0Limit = Math.min(rom.length, BANK_0_SIZE);
        Counts bank0 = countMapperSignatures(rom, 0x0010, bank0Limit);
        if (bank0.hasDecisiveSignal()) {
            return classifyMapper(bank0);
        }
        return classifyMapper(countMapperSignatures(rom, 0x0010, rom.length));
    }

    private static MapperType classifyMapper(Counts c) {
        int k4Score = c.b6000 + c.b8000 + c.bA000;
        int k5Score = c.b5000 + c.b7000 + c.b9000 + c.bB000;
        int k5Upper = c.b9000 + c.bB000;
        int k5Exclusive = c.b5000 + k5Upper;
        int a8Exclusive = c.b6800 + c.b7800;
        int a8Score = c.b6000 + c.b6800 + c.b7000 + c.b7800;
        int k4Upper = c.b8000 + c.bA000;
        int a16Switch = c.b7000 + c.b77FF;

        // Require at least one upper-page register (0x9000/0xB000) so stray 0x5000 data bytes don't mask ASCII16.
        if (k5Upper > 0 && k5Score > k4Score && k5Exclusive >= a8Exclusive) {
            return MapperType.KONAMI5;
        }
        if (a8Exclusive > 0 && a8Score > k4Upper && a8Exclusive > c.b77FF) {
            return MapperType.ASCII8;
        }
        if (a16Switch > 0 && (c.b6000 + a16Switch) >= 2 && a8Exclusive <= c.b77FF && k5Upper == 0
                && (c.b6000 + a16Switch) > k4Upper * 2) {
            return MapperType.ASCII16;
        }
        return MapperType.KONAMI4;
    }

    /**
     * Patch a ROM image to run on Konami4-compatible MegaRAM hardware.
     *
     * @param rom Raw ROM image bytes
     * @param requestedMapper Explicit mapper type or {@link MapperType#AUTO}
     * @return Original array for {@link MapperType#KONAMI4}, or a patched copy
     * @throws FlowException If {@link MapperType#ASCII16} patching cannot find free Bank 0 space for its helper stub
     */
    public static byte[] patchToKonami4(byte[] rom, MapperType requestedMapper) throws FlowException {
        MapperType effective = (requestedMapper == null || requestedMapper == MapperType.AUTO)
                ? detectMapper(rom)
                : requestedMapper;

        return switch (effective) {
            case KONAMI4 -> rom;
            case ASCII8 -> patchAscii8(rom);
            case KONAMI5 -> patchKonami5(rom);
            case ASCII16 -> patchAscii16(rom);
            case AUTO -> rom;
        };
    }

    private static byte[] patchAscii8(byte[] rom) {
        byte[] patched = Arrays.copyOf(rom, rom.length);
        for (int i = 0x0010; i <= patched.length - 3; i++) {
            int opcode = patched[i] & 0xFF;
            if (opcode == 0x32 && (patched[i + 1] & 0xFF) == 0x00) {
                int newHi = mapAscii8HighByte(patched[i + 2] & 0xFF);
                if (newHi >= 0) {
                    patched[i + 2] = (byte) newHi;
                    i += 2;
                }
            } else if (opcode == 0x21 && (patched[i + 1] & 0xFF) == 0x00) {
                int newHi = mapAscii8HighByte(patched[i + 2] & 0xFF);
                if (newHi >= 0 && hasNearbyHlWrite(patched, i + 3)) {
                    patched[i + 2] = (byte) newHi;
                    i += 2;
                }
            }
        }
        return patched;
    }

    private static byte[] patchKonami5(byte[] rom) {
        byte[] patched = Arrays.copyOf(rom, rom.length);
        for (int i = 0x0010; i <= patched.length - 3; i++) {
            int opcode = patched[i] & 0xFF;
            if (opcode == 0x32) {
                int lo = patched[i + 1] & 0xFF;
                int hi = patched[i + 2] & 0xFF;
                if (hi == 0x98 && lo <= 0xAF) {
                    // Neutralize direct SCC register writes (0x9800-0x98FF) so MegaRAM Window 2 isn't corrupted.
                    patched[i] = 0x00;
                    patched[i + 1] = 0x00;
                    patched[i + 2] = 0x00;
                    i += 2;
                } else if (lo == 0x00) {
                    int newHi = mapKonami5HighByte(hi);
                    if (newHi >= 0) {
                        patched[i + 2] = (byte) newHi;
                        i += 2;
                    }
                }
            } else if (opcode == 0x21 && (patched[i + 1] & 0xFF) == 0x00) {
                int newHi = mapKonami5HighByte(patched[i + 2] & 0xFF);
                if (newHi >= 0 && hasNearbyHlWrite(patched, i + 3)) {
                    patched[i + 2] = (byte) newHi;
                    i += 2;
                }
            }
        }
        return patched;
    }

    private static byte[] patchAscii16(byte[] rom) throws FlowException {
        byte[] patched = Arrays.copyOf(rom, rom.length);
        boolean patchedAny = false;

        for (int i = 0x0010; i <= patched.length - 3; i++) {
            int opcode = patched[i] & 0xFF;
            if (opcode == 0x32) {
                int addr = (patched[i + 1] & 0xFF) | ((patched[i + 2] & 0xFF) << 8);
                int target = mapAscii16CallTarget(addr);
                if (target >= 0) {
                    patched[i] = (byte) 0xCD; // CALL target
                    patched[i + 1] = (byte) (target & 0xFF);
                    patched[i + 2] = (byte) (target >> 8);
                    patchedAny = true;
                    i += 2;
                }
            } else if (opcode == 0x21 && i <= patched.length - 4) {
                int addr = (patched[i + 1] & 0xFF) | ((patched[i + 2] & 0xFF) << 8);
                int next = patched[i + 3] & 0xFF;
                int target = mapAscii16CallTarget(addr);
                if (target >= 0 && next == 0x77) {
                    // Replace LD HL, nn; LD (HL), A (4 bytes) with CALL target; NOP
                    patched[i] = (byte) 0xCD;
                    patched[i + 1] = (byte) (target & 0xFF);
                    patched[i + 2] = (byte) (target >> 8);
                    patched[i + 3] = 0x00;
                    patchedAny = true;
                    i += 3;
                }
            }
        }

        if (patchedAny) {
            installAscii16HelperStub(patched);
        }
        return patched;
    }

    private static void installAscii16HelperStub(byte[] rom) throws FlowException {
        byte[] helperPayload = buildAscii16HelperPayload();
        int totalStubSize = 14 + helperPayload.length;
        int stubOffset = findFreeSpaceInBank0(rom, totalStubSize + 4);
        if (stubOffset < 0) {
            throw FlowException.error("mappers_not_supported");
        }
        // Leave 2 guard bytes ahead of the run in case a 0x00 terminator ends preceding code/data.
        stubOffset += 2;

        int stubZ80Addr = 0x4000 + stubOffset;
        int payloadZ80Addr = stubZ80Addr + 14;
        int origInitLo = rom[2] & 0xFF;
        int origInitHi = rom[3] & 0xFF;

        byte[] loaderStub = {
                0x21, (byte) (payloadZ80Addr & 0xFF), (byte) (payloadZ80Addr >> 8), // LD HL, payload
                0x11, (byte) (ASCII16_HELPER_ADDR & 0xFF), (byte) (ASCII16_HELPER_ADDR >> 8), // LD DE, 0xF560
                0x01, (byte) helperPayload.length, 0x00,                            // LD BC, 22
                (byte) 0xED, (byte) 0xB0,                                           // LDIR
                (byte) 0xC3, (byte) origInitLo, (byte) origInitHi                   // JP origInit
        };

        System.arraycopy(loaderStub, 0, rom, stubOffset, loaderStub.length);
        System.arraycopy(helperPayload, 0, rom, stubOffset + loaderStub.length, helperPayload.length);
        rom[2] = (byte) (stubZ80Addr & 0xFF);
        rom[3] = (byte) (stubZ80Addr >> 8);
    }

    private static byte[] buildAscii16HelperPayload() {
        return new byte[] {
                // 0xF560: Window 0 (0x4000..0x7FFF) = 8KB banks 2*A and 2*A+1
                (byte) 0xF5,             // PUSH AF
                (byte) 0x87,             // ADD A, A
                0x32, 0x00, 0x40,        // LD (0x4000), A
                0x3C,                    // INC A
                0x32, 0x00, 0x60,        // LD (0x6000), A
                (byte) 0xF1,             // POP AF
                (byte) 0xC9,             // RET
                // 0xF56B: Window 2 (0x8000..0xBFFF) = 8KB banks 2*A and 2*A+1
                (byte) 0xF5,             // PUSH AF
                (byte) 0x87,             // ADD A, A
                0x32, 0x00, (byte) 0x80, // LD (0x8000), A
                0x3C,                    // INC A
                0x32, 0x00, (byte) 0xA0, // LD (0xA000), A
                (byte) 0xF1,             // POP AF
                (byte) 0xC9              // RET
        };
    }

    private static int findFreeSpaceInBank0(byte[] rom, int requiredLen) {
        int limit = Math.min(rom.length, BANK_0_SIZE);
        for (int filler : new int[] { 0xFF, 0x00 }) {
            int run = 0;
            for (int pos = limit - 1; pos >= 0x40; pos--) {
                if ((rom[pos] & 0xFF) == filler) {
                    run++;
                    if (run >= requiredLen) {
                        return pos;
                    }
                } else {
                    run = 0;
                }
            }
        }
        return -1;
    }

    private static int mapAscii8HighByte(int hi) {
        return switch (hi) {
            case 0x60 -> 0x40;
            case 0x68 -> 0x60;
            case 0x70 -> 0x80;
            case 0x78 -> 0xA0;
            default -> -1;
        };
    }

    private static int mapKonami5HighByte(int hi) {
        return switch (hi) {
            case 0x50 -> 0x40;
            case 0x70 -> 0x60;
            case 0x90 -> 0x80;
            case 0xB0 -> 0xA0;
            default -> -1;
        };
    }

    private static int mapAscii16CallTarget(int addr) {
        if (addr == 0x6000) {
            return ASCII16_HELPER_ADDR;
        }
        if (addr == 0x7000 || addr == 0x77FF) {
            return ASCII16_HELPER_ADDR + ASCII16_HELPER_W2_OFFSET;
        }
        return -1;
    }

    private static boolean hasNearbyHlWrite(byte[] rom, int startIdx) {
        int limit = Math.min(rom.length, startIdx + 8);
        for (int j = startIdx; j < limit; j++) {
            int op = rom[j] & 0xFF;
            if (op == 0x77 || op == 0x36 || (op >= 0x70 && op <= 0x75)) {
                return true;
            }
            // Stop scanning on flow control, stack ops, block transfer, or another 16-bit load into HL.
            if (op == 0x21 || op == 0x11 || op == 0x01 || op == 0xC3 || op == 0xCD || op == 0xC9 || op == 0xED) {
                return false;
            }
        }
        return false;
    }

    private static Counts countMapperSignatures(byte[] rom, int startOffset, int endExclusive) {
        Counts c = new Counts();
        int limit = Math.min(endExclusive, rom.length) - 3;
        for (int i = startOffset; i <= limit; i++) {
            int op = rom[i] & 0xFF;
            if (op == 0x32) {
                int lo = rom[i + 1] & 0xFF;
                int hi = rom[i + 2] & 0xFF;
                c.record(lo, hi);
            } else if (op == 0x21) {
                int lo = rom[i + 1] & 0xFF;
                int hi = rom[i + 2] & 0xFF;
                if ((lo == 0x00 || (lo == 0xFF && hi == 0x77)) && hasNearbyHlWrite(rom, i + 3)) {
                    c.record(lo, hi);
                }
            }
        }
        return c;
    }

    private static final class Counts {
        int b5000;
        int b6000;
        int b6800;
        int b7000;
        int b77FF;
        int b7800;
        int b8000;
        int b9000;
        int bA000;
        int bB000;

        boolean hasDecisiveSignal() {
            int non6000 = b5000 + b6800 + b7000 + b77FF + b7800 + b8000 + b9000 + bA000 + bB000;
            return non6000 > 0 && (b6000 + non6000) >= 2;
        }

        void record(int lo, int hi) {
            if (lo == 0xFF && hi == 0x77) {
                b77FF++;
                return;
            }
            if (lo != 0x00) {
                return;
            }
            switch (hi) {
                case 0x50 -> b5000++;
                case 0x60 -> b6000++;
                case 0x68 -> b6800++;
                case 0x70 -> b7000++;
                case 0x78 -> b7800++;
                case 0x80 -> b8000++;
                case 0x90 -> b9000++;
                case 0xA0 -> bA000++;
                case 0xB0 -> bB000++;
                default -> { }
            }
        }
    }
}
