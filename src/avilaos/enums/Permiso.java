package avilaos.enums;

/**
 * Permiso de acceso de un archivo o directorio.
 *
 * <p>El valor JSON corresponde al formato del enunciado ({@code "public"} / {@code "private"}).</p>
 */
public enum Permiso {

    PUBLICO("Público", "public"),

    PRIVADO("Privado", "private");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String nombreLegible;
    private final String valorJson;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    Permiso(String nombreLegible, String valorJson) {
        this.nombreLegible = nombreLegible;
        this.valorJson = valorJson;
    }

    // -------------------------------------------------------------------------
    // Getters y conversión
    // -------------------------------------------------------------------------

    public String getNombreLegible() {
        return nombreLegible;
    }

    public String getValorJson() {
        return valorJson;
    }

    /**
     * Convierte el valor usado en los JSON del enunciado en el permiso correspondiente.
     *
     * @param valor texto {@code "public"} o {@code "private"} (sin distinguir mayúsculas)
     * @return permiso correspondiente
     * @throws IllegalArgumentException si el valor no es reconocido
     */
    public static Permiso desdeJson(String valor) {
        for (Permiso permiso : values()) {
            if (permiso.valorJson.equalsIgnoreCase(valor == null ? "" : valor.trim())) {
                return permiso;
            }
        }
        throw new IllegalArgumentException("Permiso desconocido: " + valor);
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
