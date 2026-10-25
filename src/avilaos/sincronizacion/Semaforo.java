package avilaos.sincronizacion;

import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoSemaforo;
import avilaos.estructuras.Cola;
import avilaos.proceso.Proceso;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;

/**
 * Implementación del Semáforo propio de ÁvilaOS para control de concurrencia y sincronización.
 *
 * <p>Cumple con las restricciones y métricas obligatorias del <strong>Requerimiento 4</strong>:
 * <ul>
 *   <li><strong>Sin colecciones nativas:</strong> Gestiona los procesos en espera mediante nuestra
 *       estructura propia {@link Cola} basada en punteros enlazados.</li>
 *   <li><strong>Tipos diferenciados:</strong> Soporta semáforos binarios ({@link TipoSemaforo#BINARIO_MUTEX})
 *       y semáforos de conteo ({@link TipoSemaforo#CONTEO}).</li>
 *   <li><strong>Transición de estados formal:</strong> Al agotarse el recurso, transiciona el proceso
 *       a {@code BLOQUEADO} en el sistema operativo asociando su {@link MotivoBloqueo} específico.</li>
 *   <li><strong>Despertar equitativo (FIFO):</strong> La operación {@code signal} extrae al primer proceso
 *       en espera y solicita al SO su restitución inmediata a la cola de {@code LISTOS}.</li>
 *   <li><strong>Métricas de contención:</strong> Registra el total histórico de bloqueos ocurridos.</li>
 * </ul>
 * </p>
 */
public class Semaforo {

    /** Identificador descriptivo del semáforo (ej. "MUTEX_B1", "ESPACIOS_B1", "ELEMENTOS_B1"). */
    private final String id;

    /** Tipo de semáforo: Binario (Mutex) o Conteo. */
    private final TipoSemaforo tipo;

    /** Valor entero del contador del semáforo. */
    private int valor;

    /** Valor inicial con el que fue configurado el semáforo. */
    private final int valorInicial;

    /** Motivo de bloqueo asignado a los procesos que deban esperar en este semáforo. */
    private final MotivoBloqueo motivoBloqueo;

    /** Cola FIFO propia de procesos bloqueados en espera de este semáforo. */
    private final Cola<Proceso> colaBloqueados;

    /** Métrica: cantidad total acumulada de veces que algún proceso tuvo que bloquearse aquí. */
    private int totalBloqueos;

