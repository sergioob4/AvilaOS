package avilaos.sistema;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.CPU;
import avilaos.hardware.MemoriaPrincipal;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.planificacion.FabricaPoliticas;
import avilaos.planificacion.PoliticaFCFS;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.GestorSincronizacion;

/**
 * Núcleo del Sistema Operativo de un computador en ÁvilaOS.
 *
 * <p>Responsable de:
 * <ul>
 *   <li>Gestión de colas de procesos: Nuevos, Listos, Bloqueados y Terminados
 *       (implementadas con {@link ListaEnlazada} propia con semáforos, sin colecciones de Java).</li>
 *   <li>Planificación a largo plazo: admisión de procesos nuevos basada en la disponibilidad de RAM.</li>
 *   <li>Planificación a corto plazo: despacho de procesos hacia la CPU mediante {@link IPoliticaPlanificacion}.</li>
 *   <li>Supervisión estricta de Deadlines: cuando el deadline llega a 0, el proceso pasa a TERMINADO
 *       y se libera inmediatamente su memoria, revisando admisión en la cola de nuevos.</li>
 *   <li>Conmutación de políticas de planificación y quantum en tiempo de ejecución.</li>
 * </ul>
 * </p>
 */
public class SistemaOperativo {

    /** Nombre o identificador del computador al que pertenece este núcleo. */
    private final String idComputador;

    /** Referencia a la CPU del computador. */
    private final CPU cpu;

    /** Referencia a la memoria principal (RAM) del computador. */
    private final MemoriaPrincipal memoria;

    /** Política de planificación activa. */
    private IPoliticaPlanificacion politica;

    /** Quantum configurado para políticas rotativas como Round Robin. */
    private int quantum;

    /** Gestor de sincronización global para buffers y semáforos distribuidos. */
    private GestorSincronizacion gestorSincronizacion;

    // -------------------------------------------------------------------------
    // Colas del Sistema Operativo (Estructuras propias de ÁvilaOS)
    // -------------------------------------------------------------------------

    /** Procesos en espera de asignación de memoria RAM (largo plazo). */
    private final ListaEnlazada<Proceso> colaNuevos;

    /** Procesos cargados en RAM listos para ser atendidos por la CPU (corto plazo). */
    private final ListaEnlazada<Proceso> colaListos;

    /** Procesos en espera de semáforos, red o E/S. */
    private final ListaEnlazada<Proceso> colaBloqueados;

    /** Procesos que han concluido o han sido terminados por vencimiento de deadline. */
    private final ListaEnlazada<Proceso> colaTerminados;

    // -------------------------------------------------------------------------
    // Métricas del Núcleo
    // -------------------------------------------------------------------------

    /** Cantidad total de procesos completados con éxito dentro de su deadline. */
    private int procesosCompletadosExitosos;

    /** Cantidad total de procesos abortados por vencimiento de deadline. */
    private int procesosTerminadosPorDeadline;

    /**
     * Construye el núcleo del sistema operativo para un computador.
     *
     * @param idComputador identificador del computador anfitrión
     * @param cpu unidad de procesamiento
     * @param memoria memoria principal RAM
     */
    public SistemaOperativo(String idComputador, CPU cpu, MemoriaPrincipal memoria) {
        this.idComputador = idComputador;
        this.cpu = cpu;
        this.memoria = memoria;
        this.politica = new PoliticaFCFS(); // Política por defecto
        this.quantum = 3;

        this.colaNuevos = new ListaEnlazada<>();
        this.colaListos = new ListaEnlazada<>();
        this.colaBloqueados = new ListaEnlazada<>();
        this.colaTerminados = new ListaEnlazada<>();

        this.procesosCompletadosExitosos = 0;
        this.procesosTerminadosPorDeadline = 0;
    }

    // -------------------------------------------------------------------------
    // Ciclo de Operación del Núcleo
    // -------------------------------------------------------------------------

