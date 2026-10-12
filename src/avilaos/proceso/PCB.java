package avilaos.proceso;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;

/**
 * Bloque de Control de Proceso (Process Control Block - PCB) de ÁvilaOS.
 *
 * <p>Estructura de datos central que almacena todo el estado, contexto de hardware,
 * parámetros de planificación y métricas de un proceso en el clúster distribuido.</p>
 *
 * <p>Cumple con los elementos mínimos exigidos por el Requerimiento 2:
 * <ul>
 *   <li>ID (generado dinámicamente y único en todo el sistema distribuido).</li>
 *   <li>Nombre del proceso.</li>
 *   <li>Computador en el que se ejecuta.</li>
 *   <li>Estado del proceso ({@link EstadoProceso}).</li>
 *   <li>Tipo de proceso ({@link TipoProceso}).</li>
 *   <li>Prioridad.</li>
 *   <li>Memoria asignada.</li>
 *   <li>Deadline (inicial y tiempo límite restante).</li>
 *   <li>Tiempo restante de ejecución.</li>
 * </ul>
 * E integra el contexto de ejecución monohilo 1:1 (PC, MAR) y datos del productor-consumidor.
 * </p>
 */
public class PCB {

    // -------------------------------------------------------------------------
    // Identificación y ubicación
    // -------------------------------------------------------------------------

    /** Identificador textual único en todo el sistema distribuido (ej. "P1"). */
    private final String id;

    /** Identificador numérico secuencial. */
    private final int idNumero;

    /** Nombre asignado al proceso (ej. "Navegador Web", "Productor Alpha"). */
    private String nombre;

    /** Identificador o nombre del computador asignado (ej. "Computador 1"). */
    private String computadorAsignado;

    // -------------------------------------------------------------------------
    // Estado y Tipo
    // -------------------------------------------------------------------------

    /** Estado actual del ciclo de vida del proceso. */
    private EstadoProceso estado;

    /** Causa o justificación de bloqueo cuando {@code estado == BLOQUEADO}. */
    private MotivoBloqueo motivoBloqueo;

    /** Clasificación del proceso según su carga o rol. */
    private final TipoProceso tipo;

    // -------------------------------------------------------------------------
    // Planificación y Recursos
    // -------------------------------------------------------------------------

    /** Nivel de prioridad asignado (mayor valor numérico = mayor prioridad). */
    private int prioridad;

    /** Cantidad de memoria principal (RAM) requerida/asignada en MB. */
    private int memoriaAsignada;

    /** Tiempo límite (deadline) inicial configurado en ciclos. */
    private int deadlineInicial;

    /** Tiempo restante del deadline en ciclos (al llegar a 0 el proceso termina). */
    private int deadlineRestante;

    /** Total de instrucciones iniciales que debe ejecutar el proceso. */
    private int tiempoEjecucionTotal;

    /** Cantidad de ciclos/instrucciones pendientes para completar la ejecución. */
    private int tiempoRestanteEjecucion;

    // -------------------------------------------------------------------------
    // Contexto de Ejecución de Hardware (Modelo Monohilo 1:1)
    // -------------------------------------------------------------------------

    /** Program Counter (PC): dirección de la siguiente instrucción. */
    private int programCounter;

    /** Memory Address Register (MAR): dirección de memoria referenciada. */
    private int memoryAddressRegister;

    // -------------------------------------------------------------------------
    // Parámetros específicos para Productor / Consumidor
    // -------------------------------------------------------------------------

    /** Identificador del buffer con el que interactúa (si aplica). */
    private String idBuffer;

    /** Intervalo de ciclos requerido para producir o consumir un elemento. */
    private int ciclosPorItem;

    /** Cantidad total de elementos requeridos para completar el proceso. */
    private int itemsRequeridos;

    /** Cantidad de elementos producidos o consumidos exitosamente hasta ahora. */
    private int itemsProcesados;

    /** Contador interno de ciclos acumulados para la producción/consumo actual. */
    private int ciclosAcumuladosItem;

    // -------------------------------------------------------------------------
    // Parámetros específicos para I/O Bound
    // -------------------------------------------------------------------------

    /** Intervalo de ciclos de CPU antes de solicitar una operación de E/S. */
    private int ciclosEntreIO;

    /** Duración en ciclos del bloqueo por operación de Entrada/Salida. */
    private int duracionBloqueoIO;

    /** Contador de ciclos transcurridos en el bloqueo de E/S actual. */
    private int ciclosBloqueadoIOActual;

    // -------------------------------------------------------------------------
    // Parámetros para latencia de red en acceso remoto a buffers
    // -------------------------------------------------------------------------

    /** Ciclos de latencia pendientes por completar en acceso a buffer remoto. */
    private int ciclosLatenciaRedRestante;

