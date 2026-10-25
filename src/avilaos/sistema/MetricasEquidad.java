package avilaos.sistema;

import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;

/**
 * Métrica de equidad (Requerimiento 8) basada en el índice de Jain.
 *
 * <p>Para cada proceso se mide su <em>fracción de servicio</em>:
 * {@code tiempoEnCpu / (tiempoEnCpu + tiempoEnEspera)}, es decir, qué parte del tiempo que
 * estuvo listo para ejecutar realmente tuvo el procesador. Los ciclos bloqueados no cuentan,
 * porque no dependen del planificador. Sobre esas fracciones se calcula
 * {@code J = (Σx)² / (n · Σx²)}:</p>
 * <ul>
 *   <li>{@code J = 1.0}: todos los procesos recibieron el mismo servicio relativo (equidad perfecta).</li>
 *   <li>{@code J → 1/n}: un solo proceso acapara el procesador.</li>
 * </ul>
 *
 * <p>Solo se necesitan {@code n}, {@code Σx} y {@code Σx²}, por lo que se acumulan sin
 * almacenar los valores.</p>
 */
public final class MetricasEquidad {

    private MetricasEquidad() {
    }

    /** Resultado inmutable de una medición. */
    public static final class Resultado {
        private final int procesosMedidos;
        private final double indice;

        Resultado(int procesosMedidos, double indice) {
            this.procesosMedidos = procesosMedidos;
            this.indice = indice;
        }

        /** Cantidad de procesos con al menos un ciclo listo o en CPU. */
        public int getProcesosMedidos() {
            return procesosMedidos;
        }

        /** Índice de Jain entre 1/n y 1.0 (1.0 si hay menos de dos procesos medidos). */
        public double getIndice() {
            return indice;
        }
    }

    /** Acumulador de las sumas necesarias para el índice de Jain. */
    private static final class Acumulador {
        int n;
        double suma;
        double sumaCuadrados;

        void agregar(Proceso p) {
            if (p == null) {
                return;
            }
            PCB pcb = p.getPcb();
            long cpu = pcb.getTiempoEnCpu();
            long espera = pcb.getTiempoEnEspera();
            long total = cpu + espera;
            if (total <= 0) {
                return; // nunca compitió por el procesador
            }
            double x = (double) cpu / total;
            n++;
            suma += x;
            sumaCuadrados += x * x;
        }

        void agregarCola(ListaEnlazada<Proceso> cola) {
            int total = cola.obtenerTamano();
            for (int i = 0; i < total; i++) {
                agregar(cola.obtener(i));
            }
        }

        Resultado resultado() {
            // Con menos de 2 procesos (o sin servicio alguno) no hay desigualdad que medir
            if (n < 2 || sumaCuadrados == 0) {
                return new Resultado(n, 1.0);
            }
            return new Resultado(n, (suma * suma) / (n * sumaCuadrados));
        }
    }

    private static void recolectar(Acumulador acc, Computador c) {
        acc.agregar(c.getCpu().getProcesoActual());
        acc.agregarCola(c.getSo().getColaListos());
        acc.agregarCola(c.getSo().getColaBloqueados());
        acc.agregarCola(c.getSo().getColaTerminados());
    }

    /** Equidad entre los procesos de un computador. */
    public static Resultado calcular(Computador computador) {
        Acumulador acc = new Acumulador();
        recolectar(acc, computador);
        return acc.resultado();
    }

    /** Equidad entre todos los procesos del clúster. */
    public static Resultado calcular(ClusterDistribuido cluster) {
        Acumulador acc = new Acumulador();
        int total = cluster.getCantidadComputadores();
        for (int i = 0; i < total; i++) {
            recolectar(acc, cluster.getComputadores().obtener(i));
        }
        return acc.resultado();
    }
}
