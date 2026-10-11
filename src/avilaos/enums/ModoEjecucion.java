package avilaos.enums;

/**
 * Representa el modo de ejecución del procesador (CPU) en ÁvilaOS.
 *
 * <p>El procesador conmuta entre dos modos de ejecución según la naturaleza de la instrucción
 * que está procesando en cada ciclo de reloj:</p>
 * <ul>
 *   <li>{@link #USUARIO}: Modo no privilegiado para instrucciones de usuario y cálculos de procesos.</li>
 *   <li>{@link #SISTEMA_OPERATIVO}: Modo privilegiado (kernel) en el que se atienden llamadas al sistema
 *       (syscalls), operaciones de semáforos ({@code semWait}, {@code semSignal}), manipulación segura
 *       de buffers y cambios de contexto.</li>
 * </ul>
 */
public enum ModoEjecucion {

    /**
     * Modo Usuario (User Mode).
     * El procesador ejecuta instrucciones ordinarias del programa del usuario.
     * No tiene privilegios para acceder directamente a las estructuras de control del núcleo.
     */
    USUARIO("Modo Usuario", "User", false),

    /**
     * Modo Sistema Operativo / Kernel (Kernel Mode).
     * El procesador ejecuta código del núcleo de ÁvilaOS con privilegios completos.
     * En este modo se ejecutan las operaciones de {@code semWait}, {@code semSignal},
     * inserción/extracción en buffers y decisiones de planificación.
     */
    SISTEMA_OPERATIVO("Modo Sistema Operativo", "Kernel", true);

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    /** Nombre legible completo para la interfaz gráfica y logs. */
    private final String nombreLegible;

    /** Etiqueta corta o abreviación habitual (User / Kernel). */
    private final String etiquetaCorta;

    /** Indica si el modo cuenta con privilegios de sistema operativo. */
    private final boolean privilegiado;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    ModoEjecucion(String nombreLegible, String etiquetaCorta, boolean privilegiado) {
        this.nombreLegible = nombreLegible;
        this.etiquetaCorta = etiquetaCorta;
        this.privilegiado = privilegiado;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el nombre legible completo del modo.
     *
     * @return nombre legible
     */
    public String getNombreLegible() {
        return nombreLegible;
    }

    /**
     * Devuelve la etiqueta corta del modo (ej. "User", "Kernel").
     *
     * @return etiqueta corta
     */
    public String getEtiquetaCorta() {
        return etiquetaCorta;
    }

    /**
     * Indica si el modo de ejecución es privilegiado (sistema operativo / kernel).
     *
     * @return {@code true} si es modo sistema operativo, {@code false} si es modo usuario
     */
    public boolean esPrivilegiado() {
        return privilegiado;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
