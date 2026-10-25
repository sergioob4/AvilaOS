package avilaos;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.gui.DialogoConfiguracionInicial;
import avilaos.gui.VentanaPrincipal;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.ConfiguracionInicial;
import avilaos.sistema.Computador;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada principal para la aplicación ÁvilaOS.
 *
 * <p>Demuestra y valida:
 * <ul>
 *   <li>Requerimiento 1 (Arquitectura): Clúster distribuido, CPUs, RAM y reloj global sincronizado.</li>
 *   <li>Requerimiento 2 (Procesos): PCB 1:1 con ID único dinámico y control estricto de deadline.</li>
 *   <li>Requerimiento 3 (Planificación):
 *       - Round Robin con quantum configurable y rotación al final de la cola.
 *       - Prioridades Apropiativas con desalojo preventivo ante procesos más prioritarios.
 *       - Earliest Deadline First (EDF) con ordenamiento manual por urgencia.
 *       - Conmutación dinámica de políticas en tiempo de ejecución.
 *   </li>
 * </ul>
 * </p>
 */
public class Main {

    public static void main(String[] args) {
        boolean modoConsola = false;
        for (String arg : args) {
            if ("--console".equalsIgnoreCase(arg) || "--cli".equalsIgnoreCase(arg)) {
                modoConsola = true;
                break;
            }
        }

        if (modoConsola) {
            ejecutarSimulacionConsola();
        } else {
            lanzarInterfazGrafica();
        }
    }

    /**
     * Inicia el simulador con la Interfaz Gráfica de Usuario (GUI) Swing.
     */
    public static void lanzarInterfazGrafica() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            // Vista de configuración inicial: si el usuario cancela, no se inicia la simulación
            ConfiguracionInicial config = new DialogoConfiguracionInicial(ConfiguracionInicial.porDefecto()).mostrar();
            if (config == null) {
                return;
            }

