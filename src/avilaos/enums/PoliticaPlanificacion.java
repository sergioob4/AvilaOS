package avilaos.enums;


public enum PoliticaPlanificacion {

    /**
     * First-Come, First-Served (Primero en llegar, primero en ser servido).
     * El proceso que primero ingresa a la cola de listos es seleccionado y no
     * se desaloja hasta que termina o se bloquea voluntariamente.
     */
    FCFS("FCFS", "First-Come, First-Served", false, false),

    /**
     * Round Robin (Turno rotatorio).
     * Asigna a cada proceso en listo una porción finita de tiempo de CPU llamada quantum.
     * Al agotarse el quantum, el proceso es desalojado y vuelve al final de la cola de listos.
     */
    ROUND_ROBIN("Round Robin", "Turno rotativo con quantum configurable", true, true),

    /**
     * Prioridades Apropiativas (Preemptive Priority Scheduling).
     * Selecciona el proceso con mayor prioridad en la cola de listos. Si arriba un proceso
     * con mayor prioridad que el que se encuentra en ejecución, este último es desalojado.
     */
    PRIORIDADES_APROPIATIVAS("Prioridades Apropiativas", "Prioridad fija con desalojo preventivo", true, false),

    /**
     * Earliest Deadline First (EDF).
     * Política de tiempo real donde el proceso con el tiempo límite (deadline) más próximo
     * a cero obtiene la mayor prioridad de ejecución. Puede desalojar al proceso en CPU
     * si un proceso recién llegado tiene un deadline menor.
     */
    EDF("EDF", "Earliest Deadline First (Mayor urgencia por deadline)", true, false);

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    /** Nombre corto o acrónimo para identificación en interfaz y configuración. */
    private final String codigo;

    /** Nombre descriptivo de la política. */
    private final String descripcion;

    /** Indica si la política permite el desalojo preventivo (apropiación) del procesador. */
    private final boolean apropiativa;

    /** Indica si esta política depende de un valor de quantum para su funcionamiento. */
    private final boolean requiereQuantum;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    PoliticaPlanificacion(String codigo, String descripcion, boolean apropiativa, boolean requiereQuantum) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.apropiativa = apropiativa;
        this.requiereQuantum = requiereQuantum;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el código o nombre corto de la política.
     *
     * @return código de la política
     */
    public String getCodigo() {
        return codigo;
    }

    /**
     * Devuelve la descripción de la política.
     *
     * @return descripción de la política
     */
    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Indica si la política es apropiativa (preemptive), es decir, si puede expulsar
     * al proceso de la CPU antes de que termine o se bloquee.
     *
     * @return {@code true} si es apropiativa, {@code false} si es no apropiativa
     */
    public boolean esApropiativa() {
        return apropiativa;
    }

    /**
     * Indica si la política requiere la configuración de un quantum de tiempo.
     *
     * @return {@code true} si requiere quantum (ej. Round Robin), {@code false} de lo contrario
     */
    public boolean requiereQuantum() {
        return requiereQuantum;
    }

    @Override
    public String toString() {
        return codigo;
    }
}
