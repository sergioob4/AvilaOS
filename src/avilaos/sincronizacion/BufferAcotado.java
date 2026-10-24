package avilaos.sincronizacion;

import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoSemaforo;
import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.Proceso;
import avilaos.sistema.SistemaOperativo;

/**
 * Representa un Buffer Acotado (Bounded Buffer) compartido en ÁvilaOS.
 *
 * <p>Implementa la solución al problema clásico del <strong>Productor-Consumidor</strong>
 * coordinado mediante tres semáforos propios de ÁvilaOS:
 * <ul>
 *   <li>{@code semaforoMutex}: Semáforo binario (valor inicial 1) para garantizar exclusión mutua
 *       en la sección crítica durante la inserción o extracción de datos.</li>
 *   <li>{@code semaforoEspacios}: Semáforo de conteo (valor inicial = capacidad) que controla
 *       las posiciones libres restantes en el buffer. Si llega a 0, los productores se bloquean con
 *       {@link MotivoBloqueo#SEMAFORO_VACIO}.</li>
 *   <li>{@code semaforoElementos}: Semáforo de conteo (valor inicial = 0) que controla los
 *       elementos producidos disponibles. Si llega a 0, los consumidores se bloquean con
 *       {@link MotivoBloqueo#SEMAFORO_LLENO}.</li>
 * </ul>
 * </p>
 *
 * <p>El buffer reside físicamente en la memoria RAM de un computador anfitrión específico
 * del clúster, descontando memoria de dicha máquina y aplicando latencia de red a accesos remotos.</p>
 */
public class BufferAcotado {

    /** Identificador único del buffer (ej. "B1", "Buffer-Principal"). */
    private final String id;

    /** Nombre legible o descriptivo del buffer. */
    private final String nombre;

    /** Identificador del computador anfitrión donde reside físicamente en RAM (ej. "C1"). */
    private final String idComputadorAnfitrion;

    /** Capacidad máxima finita de elementos que puede almacenar simultáneamente. */
    private final int capacidad;

    /** Memoria RAM ocupada por el buffer en el computador anfitrión (en MB). */
    private final int memoriaOcupadaMb;

    /** Cola FIFO propia para almacenar los elementos producidos. */
    private final Cola<ElementoBuffer> colaElementos;

    // -------------------------------------------------------------------------
    // Los Tres Semáforos Propios del Algoritmo Productor-Consumidor
    // -------------------------------------------------------------------------

    /** Semáforo binario (Mutex) para garantizar exclusión mutua en sección crítica. */
    private final Semaforo semaforoMutex;

    /** Semáforo de conteo para controlar espacios libres disponibles (vacíos). */
    private final Semaforo semaforoEspacios;

    /** Semáforo de conteo para controlar elementos producidos disponibles (llenos). */
    private final Semaforo semaforoElementos;

    // -------------------------------------------------------------------------
    // Métricas para Evaluación y Reportes
    // -------------------------------------------------------------------------

    /** Total acumulado de elementos que han sido insertados con éxito. */
    private int totalProducidos;

    /** Total acumulado de elementos que han sido extraídos y consumidos. */
    private int totalConsumidos;

    /** Sumatoria histórica de ocupación para calcular el promedio por ciclo. */
    private long sumatoriaOcupacion;

    /** Cantidad de ciclos muestreados para la métrica de ocupación promedio. */
    private long ciclosMuestreados;

    /** Secuenciador para generar IDs únicos de elementos insertados. */
    private int generadorIdElementos;

