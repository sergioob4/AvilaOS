package avilaos.sistema;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Cola;
import avilaos.hardware.RelojGlobal;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.GestorSincronizacion;

/**
 * Representa el clúster distribuido de computadores en ÁvilaOS.
 *
 * <p>Coordina el conjunto de máquinas (mínimo 2 según el Requerimiento 1),
 * el reloj global de sincronización y las políticas de distribución de procesos.</p>
 */
public class ClusterDistribuido {

    /** Reloj global que sincroniza a todos los nodos del clúster. */
    private final RelojGlobal relojGlobal;

    /** Lista enlazada propia de computadores que conforman el clúster. */
    private final ListaEnlazada<Computador> computadores;

    /** Latencia de red global (en ciclos de reloj) para accesos remotos a buffers. */
    private int latenciaRedCiclos;

    /** Gestor de sincronización global para buffers y semáforos del clúster. */
    private final GestorSincronizacion gestorSincronizacion;

    /** Eventos producidos por el núcleo y pendientes de presentar en la interfaz. */
    private final Cola<String> eventos;

    /**
     * Construye un clúster distribuido con una cantidad dada de computadores.
     *
     * @param cantidadComputadores cantidad de computadores a simular (mínimo 2)
     * @param ramPorDefectoMb memoria RAM en MB para cada computador
     */
    public ClusterDistribuido(int cantidadComputadores, int ramPorDefectoMb) {
        this(ramUniforme(cantidadComputadores, ramPorDefectoMb));
    }

    private static int[] ramUniforme(int cantidad, int ramMb) {
        int[] rams = new int[Math.max(0, cantidad)];
        for (int i = 0; i < rams.length; i++) {
            rams[i] = ramMb;
        }
        return rams;
    }

    /**
     * Construye un clúster donde cada computador puede tener una RAM distinta.
     *
     * @param ramPorComputadorMb RAM en MB de cada computador (longitud = cantidad, mínimo 2)
     */
    public ClusterDistribuido(int[] ramPorComputadorMb) {
        int cantidadComputadores = ramPorComputadorMb.length;
        if (cantidadComputadores < 2) {
            throw new IllegalArgumentException("El sistema debe simular como mínimo 2 computadores.");
        }

        this.relojGlobal = new RelojGlobal();
        this.computadores = new ListaEnlazada<>();
        this.eventos = new Cola<>();
        this.latenciaRedCiclos = 2; // Valor por defecto
        this.gestorSincronizacion = new GestorSincronizacion(this);
        this.relojGlobal.registrarComponente(this.gestorSincronizacion);

        for (int i = 1; i <= cantidadComputadores; i++) {
            Computador comp = new Computador("C" + i, "Computador " + i, ramPorComputadorMb[i - 1]);
            comp.getSo().setGestorSincronizacion(this.gestorSincronizacion);
            computadores.agregar(comp);
            relojGlobal.registrarComponente(comp);
        }
    }

    /**
     * Agrega un nuevo computador al clúster de forma dinámica.
     *
     * @param computador instancia de computador a incorporar
     */
    public void agregarComputador(Computador computador) {
        if (computador != null && !computadores.contiene(computador)) {
            computador.getSo().setGestorSincronizacion(this.gestorSincronizacion);
            computadores.agregar(computador);
            relojGlobal.registrarComponente(computador);
        }
    }

    /**
     * Busca y devuelve un computador por su ID (ej. "C1").
     *
     * @param id identificador a buscar
     * @return el computador encontrado, o {@code null} si no existe
     */
    public Computador buscarPorId(String id) {
        int total = computadores.obtenerTamano();
        for (int i = 0; i < total; i++) {
            Computador c = computadores.obtener(i);
            if (c != null && c.getId().equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Asigna un proceso de manera automática al computador con menor carga (balanceo de carga básico).
     *
     * @param proceso proceso a distribuir
     */
    public void asignarProcesoAutomatico(Proceso proceso) {
        if (proceso == null || computadores.estaVacia()) {
            return;
        }

        Computador mejorCandidato = computadores.obtener(0);
        int menorCarga = mejorCandidato.getSo().getColaListos().obtenerTamano()
                + mejorCandidato.getSo().getColaNuevos().obtenerTamano();

        int total = computadores.obtenerTamano();
        for (int i = 1; i < total; i++) {
            Computador actual = computadores.obtener(i);
            int cargaActual = actual.getSo().getColaListos().obtenerTamano()
                    + actual.getSo().getColaNuevos().obtenerTamano();
            if (cargaActual < menorCarga) {
                mejorCandidato = actual;
                menorCarga = cargaActual;
            }
        }

        mejorCandidato.admitirProceso(proceso, relojGlobal.getCicloActual());
    }

    public RelojGlobal getRelojGlobal() {
        return relojGlobal;
    }

    public ListaEnlazada<Computador> getComputadores() {
        return computadores;
    }

    public int getCantidadComputadores() {
        return computadores.obtenerTamano();
    }

    public int getLatenciaRedCiclos() {
        return latenciaRedCiclos;
    }

    public void setLatenciaRedCiclos(int latenciaRedCiclos) {
        this.latenciaRedCiclos = Math.max(0, latenciaRedCiclos);
    }

    public GestorSincronizacion getGestorSincronizacion() {
        return gestorSincronizacion;
    }

    /** Registra un evento del sistema junto con el ciclo global en que ocurrió. */
    public synchronized void registrarEvento(String mensaje) {
        if (mensaje != null && !mensaje.trim().isEmpty()) {
            eventos.encolar("[Ciclo " + relojGlobal.getCicloActual() + "] " + mensaje);
        }
    }

    /** Extrae en orden todos los eventos pendientes para que la interfaz los presente. */
    public synchronized ListaEnlazada<String> drenarEventos() {
        ListaEnlazada<String> pendientes = new ListaEnlazada<>();
        while (!eventos.estaVacia()) {
            pendientes.agregar(eventos.desencolar());
        }
        return pendientes;
    }
}

