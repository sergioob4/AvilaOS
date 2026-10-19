package avilaos.sincronizacion;

import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IComponenteReloj;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;

/**
 * Coordinador central de sincronización y acceso distribuido a buffers en ÁvilaOS.
 *
 * <p>Responsable de:
 * <ul>
 *   <li>Administrar el catálogo de buffers acotados compartidos en el clúster.</li>
 *   <li>Gestionar la reserva de memoria RAM en los computadores anfitriones de cada buffer (Requerimiento 5).</li>
 *   <li>Coordinar las operaciones de los procesos {@link TipoProceso#PRODUCTOR} y {@link TipoProceso#CONSUMIDOR}.</li>
 *   <li>Simular la <strong>latencia de red</strong> en accesos remotos entre computadores distintos (Requerimiento 1 y 4).</li>
 *   <li>Supervisar y decrementar los ciclos de bloqueo por red y E/S en cada pulso de reloj.</li>
 *   <li>Muestrear métricas de ocupación para los reportes y la interfaz gráfica.</li>
 * </ul>
 * </p>
 */
public class GestorSincronizacion implements IComponenteReloj {

    /** Clúster distribuido al que pertenece este gestor. */
    private final ClusterDistribuido cluster;

    /** Lista enlazada propia de buffers acotados activos en el clúster. */
    private final ListaEnlazada<BufferAcotado> buffers;

