package avilaos.enums;

/**
 * Identifica y justifica la causa por la cual un proceso se encuentra en estado {@link EstadoProceso#BLOQUEADO}.
 *
 * <p>El requerimiento del sistema exige especificar e identificar claramente el motivo de bloqueo
 * en las colas y en la interfaz gráfica, contemplando la sincronización por semáforos, el acceso
 * distribuido y las operaciones periféricas:</p>
 * <ul>
 *   <li>{@link #SEMAFORO_MUTEX}: Bloqueado en semáforo binario de exclusión mutua de un buffer.</li>
 *   <li>{@link #SEMAFORO_LLENO}: Bloqueado en semáforo de conteo de elementos (consumidor esperando elementos).</li>
 *   <li>{@link #SEMAFORO_VACIO}: Bloqueado en semáforo de conteo de espacios libres (productor esperando espacio).</li>
 *   <li>{@link #LATENCIA_RED}: Bloqueado temporalmente por la latencia de comunicación remota con otro computador.</li>
 *   <li>{@link #ENTRADA_SALIDA}: Bloqueado en espera de completar una ráfaga de E/S (procesos I/O-bound).</li>
 *   <li>{@link #NINGUNO}: El proceso no está bloqueado.</li>
 * </ul>
 */
public enum MotivoBloqueo {

    /** El proceso no está bloqueado. */
    NINGUNO("Ninguno", "El proceso no está bloqueado"),

    /**
     * Esperando que se libere la región crítica del buffer.
     * Corresponde al semáforo binario {@code s} (mutex) del algoritmo productor-consumidor.
     */
    SEMAFORO_MUTEX("Semáforo Mutex", "Esperando exclusión mutua para acceder al buffer"),

    /**
     * Consumidor bloqueado esperando que se produzcan elementos en el buffer.
     * Corresponde al semáforo de conteo {@code n} (elementos almacenados) cuando su valor es 0.
     */
    SEMAFORO_LLENO("Semáforo Elementos", "Buffer vacío: consumidor esperando elementos disponibles"),

    /**
     * Productor bloqueado esperando que haya espacio disponible en el buffer acotado.
     * Corresponde al semáforo de conteo {@code e} (espacios libres) cuando su valor es 0.
     */
    SEMAFORO_VACIO("Semáforo Espacios", "Buffer lleno: productor esperando espacio libre disponible"),

    /**
     * Proceso bloqueado debido al costo de latencia de red al acceder a un buffer remoto
     * ubicado en la memoria de otro computador del clúster.
     */
    LATENCIA_RED("Latencia de Red", "Esperando transmisión de red hacia computador anfitrión remoto"),

    /**
     * Proceso I/O-bound bloqueado esperando la finalización de una operación de E/S.
     */
    ENTRADA_SALIDA("Entrada/Salida", "Esperando finalización de operación de E/S");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String etiqueta;
    private final String justificacion;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    MotivoBloqueo(String etiqueta, String justificacion) {
        this.etiqueta = etiqueta;
        this.justificacion = justificacion;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getJustificacion() {
        return justificacion;
    }

    /**
     * Indica si el motivo de bloqueo está asociado a un semáforo de ÁvilaOS.
     *
     * @return {@code true} si es {@link #SEMAFORO_MUTEX}, {@link #SEMAFORO_LLENO} o {@link #SEMAFORO_VACIO}
     */
    public boolean esPorSemaforo() {
        return this == SEMAFORO_MUTEX || this == SEMAFORO_LLENO || this == SEMAFORO_VACIO;
    }

    /**
     * Indica si el bloqueo se debe a la latencia de red del clúster distribuido.
     *
     * @return {@code true} si es {@link #LATENCIA_RED}
     */
    public boolean esPorRed() {
        return this == LATENCIA_RED;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
