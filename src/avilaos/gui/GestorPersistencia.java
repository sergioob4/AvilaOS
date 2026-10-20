package avilaos.gui;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Gestor de persistencia para guardar y cargar escenarios de simulación en formato CSV o JSON básico.
 *
 * <p>Conforme al Requerimiento 7, serializa y deserializa:
 * <ul>
 *   <li>Duración del ciclo de ejecución (ms).</li>
 *   <li>Cantidad de computadores y RAM de cada uno.</li>
 *   <li>Políticas de planificación y quantum iniciales.</li>
 *   <li>Latencia de la red en ciclos.</li>
 *   <li>Carga inicial de procesos con todos sus atributos.</li>
 * </ul>
 * Sin depender de librerías externas complejas, asegurando portabilidad en NetBeans.
 * </p>
 */
public class GestorPersistencia {

    /**
     * Guarda la configuración y procesos actuales en un archivo de texto con formato CSV delimitado.
     *
     * @param archivo destino
     * @param cluster clúster a exportar
     * @throws IOException si ocurre un error de escritura
     */
    public static void exportarEscenario(File archivo, ClusterDistribuido cluster) throws IOException {
        try (FileWriter writer = new FileWriter(archivo)) {
            // Sección de Clúster
            writer.write("# CONFIGURACION_CLUSTER\n");
            writer.write("duracionCicloMs," + cluster.getRelojGlobal().getDuracionCicloMs() + "\n");
            writer.write("latenciaRedCiclos," + cluster.getLatenciaRedCiclos() + "\n");
            writer.write("cantidadComputadores," + cluster.getCantidadComputadores() + "\n");

            // Sección de Computadores
            writer.write("# COMPUTADORES\n");
            writer.write("id,nombre,ramTotalMb,politica,quantum\n");
            int totalComp = cluster.getCantidadComputadores();
            for (int i = 0; i < totalComp; i++) {
                Computador c = cluster.getComputadores().obtener(i);
                writer.write(String.format("%s,%s,%d,%s,%d\n",
                        c.getId(), c.getNombre(), c.getMemoria().getCapacidadTotal(),
                        c.getSo().getPolitica().getTipo().name(), c.getSo().getQuantum()));
            }

            // Sección de Procesos Activos / Nuevos / Listos
            writer.write("# PROCESOS\n");
            writer.write("nombre,computador,tipo,prioridad,memoria,deadline,instrucciones\n");
            for (int i = 0; i < totalComp; i++) {
                Computador c = cluster.getComputadores().obtener(i);

                // Procesos en cola de listos
                int listos = c.getSo().getColaListos().obtenerTamano();
                for (int j = 0; j < listos; j++) {
                    Proceso p = c.getSo().getColaListos().obtener(j);
                    if (p != null) {
                        writer.write(String.format("%s,%s,%s,%d,%d,%d,%d\n",
                                p.getPcb().getNombre(), c.getId(), p.getPcb().getTipo().name(),
                                p.getPcb().getPrioridad(), p.getPcb().getMemoriaAsignada(),
                                p.getPcb().getDeadlineInicial(), p.getPcb().getTiempoEjecucionTotal()));
                    }
                }

                // Procesos en cola de nuevos
                int nuevos = c.getSo().getColaNuevos().obtenerTamano();
                for (int j = 0; j < nuevos; j++) {
                    Proceso p = c.getSo().getColaNuevos().obtener(j);
                    if (p != null) {
                        writer.write(String.format("%s,%s,%s,%d,%d,%d,%d\n",
                                p.getPcb().getNombre(), c.getId(), p.getPcb().getTipo().name(),
                                p.getPcb().getPrioridad(), p.getPcb().getMemoriaAsignada(),
                                p.getPcb().getDeadlineInicial(), p.getPcb().getTiempoEjecucionTotal()));
                    }
                }
            }
        }
    }

    /**
     * Importa procesos desde un archivo y los inyecta al clúster existente.
     *
     * @param archivo archivo a importar
     * @param cluster clúster donde se cargarán los procesos
     * @return cantidad de procesos cargados exitosamente
     * @throws IOException si ocurre un error de lectura
     */
    public static int importarProcesos(File archivo, ClusterDistribuido cluster) throws IOException {
        int cargados = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            boolean seccionProcesos = false;

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                if (linea.startsWith("# PROCESOS")) {
                    seccionProcesos = true;
                    continue;
                } else if (linea.startsWith("#")) {
                    seccionProcesos = false;
                    continue;
                }

                if (seccionProcesos) {
                    if (linea.startsWith("nombre,")) continue; // Cabecera

                    String[] partes = linea.split(",");
                    if (partes.length >= 7) {
                        String nombre = partes[0].trim();
                        String idComp = partes[1].trim();
                        TipoProceso tipo = TipoProceso.valueOf(partes[2].trim());
                        int prioridad = Integer.parseInt(partes[3].trim());
                        int memoria = Integer.parseInt(partes[4].trim());
                        int deadline = Integer.parseInt(partes[5].trim());
                        int instrucciones = Integer.parseInt(partes[6].trim());

                        Proceso p = new Proceso(nombre, idComp, tipo, prioridad, memoria, deadline, instrucciones);

                        Computador comp = cluster.buscarPorId(idComp);
                        if (comp != null) {
                            comp.admitirProceso(p, cluster.getRelojGlobal().getCicloActual());
                            cargados++;
                        } else {
                            cluster.asignarProcesoAutomatico(p);
                            cargados++;
                        }
                    }
                } else if (linea.startsWith("duracionCicloMs,")) {
                    String[] partes = linea.split(",");
                    if (partes.length == 2) {
                        cluster.getRelojGlobal().setDuracionCicloMs(Long.parseLong(partes[1].trim()));
                    }
                } else if (linea.startsWith("latenciaRedCiclos,")) {
                    String[] partes = linea.split(",");
                    if (partes.length == 2) {
                        cluster.setLatenciaRedCiclos(Integer.parseInt(partes[1].trim()));
                    }
                }
            }
        }
        return cargados;
    }
}
