package br.com.dod.vcas.converter;

/**
 * Options controlling file conversion behavior.
 *
 * @param resetRom Whether to reset the MSX after loading a ROM
 * @param megaRam Whether to target a MegaRAM cartridge (port 0x8E) for ROMs
 */
public record ConversionOptions(boolean resetRom, boolean megaRam) {

    /**
     * Backward-compatible constructor preserving {@code megaRam = false}.
     *
     * @param resetRom Whether to reset the MSX after loading a ROM
     */
    public ConversionOptions(boolean resetRom) {
        this(resetRom, false);
    }

    /**
     * Default conversion options.
     *
     * @return Default {@link ConversionOptions} with {@code resetRom = false} and {@code megaRam = false}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false, false);
    }
}
