package avilaos;

import avilaos.enums.EstadoProceso;
import avilaos.enums.TipoProceso;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

/**
 * Punto de entrada principal para la aplicación ÁvilaOS.
 *
 * <p>Demuestra y valida la arquitectura distribuida (Requerimiento 1)
 * y el modelo de procesos con PCB y supervisión estricta de deadline (Requerimiento 2):</p>
 * <ul>
 *   <li>Simulación de al menos 2 computadores como instancias de la misma clase.</li>
 *   <li>Reloj global que sincroniza el avance de ciclos en todo el clúster.</li>
 *   <li>ID de procesos generado dinámicamente y único en todo el clúster.</li>
 *   <li>Admisión basada en RAM y avance lineal de PC y MAR en CPU.</li>
 *   <li>Terminación inmediata cuando el deadline llega a 0 y liberación de memoria.</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                      Á V I L A O S   -   S I M U L A D O R                     ");
        System.out.println("            Validación de Requerimiento 1 (Arquitectura) y 2 (Procesos)        ");
        System.out.println("================================================================================\n");

        // 1. Inicializar Clúster Distribuido con 2 computadores (Requerimiento 1)
        // Cada computador cuenta con CPU, RAM (ej. 2048 MB) y su propio Sistema Operativo
        int numComputadores = 2;
        int ramMb = 2048;
        ClusterDistribuido cluster = new ClusterDistribuido(numComputadores, ramMb);

        Computador c1 = cluster.buscarPorId("C1");
        Computador c2 = cluster.buscarPorId("C2");

        System.out.println("1. Clúster Distribuido inicializado:");
        System.out.println("   - " + c1.getNombre() + " (" + c1.getId() + ") con RAM: " + c1.getMemoria().getCapacidadTotal() + " MB");
        System.out.println("   - " + c2.getNombre() + " (" + c2.getId() + ") con RAM: " + c2.getMemoria().getCapacidadTotal() + " MB\n");

        // 2. Creación de Procesos (Requerimiento 2: PCB con ID único dinámico, nombre, computador, etc.)
        System.out.println("2. Creación de Procesos con ID dinámico único en el sistema distribuido:");

        // Proceso 1: CPU-bound, requiere 4 instrucciones, deadline holgado de 10 ciclos -> Debe terminar exitosamente
        Proceso p1 = new Proceso("Cálculo Matricial", c1.getId(), TipoProceso.CPU_BOUND, 2, 512, 10, 4);
        c1.admitirProceso(p1, 0);
        System.out.println("   - Creado en C1: " + p1.getPcb());

        // Proceso 2: CPU-bound, requiere 6 instrucciones pero deadline corto de 3 ciclos -> Debe terminar por deadline = 0
        Proceso p2 = new Proceso("Compresión Datos", c1.getId(), TipoProceso.CPU_BOUND, 1, 1024, 3, 6);
        c1.admitirProceso(p2, 0);
        System.out.println("   - Creado en C1: " + p2.getPcb());

        // Proceso 3: Asignado a C2 mediante balanceo automático
        Proceso p3 = new Proceso("Indexador Web", c2.getId(), TipoProceso.CPU_BOUND, 3, 512, 8, 3);
        cluster.asignarProcesoAutomatico(p3);
        System.out.println("   - Creado en " + p3.getPcb().getComputadorAsignado() + ": " + p3.getPcb());

        System.out.println("\n3. Iniciando avance de ciclos sincronizados por el Reloj Global:\n");

        // 3. Ejecutar 6 ciclos del reloj global para observar el avance de la CPU y la terminación por deadline
        for (int ciclo = 1; ciclo <= 6; ciclo++) {
            System.out.println("--- [ Ciclo Global " + ciclo + " ] ---");
            cluster.getRelojGlobal().avanzarUnCiclo();

            // Reportar estado de C1
            Proceso procCpuC1 = c1.getCpu().getProcesoActual();
            System.out.println("  " + c1.getNombre() + ":");
            System.out.println("    CPU: " + (procCpuC1 != null
                    ? procCpuC1.getId() + " (" + procCpuC1.getNombre() + ") | PC=" + c1.getCpu().getProgramCounter()
                      + " | MAR=" + c1.getCpu().getMemoryAddressRegister()
                      + " | Restante=" + procCpuC1.getTiempoRestanteEjecucion()
                      + " | DL=" + procCpuC1.getDeadlineRestante()
                    : "OCIOSA"));
            System.out.println("    Listos: " + c1.getSo().getColaListos());
            System.out.println("    Terminados: " + c1.getSo().getColaTerminados());
            System.out.println("    RAM Usada: " + c1.getMemoria().getMemoriaUsada() + " MB / " + c1.getMemoria().getCapacidadTotal() + " MB");

            // Reportar estado de C2
            Proceso procCpuC2 = c2.getCpu().getProcesoActual();
            System.out.println("  " + c2.getNombre() + ":");
            System.out.println("    CPU: " + (procCpuC2 != null
                    ? procCpuC2.getId() + " (" + procCpuC2.getNombre() + ") | PC=" + c2.getCpu().getProgramCounter()
                      + " | MAR=" + c2.getCpu().getMemoryAddressRegister()
                      + " | Restante=" + procCpuC2.getTiempoRestanteEjecucion()
                      + " | DL=" + procCpuC2.getDeadlineRestante()
                    : "OCIOSA"));
            System.out.println("    Listos: " + c2.getSo().getColaListos());
            System.out.println("    Terminados: " + c2.getSo().getColaTerminados());
            System.out.println("    RAM Usada: " + c2.getMemoria().getMemoriaUsada() + " MB / " + c2.getMemoria().getCapacidadTotal() + " MB\n");
        }

        System.out.println("================================================================================");
        System.out.println("                           RESUMEN DE MÉTRICAS Y AUDITORÍA                      ");
        System.out.println("================================================================================");
        System.out.println("C1 - Procesos exitosos: " + c1.getSo().getProcesosCompletadosExitosos());
        System.out.println("C1 - Procesos terminados por vencimiento de deadline: " + c1.getSo().getProcesosTerminadosPorDeadline());
        System.out.println("C1 - Utilización CPU: " + String.format("%.1f", c1.getCpu().getPorcentajeUtilizacion()) + "%");
        System.out.println("C2 - Utilización CPU: " + String.format("%.1f", c2.getCpu().getPorcentajeUtilizacion()) + "%");

        System.out.println("\nDetalle del proceso P2 (evaluación de deadline a 0):");
        System.out.println(" - Estado final: " + p2.getEstado());
        System.out.println(" - ¿Terminó por deadline?: " + p2.getPcb().isTerminoPorDeadline());
        System.out.println(" - ¿Cumplió deadline?: " + p2.getPcb().isCumplioDeadline());
        System.out.println("================================================================================");
    }
}