    /**
     * Construye un nuevo semáforo propio para ÁvilaOS.
     *
     * @param id identificador único del semáforo
     * @param tipo tipo de semáforo (Mutex o Conteo)
     * @param valorInicial valor entero inicial (ej. 1 para mutex, capacidad N para espacios, 0 para elementos)
     * @param motivoBloqueo motivo que se registrará en el PCB al bloquearse el proceso
     */
    public Semaforo(String id, TipoSemaforo tipo, int valorInicial, MotivoBloqueo motivoBloqueo) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del semáforo no puede estar vacío.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de semáforo no puede ser nulo.");
        }
        if (tipo == TipoSemaforo.BINARIO_MUTEX && (valorInicial < 0 || valorInicial > 1)) {
            throw new IllegalArgumentException("Un semáforo binario solo admite valor inicial 0 o 1.");
        }

        this.id = id;
        this.tipo = tipo;
        this.valorInicial = valorInicial;
        this.valor = valorInicial;
        this.motivoBloqueo = motivoBloqueo != null ? motivoBloqueo : MotivoBloqueo.SEMAFORO_MUTEX;
        this.colaBloqueados = new Cola<>();
        this.totalBloqueos = 0;
    }

    /**
     * Operación atómica clásica {@code wait()} / {@code P()}.
     *
     * <p>Decrementa el valor del semáforo:
     * <ul>
     *   <li>Si el recurso está disponible ({@code valor > 0} antes de decrementar),
     *       el proceso continúa sin interrupción.</li>
     *   <li>Si el recurso no está disponible ({@code valor <= 0}), el proceso es encolado
     *       en la {@link Cola} interna del semáforo y transicionado a estado
     *       {@link avilaos.enums.EstadoProceso#BLOQUEADO} en el sistema operativo.</li>
     * </ul>
     * </p>
     *
     * @param proceso proceso que solicita el recurso
     * @param so sistema operativo del nodo donde corre el proceso (puede ser null en pruebas aisladas)
     * @return {@code true} si el recurso fue adquirido inmediatamente; {@code false} si el proceso fue bloqueado
     */
    public synchronized boolean waitSemaforo(Proceso proceso, SistemaOperativo so) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser nulo en waitSemaforo.");
        }

        if (valor > 0) {
            valor--;
            return true;
        }

        // Recurso no disponible: se bloquea
        valor--;
        colaBloqueados.encolar(proceso);
        totalBloqueos++;

        if (so != null) {
            so.bloquearProceso(proceso, motivoBloqueo);
        } else {
            proceso.bloquear(motivoBloqueo);
        }

        return false;
    }

    /**
     * Operación atómica clásica {@code signal()} / {@code V()}.
     *
     * <p>Incrementa el valor del semáforo:
     * <ul>
     *   <li>Si existen procesos en la cola de bloqueados, se desencola al primero
     *       en estricto orden de llegada (FIFO) y se le pide al sistema operativo
     *       que lo transicione de vuelta a la cola de {@code LISTOS}.</li>
     * </ul>
     * </p>
     *
     * @param so sistema operativo anfitrión (puede ser null en pruebas aisladas)
     * @param cicloActual ciclo global de reloj
     * @return el proceso que fue despertado, o {@code null} si no había nadie en espera
     */
    public synchronized Proceso signalSemaforo(SistemaOperativo so, long cicloActual) {
        valor++;

        // Invariante: valor = valorInicial - adquiridos - esperando. Un proceso terminado (p. ej. por
        // deadline) que seguía en la cola ya no va a usar el recurso: se descarta devolviendo su
        // unidad (valor++) y se despierta al siguiente proceso vivo.
        while (!colaBloqueados.estaVacia()) {
            Proceso procesoDespertado = colaBloqueados.desencolar();
            if (procesoDespertado == null) {
                continue;
            }
            if (procesoDespertado.estaTerminado()) {
                valor++;
                continue;
            }
            SistemaOperativo soDestino = resolverSoDelProceso(procesoDespertado, so);
            if (soDestino != null) {
                soDestino.desbloquearProceso(procesoDespertado, cicloActual);
            } else {
                procesoDespertado.pasarAListo(cicloActual);
            }
            return procesoDespertado;
        }

        return null;
    }

    /**
     * El proceso a despertar puede vivir en otro computador que el que ejecuta el signal
     * (acceso remoto al buffer): hay que devolverlo a la cola de su propio sistema operativo.
     */
    private SistemaOperativo resolverSoDelProceso(Proceso proceso, SistemaOperativo soSignal) {
        if (soSignal == null) {
            return null;
        }
        if (soSignal.getIdComputador().equalsIgnoreCase(proceso.getPcb().getComputadorAsignado())) {
            return soSignal;
        }
        if (soSignal.getGestorSincronizacion() != null) {
            Computador c = soSignal.getGestorSincronizacion().getCluster()
                    .buscarPorId(proceso.getPcb().getComputadorAsignado());
            if (c != null) {
                return c.getSo();
            }
        }
        return soSignal;
    }

    public String getId() {
        return id;
    }

    public TipoSemaforo getTipo() {
        return tipo;
    }

    public synchronized int getValor() {
        return valor;
    }

    public int getValorInicial() {
        return valorInicial;
    }

    public MotivoBloqueo getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public synchronized int getCantidadBloqueados() {
        return colaBloqueados.obtenerTamano();
    }

    public synchronized int getCantidadEnEspera() {
        return getCantidadBloqueados();
    }

    public synchronized Cola<Proceso> getColaBloqueados() {
        return colaBloqueados;
    }

    public synchronized String getColaEspera() {
        return colaBloqueados.toString();
    }

    public synchronized int getTotalBloqueos() {
        return totalBloqueos;
    }

    @Override
    public synchronized String toString() {
        return String.format("Semaforo[%s | Tipo: %s | Valor: %d | Bloqueados: %d | TotalBloqueos: %d]",
                id, tipo.getNombreLegible(), valor, colaBloqueados.obtenerTamano(), totalBloqueos);
    }
}