    /**
     * Ejecuta las rutinas del sistema operativo en cada ciclo de reloj.
     *
     * @param cicloActual ciclo global de la simulación
     */
    public synchronized void ejecutarCicloSO(long cicloActual) {
        // 0. Si hay gestor de sincronización, actualizar procesos bloqueados por tiempo (latencia de red y E/S)
        if (gestorSincronizacion != null) {
            gestorSincronizacion.actualizarBloqueadosPorTiempo(this, cicloActual);
        }

        // 1. Supervisar deadlines de todos los procesos activos en el computador
        supervisarDeadlines(cicloActual);

        // 2. Si el proceso en CPU es productor o consumidor, coordinar sincronización con su buffer
        Proceso procesoCpu = cpu.getProcesoActual();
        if (procesoCpu != null && gestorSincronizacion != null
                && (procesoCpu.getPcb().getTipo() == TipoProceso.PRODUCTOR
                || procesoCpu.getPcb().getTipo() == TipoProceso.CONSUMIDOR)) {
            gestorSincronizacion.procesarCicloProductorConsumidor(procesoCpu, this, cicloActual);
        }

        // 3. Verificar si el proceso en CPU terminó de ejecutar
        verificarProcesoEnCpuTerminado(cicloActual);

        // 4. Planificador a largo plazo: intentar admitir procesos de la cola de nuevos
        planificarLargoPlazo(cicloActual);

        // 5. Planificador a corto plazo: despachar o evaluar desalojos en la CPU
        planificarCortoPlazo(cicloActual);

        // 6. Actualizar tiempos de espera en la cola de listos y bloqueados
        actualizarTiemposEspera();
    }

    // -------------------------------------------------------------------------
    // Supervisión de Deadlines (Requerimiento 2)
    // -------------------------------------------------------------------------

    private void supervisarDeadlines(long cicloActual) {
        // A) Proceso en CPU
        Proceso procesoCpu = cpu.getProcesoActual();
        if (procesoCpu != null && procesoCpu.getEstado() != EstadoProceso.TERMINADO) {
            boolean expiro = procesoCpu.decrementarDeadline(cicloActual);
            if (expiro) {
                cpu.desalojarProceso();
                finalizarProceso(procesoCpu, cicloActual);
            }
        }

        // B) Procesos en cola de listos
        int totalListos = colaListos.obtenerTamano();
        for (int i = totalListos - 1; i >= 0; i--) {
            Proceso p = colaListos.obtener(i);
            if (p != null) {
                boolean expiro = p.decrementarDeadline(cicloActual);
                if (expiro) {
                    colaListos.eliminarEn(i);
                    finalizarProceso(p, cicloActual);
                }
            }
        }

        // C) Procesos en cola de bloqueados
        int totalBloqueados = colaBloqueados.obtenerTamano();
        for (int i = totalBloqueados - 1; i >= 0; i--) {
            Proceso p = colaBloqueados.obtener(i);
            if (p != null) {
                boolean expiro = p.decrementarDeadline(cicloActual);
                if (expiro) {
                    colaBloqueados.eliminarEn(i);
                    finalizarProceso(p, cicloActual);
                }
            }
        }

        // D) Procesos en cola de nuevos
        int totalNuevos = colaNuevos.obtenerTamano();
        for (int i = totalNuevos - 1; i >= 0; i--) {
            Proceso p = colaNuevos.obtener(i);
            if (p != null) {
                boolean expiro = p.decrementarDeadline(cicloActual);
                if (expiro) {
                    colaNuevos.eliminarEn(i);
                    finalizarProcesoSinMemoria(p, cicloActual);
                }
            }
        }
    }

    private void verificarProcesoEnCpuTerminado(long cicloActual) {
        Proceso p = cpu.getProcesoActual();
        if (p != null && p.estaTerminado()) {
            cpu.desalojarProceso();
            finalizarProceso(p, cicloActual);
        }
    }

    private void finalizarProceso(Proceso p, long cicloActual) {
        colaTerminados.agregar(p);
        memoria.liberarMemoria(p.getPcb().getMemoriaAsignada());

        if (p.getPcb().isTerminoPorDeadline()) {
            procesosTerminadosPorDeadline++;
            registrarEvento("Proceso " + p.getId() + " (" + p.getNombre()
                    + ") termina por vencimiento de deadline en " + nombreComputador() + ".");
        } else {
            procesosCompletadosExitosos++;
            registrarEvento("Proceso " + p.getId() + " (" + p.getNombre()
                    + ") finaliza correctamente en " + nombreComputador() + ".");
        }

        // Al liberar memoria, revisar si algún proceso en cola de nuevos puede entrar
        planificarLargoPlazo(cicloActual);
    }

