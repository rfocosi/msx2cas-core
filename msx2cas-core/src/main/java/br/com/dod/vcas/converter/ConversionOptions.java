package br.com.dod.vcas.converter;

import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.TapeSpeed;

/**
 * Options controlling file conversion behavior.
 *
 * @param resetRom Whether to reset the MSX after loading a ROM
 * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
 * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
 * @param speed Target {@link TapeSpeed} ({@code null} preserves native T-state timing on TSX inputs and defaults to {@link TapeSpeed#BPS_2400} for other formats)
 * @param turbo Whether to enable Stage-2 Z80 PWM turbo mode for ROM and BIN files
 */
public record ConversionOptions(
        boolean resetRom,
        boolean megaRam,
        MapperType mapperType,
        TapeSpeed speed,
        boolean turbo) {

    /**
     * Convenience constructor defaulting to {@code megaRam = false}, {@code mapperType = AUTO},
     * {@link TapeSpeed#BPS_2400}, and {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     */
    public ConversionOptions(boolean resetRom) {
        this(resetRom, false, MapperType.AUTO, TapeSpeed.BPS_2400, false);
    }

    /**
     * Convenience constructor defaulting to {@code mapperType = AUTO}, {@link TapeSpeed#BPS_2400},
     * and {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     */
    public ConversionOptions(boolean resetRom, boolean megaRam) {
        this(resetRom, megaRam, MapperType.AUTO, TapeSpeed.BPS_2400, false);
    }

    /**
     * Convenience constructor defaulting to {@link TapeSpeed#BPS_2400} and {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType) {
        this(resetRom, megaRam, mapperType, TapeSpeed.BPS_2400, false);
    }

    /**
     * Convenience constructor defaulting to {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     * @param speed Target {@link TapeSpeed}
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType, TapeSpeed speed) {
        this(resetRom, megaRam, mapperType, speed, false);
    }

    /**
     * Convenience constructor using {@link TapeSpeed#BPS_2400} Stage-1 timing with optional turbo mode.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     * @param turbo True to enable Stage-2 Z80 PWM turbo mode
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType, boolean turbo) {
        this(resetRom, megaRam, mapperType, TapeSpeed.BPS_2400, turbo);
    }

    /**
     * Whether Stage-2 Z80 turbo mode is enabled.
     *
     * @return {@code true} when turbo mode is enabled
     */
    public boolean isTurbo() {
        return turbo;
    }

    /**
     * Default conversion options ({@link TapeSpeed#BPS_2400}, {@code turbo = false}).
     *
     * @return Default {@link ConversionOptions}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false, false, MapperType.AUTO, TapeSpeed.BPS_2400, false);
    }

    /**
     * Options preserving native T-state timings when reading {@code .tsx} inputs.
     *
     * @return Native TSX {@link ConversionOptions} with {@code speed == null} and {@code turbo = false}
     */
    public static ConversionOptions forTsxNative() {
        return new ConversionOptions(false, false, MapperType.AUTO, null, false);
    }
}
