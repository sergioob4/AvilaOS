package avilaos.enums;

/**
 * Tipos de operación de E/S soportados por el disco de ÁvilaOS (Proyecto 2).
 *
 * <p>Incluye tanto las operaciones CRUD sobre el sistema de archivos como las
 * operaciones de intercambio (swap) del planificador a mediano plazo.</p>
 */
public enum TipoOperacionES {

    CREAR("Crear"),
    LEER("Leer"),
    ACTUALIZAR("Actualizar"),
    ELIMINAR("Eliminar"),
    SUSPENDER("Suspender (Swap-out)"),
    REACTIVAR("Reactivar (Swap-in)");

    private final String etiqueta;

    TipoOperacionES(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean esOperacionSwap() {
        return this == SUSPENDER || this == REACTIVAR;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
