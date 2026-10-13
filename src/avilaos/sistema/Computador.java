package avilaos.sistema;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.hardware.CPU;
import avilaos.hardware.MemoriaPrincipal;
import avilaos.interfaces.IComponenteReloj;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.proceso.Proceso;

/**
 * Representa una máquina o nodo del clúster distribuido en ÁvilaOS.
 *
 * <p>Cumple con los requisitos fundamentales del Requerimiento 1:
 * <ul>
 *   <li>Posee como mínimo: CPU, Memoria Principal (RAM) y su propio Sistema Operativo.</li>
 *   <li>Es una unidad reutilizable: todos los computadores del clúster (C1, C2, C3, ...)
 *       son instancias directas de esta misma clase, sin duplicación de código.</li>
 *   <li>Implementa {@link IComponenteReloj} para responder al pulso del reloj global:
 *       en cada ciclo de reloj, avanza exactamente un ciclo de ejecución.</li>
 * </ul>
 * </p>
 */
public class Computador implements IComponenteReloj {

    /** Identificador único del computador (ej. "C1", "C2"). */
    private final String id;

    /** Nombre amigable para la interfaz de usuario (ej. "Computador 1"). */
    private final String nombre;

    /** Unidad Central de Procesamiento propia de la máquina. */
    private final CPU cpu;

    /** Memoria Principal (RAM) propia de la máquina. */
    private final MemoriaPrincipal memoria;

    /** Núcleo del sistema operativo local con su propio planificador y colas. */
    private final SistemaOperativo so;

    /**
     * Construye una nueva instancia reutilizable de computador.
     *
     * @param id identificador único del nodo (ej. "C1")
     * @param nombre nombre legible del computador (ej. "Computador 1")
     * @param tamanoRamMb tamaño total de la memoria RAM en megabytes
     */
    public Computador(String id, String nombre, int tamanoRamMb) {
        this.id = id;
        this.nombre = nombre;
        this.cpu = new CPU("CPU-" + id);
        this.memoria = new MemoriaPrincipal(tamanoRamMb);
        this.so = new SistemaOperativo(id, this.cpu, this.memoria);
    }

    // -------------------------------------------------------------------------
    // Sincronización con el Reloj Global (IComponenteReloj)
    // -------------------------------------------------------------------------

    @Override
    public synchronized void ejecutarCiclo(long cicloActual) {
        // 1. La CPU ejecuta la instrucción correspondiente a este ciclo
        cpu.ejecutarCiclo(cicloActual);

        // 2. El Sistema Operativo ejecuta su ciclo: gestión de deadlines,
        //    liberación de recursos, planificación a largo y corto plazo
        so.ejecutarCicloSO(cicloActual);
    }

    // -------------------------------------------------------------------------
    // Operaciones del Computador
    // -------------------------------------------------------------------------

    /**
     * Crea o ingresa un nuevo proceso a este computador.
     *
     * @param proceso proceso a admitir
     * @param cicloActual ciclo global actual
     */
    public void admitirProceso(Proceso proceso, long cicloActual) {
        so.agregarProcesoNuevo(proceso, cicloActual);
    }

    /**
     * Cambia la política de planificación de este computador de forma independiente.
     *
     * @param nuevaPolitica política a aplicar
     */
    public void cambiarPolitica(IPoliticaPlanificacion nuevaPolitica) {
        so.setPolitica(nuevaPolitica);
    }

    /**
     * Cambia la política de planificación a partir de su tipo enumerado.
     *
     * @param tipoPolitica enum de la política a aplicar
     */
    public void cambiarPolitica(PoliticaPlanificacion tipoPolitica) {
        so.setPolitica(tipoPolitica);
    }

    /**
     * Cambia el quantum de Round Robin para este computador.
     *
     * @param quantum nuevo quantum en ciclos
     */
    public void cambiarQuantum(int quantum) {
        so.setQuantum(quantum);
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

    public CPU getCpu() {
        return cpu;
    }

    public MemoriaPrincipal getMemoria() {
        return memoria;
    }

    public SistemaOperativo getSo() {
        return so;
    }

    @Override
    public String toString() {
        return nombre + " [" + id + " | " + cpu + " | " + memoria + " | " + so.getPolitica() + "]";
    }
}
