package avilaos.sistema;

import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.RelojGlobal;
import avilaos.proceso.Proceso;

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

    /**
     * Construye un clúster distribuido con una cantidad dada de computadores.
     *
     * @param cantidadComputadores cantidad de computadores a simular (mínimo 2)
     * @param ramPorDefectoMb memoria RAM en MB para cada computador
     */
    public ClusterDistribuido(int cantidadComputadores, int ramPorDefectoMb) {
        if (cantidadComputadores < 2) {
            throw new IllegalArgumentException("El sistema debe simular como mínimo 2 computadores.");
        }

        this.relojGlobal = new RelojGlobal();
        this.computadores = new ListaEnlazada<>();
        this.latenciaRedCiclos = 2; // Valor por defecto

        for (int i = 1; i <= cantidadComputadores; i++) {
            Computador comp = new Computador("C" + i, "Computador " + i, ramPorDefectoMb);
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
}
