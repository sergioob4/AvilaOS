package avilaos.enums;

/**
 * Representa los estados del ciclo de vida de un proceso en ÁvilaOS.
 *
 * <p>El modelo contempla los cinco estados fundamentales de la teoría de
 * Sistemas Operativos (Stallings / Silberschatz), adaptados a las reglas
 * de simulación de ÁvilaOS:</p>
 * <ul>
 *   <li>{@link #NUEVO}: Creado por el usuario o generador, en cola de largo plazo esperando memoria RAM.</li>
 *   <li>{@link #LISTO}: Cargado en memoria RAM, en cola de listos esperando asignación de CPU.</li>
 *   <li>{@link #EJECUCION}: Asignado a la CPU ejecutando instrucciones.</li>
 *   <li>{@link #BLOQUEADO}: En espera de sincronización (semáforo), latencia de red o E/S.</li>
 *   <li>{@link #TERMINADO}: Finalizado por completar instrucciones o expirar su deadline.</li>
 * </ul>
 */
public enum EstadoProceso {

    /**
     * El proceso ha sido creado pero aún reside en la cola de nuevos (planificación
     * a largo plazo) porque la memoria principal (RAM) del computador asignado
     * no tiene espacio suficiente para alojarlo.
     */
    NUEVO("Nuevo", "Esperando espacio disponible en memoria RAM"),

    /**
     * El proceso ya tiene asignada su memoria RAM y se encuentra en la cola de listos
     * (planificación a corto plazo), esperando a que el procesador le sea asignado.
     */
    LISTO("Listo", "Cargado en RAM y listo para ser despachado a la CPU"),

    /**
     * El proceso está ocupando la CPU y ejecutando sus instrucciones.
     * Puede ejecutarse en modo usuario o en modo sistema operativo (kernel).
     */
    EJECUCION("En Ejecución", "Ejecutando instrucciones en la CPU"),

    /**
     * El proceso no puede continuar ejecutándose temporalmente. Se encuentra en la cola
     * de espera de un semáforo (por buffer lleno, buffer vacío o mutex), sufriendo latencia
     * de red en acceso remoto a un buffer, o esperando una operación de E/S.
     */
    BLOQUEADO("Bloqueado", "En espera de semáforo, recurso, red o E/S"),

    /**
     * El proceso ha culminado su ejecución con éxito o ha sido abortado debido a que su
     * deadline llegó a 0. Sus recursos en memoria RAM han sido o están por ser liberados.
     */
    TERMINADO("Terminado", "Proceso finalizado (completado o deadline expirado)");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    /** Nombre descriptivo legible para la interfaz gráfica y reportes. */
    private final String nombreLegible;

    /** Breve descripción del significado del estado. */
    private final String descripcion;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    EstadoProceso(String nombreLegible, String descripcion) {
        this.nombreLegible = nombreLegible;
        this.descripcion = descripcion;
    }

    // -------------------------------------------------------------------------
    // Métodos de consulta y utilidad
    // -------------------------------------------------------------------------

    /**
     * Devuelve el nombre legible del estado.
     *
     * @return nombre legible para la interfaz gráfica
     */
    public String getNombreLegible() {
        return nombreLegible;
    }

    /**
     * Devuelve la descripción detallada del estado.
     *
     * @return descripción del estado
     */
    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Indica si el estado representa una condición terminal del proceso.
     *
     * @return {@code true} si el estado es {@link #TERMINADO}, {@code false} en caso contrario
     */
    public boolean esTerminal() {
        return this == TERMINADO;
    }

    /**
     * Valida si una transición desde el estado actual hacia un nuevo estado es válida
     * según el modelo de transiciones de ÁvilaOS.
     *
     * @param nuevoEstado estado destino
     * @return {@code true} si la transición es permitida; {@code false} de lo contrario
     */
    public boolean esTransicionValida(EstadoProceso nuevoEstado) {
        if (nuevoEstado == null) {
            return false;
        }

        return switch (this) {
            case NUEVO -> nuevoEstado == LISTO || nuevoEstado == TERMINADO;
            case LISTO -> nuevoEstado == EJECUCION || nuevoEstado == TERMINADO;
            case EJECUCION -> nuevoEstado == LISTO || nuevoEstado == BLOQUEADO || nuevoEstado == TERMINADO;
            case BLOQUEADO -> nuevoEstado == LISTO || nuevoEstado == TERMINADO;
            case TERMINADO -> false; // Ninguna transición válida desde terminado
        };
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
