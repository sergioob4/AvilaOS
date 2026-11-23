package avilaos.hardware;

/** Transferencia de una cadena completa, identificada por su primer bloque. */
public record SolicitudDisco(long id, int inicio, DiscoSimulado.Operacion operacion, String ocupante) {
    @Override
    public String toString() {
        return "#" + id + " " + operacion + " " + ocupante + " [" + inicio + "]";
    }
}