    /**
     * Construye un nuevo gestor de sincronización para el clúster.
     *
     * @param cluster clúster distribuido anfitrión
     */
    public GestorSincronizacion(ClusterDistribuido cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("El clúster no puede ser nulo.");
        }
        this.cluster = cluster;
        this.buffers = new ListaEnlazada<>();
    }

    // -------------------------------------------------------------------------
    // Gestión y Creación de Buffers Acotados
    // -------------------------------------------------------------------------

    /**
     * Crea y registra un nuevo buffer acotado en el clúster, descontando la memoria
     * RAM requerida en el computador anfitrión correspondiente.
     *
     * @param id identificador único del buffer (ej. "B1")
     * @param nombre nombre legible
     * @param idComputadorAnfitrion nodo donde residirá en RAM (ej. "C1")
     * @param capacidad capacidad máxima de elementos
     * @param memoriaMb memoria en MB que ocupará el buffer en el nodo anfitrión
     * @return el {@link BufferAcotado} creado, o {@code null} si el computador no existe o no tiene RAM suficiente
     */
    public synchronized BufferAcotado crearBuffer(String id, String nombre, String idComputadorAnfitrion,
                                                  int capacidad, int memoriaMb) {
        Computador host = cluster.buscarPorId(idComputadorAnfitrion);
        if (host == null) {
            System.err.println("Error: No se encontró el computador anfitrión '" + idComputadorAnfitrion + "' para el buffer " + id);
            return null;
        }

        // Descontar la memoria ocupada por el buffer en la RAM del computador anfitrión
        if (memoriaMb > 0) {
            if (!host.getMemoria().hayEspacioDisponible(memoriaMb)) {
                System.err.println("Error: El computador " + idComputadorAnfitrion + " no tiene suficiente RAM para el buffer " + id);
                return null;
            }
            host.getMemoria().asignarMemoria(memoriaMb);
        }

        BufferAcotado buffer = new BufferAcotado(id, nombre, idComputadorAnfitrion, capacidad, memoriaMb);
        buffers.agregar(buffer);
        return buffer;
    }

    /**
     * Busca un buffer acotado por su identificador único.
     *
     * @param idBuffer ID del buffer a buscar
     * @return el buffer encontrado, o {@code null} si no existe
     */
    public BufferAcotado buscarBuffer(String idBuffer) {
        if (idBuffer == null) {
            return null;
        }
        int total = buffers.obtenerTamano();
        for (int i = 0; i < total; i++) {
            BufferAcotado b = buffers.obtener(i);
            if (b != null && b.getId().equalsIgnoreCase(idBuffer)) {
                return b;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Coordinación de Producción y Consumo en CPU
    // -------------------------------------------------------------------------

    /**
     * Ejecuta la lógica de sincronización para un proceso Productor o Consumidor
     * que se encuentra ejecutando en la CPU durante el ciclo actual.
     *
     * @param proceso proceso activo en CPU
     * @param so sistema operativo del nodo
     * @param cicloActual ciclo global del reloj
     * @return {@code true} si el proceso continúa en CPU; {@code false} si fue bloqueado o terminó
     */
    public synchronized boolean procesarCicloProductorConsumidor(Proceso proceso, SistemaOperativo so, long cicloActual) {
        if (proceso == null || !proceso.getPcb().getTipo().interactuaConBuffer()) {
            return true; // No es productor ni consumidor, flujo normal
        }

        PCB pcb = proceso.getPcb();
        String idBuffer = pcb.getIdBuffer();
        BufferAcotado buffer = buscarBuffer(idBuffer);

        if (buffer == null) {
            // Si no tiene buffer específico, se asocia al primer buffer disponible
            if (!buffers.estaVacia()) {
                buffer = buffers.obtener(0);
                pcb.setIdBuffer(buffer.getId());
            } else {
                return true; // No hay buffers registrados en el sistema
            }
        }

        // Acumular un ciclo de avance hacia la siguiente producción/consumo
        int intervalo = Math.max(1, pcb.getCiclosPorItem());
        int acumulado = pcb.getCiclosAcumuladosItem() + 1;
        pcb.setCiclosAcumuladosItem(acumulado);

        // Si aún no alcanza el ciclo en el que debe interactuar con el buffer
        if (acumulado < intervalo) {
            return true; // Continúa ejecutando instrucciones en CPU
        }

        // ---------------------------------------------------------------------
        // Verificación de Latencia de Red (Acceso Remoto)
        // ---------------------------------------------------------------------
        boolean esLocal = buffer.esAccesoLocal(so.getIdComputador());
        if (!esLocal && cluster.getLatenciaRedCiclos() > 0 && !pcb.isLatenciaRedCompletada()) {
            // Requiere transmisión por red hacia el computador anfitrión del buffer
            pcb.setCiclosLatenciaRedRestante(cluster.getLatenciaRedCiclos());
            so.bloquearProceso(proceso, MotivoBloqueo.LATENCIA_RED);
            return false; // Proceso desalojado de CPU por latencia de red
        }

        // ---------------------------------------------------------------------
        // Ejecución de la Operación con Semáforos (Local o tras completar red)
        // ---------------------------------------------------------------------
        return ejecutarOperacionBuffer(proceso, buffer, so, cicloActual);
    }

    /**
     * Ejecuta directamente la operación de inserción (productor) o extracción (consumidor)
     * en el buffer mediante los semáforos correspondientes.
     */
    public synchronized boolean ejecutarOperacionBuffer(Proceso proceso, BufferAcotado buffer,
                                                        SistemaOperativo so, long cicloActual) {
        PCB pcb = proceso.getPcb();

        if (pcb.getTipo() == TipoProceso.PRODUCTOR) {
            String contenido = String.format("Dato #%d generado por %s en T=%d",
                    pcb.getItemsProcesados() + 1, proceso.getId(), cicloActual);

            ElementoBuffer item = buffer.producir(proceso, so, contenido, cicloActual);
            if (item == null) {
                // El proceso fue bloqueado por semáforo (buffer lleno o mutex)
                return false;
            }

            // Éxito en la producción: reiniciar acumulador para el próximo elemento
            pcb.setCiclosAcumuladosItem(0);
            pcb.setLatenciaRedCompletada(false);

        } else if (pcb.getTipo() == TipoProceso.CONSUMIDOR) {
            ElementoBuffer item = buffer.consumir(proceso, so, cicloActual);
            if (item == null) {
                // El proceso fue bloqueado por semáforo (buffer vacío o mutex)
                return false;
            }

            // Éxito en el consumo
            pcb.setCiclosAcumuladosItem(0);
            pcb.setLatenciaRedCompletada(false);
        }

        // Comprobar si el proceso cumplió su cuota total de elementos requeridos
        if (pcb.getItemsRequeridos() > 0 && pcb.getItemsProcesados() >= pcb.getItemsRequeridos()) {
            proceso.terminarExitoso(cicloActual);
            return false;
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Avance de Procesos Bloqueados (Latencia de Red y E/S)
    // -------------------------------------------------------------------------

    /**
     * Supervisa la cola de bloqueados de un computador en cada ciclo, decrementando
     * los tiempos de espera por latencia de red o por operaciones periféricas de E/S.
     * Al expirar el tiempo de espera, el proceso es devuelto a la cola de LISTOS.
     *
     * @param so sistema operativo a revisar
     * @param cicloActual ciclo global de reloj
     */
    public synchronized void actualizarBloqueadosPorTiempo(SistemaOperativo so, long cicloActual) {
        ListaEnlazada<Proceso> bloqueados = so.getColaBloqueados();
        int total = bloqueados.obtenerTamano();

        for (int i = total - 1; i >= 0; i--) {
            Proceso p = bloqueados.obtener(i);
            if (p == null) {
                continue;
            }

            PCB pcb = p.getPcb();
            MotivoBloqueo motivo = pcb.getMotivoBloqueo();

            // 1. Latencia de red concluida
            if (motivo == MotivoBloqueo.LATENCIA_RED) {
                int latenciaRestante = pcb.getCiclosLatenciaRedRestante() - 1;
                pcb.setCiclosLatenciaRedRestante(latenciaRestante);

                if (latenciaRestante <= 0) {
                    // El paquete de red llegó al computador anfitrión; reingresa a LISTOS para operar
                    pcb.setLatenciaRedCompletada(true);
                    so.desbloquearProceso(p, cicloActual);
                }
            }

            // 2. Operación de E/S concluida (I/O Bound)
            else if (motivo == MotivoBloqueo.ENTRADA_SALIDA) {
                int ioRestante = pcb.getCiclosBloqueadoIOActual() - 1;
                pcb.setCiclosBloqueadoIOActual(ioRestante);

                if (ioRestante <= 0) {
                    so.desbloquearProceso(p, cicloActual);
                }
            }
        }
    }

    /**
     * Muestrea las métricas de ocupación de todos los buffers activos en este ciclo.
     */
    public synchronized void muestrearOcupacionBuffers() {
        int total = buffers.obtenerTamano();
        for (int i = 0; i < total; i++) {
            BufferAcotado b = buffers.obtener(i);
            if (b != null) {
                b.registrarMuestraCiclo();
            }
        }
    }

    @Override
    public synchronized void ejecutarCiclo(long cicloActual) {
        muestrearOcupacionBuffers();
    }

    public ListaEnlazada<BufferAcotado> getBuffers() {
        return buffers;
    }

    public int getCantidadBuffers() {
        return buffers.obtenerTamano();
    }

    public ClusterDistribuido getCluster() {
        return cluster;
    }

}

