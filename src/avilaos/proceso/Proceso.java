package avilaos.proceso;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;

/**
 * Representa un proceso monohilo en ÁvilaOS.
 *
 * <p>En ÁvilaOS, cada proceso se asocia 1:1 con su PCB y su hilo de ejecución único (TCB).
 * Gestiona de forma directa su avance lineal en la CPU (incremento de PC y MAR por ciclo),
 * sus transiciones de estado, y la supervisión estricta de su tiempo límite (deadline):
 * <em>"Al llegar a 0 el deadline de un proceso, este tiene que ser terminado."</em>
 * </p>
 */
public class Proceso {

    /** Bloque de control de proceso (asociación 1:1). */
    private final PCB pcb;

    // -------------------------------------------------------------------------
    // Constructores
    // -------------------------------------------------------------------------

    /**
     * Construye un proceso nuevo con su PCB asociado.
     *
     * @param pcb bloque de control de proceso
     */
    public Proceso(PCB pcb) {
        if (pcb == null) {
            throw new IllegalArgumentException("El PCB no puede ser nulo.");
        }
        this.pcb = pcb;
    }

    /**
     * Constructor de conveniencia que instancia automáticamente el PCB y genera el ID único.
     *
     * @param nombre nombre descriptivo
     * @param computador computador anfitrión asignado
     * @param tipo tipo de proceso
     * @param prioridad nivel de prioridad
     * @param memoria MB requeridos de RAM
     * @param deadline límite en ciclos
     * @param tiempoEjecucion instrucciones totales a ejecutar
     */
    public Proceso(String nombre, String computador, TipoProceso tipo,
                   int prioridad, int memoria, int deadline, int tiempoEjecucion) {
        int idNumero = GeneradorIdProceso.siguienteIdNumero();
        String idTexto = "P" + idNumero;
        this.pcb = new PCB(idTexto, idNumero, nombre, computador, tipo, prioridad, memoria, deadline, tiempoEjecucion);
    }

    // -------------------------------------------------------------------------
    // Ciclo de Ejecución en CPU
    // -------------------------------------------------------------------------

