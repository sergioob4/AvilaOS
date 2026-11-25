package avilaos.hardware;

import avilaos.enums.PoliticaDisco;

public record ConfiguracionDisco(int bloques, int swap, int cabezal, int velocidad, PoliticaDisco politica) {
    public ConfiguracionDisco(int bloques, int swap, int cabezal, int velocidad) {
        this(bloques, swap, cabezal, velocidad, PoliticaDisco.FIFO);
    }

    public static final ConfiguracionDisco PREDETERMINADA = new ConfiguracionDisco(200, 40, 50, 1);

    public ConfiguracionDisco {
        if (politica == null || bloques < 1 || bloques > 1000 || swap < 0 || swap >= bloques
                || cabezal < 0 || cabezal >= bloques || velocidad < 1 || velocidad > bloques) {
            throw new IllegalArgumentException("El disco admite entre 1 y 1000 bloques. "
                    + "Swap y cabezal deben ser menores que su tamaño y la velocidad debe estar entre 1 y el total.");
        }
    }
}
