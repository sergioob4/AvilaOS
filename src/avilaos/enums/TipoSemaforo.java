package avilaos.enums;

/**
 * Representa los dos tipos de semáforos utilizados en la simulación de ÁvilaOS.
 *
 * <p>Tal como se destaca en las consideraciones del proyecto, estos dos tipos cumplen
 * funciones fundamentales y diferenciadas:</p>
 * <ul>
 *   <li>{@link #BINARIO_MUTEX}: Garantiza la exclusión mutua para la sección crítica al acceder
 *       a la estructura del buffer acotado (toma valores 0 y 1).</li>
 *   <li>{@link #CONTEO}: Coordina la sincronización de recursos finitos, controlando la cantidad
 *       de elementos disponibles en el buffer y los espacios libres restantes (valores enteros ≥ 0).</li>
 * </ul>
 */
public enum TipoSemaforo {

    /**
     * Semáforo binario utilizado como cerrojo de exclusión mutua (Mutex).
     * Solo permite un valor de 0 (ocupado) o 1 (disponible).
     */
    BINARIO_MUTEX("Binario / Mutex", "Garantiza exclusión mutua en sección crítica"),

    /**
     * Semáforo de conteo.
     * Su valor entero representa la cantidad de unidades de un recurso disponible
     * (por ejemplo, espacios libres o elementos producidos).
     */
    CONTEO("Conteo", "Coordina la disponibilidad de recursos discretos finitos");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String nombreLegible;
    private final String descripcion;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    TipoSemaforo(String nombreLegible, String descripcion) {
        this.nombreLegible = nombreLegible;
        this.descripcion = descripcion;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getNombreLegible() {
        return nombreLegible;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
