package avilaos.sistema;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoEjecucion;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoOperacionES;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.CPU;
import avilaos.hardware.DiscoSimulado;
import avilaos.hardware.MemoriaPrincipal;
import avilaos.hardware.SolicitudDisco;
import avilaos.sistemaarchivos.SistemaArchivos;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.planificacion.FabricaPoliticas;
import avilaos.planificacion.PoliticaFCFS;
import avilaos.proceso.PCB;
import avilaos.proceso.PeticionArchivo;
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

    /** Referencia al disco local para operaciones de intercambio (swap). */
    private DiscoSimulado disco;

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

    /** Procesos suspendidos en swap que ya están listos para regresar a RAM (mediano plazo). */
    private final ListaEnlazada<Proceso> colaListosSuspendidos;

    /** Procesos suspendidos en swap que siguen esperando su evento de bloqueo (mediano plazo). */
    private final ListaEnlazada<Proceso> colaBloqueadosSuspendidos;

    /** Procesos que han concluido o han sido terminados por vencimiento de deadline. */
    private final ListaEnlazada<Proceso> colaTerminados;

    // -------------------------------------------------------------------------
    // Métricas del Núcleo
    // -------------------------------------------------------------------------

    /** Cantidad total de procesos completados con éxito dentro de su deadline. */
    private int procesosCompletadosExitosos;

    /** Cantidad total de procesos abortados por vencimiento de deadline. */
    private int procesosTerminadosPorDeadline;

    /** Cantidad total de suspensiones (swap-out) completadas por el planificador a mediano plazo. */
    private int totalSuspensiones;

    /** Cantidad total de reactivaciones (swap-in) completadas por el planificador a mediano plazo. */
    private int totalReactivaciones;

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
        this.colaListosSuspendidos = new ListaEnlazada<>();
        this.colaBloqueadosSuspendidos = new ListaEnlazada<>();
        this.colaTerminados = new ListaEnlazada<>();

        this.procesosCompletadosExitosos = 0;
        this.procesosTerminadosPorDeadline = 0;
        this.totalSuspensiones = 0;
        this.totalReactivaciones = 0;
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
        ejecutarCicloSO(cicloActual, null, null);
    }

    synchronized void ejecutarCicloSO(long cicloActual, SistemaArchivos archivos, SolicitudDisco terminada) {
        // 0. Actualizar procesos bloqueados por tiempo (latencia de red y E/S)
        if (gestorSincronizacion != null) {
            gestorSincronizacion.actualizarBloqueadosPorTiempo(this, cicloActual);
        } else {
            actualizarBloqueadosPorTiempoLocal(cicloActual);
        }

        // 1. Supervisar deadlines de todos los procesos activos en el computador
        supervisarDeadlines(cicloActual, archivos);
        if (terminada != null) {
            procesarTransferenciaDiscoTerminada(terminada, archivos, cicloActual);
        }

        // 2. Coordinación de procesos en CPU según su tipo (Productor, Consumidor o I/O Bound)
        Proceso procesoCpu = cpu.getProcesoActual();
        if (procesoCpu != null && !procesoCpu.estaTerminado()) {
            if (procesoCpu.getPcb().getTipo() == TipoProceso.PRODUCTOR
                    || procesoCpu.getPcb().getTipo() == TipoProceso.CONSUMIDOR) {
                if (gestorSincronizacion != null) {
                    gestorSincronizacion.procesarCicloProductorConsumidor(procesoCpu, this, cicloActual);
                }
            } else if (procesoCpu.getPcb().getPeticionArchivo() != null) {
                if (procesoCpu.getPcb().getIdSolicitudDisco() == 0) {
                    solicitarArchivo(procesoCpu, archivos, cicloActual);
                }
            } else if (procesoCpu.getPcb().getTipo() == TipoProceso.IO_BOUND) {
                procesarCicloIOBound(procesoCpu, cicloActual);
            }
        }

        // 3. Verificar si el proceso en CPU terminó de ejecutar
        verificarProcesoEnCpuTerminado(cicloActual);

        // 4. Planificador a mediano plazo: reactivar procesos Listo/Suspendido si hay RAM libre
        planificarMedianoPlazoReactivacion(cicloActual);

        // 5. Planificador a largo plazo: intentar admitir procesos de la cola de nuevos (o suspender bloqueados)
        planificarLargoPlazo(cicloActual);

        // 6. Planificador a corto plazo: despachar o evaluar desalojos en la CPU
        planificarCortoPlazo(cicloActual);

        // 7. Actualizar tiempos de espera en la cola de listos y bloqueados
        actualizarTiemposEspera();
    }

    /** Procesa la finalización de una transferencia de disco (swap-out, swap-in o archivo). */
    private void procesarTransferenciaDiscoTerminada(SolicitudDisco terminada, SistemaArchivos archivos, long cicloActual) {
        long id = terminada.id();

        // A) Swap-out completado (SUSPENDER): proceso pasa de Bloqueado a Bloqueado/Suspendido (o Listo/Suspendido)
        for (int i = 0; i < colaBloqueados.obtenerTamano(); i++) {
            Proceso proceso = colaBloqueados.obtener(i);
            PCB pcb = proceso.getPcb();
            if (pcb.getIdSolicitudSwap() == id && pcb.getOperacionSwapEnCurso() == TipoOperacionES.SUSPENDER) {
                colaBloqueados.eliminarEn(i);
                pcb.setIdSolicitudSwap(0);
                pcb.setOperacionSwapEnCurso(null);
                pcb.incrementarVecesSuspendido();
                memoria.liberarMemoria(pcb.getMemoriaAsignada());
                totalSuspensiones++;

                if (pcb.isDesbloqueoPendienteTrasSwap()) {
                    pcb.setDesbloqueoPendienteTrasSwap(false);
                    proceso.suspenderBloqueado();
                    proceso.pasarAListoSuspendido();
                    colaListosSuspendidos.agregar(proceso);
                    registrarEvento("Se suspende al proceso " + proceso.getId()
                            + " (evento ocurrido durante swap-out; pasa a Listo/Suspendido, RAM liberada: "
                            + pcb.getMemoriaAsignada() + " MB).");
                } else {
                    proceso.suspenderBloqueado();
                    colaBloqueadosSuspendidos.agregar(proceso);
                    registrarEvento("Se suspende al proceso " + proceso.getId()
                            + " en swap [bloque #" + pcb.getPrimerBloqueSwap()
                            + "] (RAM liberada: " + pcb.getMemoriaAsignada() + " MB).");
                }
                planificarLargoPlazo(cicloActual);
                return;
            }
        }

        // B) Swap-in completado (REACTIVAR): proceso pasa de Listo/Suspendido a Listo en RAM
        for (int i = 0; i < colaListosSuspendidos.obtenerTamano(); i++) {
            Proceso proceso = colaListosSuspendidos.obtener(i);
            PCB pcb = proceso.getPcb();
            if (pcb.getIdSolicitudSwap() == id && pcb.getOperacionSwapEnCurso() == TipoOperacionES.REACTIVAR) {
                colaListosSuspendidos.eliminarEn(i);
                if (disco != null && pcb.getPrimerBloqueSwap() >= 0) {
                    disco.liberar(pcb.getPrimerBloqueSwap());
                }
                pcb.setPrimerBloqueSwap(-1);
                pcb.setIdSolicitudSwap(0);
                pcb.setOperacionSwapEnCurso(null);
                proceso.reactivarAListo(cicloActual);
                colaListos.agregar(proceso);
                politica.ordenarCola(colaListos);
                totalReactivaciones++;
                registrarEvento("Se reactiva al proceso " + proceso.getId() + " (" + proceso.getNombre()
                        + ") desde swap a Listos en " + nombreComputador() + ".");
                return;
            }
        }

        // C) Operación de archivo completada para un proceso en Bloqueados (en RAM)
        for (int i = 0; i < colaBloqueados.obtenerTamano(); i++) {
            Proceso proceso = colaBloqueados.obtener(i);
            if (proceso.getMotivoBloqueo() == MotivoBloqueo.DISCO
                    && proceso.getPcb().getIdSolicitudDisco() == id) {
                try {
                    if (archivos != null) archivos.completarOperacion(id);
                    desbloquearProceso(proceso, cicloActual);
                } catch (IllegalStateException | IllegalArgumentException rechazo) {
                    colaBloqueados.eliminarEn(i);
                    liberarSwapDeProceso(proceso);
                    proceso.terminarPorErrorArchivo(cicloActual, rechazo.getMessage());
                    finalizarProceso(proceso, cicloActual);
                }
                return;
            }
        }

        // D) Operación de archivo completada mientras el proceso estaba Bloqueado/Suspendido
        for (int i = 0; i < colaBloqueadosSuspendidos.obtenerTamano(); i++) {
            Proceso proceso = colaBloqueadosSuspendidos.obtener(i);
            if (proceso.getMotivoBloqueo() == MotivoBloqueo.DISCO
                    && proceso.getPcb().getIdSolicitudDisco() == id) {
                try {
                    if (archivos != null) archivos.completarOperacion(id);
                    desbloquearProceso(proceso, cicloActual);
                } catch (IllegalStateException | IllegalArgumentException rechazo) {
                    colaBloqueadosSuspendidos.eliminarEn(i);
                    liberarSwapDeProceso(proceso);
                    proceso.terminarPorErrorArchivo(cicloActual, rechazo.getMessage());
                    finalizarProcesoSinMemoria(proceso, cicloActual);
                }
                return;
            }
        }
    }

    /**
     * Procesa la lógica de ejecución para procesos I/O Bound en la CPU.
     * Incrementa el contador de ciclos de CPU ejecutados desde la última operación de E/S.
     * Al alcanzar el intervalo configurado (ciclosEntreIO), el proceso efectúa una syscall
     * de E/S, la CPU conmuta a Modo SO, y el proceso es trasladado a la cola de BLOQUEADOS.
     *
     * @param proceso proceso I/O bound en ejecución
     * @param cicloActual ciclo global del reloj
     */
    private void procesarCicloIOBound(Proceso proceso, long cicloActual) {
        PCB pcb = proceso.getPcb();
        int intervaloIO = pcb.getCiclosEntreIO();
        int duracionIO = pcb.getDuracionBloqueoIO();

        if (intervaloIO <= 0 || duracionIO <= 0) {
            return;
        }

        pcb.incrementarCiclosCpuDesdeUltimoIO();

        if (pcb.getCiclosCpuDesdeUltimoIO() >= intervaloIO) {
            // Syscall de E/S: transición a modo kernel y bloqueo periférico
            cpu.setModoEjecucion(ModoEjecucion.SISTEMA_OPERATIVO);
            pcb.setCiclosCpuDesdeUltimoIO(0);
            pcb.setCiclosBloqueadoIOActual(duracionIO);
            bloquearProceso(proceso, MotivoBloqueo.ENTRADA_SALIDA);
            registrarEvento("Proceso " + proceso.getId() + " (" + proceso.getNombre()
                    + ") solicita E/S tras " + intervaloIO + " ciclos de CPU y pasa a BLOQUEADO ("
                    + duracionIO + " ciclos) en " + nombreComputador() + ".");
        }
    }

    private void solicitarArchivo(Proceso proceso, SistemaArchivos archivos, long cicloActual) {
        PCB pcb = proceso.getPcb();
        var peticion = pcb.getPeticionArchivo();
        cpu.setModoEjecucion(ModoEjecucion.SISTEMA_OPERATIVO);
        try {
            if (archivos == null) {
                throw new IllegalStateException("Operación de archivo no disponible.");
            }
            SolicitudDisco solicitud = archivos.solicitarOperacion(peticion, proceso.getId());
            pcb.setIdSolicitudDisco(solicitud.id());
        } catch (IllegalArgumentException | IllegalStateException | SecurityException rechazo) {
            proceso.terminarPorErrorArchivo(cicloActual, rechazo.getMessage());
            return;
        }
        bloquearProceso(proceso, MotivoBloqueo.DISCO);
    }

    /**
     * Actualiza el tiempo restante de procesos bloqueados por E/S cuando no hay
     * un gestor de sincronización global configurado (ej. pruebas unitarias aisladas).
     *
     * @param cicloActual ciclo global de simulación
     */
    private void actualizarBloqueadosPorTiempoLocal(long cicloActual) {
        actualizarListaBloqueadosTiempoLocal(colaBloqueados, cicloActual);
        actualizarListaBloqueadosTiempoLocal(colaBloqueadosSuspendidos, cicloActual);
    }

    private void actualizarListaBloqueadosTiempoLocal(ListaEnlazada<Proceso> lista, long cicloActual) {
        int total = lista.obtenerTamano();
        for (int i = total - 1; i >= 0; i--) {
            Proceso p = lista.obtener(i);
            if (p == null) {
                continue;
            }
            PCB pcb = p.getPcb();
            if (pcb.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA) {
                int ioRestante = pcb.getCiclosBloqueadoIOActual() - 1;
                pcb.setCiclosBloqueadoIOActual(ioRestante);
                if (ioRestante <= 0) {
                    desbloquearProceso(p, cicloActual);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Supervisión de Deadlines (Requerimiento 2)
    // -------------------------------------------------------------------------

    private void supervisarDeadlines(long cicloActual, SistemaArchivos archivos) {
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
                    if (archivos != null && p.getPcb().getIdSolicitudDisco() != 0) {
                        archivos.cancelarOperacion(p.getPcb().getIdSolicitudDisco());
                    }
                    liberarSwapDeProceso(p);
                    colaBloqueados.eliminarEn(i);
                    finalizarProceso(p, cicloActual);
                }
            }
        }

        // D) Procesos en cola de bloqueados/suspendidos (sin RAM asignada)
        int totalBloqSusp = colaBloqueadosSuspendidos.obtenerTamano();
        for (int i = totalBloqSusp - 1; i >= 0; i--) {
            Proceso p = colaBloqueadosSuspendidos.obtener(i);
            if (p != null) {
                boolean expiro = p.decrementarDeadline(cicloActual);
                if (expiro) {
                    if (archivos != null && p.getPcb().getIdSolicitudDisco() != 0) {
                        archivos.cancelarOperacion(p.getPcb().getIdSolicitudDisco());
                    }
                    liberarSwapDeProceso(p);
                    colaBloqueadosSuspendidos.eliminarEn(i);
                    finalizarProcesoSinMemoria(p, cicloActual);
                }
            }
        }

        // E) Procesos en cola de listos/suspendidos
        int totalListosSusp = colaListosSuspendidos.obtenerTamano();
        for (int i = totalListosSusp - 1; i >= 0; i--) {
            Proceso p = colaListosSuspendidos.obtener(i);
            if (p != null) {
                boolean expiro = p.decrementarDeadline(cicloActual);
                if (expiro) {
                    boolean teniaRamReservada = p.getPcb().getOperacionSwapEnCurso() == TipoOperacionES.REACTIVAR;
                    liberarSwapDeProceso(p);
                    colaListosSuspendidos.eliminarEn(i);
                    if (teniaRamReservada) {
                        finalizarProceso(p, cicloActual);
                    } else {
                        finalizarProcesoSinMemoria(p, cicloActual);
                    }
                }
            }
        }

        // F) Procesos en cola de nuevos
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

    /** Cancela cualquier transferencia de swap pendiente y libera los bloques swap ocupados por el proceso. */
    private void liberarSwapDeProceso(Proceso p) {
        PCB pcb = p.getPcb();
        if (disco != null) {
            if (pcb.getIdSolicitudSwap() != 0) {
                disco.cancelarTransferencia(pcb.getIdSolicitudSwap());
            }
            if (pcb.getPrimerBloqueSwap() >= 0) {
                disco.liberar(pcb.getPrimerBloqueSwap());
            }
        }
        pcb.setIdSolicitudSwap(0);
        pcb.setOperacionSwapEnCurso(null);
        pcb.setPrimerBloqueSwap(-1);
        pcb.setDesbloqueoPendienteTrasSwap(false);
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
        } else if (p.getPcb().getErrorArchivo() != null) {
            registrarEvento("Proceso " + p.getId() + ": " + p.getPcb().getErrorArchivo());
        } else {
            procesosCompletadosExitosos++;
            registrarEvento("Proceso " + p.getId() + " (" + p.getNombre()
                    + ") finaliza correctamente en " + nombreComputador() + ".");
        }

        // Al liberar memoria, revisar primero si algún proceso Listo/Suspendido puede reactivarse
        // y luego si algún proceso nuevo puede ser admitido
        planificarMedianoPlazoReactivacion(cicloActual);
        planificarLargoPlazo(cicloActual);
    }

    private void finalizarProcesoSinMemoria(Proceso p, long cicloActual) {
        colaTerminados.agregar(p);
        if (p.getPcb().getErrorArchivo() != null) {
            registrarEvento("Proceso " + p.getId() + ": " + p.getPcb().getErrorArchivo());
        } else {
            procesosTerminadosPorDeadline++;
            registrarEvento("Proceso " + p.getId() + " (" + p.getNombre()
                    + ") termina por vencimiento de deadline sin RAM asignada en " + nombreComputador() + ".");
        }
    }

    // -------------------------------------------------------------------------
    // Planificación a Mediano Plazo (Suspensión y Reactivación / Swap)
    // -------------------------------------------------------------------------

    /**
     * Planificador a mediano plazo (Swap-out): cuando un proceso nuevo no cabe en RAM,
     * selecciona un proceso en estado BLOQUEADO (criterio: menor prioridad y, en empate,
     * mayor tiempo bloqueado) para escribirlo en el área de intercambio del disco.
     */
    private void intentarSuspenderParaAdmitir(Proceso candidato, long cicloActual) {
        if (disco == null || candidato == null) {
            return;
        }
        int ramNecesaria = candidato.getPcb().getMemoriaAsignada();
        int ramPendienteLiberar = 0;
        for (int i = 0; i < colaBloqueados.obtenerTamano(); i++) {
            Proceso p = colaBloqueados.obtener(i);
            if (p != null && p.getPcb().getOperacionSwapEnCurso() == TipoOperacionES.SUSPENDER) {
                ramPendienteLiberar += p.getPcb().getMemoriaAsignada();
            }
        }
        if (memoria.getMemoriaLibre() + ramPendienteLiberar >= ramNecesaria) {
            return; // Ya hay suspensiones en curso suficientes para admitir al candidato
        }

        int swapLibre = disco.getBloquesSwapLibres();
        Proceso victima = null;
        for (int i = 0; i < colaBloqueados.obtenerTamano(); i++) {
            Proceso p = colaBloqueados.obtener(i);
            if (p == null) {
                continue;
            }
            PCB pcb = p.getPcb();
            if (pcb.getIdSolicitudSwap() != 0 || pcb.getPrimerBloqueSwap() >= 0) {
                continue;
            }
            if (pcb.getMemoriaAsignada() > swapLibre) {
                continue;
            }
            if (victima == null
                    || p.getPrioridad() < victima.getPrioridad()
                    || (p.getPrioridad() == victima.getPrioridad()
                        && pcb.getTiempoBloqueadoTotal() > victima.getPcb().getTiempoBloqueadoTotal())) {
                victima = p;
            }
        }

        if (victima == null) {
            return;
        }

        try {
            PCB pcbVictima = victima.getPcb();
            int bloqueSwap = disco.asignarSwap(victima.getId(), pcbVictima.getMemoriaAsignada());
            SolicitudDisco solicitud = disco.iniciarTransferencia(bloqueSwap, DiscoSimulado.Operacion.ESCRITURA,
                    TipoOperacionES.SUSPENDER.getEtiqueta());
            pcbVictima.setPrimerBloqueSwap(bloqueSwap);
            pcbVictima.setIdSolicitudSwap(solicitud.id());
            pcbVictima.setOperacionSwapEnCurso(TipoOperacionES.SUSPENDER);
            registrarEvento("Planificador mediano plazo solicita suspender a " + victima.getId()
                    + " (Prio " + victima.getPrioridad() + ", Swap #" + bloqueSwap
                    + ") para admitir a " + candidato.getId() + ".");
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // Sin bloques swap suficientes en este instante; se reintentará en el próximo ciclo
        }
    }

    /**
     * Planificador a mediano plazo (Swap-in): revisa la cola de Listo/Suspendido
     * y solicita al disco reactivar el proceso de mayor prioridad que quepa en RAM.
     */
    public synchronized void planificarMedianoPlazoReactivacion(long cicloActual) {
        if (disco == null || colaListosSuspendidos.estaVacia()) {
            return;
        }

        while (true) {
            Proceso elegido = null;
            for (int i = 0; i < colaListosSuspendidos.obtenerTamano(); i++) {
                Proceso p = colaListosSuspendidos.obtener(i);
                if (p == null) {
                    continue;
                }
                PCB pcb = p.getPcb();
                if (pcb.getIdSolicitudSwap() != 0 || pcb.getPrimerBloqueSwap() < 0) {
                    continue;
                }
                if (!memoria.hayEspacioDisponible(pcb.getMemoriaAsignada())) {
                    continue;
                }
                if (elegido == null || p.getPrioridad() > elegido.getPrioridad()) {
                    elegido = p;
                }
            }
            if (elegido == null) {
                break;
            }

            PCB pcb = elegido.getPcb();
            memoria.asignarMemoria(pcb.getMemoriaAsignada());
            try {
                SolicitudDisco solicitud = disco.iniciarTransferencia(pcb.getPrimerBloqueSwap(), DiscoSimulado.Operacion.LECTURA,
                        TipoOperacionES.REACTIVAR.getEtiqueta());
                pcb.setIdSolicitudSwap(solicitud.id());
                pcb.setOperacionSwapEnCurso(TipoOperacionES.REACTIVAR);
                registrarEvento("Planificador mediano plazo solicita reactivar a " + elegido.getId()
                        + " desde swap [bloque #" + pcb.getPrimerBloqueSwap() + "] (RAM: "
                        + pcb.getMemoriaAsignada() + " MB).");
            } catch (IllegalStateException | IllegalArgumentException ex) {
                memoria.liberarMemoria(pcb.getMemoriaAsignada());
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Planificación a Largo Plazo (Admisión por RAM)
    // -------------------------------------------------------------------------

    /**
     * Revisa la cola de nuevos e ingresa a listos aquellos para los que haya RAM disponible.
     * Si el proceso al frente no cabe en RAM, invoca al planificador a mediano plazo.
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
                // Sin RAM suficiente: solicitar al planificador a mediano plazo suspender un bloqueado
                intentarSuspenderParaAdmitir(candidato, cicloActual);
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

    /** Parámetros de los procesos de archivo del escenario de ejemplo del proyecto. */
    public synchronized Proceso crearProcesoArchivo(PeticionArchivo peticion, long cicloActual) {
        if (peticion == null) throw new IllegalArgumentException("Indique una petición de archivo.");
        String nombre = peticion.operacion() + " " + peticion.ruta();
        Proceso proceso = new Proceso(nombre, idComputador, TipoProceso.IO_BOUND, 1, 16, 500, 2, peticion);
        agregarProcesoNuevo(proceso, cicloActual);
        return proceso;
    }

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

        if (colaBloqueados.contiene(proceso)) {
            if (proceso.getPcb().getOperacionSwapEnCurso() == TipoOperacionES.SUSPENDER) {
                proceso.getPcb().setDesbloqueoPendienteTrasSwap(true);
                return;
            }
            colaBloqueados.eliminar(proceso);
            MotivoBloqueo motivoAnterior = proceso.getPcb().getMotivoBloqueo();
            proceso.pasarAListo(cicloActual);
            colaListos.agregar(proceso);
            politica.ordenarCola(colaListos);
            if (motivoAnterior == null || !motivoAnterior.esPorSemaforo()) {
                registrarEvento("Proceso " + proceso.getId() + " se desbloquea y vuelve a listos en "
                        + nombreComputador() + ".");
            }
            return;
        }

        if (colaBloqueadosSuspendidos.eliminar(proceso)) {
            proceso.pasarAListoSuspendido();
            colaListosSuspendidos.agregar(proceso);
            registrarEvento("Proceso " + proceso.getId()
                    + " pasa de Bloqueado/Suspendido a Listo/Suspendido en " + nombreComputador() + ".");
            planificarMedianoPlazoReactivacion(cicloActual);
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

        int totalBloqSusp = colaBloqueadosSuspendidos.obtenerTamano();
        for (int i = 0; i < totalBloqSusp; i++) {
            Proceso p = colaBloqueadosSuspendidos.obtener(i);
            if (p != null) {
                p.getPcb().incrementarTiempoBloqueadoTotal();
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

    public DiscoSimulado getDisco() {
        return disco;
    }

    public void setDisco(DiscoSimulado disco) {
        this.disco = disco;
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

    /**
     * Reinicia CPU, todas las colas de procesos, uso de RAM y contadores del SO,
     * preservando el estado del sistema de archivos en el disco (usado al cargar un nuevo JSON 2).
     */
    public synchronized void reiniciarProcesos() {
        cpu.desalojarProceso();
        colaNuevos.vaciar();
        colaListos.vaciar();
        colaBloqueados.vaciar();
        colaListosSuspendidos.vaciar();
        colaBloqueadosSuspendidos.vaciar();
        colaTerminados.vaciar();
        memoria.reiniciarUso();
        procesosCompletadosExitosos = 0;
        procesosTerminadosPorDeadline = 0;
        totalSuspensiones = 0;
        totalReactivaciones = 0;
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

    public ListaEnlazada<Proceso> getColaListosSuspendidos() {
        return colaListosSuspendidos;
    }

    public ListaEnlazada<Proceso> getColaBloqueadosSuspendidos() {
        return colaBloqueadosSuspendidos;
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

    public int getTotalSuspensiones() {
        return totalSuspensiones;
    }

    public int getTotalReactivaciones() {
        return totalReactivaciones;
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
