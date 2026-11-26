package avilaos.sistema;

import avilaos.hardware.ConfiguracionDisco;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Cola;
import avilaos.hardware.RelojGlobal;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.GestorSincronizacion;

/** Coordina el computador local, su reloj y el registro de eventos de ÁvilaOS. */
public class ClusterDistribuido {

    /** Reloj global que sincroniza a todos los nodos del clúster. */
    private final RelojGlobal relojGlobal;

    private final Computador computador;

    /** Latencia de red global (en ciclos de reloj) para accesos remotos a buffers. */
    private int latenciaRedCiclos;

    /** Gestor de sincronización global para buffers y semáforos del clúster. */
    private final GestorSincronizacion gestorSincronizacion;

    /** Eventos producidos por el núcleo y pendientes de presentar en la interfaz. */
    private final Cola<String> eventos;

    /**
     * Construye un clúster distribuido con una cantidad dada de computadores.
     *
     * @param cantidadComputadores cantidad de computadores a simular (uno)
     * @param ramPorDefectoMb memoria RAM en MB para cada computador
     */
    public ClusterDistribuido(int cantidadComputadores, int ramPorDefectoMb) {
        this(ramUniforme(cantidadComputadores, ramPorDefectoMb));
    }

    private static int[] ramUniforme(int cantidad, int ramMb) {
        if (cantidad != 1) throw new IllegalArgumentException("ÁvilaOS requiere un solo computador.");
        return new int[]{ramMb};
    }

    /**
     * Construye un clúster donde cada computador puede tener una RAM distinta.
     *
     * @param ramPorComputadorMb RAM en MB de cada computador (longitud = cantidad, uno)
     */
    public ClusterDistribuido(int[] ramPorComputadorMb) {
        this(ramPorComputadorMb, ConfiguracionDisco.PREDETERMINADA);
    }

    public ClusterDistribuido(int[] ramPorComputadorMb, ConfiguracionDisco configDisco) {
        int cantidadComputadores = ramPorComputadorMb.length;
        if (cantidadComputadores != 1) {
            throw new IllegalArgumentException("El sistema debe simular exactamente un computador.");
        }

        this.relojGlobal = new RelojGlobal();
        this.eventos = new Cola<>();
        this.latenciaRedCiclos = 2; // Valor por defecto
        this.gestorSincronizacion = new GestorSincronizacion(this);
        this.relojGlobal.registrarComponente(this.gestorSincronizacion);

        computador = new Computador("C1", "Computador 1", ramPorComputadorMb[0], configDisco);
        computador.getSo().setGestorSincronizacion(gestorSincronizacion);
        relojGlobal.registrarComponente(computador);
    }

    /**
     * Busca y devuelve un computador por su ID (ej. "C1").
     *
     * @param id identificador a buscar
     * @return el computador encontrado, o {@code null} si no existe
     */
    public Computador buscarPorId(String id) {
        return computador.getId().equalsIgnoreCase(id) ? computador : null;
    }

    public void asignarProcesoAutomatico(Proceso proceso) {
        if (proceso != null) computador.admitirProceso(proceso, relojGlobal.getCicloActual());
    }

    public RelojGlobal getRelojGlobal() {
        return relojGlobal;
    }

    public ListaEnlazada<Computador> getComputadores() {
        var copia = new ListaEnlazada<Computador>();
        copia.agregar(computador);
        return copia;
    }

    public int getCantidadComputadores() {
        return 1;
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