    private void finalizarProcesoSinMemoria(Proceso p, long cicloActual) {
        colaTerminados.agregar(p);
        procesosTerminadosPorDeadline++;
        registrarEvento("Proceso " + p.getId() + " (" + p.getNombre()
                + ") termina por vencimiento de deadline mientras esperaba memoria en " + nombreComputador() + ".");
    }

    // -------------------------------------------------------------------------
    // Planificación a Largo Plazo (Admisión por RAM)
    // -------------------------------------------------------------------------

    /**
     * Revisa la cola de nuevos e ingresa a listos aquellos para los que haya RAM disponible.
     *
     * @param cicloActual ciclo global de la simulación
     */
    public synchronized void planificarLargoPlazo(long cicloActual) {
        int total = colaNuevos.obtenerTamano();
        for (int i = 0; i < total; i++) {
            if (colaNuevos.estaVacia()) {
                break;
            }
            Proceso candidato = colaNuevos.obtener(0);
            int memoriaNecesaria = candidato.getPcb().getMemoriaAsignada();

            if (memoria.hayEspacioDisponible(memoriaNecesaria)) {
                colaNuevos.eliminarEn(0);
                memoria.asignarMemoria(memoriaNecesaria);
                candidato.pasarAListo(cicloActual);
                colaListos.agregar(candidato);
                politica.ordenarCola(colaListos);
                registrarEvento("Proceso " + candidato.getId() + " admitido en memoria y agregado a listos en "
                        + nombreComputador() + ".");
            } else {
                // Si el proceso al frente no cabe, espera hasta que se libere memoria
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Planificación a Corto Plazo (Despacho a CPU)
    // -------------------------------------------------------------------------

    /**
     * Selecciona el siguiente proceso a ejecutar o efectúa desalojos preventivos si corresponde.
     *
     * @param cicloActual ciclo de simulación
     */
    public synchronized void planificarCortoPlazo(long cicloActual) {
        Proceso enCpu = cpu.getProcesoActual();

        // Caso 1: CPU libre
        if (enCpu == null) {
            if (!colaListos.estaVacia()) {
                Proceso siguiente = politica.seleccionarSiguiente(colaListos);
                if (siguiente != null) {
                    cpu.cargarProceso(siguiente, cicloActual);
                    registrarEvento(nombreComputador() + " selecciona el proceso " + siguiente.getId() + ".");
                }
            }
            return;
        }

        // Caso 2: CPU ocupada, evaluar política apropiativa o Round Robin
        Proceso candidatoListo = !colaListos.estaVacia() ? colaListos.obtener(0) : null;
        boolean desalojar = politica.debeDesalojar(enCpu, candidatoListo, cpu.getCiclosEnRafagaActual(), quantum);

        if (desalojar) {
            Proceso desalojado = cpu.desalojarProceso();
            desalojado.pasarAListo(cicloActual);
            colaListos.agregar(desalojado);
            politica.ordenarCola(colaListos);

            Proceso siguiente = politica.seleccionarSiguiente(colaListos);
            if (siguiente != null) {
                cpu.cargarProceso(siguiente, cicloActual);
                registrarEvento(nombreComputador() + " selecciona el proceso " + siguiente.getId()
                        + " tras desalojar " + desalojado.getId() + ".");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Creación y Bloqueo de Procesos
    // -------------------------------------------------------------------------

    /**
     * Encola un nuevo proceso en la cola de nuevos del sistema operativo.
     *
     * @param proceso proceso a admitir
     * @param cicloActual ciclo de reloj en que se crea
     */
    public synchronized void agregarProcesoNuevo(Proceso proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }
        proceso.getPcb().setCicloCreacion(cicloActual);
        proceso.getPcb().setComputadorAsignado(idComputador);
        colaNuevos.agregar(proceso);
        registrarEvento("Proceso " + proceso.getId() + " (" + proceso.getNombre()
                + ") ingresa a la cola de nuevos de " + nombreComputador() + ".");
        planificarLargoPlazo(cicloActual);
    }

    /**
     * Bloquea el proceso actual de la CPU o un proceso específico en la cola de bloqueados.
     *
     * @param proceso proceso a bloquear
     * @param motivo motivo por el cual se bloquea
     */
    public synchronized void bloquearProceso(Proceso proceso, MotivoBloqueo motivo) {
        if (proceso == null) {
            return;
        }

        if (cpu.getProcesoActual() == proceso) {
            cpu.desalojarProceso();
        } else {
            colaListos.eliminar(proceso);
        }

        proceso.bloquear(motivo);
        colaBloqueados.agregar(proceso);
        if (motivo != null && !motivo.esPorSemaforo()) {
            registrarEvento("Proceso " + proceso.getId() + " se bloquea en " + motivo.getEtiqueta()
                    + " en " + nombreComputador() + ".");
        }
    }

    /**
     * Desbloquea un proceso y lo retorna a la cola de listos.
     *
     * @param proceso proceso que puede continuar
     * @param cicloActual ciclo global de simulación
     */
    public synchronized void desbloquearProceso(Proceso proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }

        if (colaBloqueados.eliminar(proceso)) {
            MotivoBloqueo motivoAnterior = proceso.getPcb().getMotivoBloqueo();
            proceso.pasarAListo(cicloActual);
            colaListos.agregar(proceso);
            politica.ordenarCola(colaListos);
            if (motivoAnterior == null || !motivoAnterior.esPorSemaforo()) {
                registrarEvento("Proceso " + proceso.getId() + " se desbloquea y vuelve a listos en "
                        + nombreComputador() + ".");
            }
        }
    }

    private void actualizarTiemposEspera() {
        int totalListos = colaListos.obtenerTamano();
        for (int i = 0; i < totalListos; i++) {
            Proceso p = colaListos.obtener(i);
            if (p != null) {
                p.getPcb().incrementarTiempoEnEspera();
            }
        }

        int totalBloqueados = colaBloqueados.obtenerTamano();
        for (int i = 0; i < totalBloqueados; i++) {
            Proceso p = colaBloqueados.obtener(i);
            if (p != null) {
                p.getPcb().incrementarTiempoBloqueadoTotal();
                if (p.getPcb().getMotivoBloqueo().esPorSemaforo()) {
                    p.getPcb().incrementarTiempoBloqueadoSemaforos();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Getters y Configuración Dinámica
    // -------------------------------------------------------------------------

    public String getIdComputador() {
        return idComputador;
    }

    public CPU getCpu() {
        return cpu;
    }

    public MemoriaPrincipal getMemoria() {
        return memoria;
    }

    public synchronized IPoliticaPlanificacion getPolitica() {
        return politica;
    }

    public synchronized void setPolitica(IPoliticaPlanificacion nuevaPolitica) {
        if (nuevaPolitica != null) {
            this.politica = nuevaPolitica;
            this.politica.ordenarCola(colaListos);
        }
    }

    public GestorSincronizacion getGestorSincronizacion() {
        return gestorSincronizacion;
    }

    public void setGestorSincronizacion(GestorSincronizacion gestorSincronizacion) {
        this.gestorSincronizacion = gestorSincronizacion;
    }

    /**
     * Cambia la política de planificación a partir de su tipo enumerado.
     *
     * @param tipoPolitica enum de la nueva política
     */
    public synchronized void setPolitica(PoliticaPlanificacion tipoPolitica) {
        setPolitica(FabricaPoliticas.crear(tipoPolitica));
    }

    public synchronized int getQuantum() {
        return quantum;
    }

    public synchronized void setQuantum(int quantum) {
        this.quantum = Math.max(1, quantum);
    }

    public ListaEnlazada<Proceso> getColaNuevos() {
        return colaNuevos;
    }

    public ListaEnlazada<Proceso> getColaListos() {
        return colaListos;
    }

    public ListaEnlazada<Proceso> getColaBloqueados() {
        return colaBloqueados;
    }

    public ListaEnlazada<Proceso> getColaTerminados() {
        return colaTerminados;
    }

    public int getProcesosCompletadosExitosos() {
        return procesosCompletadosExitosos;
    }

    public int getProcesosTerminadosPorDeadline() {
        return procesosTerminadosPorDeadline;
    }

    /** Publica un evento del núcleo en el registro global del clúster. */
    public void registrarEvento(String mensaje) {
        if (gestorSincronizacion != null) {
            gestorSincronizacion.getCluster().registrarEvento(mensaje);
        }
    }

    private String nombreComputador() {
        if (gestorSincronizacion != null) {
            Computador computador = gestorSincronizacion.getCluster().buscarPorId(idComputador);
            if (computador != null) {
                return computador.getNombre();
            }
        }
        return idComputador;
    }
}
