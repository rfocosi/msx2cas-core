package br.com.dod.vcas.converter;

import br.com.dod.vcas.model.MapperType;

/**
 * Options controlling file conversion behavior.
 *
 * @param resetRom Whether to reset the MSX after loading a ROM
 * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
 * @param mapperType MegaROM mapper type (or {@link MapperType#AUTO}) for MegaRAM conversion
 */
public record ConversionOptions(boolean resetRom, boolean megaRam, MapperType mapperType) {

    /**
     * Backward-compatible constructor preserving {@code megaRam = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     */
    public ConversionOptions(boolean resetRom) {
        this(resetRom, false, MapperType.AUTO);
    }

    /**
     * Backward-compatible constructor preserving {@code mapperType = MapperType.AUTO}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
     */
    public ConversionOptions(boolean resetRom, boolean megaRam) {
        this(resetRom, megaRam, MapperType.AUTO);
    }

    /**
     * Default conversion options.
     *
     * @return Default {@link ConversionOptions} with {@code resetRom = false}, {@code megaRam = false}, and {@code mapperType = AUTO}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false, false, MapperType.AUTO);
    }
}