    /**
     * Ejecuta exactamente un ciclo de instrucción en la CPU.
     *
     * <p>Reglas del PDF:
     * <ul>
     *   <li>Todas las instrucciones se ejecutan en un único ciclo de instrucción.</li>
     *   <li>El PC y el MAR se incrementan una unidad por cada ciclo del reloj.</li>
     *   <li>Se decrementa el tiempo restante de ejecución.</li>
     * </ul>
     * </p>
     *
     * @param cicloGlobal ciclo de reloj global de la simulación
     * @return {@code true} si el proceso finalizó todas sus instrucciones en este ciclo; {@code false} de lo contrario
     */
    public boolean ejecutarInstruccion(long cicloGlobal) {
        if (pcb.getEstado() != EstadoProceso.EJECUCION) {
            return false;
        }

        if (pcb.getCicloPrimeraEjecucion() == -1) {
            pcb.setCicloPrimeraEjecucion(cicloGlobal);
        }

        // Incremento lineal de registros de hardware (PC y MAR)
        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        pcb.setMemoryAddressRegister(pcb.getMemoryAddressRegister() + 1);

        pcb.incrementarTiempoEnCpu();

        int restante = pcb.getTiempoRestanteEjecucion() - 1;
        pcb.setTiempoRestanteEjecucion(Math.max(0, restante));

        // Si completó todas las instrucciones
        if (pcb.getTiempoRestanteEjecucion() == 0) {
            terminarExitoso(cicloGlobal);
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Control de Deadline (Requerimiento Crítico)
    // -------------------------------------------------------------------------

    /**
     * Decrementa el deadline del proceso en un ciclo de reloj.
     *
     * <p>Si el deadline restante llega a 0 y el proceso no ha terminado,
     * el proceso es terminado de forma inmediata:
     * <em>"Al llegar a 0 el deadline de un proceso, este tiene que ser terminado."</em>
     * </p>
     *
     * @param cicloGlobal ciclo de reloj actual
     * @return {@code true} si el proceso fue terminado forzosamente por vencimiento de deadline; {@code false} en caso contrario
     */
    public boolean decrementarDeadline(long cicloGlobal) {
        if (pcb.getEstado() == EstadoProceso.TERMINADO) {
            return false;
        }

        int nuevoDeadline = pcb.getDeadlineRestante() - 1;
        pcb.setDeadlineRestante(nuevoDeadline);

        if (nuevoDeadline <= 0) {
            terminarPorDeadline(cicloGlobal);
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Transiciones de Estado
    // -------------------------------------------------------------------------

    /**
     * Transiciona el proceso a estado {@link EstadoProceso#LISTO}.
     *
     * @param cicloGlobal ciclo actual
     */
    public void pasarAListo(long cicloGlobal) {
        if (pcb.getEstado().esTransicionValida(EstadoProceso.LISTO)) {
            pcb.setEstado(EstadoProceso.LISTO);
            pcb.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            if (pcb.getCicloPrimerListo() == -1) {
                pcb.setCicloPrimerListo(cicloGlobal);
            }
        }
    }

    /**
     * Transiciona el proceso a estado {@link EstadoProceso#EJECUCION}.
     *
     * @param cicloGlobal ciclo actual
     */
    public void pasarAEjecucion(long cicloGlobal) {
        if (pcb.getEstado().esTransicionValida(EstadoProceso.EJECUCION)) {
            pcb.setEstado(EstadoProceso.EJECUCION);
            pcb.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            if (pcb.getCicloPrimeraEjecucion() == -1) {
                pcb.setCicloPrimeraEjecucion(cicloGlobal);
            }
        }
    }

    /**
     * Bloquea el proceso especificando y justificando el motivo.
     *
     * @param motivo motivo por el cual se bloquea el proceso
     */
    public void bloquear(MotivoBloqueo motivo) {
        if (pcb.getEstado().esTransicionValida(EstadoProceso.BLOQUEADO)) {
            pcb.setEstado(EstadoProceso.BLOQUEADO);
            pcb.setMotivoBloqueo(motivo != null ? motivo : MotivoBloqueo.NINGUNO);
        }
    }

    /**
     * Termina el proceso exitosamente al haber completado todas sus instrucciones.
     *
     * @param cicloGlobal ciclo actual
     */
    public void terminarExitoso(long cicloGlobal) {
        pcb.setEstado(EstadoProceso.TERMINADO);
        pcb.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        pcb.setCicloTerminacion(cicloGlobal);
        pcb.setCumplioDeadline(true);
        pcb.setTerminoPorDeadline(false);
    }

    /**
     * Termina el proceso forzosamente debido a que su deadline llegó a 0.
     *
     * @param cicloGlobal ciclo actual
     */
    public void terminarPorDeadline(long cicloGlobal) {
        pcb.setEstado(EstadoProceso.TERMINADO);
        pcb.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        pcb.setCicloTerminacion(cicloGlobal);
        pcb.setCumplioDeadline(false);
        pcb.setTerminoPorDeadline(true);
    }

    // -------------------------------------------------------------------------
    // Getters y Utilidades
    // -------------------------------------------------------------------------

    public PCB getPcb() {
        return pcb;
    }

    public String getId() {
        return pcb.getId();
    }

    public String getNombre() {
        return pcb.getNombre();
    }

    public EstadoProceso getEstado() {
        return pcb.getEstado();
    }

    public MotivoBloqueo getMotivoBloqueo() {
        return pcb.getMotivoBloqueo();
    }

    public TipoProceso getTipo() {
        return pcb.getTipo();
    }

    public int getPrioridad() {
        return pcb.getPrioridad();
    }

    public int getDeadlineRestante() {
        return pcb.getDeadlineRestante();
    }

    public int getTiempoRestanteEjecucion() {
        return pcb.getTiempoRestanteEjecucion();
    }

    public boolean estaTerminado() {
        return pcb.getEstado() == EstadoProceso.TERMINADO;
    }

    @Override
    public String toString() {
        return pcb.toString();
    }
}
