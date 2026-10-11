package avilaos.enums;

/**
 * Representa los tipos de procesos admitidos por ÁvilaOS según sus requerimientos de ejecución
 * y comportamiento con respecto a los recursos del clúster.
 *
 * <ul>
 *   <li>{@link #CPU_BOUND}: Intensivo en procesador; ejecuta ráfagas continuas de cálculo.</li>
 *   <li>{@link #IO_BOUND}: Intensivo en operaciones de entrada/salida; requiere ciclos de espera por dispositivos.</li>
 *   <li>{@link #PRODUCTOR}: Genera elementos periódicamente y los deposita en un buffer acotado compartido con semáforos.</li>
 *   <li>{@link #CONSUMIDOR}: Retira elementos periódicamente de un buffer acotado compartido con semáforos.</li>
 * </ul>
 */
public enum TipoProceso {

    /**
     * Proceso dependiente principalmente de la CPU (CPU-bound).
     * Requiere mayor cantidad de ciclos de procesador para completar sus instrucciones lineales.
     */
    CPU_BOUND("CPU-bound", "Intensivo en procesamiento numérico/lógico"),

    /**
     * Proceso dependiente principalmente de Entrada/Salida (I/O-bound).
     * Alterna ráfagas breves de CPU con operaciones que lo bloquean esperando ciclos de E/S.
     */
    IO_BOUND("I/O-bound", "Intensivo en operaciones de Entrada/Salida"),

    /**
     * Proceso Productor.
     * Sincronizado mediante semáforos para insertar datos en un buffer de capacidad limitada
     * (residente en memoria local o remota a través de la red).
     */
    PRODUCTOR("Productor", "Genera elementos y los deposita en un buffer compartido"),

    /**
     * Proceso Consumidor.
     * Sincronizado mediante semáforos para extraer datos de un buffer de capacidad limitada
     * (residente en memoria local o remota a través de la red).
     */
    CONSUMIDOR("Consumidor", "Extrae y consume elementos desde un buffer compartido");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String nombreLegible;
    private final String descripcion;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    TipoProceso(String nombreLegible, String descripcion) {
        this.nombreLegible = nombreLegible;
        this.descripcion = descripcion;
    }

    // -------------------------------------------------------------------------
    // Getters y Métodos de utilidad
    // -------------------------------------------------------------------------

    public String getNombreLegible() {
        return nombreLegible;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Determina si el tipo de proceso interactúa con un buffer de comunicación interproceso.
     *
     * @return {@code true} si es {@link #PRODUCTOR} o {@link #CONSUMIDOR}; {@code false} en caso contrario
     */
    public boolean interactuaConBuffer() {
        return this == PRODUCTOR || this == CONSUMIDOR;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