    // -------------------------------------------------------------------------
    // Métricas para Evaluación de Eficiencia
    // -------------------------------------------------------------------------

    /** Ciclo de reloj global en el que se creó el proceso. */
    private long cicloCreacion;

    /** Ciclo de reloj en el que ingresó por primera vez a la cola de LISTOS. */
    private long cicloPrimerListo;

    /** Ciclo de reloj en el que entró por primera vez a la CPU (tiempo de respuesta). */
    private long cicloPrimeraEjecucion;

    /** Ciclo de reloj global en el que el proceso finalizó. */
    private long cicloTerminacion;

    /** Total de ciclos que el proceso ha estado en la CPU ejecutando. */
    private long tiempoEnCpu;

    /** Total de ciclos que el proceso ha esperado en la cola de LISTOS. */
    private long tiempoEnEspera;

    /** Total de ciclos que el proceso ha pasado en estado BLOQUEADO. */
    private long tiempoBloqueadoTotal;

    /** Total de ciclos bloqueado específicamente esperando semáforos. */
    private long tiempoBloqueadoSemaforos;

    /** Indica si el proceso completó todas sus instrucciones dentro de su deadline. */
    private boolean cumplioDeadline;

    /** Indica si el proceso fue terminado prematuramente por vencimiento de su deadline. */
    private boolean terminoPorDeadline;

    // -------------------------------------------------------------------------
    // Constructores
    // -------------------------------------------------------------------------

    /**
     * Crea un nuevo PCB inicializando todos sus campos mínimos requeridos.
     *
     * @param id identificador textual único
     * @param idNumero identificador numérico secuencial
     * @param nombre nombre del proceso
     * @param computadorAsignado computador asignado
     * @param tipo tipo de proceso (CPU_BOUND, IO_BOUND, PRODUCTOR, CONSUMIDOR)
     * @param prioridad nivel de prioridad
     * @param memoriaAsignada memoria RAM requerida (MB)
     * @param deadline tiempo límite en ciclos
     * @param tiempoEjecucion instrucciones o ciclos totales a ejecutar
     */
    public PCB(String id, int idNumero, String nombre, String computadorAsignado,
               TipoProceso tipo, int prioridad, int memoriaAsignada,
               int deadline, int tiempoEjecucion) {
        this.id = id;
        this.idNumero = idNumero;
        this.nombre = nombre;
        this.computadorAsignado = computadorAsignado;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoriaAsignada = memoriaAsignada;
        this.deadlineInicial = deadline;
        this.deadlineRestante = deadline;
        this.tiempoEjecucionTotal = tiempoEjecucion;
        this.tiempoRestanteEjecucion = tiempoEjecucion;

        this.estado = EstadoProceso.NUEVO;
        this.motivoBloqueo = MotivoBloqueo.NINGUNO;

        this.programCounter = 0;
        this.memoryAddressRegister = 0;

        this.cicloPrimeraEjecucion = -1;
        this.cicloPrimerListo = -1;
        this.cicloTerminacion = -1;
        this.cumplioDeadline = false;
        this.terminoPorDeadline = false;
    }

