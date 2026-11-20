package br.com.dod.vcas.converter;

import br.com.dod.vcas.model.MapperType;

/**
 * Options controlling file conversion behavior.
 *
 * @param resetRom Whether to reset the MSX after loading a ROM
 * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
 * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
 * @param turbo Whether to enable the Stage-2 Z80 turbo loader for supported formats (ROM, BIN)
 */
public record ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType, boolean turbo) {

    /**
     * Backward-compatible constructor preserving {@code megaRam = false} and {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     */
    public ConversionOptions(boolean resetRom) {
        this(resetRom, false, MapperType.AUTO, false);
    }

    /**
     * Backward-compatible constructor preserving {@code mapperType = MapperType.AUTO} and {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     */
    public ConversionOptions(boolean resetRom, boolean megaRam) {
        this(resetRom, megaRam, MapperType.AUTO, false);
    }

    /**
     * Backward-compatible constructor preserving {@code turbo = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
     */
    public ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType) {
        this(resetRom, megaRam, mapperType, false);
    }

    /**
     * Default conversion options.
     *
     * @return Default {@link ConversionOptions} with {@code resetRom = false}, {@code megaRam = false}, {@code mapperType = AUTO}, and {@code turbo = false}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false, false, MapperType.AUTO, false);
    }
}
