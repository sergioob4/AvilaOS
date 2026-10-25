package avilaos.sistema;

import avilaos.enums.PoliticaPlanificacion;

/**
 * Parámetros con los que se arranca una simulación (resultado de la vista de configuración).
 *
 * <p>Es un objeto de datos puro: no conoce la interfaz gráfica y sabe construir el
 * {@link ClusterDistribuido} correspondiente.</p>
 */
public class ConfiguracionInicial {

    public static final int MIN_COMPUTADORES = 2;
    public static final int MAX_COMPUTADORES = 6;
    public static final int MIN_RAM_MB = 128;
    public static final int MAX_RAM_MB = 65536;
    public static final int MIN_QUANTUM = 1;
    public static final int MAX_QUANTUM = 20;
    public static final int MIN_LATENCIA = 0;
    public static final int MAX_LATENCIA = 100;
    public static final int MIN_CICLO_MS = 100;
    public static final int MAX_CICLO_MS = 2500;

    private final int[] ramMb;
    private final PoliticaPlanificacion[] politicas;
    private final int[] quantums;
    private final int latenciaRedCiclos;
    private final long duracionCicloMs;

    public ConfiguracionInicial(int[] ramMb, PoliticaPlanificacion[] politicas, int[] quantums,
                                int latenciaRedCiclos, long duracionCicloMs) {
        if (ramMb.length < MIN_COMPUTADORES || politicas.length != ramMb.length || quantums.length != ramMb.length) {
            throw new IllegalArgumentException("Configuración de computadores inconsistente.");
        }
        this.ramMb = ramMb;
        this.politicas = politicas;
        this.quantums = quantums;
        this.latenciaRedCiclos = latenciaRedCiclos;
        this.duracionCicloMs = duracionCicloMs;
    }

    /** Configuración por defecto: 2 computadores, 2048 MB, FCFS, quantum 3, latencia 2, ciclo 1000 ms. */
    public static ConfiguracionInicial porDefecto() {
        return new ConfiguracionInicial(
                new int[]{2048, 2048},
                new PoliticaPlanificacion[]{PoliticaPlanificacion.FCFS, PoliticaPlanificacion.FCFS},
                new int[]{3, 3}, 2, 1000);
    }

    /** Crea el clúster y aplica a cada computador su política, quantum, y los parámetros globales. */
    public ClusterDistribuido crearCluster() {
        ClusterDistribuido cluster = new ClusterDistribuido(ramMb);
        cluster.setLatenciaRedCiclos(latenciaRedCiclos);
        cluster.getRelojGlobal().setDuracionCicloMs(duracionCicloMs);
        for (int i = 0; i < ramMb.length; i++) {
            Computador c = cluster.getComputadores().obtener(i);
            c.cambiarPolitica(politicas[i]);
            c.cambiarQuantum(quantums[i]);
        }
        return cluster;
    }

    public int getCantidadComputadores() {
        return ramMb.length;
    }

    public int getRamMb(int i) {
        return ramMb[i];
    }

    public PoliticaPlanificacion getPolitica(int i) {
        return politicas[i];
    }

    public int getQuantum(int i) {
        return quantums[i];
    }

    public int getLatenciaRedCiclos() {
        return latenciaRedCiclos;
    }

    public long getDuracionCicloMs() {
        return duracionCicloMs;
    }
}