    // -------------------------------------------------------------------------
    // Getters y Setters
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public int getIdNumero() {
        return idNumero;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getComputadorAsignado() {
        return computadorAsignado;
    }

    public void setComputadorAsignado(String computadorAsignado) {
        this.computadorAsignado = computadorAsignado;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public MotivoBloqueo getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public void setMotivoBloqueo(MotivoBloqueo motivoBloqueo) {
        this.motivoBloqueo = motivoBloqueo;
    }

    public TipoProceso getTipo() {
        return tipo;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public int getMemoriaAsignada() {
        return memoriaAsignada;
    }

    public void setMemoriaAsignada(int memoriaAsignada) {
        this.memoriaAsignada = memoriaAsignada;
    }

    public int getDeadlineInicial() {
        return deadlineInicial;
    }

    public void setDeadlineInicial(int deadlineInicial) {
        this.deadlineInicial = deadlineInicial;
    }

    public int getDeadlineRestante() {
        return deadlineRestante;
    }

    public void setDeadlineRestante(int deadlineRestante) {
        this.deadlineRestante = deadlineRestante;
    }

    public int getTiempoEjecucionTotal() {
        return tiempoEjecucionTotal;
    }

    public void setTiempoEjecucionTotal(int tiempoEjecucionTotal) {
        this.tiempoEjecucionTotal = tiempoEjecucionTotal;
    }

    public int getTiempoRestanteEjecucion() {
        return tiempoRestanteEjecucion;
    }

    public void setTiempoRestanteEjecucion(int tiempoRestanteEjecucion) {
        this.tiempoRestanteEjecucion = tiempoRestanteEjecucion;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    public int getMemoryAddressRegister() {
        return memoryAddressRegister;
    }

    public void setMemoryAddressRegister(int memoryAddressRegister) {
        this.memoryAddressRegister = memoryAddressRegister;
    }

    public String getIdBuffer() {
        return idBuffer;
    }

    public void setIdBuffer(String idBuffer) {
        this.idBuffer = idBuffer;
    }

    public int getCiclosPorItem() {
        return ciclosPorItem;
    }

    public void setCiclosPorItem(int ciclosPorItem) {
        this.ciclosPorItem = ciclosPorItem;
    }

    public int getItemsRequeridos() {
        return itemsRequeridos;
    }

    public void setItemsRequeridos(int itemsRequeridos) {
        this.itemsRequeridos = itemsRequeridos;
    }

    public int getItemsProcesados() {
        return itemsProcesados;
    }

    public void setItemsProcesados(int itemsProcesados) {
        this.itemsProcesados = itemsProcesados;
    }

    public int getCiclosAcumuladosItem() {
        return ciclosAcumuladosItem;
    }

    public void setCiclosAcumuladosItem(int ciclosAcumuladosItem) {
        this.ciclosAcumuladosItem = ciclosAcumuladosItem;
    }

    public int getCiclosEntreIO() {
        return ciclosEntreIO;
    }

    public void setCiclosEntreIO(int ciclosEntreIO) {
        this.ciclosEntreIO = ciclosEntreIO;
    }

    public int getDuracionBloqueoIO() {
        return duracionBloqueoIO;
    }

    public void setDuracionBloqueoIO(int duracionBloqueoIO) {
        this.duracionBloqueoIO = duracionBloqueoIO;
    }

    public int getCiclosBloqueadoIOActual() {
        return ciclosBloqueadoIOActual;
    }

    public void setCiclosBloqueadoIOActual(int ciclosBloqueadoIOActual) {
        this.ciclosBloqueadoIOActual = ciclosBloqueadoIOActual;
    }

    public int getCiclosLatenciaRedRestante() {
        return ciclosLatenciaRedRestante;
    }

    public void setCiclosLatenciaRedRestante(int ciclosLatenciaRedRestante) {
        this.ciclosLatenciaRedRestante = ciclosLatenciaRedRestante;
    }

    public long getCicloCreacion() {
        return cicloCreacion;
    }

    public void setCicloCreacion(long cicloCreacion) {
        this.cicloCreacion = cicloCreacion;
    }

    public long getCicloPrimerListo() {
        return cicloPrimerListo;
    }

    public void setCicloPrimerListo(long cicloPrimerListo) {
        this.cicloPrimerListo = cicloPrimerListo;
    }

    public long getCicloPrimeraEjecucion() {
        return cicloPrimeraEjecucion;
    }

    public void setCicloPrimeraEjecucion(long cicloPrimeraEjecucion) {
        this.cicloPrimeraEjecucion = cicloPrimeraEjecucion;
    }

    public long getCicloTerminacion() {
        return cicloTerminacion;
    }

    public void setCicloTerminacion(long cicloTerminacion) {
        this.cicloTerminacion = cicloTerminacion;
    }

    public long getTiempoEnCpu() {
        return tiempoEnCpu;
    }

    public void incrementarTiempoEnCpu() {
        this.tiempoEnCpu++;
    }

    public long getTiempoEnEspera() {
        return tiempoEnEspera;
    }

    public void incrementarTiempoEnEspera() {
        this.tiempoEnEspera++;
    }

    public long getTiempoBloqueadoTotal() {
        return tiempoBloqueadoTotal;
    }

    public void incrementarTiempoBloqueadoTotal() {
        this.tiempoBloqueadoTotal++;
    }

    public long getTiempoBloqueadoSemaforos() {
        return tiempoBloqueadoSemaforos;
    }

    public void incrementarTiempoBloqueadoSemaforos() {
        this.tiempoBloqueadoSemaforos++;
    }

    public boolean isCumplioDeadline() {
        return cumplioDeadline;
    }

    public void setCumplioDeadline(boolean cumplioDeadline) {
        this.cumplioDeadline = cumplioDeadline;
    }

    public boolean isTerminoPorDeadline() {
        return terminoPorDeadline;
    }

    public void setTerminoPorDeadline(boolean terminoPorDeadline) {
        this.terminoPorDeadline = terminoPorDeadline;
    }

    @Override
    public String toString() {
        return "PCB[" + id + " | " + nombre + " | " + estado +
                (estado == EstadoProceso.BLOQUEADO ? " (" + motivoBloqueo + ")" : "") +
                " | Host:" + computadorAsignado +
                " | DL:" + deadlineRestante + "/" + deadlineInicial +
                " | Rest:" + tiempoRestanteEjecucion + "/" + tiempoEjecucionTotal +
                " | RAM:" + memoriaAsignada + "MB]";
    }
}
