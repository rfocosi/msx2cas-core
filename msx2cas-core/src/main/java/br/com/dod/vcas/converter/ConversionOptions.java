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
 */
public record ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType, TapeSpeed speed) {

    /**
     * Convenience constructor defaulting to {@code megaRam = false}, {@code mapperType = AUTO}, and {@link TapeSpeed#BPS_2400}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     */
    public ConversionOptions(boolean resetRom) {
        this(resetRom, false, MapperType.AUTO, TapeSpeed.BPS_2400);
    }

    /**
     * Convenience constructor defaulting to {@code mapperType = AUTO} and {@link TapeSpeed#BPS_2400}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     */
    public ConversionOptions(boolean resetRom, boolean megaRam) {
        this(resetRom, megaRam, MapperType.AUTO, TapeSpeed.BPS_2400);
    }

    /**
     * Convenience constructor defaulting to {@link TapeSpeed#BPS_2400}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType) {
        this(resetRom, megaRam, mapperType, TapeSpeed.BPS_2400);
    }

    /**
     * Convenience constructor mapping {@code turbo} to {@link TapeSpeed#TURBO} or {@link TapeSpeed#BPS_2400}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     * @param turbo True to select {@link TapeSpeed#TURBO}
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType, boolean turbo) {
        this(resetRom, megaRam, mapperType, turbo ? TapeSpeed.TURBO : TapeSpeed.BPS_2400);
    }

    /**
     * Whether Stage-2 Z80 turbo mode is enabled.
     *
     * @return {@code true} when {@code speed == TapeSpeed.TURBO}
     */
    public boolean isTurbo() {
        return speed == TapeSpeed.TURBO;
    }

    /**
     * Default conversion options ({@link TapeSpeed#BPS_2400}).
     *
     * @return Default {@link ConversionOptions}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false, false, MapperType.AUTO, TapeSpeed.BPS_2400);
    }

    /**
     * Options preserving native T-state timings when reading {@code .tsx} inputs.
     *
     * @return Native TSX {@link ConversionOptions} with {@code speed == null}
     */
    public static ConversionOptions forTsxNative() {
        return new ConversionOptions(false, false, MapperType.AUTO, null);
    }
}
