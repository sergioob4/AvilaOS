package avilaos.enums;

/**
 * Tipo de elemento del sistema de archivos jerárquico.
 *
 * <p>El valor JSON corresponde al formato del enunciado ({@code "FILE"} / {@code "DIRECTORY"}).</p>
 */
public enum TipoNodo {

    ARCHIVO("Archivo", "FILE"),

    DIRECTORIO("Directorio", "DIRECTORY");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String nombreLegible;
    private final String valorJson;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    TipoNodo(String nombreLegible, String valorJson) {
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
     * Convierte el valor usado en los JSON del enunciado en el tipo correspondiente.
     *
     * @param valor texto {@code "FILE"} o {@code "DIRECTORY"} (sin distinguir mayúsculas)
     * @return tipo correspondiente
     * @throws IllegalArgumentException si el valor no es reconocido
     */
    public static TipoNodo desdeJson(String valor) {
        for (TipoNodo tipo : values()) {
            if (tipo.valorJson.equalsIgnoreCase(valor == null ? "" : valor.trim())) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de nodo desconocido: " + valor);
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
