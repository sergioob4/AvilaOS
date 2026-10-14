package avilaos.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoEjecucion;
import avilaos.hardware.CPU;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sistema.Computador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Panel visual reutilizable que representa el estado completo de un Computador del clúster.
 *
 * <p>Enfocado específicamente en la supervisión de hardware y CPU requerida por ÁvilaOS:
 * <ul>
 *   <li>Identificación del Computador (Nombre y ID).</li>
 *   <li>Proceso en ejecución en la CPU (o estado Ocioso).</li>
 *   <li>Modo de ejecución del procesador (Usuario vs. Sistema Operativo).</li>
 *   <li>Contador de Programa (Program Counter - PC) y MAR.</li>
 *   <li>Nivel de Prioridad del proceso activo.</li>
 *   <li>Tiempo límite restante (Deadline) y tasa de urgencia.</li>
 *   <li>Tiempo restante de ejecución e instrucciones completadas.</li>
 *   <li>Porcentaje de utilización de la CPU.</li>
 * </ul>
 * </p>
 */
public class PanelComputador extends JPanel {

    private final Computador computador;

    // Componentes del encabezado
    private JLabel lblNombreComputador;
    private JLabel lblUtilizacionCpu;

    // Componentes del bloque de CPU
    private JPanel panelCpuBox;
    private JLabel lblEstadoCpu;
    private JLabel lblModoEjecucion;
    private JLabel lblProcesoNombre;
    private JLabel lblProcesoTipo;

    // Métricas y registros específicos requeridos
    private JLabel lblProgramCounter;
    private JLabel lblPrioridad;
    private JLabel lblDeadline;
    private JLabel lblTiempoRestante;
    private JLabel lblMemoriaProceso;
    private JLabel lblPoliticaComp;
    private JProgressBar barraProgresoInstrucciones;
    private JProgressBar barraDeadline;

    // Paleta de colores Dark Theme moderno
    private static final Color COLOR_BG_PANEL = new Color(30, 34, 43);
    private static final Color COLOR_CARD_CPU = new Color(40, 46, 58);
    private static final Color COLOR_TEXTO_TITULO = new Color(240, 243, 246);
    private static final Color COLOR_TEXTO_SECUNDARIO = new Color(154, 165, 177);
    private static final Color COLOR_TEXTO_VALOR = new Color(255, 255, 255);
    private static final Color COLOR_BORDE = new Color(55, 65, 81);

    // Indicadores semánticos
    private static final Color COLOR_ACTIVO = new Color(46, 204, 113);
    private static final Color COLOR_OCIOSO = new Color(120, 144, 156);
    private static final Color COLOR_MODO_USUARIO = new Color(0, 188, 212);
    private static final Color COLOR_MODO_KERNEL = new Color(255, 167, 38);
    private static final Color COLOR_DEADLINE_ALERTA = new Color(239, 83, 80);
    private static final Color COLOR_DEADLINE_OK = new Color(156, 39, 176);