    /**
     * Construye un nuevo Buffer Acotado.
     *
     * @param id identificador único del buffer
     * @param nombre nombre descriptivo
     * @param idComputadorAnfitrion nodo del clúster donde se aloja físicamente
     * @param capacidad cantidad máxima de elementos (&ge; 1)
     * @param memoriaOcupadaMb memoria RAM en MB que reserva en el anfitrión
     */
    public BufferAcotado(String id, String nombre, String idComputadorAnfitrion,
                         int capacidad, int memoriaOcupadaMb) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del buffer no puede estar vacío.");
        }
        if (capacidad < 1) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser al menos 1.");
        }

        this.id = id;
        this.nombre = nombre != null ? nombre : "Buffer " + id;
        this.idComputadorAnfitrion = idComputadorAnfitrion != null ? idComputadorAnfitrion : "C1";
        this.capacidad = capacidad;
        this.memoriaOcupadaMb = Math.max(0, memoriaOcupadaMb);

        this.colaElementos = new Cola<>();

        // 1. Mutex binario (inicia en 1)
        this.semaforoMutex = new Semaforo("MUTEX_" + id, TipoSemaforo.BINARIO_MUTEX, 1, MotivoBloqueo.SEMAFORO_MUTEX);

        // 2. Semáforo de espacios libres (inicia en N = capacidad)
        this.semaforoEspacios = new Semaforo("ESPACIOS_" + id, TipoSemaforo.CONTEO, capacidad, MotivoBloqueo.SEMAFORO_VACIO);

        // 3. Semáforo de elementos disponibles (inicia en 0)
        this.semaforoElementos = new Semaforo("ELEMENTOS_" + id, TipoSemaforo.CONTEO, 0, MotivoBloqueo.SEMAFORO_LLENO);

        this.totalProducidos = 0;
        this.totalConsumidos = 0;
        this.sumatoriaOcupacion = 0;
        this.ciclosMuestreados = 0;
        this.generadorIdElementos = 0;
    }

    // -------------------------------------------------------------------------
    // Operaciones del Algoritmo Productor-Consumidor (Dijkstra)
    // -------------------------------------------------------------------------

    /**
     * Intenta producir e insertar un nuevo elemento en el buffer acotado siguiendo
     * el protocolo estricto de semáforos de Dijkstra:
     * <pre>
     *   wait(espacios)
     *   wait(mutex)
     *   // Sección Crítica: insertar elemento
     *   signal(mutex)
     *   signal(elementos)
     * </pre>
     *
     * @param proceso proceso productor que intenta la operación
     * @param so sistema operativo del computador donde corre el proceso
     * @param contenido texto descriptivo del dato a producir
     * @param cicloActual ciclo global del reloj
     * @return el {@link ElementoBuffer} producido si tuvo éxito inmediato; {@code null} si el proceso fue bloqueado
     */
    public synchronized ElementoBuffer producir(Proceso proceso, SistemaOperativo so,
                                                String contenido, long cicloActual) {
        // Paso 1: wait(espacios) -> Si buffer lleno, se bloquea con SEMAFORO_VACIO
        boolean tieneEspacio = semaforoEspacios.waitSemaforo(proceso, so);
        if (!tieneEspacio) {
            return null; // Proceso bloqueado esperando espacio
        }

        // Paso 2: wait(mutex) -> Si alguien más está en la sección crítica, se bloquea con SEMAFORO_MUTEX
        boolean tieneMutex = semaforoMutex.waitSemaforo(proceso, so);
        if (!tieneMutex) {
            return null;
        }

        // --- SECCIÓN CRÍTICA ---
        generadorIdElementos++;
        String idProductor = proceso.getId();
        String idCompOrigen = proceso.getPcb().getComputadorAsignado();
        ElementoBuffer nuevoElemento = new ElementoBuffer(generadorIdElementos, contenido, idProductor, idCompOrigen, cicloActual);

        colaElementos.encolar(nuevoElemento);
        totalProducidos++;
        proceso.getPcb().setItemsProcesados(proceso.getPcb().getItemsProcesados() + 1);

        // --- FIN SECCIÓN CRÍTICA ---

        // Paso 3: signal(mutex) -> Libera la exclusión mutua
        semaforoMutex.signalSemaforo(so, cicloActual);

        // Paso 4: signal(elementos) -> Notifica a consumidores en espera que hay un nuevo elemento
        semaforoElementos.signalSemaforo(so, cicloActual);

        return nuevoElemento;
    }

    /**
     * Intenta extraer y consumir un elemento del buffer acotado siguiendo
     * el protocolo estricto de semáforos de Dijkstra:
     * <pre>
     *   wait(elementos)
     *   wait(mutex)
     *   // Sección Crítica: extraer elemento
     *   signal(mutex)
     *   signal(espacios)
     * </pre>
     *
     * @param proceso proceso consumidor que intenta la operación
     * @param so sistema operativo del computador donde corre el proceso
     * @param cicloActual ciclo global del reloj
     * @return el {@link ElementoBuffer} consumido si tuvo éxito inmediato; {@code null} si el proceso fue bloqueado
     */
    public synchronized ElementoBuffer consumir(Proceso proceso, SistemaOperativo so, long cicloActual) {
        // Paso 1: wait(elementos) -> Si buffer vacío, se bloquea con SEMAFORO_LLENO
        boolean hayElementos = semaforoElementos.waitSemaforo(proceso, so);
        if (!hayElementos) {
            return null; // Proceso bloqueado esperando que se produzca algo
        }

        // Paso 2: wait(mutex) -> Exclusión mutua
        boolean tieneMutex = semaforoMutex.waitSemaforo(proceso, so);
        if (!tieneMutex) {
            return null;
        }

        // --- SECCIÓN CRÍTICA ---
        ElementoBuffer elementoExtraido = colaElementos.desencolar();
        elementoExtraido.registrarConsumo(proceso.getId(), cicloActual);
        totalConsumidos++;
        proceso.getPcb().setItemsProcesados(proceso.getPcb().getItemsProcesados() + 1);

        // --- FIN SECCIÓN CRÍTICA ---

        // Paso 3: signal(mutex)
        semaforoMutex.signalSemaforo(so, cicloActual);

        // Paso 4: signal(espacios) -> Notifica a productores en espera que se liberó un slot
        semaforoEspacios.signalSemaforo(so, cicloActual);

        return elementoExtraido;
    }

    // -------------------------------------------------------------------------
    // Muestreo de Métricas de Ocupación
    // -------------------------------------------------------------------------

    /**
     * Registra una muestra de la cantidad de elementos en este ciclo para la métrica
     * de porcentaje promedio de ocupación del buffer exigida por el enunciado.
     */
    public synchronized void registrarMuestraCiclo() {
        sumatoriaOcupacion += colaElementos.obtenerTamano();
        ciclosMuestreados++;
    }

    /**
     * Calcula la ocupación promedio del buffer a lo largo del tiempo.
     *
     * @return promedio de elementos almacenados por ciclo
     */
    public synchronized double calcularOcupacionPromedio() {
        if (ciclosMuestreados == 0) {
            return 0.0;
        }
        return (double) sumatoriaOcupacion / ciclosMuestreados;
    }

    /**
     * Calcula el porcentaje promedio de ocupación con respecto a su capacidad máxima.
     *
     * @return porcentaje de 0.0 a 100.0%
     */
    public synchronized double calcularPorcentajeOcupacionPromedio() {
        if (capacidad == 0) {
            return 0.0;
        }
        return (calcularOcupacionPromedio() / capacidad) * 100.0;
    }

    /**
     * Determina si el computador solicitante reside en la misma máquina física
     * donde se aloja el buffer (acceso local sin latencia) o no (acceso remoto).
     *
     * @param idComputadorSolicitante identificador del computador a comparar
     * @return {@code true} si es acceso local; {@code false} si es remoto
     */
    public boolean esAccesoLocal(String idComputadorSolicitante) {
        if (idComputadorSolicitante == null) {
            return false;
        }
        return idComputadorAnfitrion.equalsIgnoreCase(idComputadorSolicitante);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIdComputadorAnfitrion() {
        return idComputadorAnfitrion;
    }

    public String getComputadorAnfitrion() {
        return idComputadorAnfitrion;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getMemoriaOcupadaMb() {
        return memoriaOcupadaMb;
    }

    public int getMemoriaReservadaMb() {
        return memoriaOcupadaMb;
    }

    public synchronized int getCantidadElementos() {
        return colaElementos.obtenerTamano();
    }

    public synchronized Cola<ElementoBuffer> getColaElementos() {
        return colaElementos;
    }

    public synchronized ListaEnlazada<ElementoBuffer> getElementos() {
        return colaElementos.aLista();
    }

    public Semaforo getSemaforoMutex() {
        return semaforoMutex;
    }

    public Semaforo getSemaforoEspacios() {
        return semaforoEspacios;
    }

    public Semaforo getSemaforoElementos() {
        return semaforoElementos;
    }

    public synchronized int getTotalProducidos() {
        return totalProducidos;
    }

    public synchronized int getTotalConsumidos() {
        return totalConsumidos;
    }

    public synchronized boolean estaLleno() {
        return colaElementos.obtenerTamano() >= capacidad;
    }

    public synchronized boolean estaVacio() {
        return colaElementos.estaVacia();
    }

    @Override
    public synchronized String toString() {
        return String.format("BufferAcotado[%s '%s' @ %s | Elementos: %d/%d | Prod=%d, Cons=%d]",
                id, nombre, idComputadorAnfitrion, colaElementos.obtenerTamano(), capacidad,
                totalProducidos, totalConsumidos);
    }
}
