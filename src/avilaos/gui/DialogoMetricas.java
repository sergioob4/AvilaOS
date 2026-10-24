package avilaos.gui;

import avilaos.hardware.CPU;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.BufferAcotado;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Diálogo modal para la supervisión de Métricas de Rendimiento del Clúster y de cada Computador.
 *
 * <p>Calcula y presenta conforme a la Sección 8 del proyecto:
 * <ul>
 *   <li>Throughput (procesos completados por ciclo / tiempo).</li>
 *   <li>Porcentaje de utilización del procesador por nodo y global.</li>
 *   <li>Tasa de cumplimiento de tiempos límites (Deadlines).</li>
 *   <li>Tiempo promedio de respuesta de los procesos.</li>
 *   <li>Índice de equidad entre nodos del clúster.</li>
 * </ul>
 * </p>
 */
public class DialogoMetricas extends JDialog {

    private final ClusterDistribuido cluster;

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_CARD_INTERNO = new Color(38, 46, 58);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_MUTED = new Color(175, 190, 215); // Alto contraste
    private static final Color COLOR_VERDE = new Color(34, 197, 94);
    private static final Color COLOR_AZUL = new Color(37, 99, 235);
    private static final Color COLOR_AMBAR = new Color(245, 158, 11);
    private static final Color COLOR_ROJO = new Color(239, 83, 80);

