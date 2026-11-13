package br.com.dod.vcas.converter;

/**
 * Options controlling file conversion behavior.
 *
 * @param resetRom Whether to reset the MSX after loading a ROM
 */
public record ConversionOptions(boolean resetRom) {

    /**
     * Default conversion options.
     *
     * @return Default {@link ConversionOptions} with {@code resetRom = false}
     */
    public static ConversionOptions defaults() {
        return new ConversionOptions(false);
    }
}
