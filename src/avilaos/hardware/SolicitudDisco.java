package avilaos.hardware;

/** Transferencia de una cadena completa, identificada por su primer bloque. */
public record SolicitudDisco(long id, int inicio, DiscoSimulado.Operacion operacion, String ocupante,
                             boolean metadatos, String accion) {
    public SolicitudDisco(long id, int inicio, DiscoSimulado.Operacion operacion, String ocupante, boolean metadatos) {
        this(id, inicio, operacion, ocupante, metadatos, operacion.toString());
    }

    public SolicitudDisco(long id, int inicio, DiscoSimulado.Operacion operacion, String ocupante) {
        this(id, inicio, operacion, ocupante, false);
    }

    boolean usaCadena(int primerBloque) {
        return !metadatos && inicio == primerBloque;
    }
    @Override
    public String toString() {
        return "#" + id + " " + accion + " " + ocupante + " [" + inicio + "]";
    }
}
