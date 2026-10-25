package avilaos.sistema;

import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.BufferAcotado;
import avilaos.sincronizacion.GestorSincronizacion;

/**
 * Métricas del productor-consumidor (Requerimiento 8): elementos producidos y consumidos
 * por buffer, y tiempo promedio que los procesos pasan bloqueados en semáforos.
 *
 * <p>El tiempo se mide en ciclos y solo cuenta los bloqueos por semáforo (mutex, lleno, vacío);
 * la espera por latencia de red o E/S no se incluye. El promedio es por proceso productor o
 * consumidor, tanto en ejecución como terminado.</p>
 */
public final class MetricasSincronizacion {

    private MetricasSincronizacion() {
    }

    /** Métricas de un buffer. */
    public static final class PorBuffer {
        private final BufferAcotado buffer;
        private int productores;
        private int consumidores;
        private long ciclosBloqueadoSemaforos;

        PorBuffer(BufferAcotado buffer) {
            this.buffer = buffer;
        }

        public BufferAcotado getBuffer() {
            return buffer;
        }

        public int getProducidos() {
            return buffer.getTotalProducidos();
        }

        public int getConsumidos() {
            return buffer.getTotalConsumidos();
        }

        public int getProductores() {
            return productores;
        }

        public int getConsumidores() {
            return consumidores;
        }

        /** Ciclos totales que los procesos de este buffer pasaron bloqueados en semáforos. */
        public long getCiclosBloqueadoSemaforos() {
            return ciclosBloqueadoSemaforos;
        }

        /** Ciclos promedio bloqueado en semáforos por proceso (0 si no hay procesos). */
        public double getPromedioBloqueadoSemaforos() {
            int n = productores + consumidores;
            return n > 0 ? (double) ciclosBloqueadoSemaforos / n : 0.0;
        }
    }

    /** Métricas de todo el sistema. */
    public static final class Resultado {
        private final PorBuffer[] buffers;
        private final int procesos;
        private final long ciclosBloqueadoSemaforos;

        Resultado(PorBuffer[] buffers, int procesos, long ciclosBloqueadoSemaforos) {
            this.buffers = buffers;
            this.procesos = procesos;
            this.ciclosBloqueadoSemaforos = ciclosBloqueadoSemaforos;
        }

        public int getCantidadBuffers() {
            return buffers.length;
        }

        public PorBuffer getBuffer(int i) {
            return buffers[i];
        }

        public int getTotalProducidos() {
            int t = 0;
            for (PorBuffer b : buffers) {
                t += b.getProducidos();
            }
            return t;
        }

        public int getTotalConsumidos() {
            int t = 0;
            for (PorBuffer b : buffers) {
                t += b.getConsumidos();
            }
            return t;
        }

        /** Cantidad de procesos productores y consumidores considerados. */
        public int getProcesos() {
            return procesos;
        }

        /** Ciclos promedio bloqueado en semáforos por proceso productor/consumidor. */
        public double getPromedioBloqueadoSemaforos() {
            return procesos > 0 ? (double) ciclosBloqueadoSemaforos / procesos : 0.0;
        }
    }

    /** Calcula las métricas actuales del clúster. */
    public static Resultado calcular(ClusterDistribuido cluster) {
        GestorSincronizacion gestor = cluster.getGestorSincronizacion();
        int nb = gestor.getCantidadBuffers();
        PorBuffer[] porBuffer = new PorBuffer[nb];
        for (int i = 0; i < nb; i++) {
            porBuffer[i] = new PorBuffer(gestor.getBuffers().obtener(i));
        }

        int procesos = 0;
        long ciclos = 0;
        int nc = cluster.getCantidadComputadores();
        for (int c = 0; c < nc; c++) {
            Computador comp = cluster.getComputadores().obtener(c);
            SistemaOperativo so = comp.getSo();
            Proceso enCpu = comp.getCpu().getProcesoActual();
            ListaEnlazada<Proceso>[] colas = cuatroColas(so);

            if (enCpu != null && esProdCons(enCpu)) {
                procesos++;
                ciclos += acumular(porBuffer, enCpu);
            }
            for (ListaEnlazada<Proceso> cola : colas) {
                int total = cola.obtenerTamano();
                for (int i = 0; i < total; i++) {
                    Proceso p = cola.obtener(i);
                    if (p != null && esProdCons(p)) {
                        procesos++;
                        ciclos += acumular(porBuffer, p);
                    }
                }
            }
        }
        return new Resultado(porBuffer, procesos, ciclos);
    }

    @SuppressWarnings("unchecked")
    private static ListaEnlazada<Proceso>[] cuatroColas(SistemaOperativo so) {
        ListaEnlazada<Proceso>[] colas = (ListaEnlazada<Proceso>[]) new ListaEnlazada<?>[4];
        colas[0] = so.getColaNuevos();
        colas[1] = so.getColaListos();
        colas[2] = so.getColaBloqueados();
        colas[3] = so.getColaTerminados();
        return colas;
    }

    private static boolean esProdCons(Proceso p) {
        TipoProceso t = p.getPcb().getTipo();
        return t == TipoProceso.PRODUCTOR || t == TipoProceso.CONSUMIDOR;
    }

    /** Suma el proceso a las cuentas de su buffer y devuelve sus ciclos bloqueado en semáforos. */
    private static long acumular(PorBuffer[] porBuffer, Proceso p) {
        PCB pcb = p.getPcb();
        long bloqueado = pcb.getTiempoBloqueadoSemaforos();
        for (PorBuffer pb : porBuffer) {
            if (pb.getBuffer().getId().equalsIgnoreCase(pcb.getIdBuffer())) {
                if (pcb.getTipo() == TipoProceso.PRODUCTOR) {
                    pb.productores++;
                } else {
                    pb.consumidores++;
                }
                pb.ciclosBloqueadoSemaforos += bloqueado;
                break;
            }
        }
        return bloqueado;
    }
}
