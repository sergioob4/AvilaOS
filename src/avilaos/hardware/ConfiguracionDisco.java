package avilaos.hardware;

public record ConfiguracionDisco(int bloques, int swap, int cabezal, int velocidad) {
    public static final ConfiguracionDisco PREDETERMINADA = new ConfiguracionDisco(200, 40, 50, 1);

    public ConfiguracionDisco {
        if (bloques < 1 || bloques > 500 || swap < 0 || swap >= bloques
                || cabezal < 0 || cabezal >= bloques || velocidad < 1 || velocidad > bloques) {
            throw new IllegalArgumentException("El disco admite entre 1 y 500 bloques. "
                    + "Swap y cabezal deben ser menores que su tamaño y la velocidad debe estar entre 1 y el total.");
        }
    }
}