    /**
     * Construye el panel visual para un computador específico.
     *
     * @param computador la instancia de computador a monitorear
     */
    public PanelComputador(Computador computador) {
        this.computador = computador;
        inicializarComponentes();
        actualizarVista();
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout(10, 10));
        setBackground(COLOR_BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(14, 14, 14, 14)
        ));

        // 1. Encabezado del Computador
        JPanel panelHeader = new JPanel(new BorderLayout());
        panelHeader.setOpaque(false);

        lblNombreComputador = new JLabel("💻 " + computador.getNombre() + " [" + computador.getId() + "]");
        lblNombreComputador.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblNombreComputador.setForeground(COLOR_TEXTO_TITULO);

        lblUtilizacionCpu = new JLabel("CPU: 0.0%");
        lblUtilizacionCpu.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUtilizacionCpu.setForeground(COLOR_ACTIVO);
        lblUtilizacionCpu.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(46, 204, 113, 100), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));

        panelHeader.add(lblNombreComputador, BorderLayout.WEST);
        panelHeader.add(lblUtilizacionCpu, BorderLayout.EAST);
        add(panelHeader, BorderLayout.NORTH);

        // 2. Bloque Central: Monitor de CPU y Proceso Activo
        panelCpuBox = new JPanel();
        panelCpuBox.setLayout(new BoxLayout(panelCpuBox, BoxLayout.Y_AXIS));
        panelCpuBox.setBackground(COLOR_CARD_CPU);
        panelCpuBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));

        // 2.1 Barra superior de la CPU: Estado (Activa/Ociosa) y Modo (Usuario/SO)
        JPanel panelBarraCpu = new JPanel(new BorderLayout());
        panelBarraCpu.setOpaque(false);

        lblEstadoCpu = new JLabel("⚡ CPU OCIOSA");
        lblEstadoCpu.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstadoCpu.setForeground(COLOR_OCIOSO);

        lblModoEjecucion = new JLabel("MODO: SISTEMA OPERATIVO");
        lblModoEjecucion.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblModoEjecucion.setForeground(COLOR_MODO_KERNEL);
        lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(255, 167, 38, 120), 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));

        panelBarraCpu.add(lblEstadoCpu, BorderLayout.WEST);
        panelBarraCpu.add(lblModoEjecucion, BorderLayout.EAST);
        panelCpuBox.add(panelBarraCpu);
        panelCpuBox.add(Box.createVerticalStrut(10));

        // 2.2 Título del Proceso en Ejecución
        lblProcesoNombre = new JLabel("Sin proceso asignado");
        lblProcesoNombre.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblProcesoNombre.setForeground(COLOR_TEXTO_TITULO);
        panelCpuBox.add(lblProcesoNombre);

        lblProcesoTipo = new JLabel("Tipo: -");
        lblProcesoTipo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblProcesoTipo.setForeground(COLOR_TEXTO_SECUNDARIO);
        panelCpuBox.add(lblProcesoTipo);

        panelCpuBox.add(Box.createVerticalStrut(12));
        panelCpuBox.add(new JSeparator(SwingConstants.HORIZONTAL));
        panelCpuBox.add(Box.createVerticalStrut(10));

        // 2.3 Rejilla con los campos exigidos: Program Counter, Prioridad, Deadline y Restante
        JPanel gridMetricas = new JPanel(new GridLayout(3, 2, 10, 8));
        gridMetricas.setOpaque(false);

        lblProgramCounter = crearLabelValor("0");
        lblPrioridad = crearLabelValor("-");
        lblDeadline = crearLabelValor("-");
        lblTiempoRestante = crearLabelValor("-");
        lblMemoriaProceso = crearLabelValor("-");

        gridMetricas.add(crearCajaDato("Program Counter (PC):", lblProgramCounter));
        gridMetricas.add(crearCajaDato("Nivel de Prioridad:", lblPrioridad));
        gridMetricas.add(crearCajaDato("Tiempo Límite (Deadline):", lblDeadline));
        gridMetricas.add(crearCajaDato("Instrucciones Restantes:", lblTiempoRestante));
        gridMetricas.add(crearCajaDato("Memoria RAM Asignada:", lblMemoriaProceso));

        // Espacio para la política activa del computador
        lblPoliticaComp = crearLabelValor(computador.getSo().getPolitica().getTipo().getCodigo());
        gridMetricas.add(crearCajaDato("Política Planificación:", lblPoliticaComp));

        panelCpuBox.add(gridMetricas);
        panelCpuBox.add(Box.createVerticalStrut(12));

        // 2.4 Barras de Progreso Visuales (Avance de Instrucciones y Urgencia de Deadline)
        JPanel panelBarras = new JPanel(new GridLayout(2, 1, 4, 6));
        panelBarras.setOpaque(false);

        JPanel p1 = new JPanel(new BorderLayout(5, 2));
        p1.setOpaque(false);
        JLabel lblProgresoTxt = new JLabel("Progreso de Instrucciones:");
        lblProgresoTxt.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblProgresoTxt.setForeground(COLOR_TEXTO_SECUNDARIO);
        barraProgresoInstrucciones = new JProgressBar(0, 100);
        barraProgresoInstrucciones.setStringPainted(true);
        barraProgresoInstrucciones.setForeground(COLOR_ACTIVO);
        barraProgresoInstrucciones.setBackground(new Color(25, 29, 36));
        p1.add(lblProgresoTxt, BorderLayout.NORTH);
        p1.add(barraProgresoInstrucciones, BorderLayout.CENTER);

        JPanel p2 = new JPanel(new BorderLayout(5, 2));
        p2.setOpaque(false);
        JLabel lblDeadlineTxt = new JLabel("Consumo de Deadline:");
        lblDeadlineTxt.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblDeadlineTxt.setForeground(COLOR_TEXTO_SECUNDARIO);
        barraDeadline = new JProgressBar(0, 100);
        barraDeadline.setStringPainted(true);
        barraDeadline.setForeground(COLOR_DEADLINE_OK);
        barraDeadline.setBackground(new Color(25, 29, 36));
        p2.add(lblDeadlineTxt, BorderLayout.NORTH);
        p2.add(barraDeadline, BorderLayout.CENTER);

        panelBarras.add(p1);
        panelBarras.add(p2);
        panelCpuBox.add(panelBarras);

        add(panelCpuBox, BorderLayout.CENTER);
    }

    private JPanel crearCajaDato(String etiqueta, JLabel lblValor) {
        JPanel caja = new JPanel(new BorderLayout(2, 2));
        caja.setOpaque(false);

        JLabel lblEtiq = new JLabel(etiqueta);
        lblEtiq.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblEtiq.setForeground(COLOR_TEXTO_SECUNDARIO);

        caja.add(lblEtiq, BorderLayout.NORTH);
        caja.add(lblValor, BorderLayout.CENTER);
        return caja;
    }

    private JLabel crearLabelValor(String textoInicial) {
        JLabel lbl = new JLabel(textoInicial);
        lbl.setFont(new Font("Consolas", Font.BOLD, 13));
        lbl.setForeground(COLOR_TEXTO_VALOR);
        return lbl;
    }

    /**
     * Sincroniza y refresca todos los componentes visuales con el estado actual del Computador y la CPU.
     */
    public void actualizarVista() {
        CPU cpu = computador.getCpu();
        Proceso p = cpu.getProcesoActual();
        ModoEjecucion modo = cpu.getModoEjecucion();

        // 1. Porcentaje de utilización
        lblUtilizacionCpu.setText(String.format("CPU: %.1f%%", cpu.getPorcentajeUtilizacion()));

        // 2. Modo de ejecución (Usuario vs Sistema Operativo)
        if (modo == ModoEjecucion.USUARIO) {
            lblModoEjecucion.setText("MODO: USUARIO");
            lblModoEjecucion.setForeground(COLOR_MODO_USUARIO);
            lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(0, 188, 212, 140), 1, true),
                    new EmptyBorder(2, 6, 2, 6)
            ));
        } else {
            lblModoEjecucion.setText("MODO: SISTEMA OPERATIVO");
            lblModoEjecucion.setForeground(COLOR_MODO_KERNEL);
            lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(255, 167, 38, 140), 1, true),
                    new EmptyBorder(2, 6, 2, 6)
            ));
        }

        // 3. Estado del Proceso en CPU
        if (p != null) {
            PCB pcb = p.getPcb();
            lblEstadoCpu.setText("⚡ EJECUTANDO");
            lblEstadoCpu.setForeground(COLOR_ACTIVO);

            lblProcesoNombre.setText("[" + pcb.getId() + "] " + pcb.getNombre());
            lblProcesoTipo.setText("Tipo: " + pcb.getTipo().getNombreLegible() + " | Estado: " + pcb.getEstado().getNombreLegible());

            // Program Counter
            lblProgramCounter.setText(String.format("PC: %d (MAR: %d)", cpu.getProgramCounter(), cpu.getMemoryAddressRegister()));

            // Prioridad
            lblPrioridad.setText("Prioridad " + pcb.getPrioridad());

            // Deadline restante
            int dlRestante = pcb.getDeadlineRestante();
            int dlInicial = Math.max(1, pcb.getDeadlineInicial());
            lblDeadline.setText(String.format("%d / %d ciclos", dlRestante, dlInicial));

            if (dlRestante <= 2) {
                lblDeadline.setForeground(COLOR_DEADLINE_ALERTA);
                barraDeadline.setForeground(COLOR_DEADLINE_ALERTA);
            } else {
                lblDeadline.setForeground(COLOR_TEXTO_VALOR);
                barraDeadline.setForeground(COLOR_DEADLINE_OK);
            }

            int pctDeadlineConsumido = Math.min(100, (int) (((dlInicial - dlRestante) / (double) dlInicial) * 100));
            barraDeadline.setValue(pctDeadlineConsumido);
            barraDeadline.setString(pctDeadlineConsumido + "% consumido");

            // Instrucciones / Tiempo restante
            int restante = pcb.getTiempoRestanteEjecucion();
            int total = Math.max(1, pcb.getTiempoEjecucionTotal());
            int completadas = total - restante;
            lblTiempoRestante.setText(String.format("%d pendientes (%d/%d)", restante, completadas, total));

            int pctInstrucciones = Math.min(100, (int) ((completadas / (double) total) * 100));
            barraProgresoInstrucciones.setValue(pctInstrucciones);
            barraProgresoInstrucciones.setString(pctInstrucciones + "% ejecutado");

            // Memoria
            lblMemoriaProceso.setText(pcb.getMemoriaAsignada() + " MB");

        } else {
            lblEstadoCpu.setText("💤 CPU OCIOSA");
            lblEstadoCpu.setForeground(COLOR_OCIOSO);

            lblProcesoNombre.setText("Sin proceso en CPU");
            lblProcesoTipo.setText("En espera de procesos en cola de listos");

            lblProgramCounter.setText("PC: 0");
            lblPrioridad.setText("-");
            lblDeadline.setText("-");
            lblDeadline.setForeground(COLOR_TEXTO_VALOR);
            lblTiempoRestante.setText("-");
            lblMemoriaProceso.setText("-");

            barraProgresoInstrucciones.setValue(0);
            barraProgresoInstrucciones.setString("Inactiva");
            barraDeadline.setValue(0);
            barraDeadline.setString("-");
        }

        // Actualizar política de planificación activa
        if (lblPoliticaComp != null && computador.getSo().getPolitica() != null) {
            lblPoliticaComp.setText(computador.getSo().getPolitica().getTipo().getCodigo());
        }
    }

    public Computador getComputador() {
        return computador;
    }
}