            ClusterDistribuido cluster = config.crearCluster();
            VentanaPrincipal ventana = new VentanaPrincipal(cluster);
            ventana.setVisible(true);
        });
    }

    /**
     * Ejecuta la prueba de validación por consola (útil para scripts y CI/CD).
     */
    public static void ejecutarSimulacionConsola() {
        System.out.println("================================================================================");
        System.out.println("                      Á V I L A O S   -   S I M U L A D O R                     ");
        System.out.println("            Validación de Requerimientos 1, 2 y 3 (Planificación de CPU)       ");
        System.out.println("================================================================================\n");

        // 1. Inicializar Clúster Distribuido (Mínimo 2 computadores requeridos)
        int numComputadores = 2;
        int ramMb = 2048;
        ClusterDistribuido cluster = new ClusterDistribuido(numComputadores, ramMb);

        Computador c1 = cluster.buscarPorId("C1");
        Computador c2 = cluster.buscarPorId("C2");

        // 2. Configurar políticas independientes por computador
        // C1 usará Round Robin con quantum = 2
        c1.cambiarPolitica(PoliticaPlanificacion.ROUND_ROBIN);
        c1.cambiarQuantum(2);

        // C2 usará Prioridades Apropiativas
        c2.cambiarPolitica(PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS);

        System.out.println("1. Configuración de nodos del clúster:");
        System.out.println("   - " + c1.getNombre() + ": Política=" + c1.getSo().getPolitica() + ", Quantum=" + c1.getSo().getQuantum());
        System.out.println("   - " + c2.getNombre() + ": Política=" + c2.getSo().getPolitica() + "\n");

        // 3. Crear procesos para C1 (Validación de Round Robin y rotación por quantum)
        System.out.println("2. Creando carga de trabajo para C1 (Demostración de Round Robin):");
        Proceso p1 = new Proceso("Cálculo Matricial", c1.getId(), TipoProceso.CPU_BOUND, 1, 512, 15, 5);
        Proceso p2 = new Proceso("Compresión Datos", c1.getId(), TipoProceso.CPU_BOUND, 1, 512, 12, 3);
        c1.admitirProceso(p1, 0);
        c1.admitirProceso(p2, 0);
        System.out.println("   - " + p1.getPcb());
        System.out.println("   - " + p2.getPcb());

        // 4. Crear procesos para C2 (Validación de Prioridades Apropiativas)
        System.out.println("\n3. Creando carga inicial para C2 (Demostración de Prioridades):");
        Proceso p3 = new Proceso("Indexador Web (Baja Prioridad)", c2.getId(), TipoProceso.CPU_BOUND, 1, 512, 20, 6);
        c2.admitirProceso(p3, 0);
        System.out.println("   - " + p3.getPcb());

        System.out.println("\n4. Iniciando simulación sincronizada por el Reloj Global:\n");

        // Ciclos 1 a 6
        for (int ciclo = 1; ciclo <= 7; ciclo++) {
            System.out.println("--- [ Ciclo Global " + ciclo + " ] ---");

            // Evento en Ciclo 3: Arriba un proceso de alta prioridad a C2 para probar desalojo preventivo
            if (ciclo == 3) {
                System.out.println("   >>> EVENTO EN C2: Llega P4 con ALTA PRIORIDAD (Prioridad 5 vs 1 de P3 en CPU)");
                Proceso p4 = new Proceso("Alerta Urgente (Alta Prioridad)", c2.getId(), TipoProceso.CPU_BOUND, 5, 256, 10, 2);
                c2.admitirProceso(p4, ciclo);
            }

            // Evento en Ciclo 6: Demostrar cambio dinámico de política en caliente a EDF
            if (ciclo == 6) {
                System.out.println("   >>> EVENTO EN C1: Cambio de política en caliente de Round Robin a EDF");
                c1.cambiarPolitica(PoliticaPlanificacion.EDF);
            }

            // Pulso global de reloj que avanza 1 ciclo en cada computador
            cluster.getRelojGlobal().avanzarUnCiclo();

            // Estado de C1 (Round Robin -> EDF)
            Proceso procCpuC1 = c1.getCpu().getProcesoActual();
            System.out.println("  " + c1.getNombre() + " (" + c1.getSo().getPolitica() + "):");
            System.out.println("    CPU: " + (procCpuC1 != null
                    ? procCpuC1.getId() + " (" + procCpuC1.getNombre() + ") | Ráfaga=" + c1.getCpu().getCiclosEnRafagaActual()
                      + " | PC=" + c1.getCpu().getProgramCounter()
                      + " | Restante=" + procCpuC1.getTiempoRestanteEjecucion()
                      + " | DL=" + procCpuC1.getDeadlineRestante()
                    : "OCIOSA"));
            System.out.println("    Listos: " + c1.getSo().getColaListos());
            System.out.println("    Terminados: " + c1.getSo().getColaTerminados());

            // Estado de C2 (Prioridades Apropiativas)
            Proceso procCpuC2 = c2.getCpu().getProcesoActual();
            System.out.println("  " + c2.getNombre() + " (" + c2.getSo().getPolitica() + "):");
            System.out.println("    CPU: " + (procCpuC2 != null
                    ? procCpuC2.getId() + " (" + procCpuC2.getNombre() + ") | Prio=" + procCpuC2.getPcb().getPrioridad()
                      + " | PC=" + c2.getCpu().getProgramCounter()
                      + " | Restante=" + procCpuC2.getTiempoRestanteEjecucion()
                      + " | DL=" + procCpuC2.getDeadlineRestante()
                    : "OCIOSA"));
            System.out.println("    Listos: " + c2.getSo().getColaListos());
            System.out.println("    Terminados: " + c2.getSo().getColaTerminados() + "\n");
        }

        System.out.println("================================================================================");
        System.out.println("                      RESUMEN DE RESULTADOS DE PLANIFICACIÓN                    ");
        System.out.println("================================================================================");
        System.out.println("C1 - Política final: " + c1.getSo().getPolitica());
        System.out.println("C1 - Procesos completados: " + c1.getSo().getProcesosCompletadosExitosos());
        System.out.println("C1 - Utilización CPU: " + String.format("%.1f", c1.getCpu().getPorcentajeUtilizacion()) + "%");

        System.out.println("\nC2 - Política final: " + c2.getSo().getPolitica());
        System.out.println("C2 - Procesos completados: " + c2.getSo().getProcesosCompletadosExitosos());
        System.out.println("C2 - Utilización CPU: " + String.format("%.1f", c2.getCpu().getPorcentajeUtilizacion()) + "%");
        System.out.println("================================================================================");
    }
}
