package br.com.dod.vcas.model;

/**
 * MSX MegaROM mapper types supported by MegaRAM mode.
 */
public enum MapperType {
    AUTO,
    KONAMI4,
    KONAMI5,
    ASCII8,
    ASCII16;

    /**
     * Parse a CLI/API mapper name case-insensitively.
     *
     * @param value Mapper identifier ({@code auto}, {@code konami4}, {@code konami5}, {@code ascii8}, {@code ascii16})
     * @return Matching {@link MapperType}
     * @throws IllegalArgumentException If the value is null or unrecognized
     */
    public static MapperType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mapper type cannot be blank");
        }
        String normalized = value.trim().toUpperCase().replace("-", "").replace("_", "");
        if ("SCC".equals(normalized) || "KONAMISCC".equals(normalized)) {
            return KONAMI5;
        }
        return MapperType.valueOf(normalized);
    }
}