    public DialogoMetricas(Window parent, ClusterDistribuido cluster) {
        super(parent, "ÁvilaOS — Métricas de Rendimiento del Clúster (Sección 8)", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;
        inicializarUI();
    }

    private void inicializarUI() {
        setSize(820, 660);
        setMinimumSize(new Dimension(680, 520));
        setLocationRelativeTo(getParent());
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // Header
        JPanel panelHeader = new JPanel(new BorderLayout(8, 4));
        panelHeader.setBackground(COLOR_CARD);
        panelHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE),
                new EmptyBorder(16, 22, 16, 22)
        ));

        JLabel lblTitulo = new JLabel("MÉTRICAS DE RENDIMIENTO DEL SISTEMA");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(COLOR_TITULO);

        long ciclos = cluster.getRelojGlobal().getCicloActual();
        JLabel lblSub = new JLabel("Evaluación al Ciclo Global: " + ciclos + " | Nodos activos en clúster: " + cluster.getCantidadComputadores());
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(COLOR_MUTED);

        panelHeader.add(lblTitulo, BorderLayout.NORTH);
        panelHeader.add(lblSub, BorderLayout.SOUTH);
        add(panelHeader, BorderLayout.NORTH);

        // Contenido Principal
        JPanel panelContenido = new JPanel();
        panelContenido.setLayout(new BoxLayout(panelContenido, BoxLayout.Y_AXIS));
        panelContenido.setBackground(COLOR_BG);
        panelContenido.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Resumen Global
        panelContenido.add(crearSeccionGlobal(ciclos));
        panelContenido.add(Box.createVerticalStrut(14));

        // 2. Métricas de Sincronización y Buffers Acotados (Requerimiento 4)
        panelContenido.add(crearSeccionSincronizacion());
        panelContenido.add(Box.createVerticalStrut(14));

        // 3. Métricas por Computador
        JLabel lblNodos = new JLabel("Rendimiento Desglosado por Computador");
        lblNodos.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblNodos.setForeground(COLOR_TITULO);
        lblNodos.setAlignmentX(Component.LEFT_ALIGNMENT);
        panelContenido.add(lblNodos);
        panelContenido.add(Box.createVerticalStrut(10));

        JPanel panelNodos = new JPanel(new GridLayout(0, 2, 14, 14));
        panelNodos.setOpaque(false);
        panelNodos.setAlignmentX(Component.LEFT_ALIGNMENT);

        int totalComp = cluster.getCantidadComputadores();
        for (int i = 0; i < totalComp; i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            panelNodos.add(crearTarjetaComputador(comp, ciclos));
        }
        panelContenido.add(panelNodos);

        JScrollPane scroll = new JScrollPane(panelContenido);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(COLOR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);

        // Barra inferior con botón Cerrar estilizado
        JPanel panelBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        panelBottom.setBackground(COLOR_CARD);
        panelBottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));

        VentanaPrincipal.BotonEstilizado btnCerrar = new VentanaPrincipal.BotonEstilizado("Cerrar", COLOR_AZUL, Color.WHITE);
        btnCerrar.setPreferredSize(new Dimension(100, 34));
        btnCerrar.addActionListener(e -> dispose());
        panelBottom.add(btnCerrar);

        add(panelBottom, BorderLayout.SOUTH);
    }

    private JPanel crearSeccionGlobal(long ciclos) {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(COLOR_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(14, 18, 14, 18)
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSec = new JLabel("Rendimiento Consolidado del Clúster");
        lblSec.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSec.setForeground(new Color(96, 165, 250)); // Azul brillante
        p.add(lblSec, BorderLayout.NORTH);

        // Calcular totales
        int exitosos = 0;
        int porDeadline = 0;
        double sumaUsoCpu = 0;
        long sumaTiemposRespuesta = 0;
        int procesosConRespuesta = 0;

        int totalComp = cluster.getCantidadComputadores();
        for (int i = 0; i < totalComp; i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            SistemaOperativo so = comp.getSo();
            exitosos += so.getProcesosCompletadosExitosos();
            porDeadline += so.getProcesosTerminadosPorDeadline();
            sumaUsoCpu += comp.getCpu().getPorcentajeUtilizacion();

            int term = so.getColaTerminados().obtenerTamano();
            for (int t = 0; t < term; t++) {
                Proceso proc = so.getColaTerminados().obtener(t);
                if (proc != null && proc.getPcb().getCicloPrimeraEjecucion() >= 0) {
                    long tr = proc.getPcb().getCicloPrimeraEjecucion() - proc.getPcb().getCicloCreacion();
                    if (tr >= 0) {
                        sumaTiemposRespuesta += tr;
                        procesosConRespuesta++;
                    }
                }
            }
        }

        int totalTerminados = exitosos + porDeadline;
        double tasaExito = totalTerminados > 0 ? ((double) exitosos / totalTerminados) * 100.0 : 100.0;
        double throughput = ciclos > 0 ? ((double) exitosos / ciclos) * 10.0 : 0.0;
        double cpuPromedio = totalComp > 0 ? (sumaUsoCpu / totalComp) : 0.0;
        double tiempoRespPromedio = procesosConRespuesta > 0 ? ((double) sumaTiemposRespuesta / procesosConRespuesta) : 0.0;

        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);

        grid.add(crearMetricaBox("Throughput (x10 c)", String.format("%.2f proc/10c", throughput), COLOR_VERDE));
        grid.add(crearMetricaBox("Uso Medio CPU", String.format("%.1f%%", cpuPromedio), new Color(96, 165, 250)));
        grid.add(crearMetricaBox("Cumplimiento DL", String.format("%.1f%%", tasaExito), tasaExito >= 80 ? COLOR_VERDE : COLOR_ROJO));
        grid.add(crearMetricaBox("Tiempo Resp. Prom.", String.format("%.1f ciclos", tiempoRespPromedio), COLOR_AMBAR));

        grid.add(crearMetricaBox("Completados Exitosos", String.valueOf(exitosos), COLOR_VERDE));
        grid.add(crearMetricaBox("Abortados por Deadline", String.valueOf(porDeadline), porDeadline > 0 ? COLOR_ROJO : COLOR_MUTED));
        grid.add(crearMetricaBox("Total Finalizados", String.valueOf(totalTerminados), COLOR_TITULO));
        grid.add(crearMetricaBox("Latencia Red", cluster.getLatenciaRedCiclos() + " ciclos", COLOR_MUTED));

        p.add(grid, BorderLayout.CENTER);
        return p;
    }

    private JPanel crearTarjetaComputador(Computador comp, long ciclos) {
        JPanel card = new JPanel(new BorderLayout(8, 10));
        card.setBackground(COLOR_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JPanel headerCard = new JPanel(new BorderLayout());
        headerCard.setOpaque(false);

        JLabel lblNom = new JLabel(comp.getNombre() + " [" + comp.getId() + "]");
        lblNom.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblNom.setForeground(COLOR_TITULO);

        SistemaOperativo so = comp.getSo();
        CPU cpu = comp.getCpu();

        JLabel lblUso = new JLabel(String.format("CPU: %.1f%%", cpu.getPorcentajeUtilizacion()));
        lblUso.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUso.setForeground(COLOR_VERDE);

        headerCard.add(lblNom, BorderLayout.WEST);
        headerCard.add(lblUso, BorderLayout.EAST);
        card.add(headerCard, BorderLayout.NORTH);

        JPanel subGrid = new JPanel(new GridLayout(4, 2, 8, 8));
        subGrid.setOpaque(false);

        subGrid.add(crearSubDato("Política:", so.getPolitica().getTipo().getCodigo()));
        subGrid.add(crearSubDato("Quantum:", String.valueOf(so.getQuantum())));
        subGrid.add(crearSubDato("RAM Usada:", comp.getMemoria().getMemoriaUsada() + " MB"));
        subGrid.add(crearSubDato("RAM Libre:", comp.getMemoria().getMemoriaLibre() + " MB"));
        subGrid.add(crearSubDato("Cola Listos:", String.valueOf(so.getColaListos().obtenerTamano())));
        subGrid.add(crearSubDato("Cola Bloqueados:", String.valueOf(so.getColaBloqueados().obtenerTamano())));
        subGrid.add(crearSubDato("Completados:", String.valueOf(so.getProcesosCompletadosExitosos())));
        subGrid.add(crearSubDato("Por Deadline:", String.valueOf(so.getProcesosTerminadosPorDeadline())));

        card.add(subGrid, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearMetricaBox(String titulo, String valor, Color colorValor) {
        JPanel box = new JPanel(new BorderLayout(4, 4));
        box.setBackground(COLOR_CARD_INTERNO);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel lblT = new JLabel(titulo);
        lblT.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblT.setForeground(COLOR_MUTED);

        JLabel lblV = new JLabel(valor);
        lblV.setFont(new Font("Consolas", Font.BOLD, 15));
        lblV.setForeground(colorValor);

        box.add(lblT, BorderLayout.NORTH);
        box.add(lblV, BorderLayout.CENTER);
        return box;
    }

    private JPanel crearSubDato(String label, String valor) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(COLOR_MUTED);

        JLabel v = new JLabel(valor);
        v.setFont(new Font("Segoe UI", Font.BOLD, 11));
        v.setForeground(Color.WHITE);

        p.add(l, BorderLayout.WEST);
        p.add(v, BorderLayout.EAST);
        return p;
    }

    private JPanel crearSeccionSincronizacion() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(COLOR_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(14, 18, 14, 18)
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSec = new JLabel("Sincronización y Buffers Acotados (Requerimiento 4)");
        lblSec.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSec.setForeground(new Color(168, 85, 247));
        p.add(lblSec, BorderLayout.NORTH);

        int totalBuffers = cluster.getGestorSincronizacion().getCantidadBuffers();
        int totalProd = 0;
        int totalCons = 0;
        double sumaOcupProm = 0;

        for (int i = 0; i < totalBuffers; i++) {
            BufferAcotado b = cluster.getGestorSincronizacion().getBuffers().obtener(i);
            if (b != null) {
                totalProd += b.getTotalProducidos();
                totalCons += b.getTotalConsumidos();
                sumaOcupProm += b.calcularOcupacionPromedio();
            }
        }
        double ocupPromGlobal = totalBuffers > 0 ? (sumaOcupProm / totalBuffers) : 0.0;

        JPanel grid = new JPanel(new GridLayout(1, 4, 10, 10));
        grid.setOpaque(false);

        grid.add(crearMetricaBox("Buffers en Clúster", String.valueOf(totalBuffers), new Color(168, 85, 247)));
        grid.add(crearMetricaBox("Ítems Producidos", String.valueOf(totalProd), COLOR_VERDE));
        grid.add(crearMetricaBox("Ítems Consumidos", String.valueOf(totalCons), COLOR_AZUL));
        grid.add(crearMetricaBox("Ocupación Media", String.format("%.2f elem", ocupPromGlobal), COLOR_AMBAR));

        p.add(grid, BorderLayout.CENTER);
        return p;
    }
}
