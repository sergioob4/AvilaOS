package avilaos.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoEjecucion;
import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.CPU;
import avilaos.hardware.MemoriaPrincipal;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Panel visual que representa el estado completo de un Computador del clúster ("Vista 2").
 *
 * <p>Muestra conforme al enunciado del proyecto:
 * <ul>
 *   <li>CPU: Proceso en ejecución, PC, MAR, prioridad, deadline, modo (Usuario/SO).</li>
 *   <li>Memoria Principal: Ocupación en tiempo real (usada / libre / total y porcentaje).</li>
 *   <li>Planificación: Selector dinámico para cambiar de política y quantum en caliente.</li>
 *   <li>Colas del Sistema Operativo: Nuevos, Listos, Bloqueados (con motivo) y Terminados.</li>
 *   <li>Acceso al PCB completo de cualquier proceso haciendo doble clic.</li>
 * </ul>
 * </p>
 */
public class PanelComputador extends JPanel {

    private final Computador computador;

    // Componentes del encabezado
    private JLabel lblNombreComputador;
    private JLabel lblUtilizacionCpu;

    // Componentes de control de planificación
    private JComboBox<PoliticaPlanificacion> comboPolitica;
    private JSpinner spinQuantum;
    private JLabel lblQuantum;
    private boolean actualizandoPoliticaInternamente = false;

    // Componentes del bloque de CPU
    private JPanel panelCpuBox;
    private JLabel lblEstadoCpu;
    private JLabel lblModoEjecucion;
    private JLabel lblProcesoNombre;
    private JLabel lblProcesoTipo;
    private VentanaPrincipal.BotonEstilizado btnVerPcbCpu;

    // Registros de hardware y métricas de CPU
    private JLabel lblProgramCounter;
    private JLabel lblPrioridad;
    private JLabel lblDeadline;
    private JLabel lblTiempoRestante;
    private JLabel lblMemoriaProceso;
    private JLabel lblTiempoEnCpu;
    private JProgressBar barraProgresoInstrucciones;
    private JProgressBar barraDeadline;

    // Componentes de Memoria Principal (RAM)
    private JLabel lblMemoriaInfo;
    private JProgressBar barraMemoriaRam;

    // Pestañas de Colas del Sistema Operativo
    private JTabbedPane tabbedColas;
    private DefaultListModel<ProcesoItem> modelListos;
    private DefaultListModel<ProcesoItem> modelNuevos;
    private DefaultListModel<ProcesoItem> modelBloqueados;
    private DefaultListModel<ProcesoItem> modelTerminados;
    private JList<ProcesoItem> listListos;
    private JList<ProcesoItem> listNuevos;
    private JList<ProcesoItem> listBloqueados;
    private JList<ProcesoItem> listTerminados;

    // Paleta de colores Dark Theme con alto contraste
    private static final Color COLOR_BG_PANEL = new Color(24, 28, 36);
    private static final Color COLOR_CARD_CPU = new Color(32, 38, 48);
    private static final Color COLOR_TEXTO_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_TEXTO_SECUNDARIO = new Color(180, 195, 218);
    private static final Color COLOR_TEXTO_VALOR = new Color(255, 255, 255);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);

    // Indicadores semánticos
    private static final Color COLOR_ACTIVO = new Color(34, 197, 94);
    private static final Color COLOR_AZUL = new Color(37, 99, 235);
    private static final Color COLOR_DEADLINE_ALERTA = new Color(239, 83, 80);
    private static final Color COLOR_DEADLINE_OK = new Color(168, 85, 247);

    /**
     * Elemento contenedor para mostrar procesos en las JLists con su PCB asociado.
     */
    private static class ProcesoItem {
        final Proceso proceso;
        final String texto;

        ProcesoItem(Proceso proceso, String texto) {
            this.proceso = proceso;
            this.texto = texto;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    public PanelComputador(Computador computador) {
        this.computador = computador;
        inicializarComponentes();
        actualizarVista();
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout(8, 8));
        setBackground(COLOR_BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));

        // ---------------------------------------------------------------------
        // 1. Encabezado del Computador y Control de Planificación
        // ---------------------------------------------------------------------
        JPanel panelNorte = new JPanel(new BorderLayout(8, 6));
        panelNorte.setOpaque(false);

        // 1.1 Nombre y CPU%
        JPanel panelHeader = new JPanel(new BorderLayout(8, 0));
        panelHeader.setOpaque(false);

        lblNombreComputador = new JLabel("  " + computador.getNombre() + " [" + computador.getId() + "]  ");
        lblNombreComputador.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblNombreComputador.setForeground(COLOR_TEXTO_TITULO);

        lblUtilizacionCpu = new JLabel("  CPU: 0.0%  ");
        lblUtilizacionCpu.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUtilizacionCpu.setOpaque(true);
        lblUtilizacionCpu.setBackground(new Color(20, 83, 45));
        lblUtilizacionCpu.setForeground(new Color(187, 247, 208));
        lblUtilizacionCpu.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(34, 197, 94), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));

        panelHeader.add(lblNombreComputador, BorderLayout.WEST);
        panelHeader.add(lblUtilizacionCpu, BorderLayout.EAST);
        panelNorte.add(panelHeader, BorderLayout.NORTH);

        // 1.2 Selector de Política y Quantum en tiempo de ejecución
        JPanel panelPolitica = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panelPolitica.setBackground(COLOR_CARD_CPU);
        panelPolitica.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        JLabel lblPolEtiq = new JLabel("Política:");
        lblPolEtiq.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPolEtiq.setForeground(COLOR_TEXTO_SECUNDARIO);

        comboPolitica = new JComboBox<>(PoliticaPlanificacion.values());
        estilizarComboBox(comboPolitica);
        comboPolitica.setSelectedItem(computador.getSo().getPolitica().getTipo());

        lblQuantum = new JLabel("Quantum:");
        lblQuantum.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblQuantum.setForeground(COLOR_TEXTO_SECUNDARIO);

        spinQuantum = new JSpinner(new SpinnerNumberModel(computador.getSo().getQuantum(), 1, 20, 1));
        spinQuantum.setFont(new Font("Segoe UI", Font.BOLD, 11));
        spinQuantum.setPreferredSize(new Dimension(46, 24));
        JComponent editor = spinQuantum.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setBackground(new Color(24, 28, 36));
            ((JSpinner.DefaultEditor) editor).getTextField().setForeground(Color.WHITE);
        }

        comboPolitica.addActionListener(e -> {
            if (actualizandoPoliticaInternamente) return;
            PoliticaPlanificacion seleccionada = (PoliticaPlanificacion) comboPolitica.getSelectedItem();
            if (seleccionada != null && seleccionada != computador.getSo().getPolitica().getTipo()) {
                computador.cambiarPolitica(seleccionada);
                computador.getSo().registrarEvento(computador.getNombre() + " cambia su política de planificación a "
                        + seleccionada.getCodigo() + ".");
                spinQuantum.setEnabled(seleccionada == PoliticaPlanificacion.ROUND_ROBIN);
                lblQuantum.setEnabled(seleccionada == PoliticaPlanificacion.ROUND_ROBIN);
            }
        });

        spinQuantum.addChangeListener(e -> {
            int q = (int) spinQuantum.getValue();
            int quantumAnterior = computador.getSo().getQuantum();
            computador.cambiarQuantum(q);
            if (q != quantumAnterior) {
                computador.getSo().registrarEvento(computador.getNombre() + " cambia el quantum a " + q + " ciclos.");
            }
        });

        spinQuantum.setEnabled(computador.getSo().getPolitica().getTipo() == PoliticaPlanificacion.ROUND_ROBIN);
        lblQuantum.setEnabled(spinQuantum.isEnabled());

        panelPolitica.add(lblPolEtiq);
        panelPolitica.add(comboPolitica);
        panelPolitica.add(Box.createHorizontalStrut(4));
        panelPolitica.add(lblQuantum);
        panelPolitica.add(spinQuantum);

        panelNorte.add(panelPolitica, BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // 2. Bloque Central: CPU + Memoria RAM
        // ---------------------------------------------------------------------
        JPanel panelCentro = new JPanel();
        panelCentro.setLayout(new BoxLayout(panelCentro, BoxLayout.Y_AXIS));
        panelCentro.setOpaque(false);

        // 2.1 Monitor de CPU
        panelCpuBox = new JPanel();
        panelCpuBox.setLayout(new BoxLayout(panelCpuBox, BoxLayout.Y_AXIS));
        panelCpuBox.setBackground(COLOR_CARD_CPU);
        panelCpuBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Barra superior de CPU (Estado y Modo)
        JPanel panelBarraCpu = new JPanel(new BorderLayout(8, 0));
        panelBarraCpu.setOpaque(false);

        lblEstadoCpu = new JLabel("  CPU OCIOSA  ");
        lblEstadoCpu.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblEstadoCpu.setOpaque(true);
        lblEstadoCpu.setBackground(new Color(40, 48, 62));
        lblEstadoCpu.setForeground(new Color(175, 190, 215));
        lblEstadoCpu.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(75, 85, 99), 1, true),
                new EmptyBorder(3, 6, 3, 6)
        ));

        lblModoEjecucion = new JLabel("  MODO: SISTEMA OPERATIVO  ");
        lblModoEjecucion.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblModoEjecucion.setOpaque(true);
        lblModoEjecucion.setBackground(new Color(146, 64, 14));
        lblModoEjecucion.setForeground(new Color(254, 240, 138));
        lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(245, 158, 11), 1, true),
                new EmptyBorder(3, 6, 3, 6)
        ));

        panelBarraCpu.add(lblEstadoCpu, BorderLayout.WEST);
        panelBarraCpu.add(lblModoEjecucion, BorderLayout.EAST);
        panelCpuBox.add(panelBarraCpu);
        panelCpuBox.add(Box.createVerticalStrut(8));

        // Título del Proceso en Ejecución y botón de ver PCB
        JPanel panelProcCpu = new JPanel(new BorderLayout(8, 0));
        panelProcCpu.setOpaque(false);

        JPanel panelProcTxt = new JPanel(new GridLayout(2, 1, 0, 2));
        panelProcTxt.setOpaque(false);
        lblProcesoNombre = new JLabel("Sin proceso asignado");
        lblProcesoNombre.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblProcesoNombre.setForeground(COLOR_TEXTO_TITULO);

        lblProcesoTipo = new JLabel("Tipo: -");
        lblProcesoTipo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblProcesoTipo.setForeground(COLOR_TEXTO_SECUNDARIO);

        panelProcTxt.add(lblProcesoNombre);
        panelProcTxt.add(lblProcesoTipo);

        btnVerPcbCpu = new VentanaPrincipal.BotonEstilizado("Ver PCB", COLOR_AZUL, Color.WHITE);
        btnVerPcbCpu.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnVerPcbCpu.setBorder(new EmptyBorder(4, 10, 4, 10));
        btnVerPcbCpu.setPreferredSize(new Dimension(75, 26));
        btnVerPcbCpu.setEnabled(false);
        btnVerPcbCpu.addActionListener(e -> {
            Proceso p = computador.getCpu().getProcesoActual();
            if (p != null) {
                new DialogoDetallePCB(SwingUtilities.getWindowAncestor(this), p.getPcb()).setVisible(true);
            }
        });

        panelProcCpu.add(panelProcTxt, BorderLayout.CENTER);
        panelProcCpu.add(btnVerPcbCpu, BorderLayout.EAST);
        panelCpuBox.add(panelProcCpu);

        panelCpuBox.add(Box.createVerticalStrut(8));
        panelCpuBox.add(new JSeparator(SwingConstants.HORIZONTAL));
        panelCpuBox.add(Box.createVerticalStrut(8));

        // Rejilla de Métricas de Hardware (2 filas x 3 columnas simétricas)
        JPanel gridMetricas = new JPanel(new GridLayout(2, 3, 8, 6));
        gridMetricas.setOpaque(false);

        lblProgramCounter = crearLabelValor("0");
        lblPrioridad = crearLabelValor("-");
        lblDeadline = crearLabelValor("-");
        lblTiempoRestante = crearLabelValor("-");
        lblMemoriaProceso = crearLabelValor("-");
        lblTiempoEnCpu = crearLabelValor("-");

        gridMetricas.add(crearCajaDato("PC / MAR:", lblProgramCounter));
        gridMetricas.add(crearCajaDato("Prioridad:", lblPrioridad));
        gridMetricas.add(crearCajaDato("Deadline:", lblDeadline));
        gridMetricas.add(crearCajaDato("Instrucciones:", lblTiempoRestante));
        gridMetricas.add(crearCajaDato("RAM Proceso:", lblMemoriaProceso));
        gridMetricas.add(crearCajaDato("En CPU:", lblTiempoEnCpu));

        panelCpuBox.add(gridMetricas);
        panelCpuBox.add(Box.createVerticalStrut(8));

        // Barras de progreso de CPU
        JPanel panelBarras = new JPanel(new GridLayout(2, 1, 4, 4));
        panelBarras.setOpaque(false);

        JPanel p1 = new JPanel(new BorderLayout(4, 2));
        p1.setOpaque(false);
        JLabel lblProgresoTxt = new JLabel("Instrucciones ejecutadas:");
        lblProgresoTxt.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblProgresoTxt.setForeground(COLOR_TEXTO_SECUNDARIO);
        barraProgresoInstrucciones = new JProgressBar(0, 100);
        barraProgresoInstrucciones.setStringPainted(true);
        barraProgresoInstrucciones.setForeground(COLOR_ACTIVO);
        barraProgresoInstrucciones.setBackground(new Color(21, 25, 32));
        barraProgresoInstrucciones.setFont(new Font("Segoe UI", Font.BOLD, 10));
        p1.add(lblProgresoTxt, BorderLayout.NORTH);
        p1.add(barraProgresoInstrucciones, BorderLayout.CENTER);

        JPanel p2 = new JPanel(new BorderLayout(4, 2));
        p2.setOpaque(false);
        JLabel lblDeadlineTxt = new JLabel("Consumo de Deadline:");
        lblDeadlineTxt.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblDeadlineTxt.setForeground(COLOR_TEXTO_SECUNDARIO);
        barraDeadline = new JProgressBar(0, 100);
        barraDeadline.setStringPainted(true);
        barraDeadline.setForeground(COLOR_DEADLINE_OK);
        barraDeadline.setBackground(new Color(21, 25, 32));
        barraDeadline.setFont(new Font("Segoe UI", Font.BOLD, 10));
        p2.add(lblDeadlineTxt, BorderLayout.NORTH);
        p2.add(barraDeadline, BorderLayout.CENTER);

        panelBarras.add(p1);
        panelBarras.add(p2);
        panelCpuBox.add(panelBarras);

        panelCentro.add(panelCpuBox);
        panelCentro.add(Box.createVerticalStrut(8));

        // 2.2 Ocupación de Memoria Principal (RAM)
        JPanel panelRam = new JPanel(new BorderLayout(4, 4));
        panelRam.setBackground(COLOR_CARD_CPU);
        panelRam.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel panelRamTop = new JPanel(new BorderLayout());
        panelRamTop.setOpaque(false);
        JLabel lblRamTitulo = new JLabel("Memoria Principal (RAM):");
        lblRamTitulo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblRamTitulo.setForeground(COLOR_TEXTO_TITULO);

        lblMemoriaInfo = new JLabel("0 MB / 0 MB (0% libre)");
        lblMemoriaInfo.setFont(new Font("Consolas", Font.BOLD, 11));
        lblMemoriaInfo.setForeground(COLOR_TEXTO_SECUNDARIO);

        panelRamTop.add(lblRamTitulo, BorderLayout.WEST);
        panelRamTop.add(lblMemoriaInfo, BorderLayout.EAST);

        barraMemoriaRam = new JProgressBar(0, 100);
        barraMemoriaRam.setStringPainted(true);
        barraMemoriaRam.setForeground(COLOR_AZUL);
        barraMemoriaRam.setBackground(new Color(21, 25, 32));
        barraMemoriaRam.setFont(new Font("Segoe UI", Font.BOLD, 10));

        panelRam.add(panelRamTop, BorderLayout.NORTH);
        panelRam.add(barraMemoriaRam, BorderLayout.CENTER);

        panelCentro.add(panelRam);
        add(panelCentro, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // 3. Bloque Inferior: Colas del Sistema Operativo (Nuevos, Listos, Bloqueados, Terminados)
        // ---------------------------------------------------------------------
        JPanel panelColasContenedor = new JPanel(new BorderLayout());
        panelColasContenedor.setOpaque(false);
        panelColasContenedor.setPreferredSize(new Dimension(panelColasContenedor.getPreferredSize().width, 170));

        tabbedColas = new JTabbedPane(JTabbedPane.TOP);
        tabbedColas.setUI(new VentanaPrincipal.PestanasOscurasUI());
        tabbedColas.setFont(new Font("Segoe UI", Font.BOLD, 11));
        tabbedColas.setBackground(COLOR_CARD_CPU);
        tabbedColas.setForeground(COLOR_TEXTO_TITULO);

        // Pestaña Listos
        modelListos = new DefaultListModel<>();
        listListos = crearJListProcesos(modelListos);
        tabbedColas.addTab("Listos (0)", new JScrollPane(listListos));

        // Pestaña Nuevos
        modelNuevos = new DefaultListModel<>();
        listNuevos = crearJListProcesos(modelNuevos);
        tabbedColas.addTab("Nuevos (0)", new JScrollPane(listNuevos));

        // Pestaña Bloqueados (con motivo de bloqueo)
        modelBloqueados = new DefaultListModel<>();
        listBloqueados = crearJListProcesos(modelBloqueados);
        tabbedColas.addTab("Bloqueados (0)", new JScrollPane(listBloqueados));

        // Pestaña Terminados
        modelTerminados = new DefaultListModel<>();
        listTerminados = crearJListProcesos(modelTerminados);
        tabbedColas.addTab("Terminados (0)", new JScrollPane(listTerminados));

        panelColasContenedor.add(tabbedColas, BorderLayout.CENTER);
        add(panelColasContenedor, BorderLayout.SOUTH);
    }

    private JList<ProcesoItem> crearJListProcesos(DefaultListModel<ProcesoItem> model) {
        JList<ProcesoItem> list = new JList<>(model);
        list.setFont(new Font("Consolas", Font.PLAIN, 11));
        list.setBackground(new Color(21, 25, 32));
        list.setForeground(COLOR_TEXTO_TITULO);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionBackground(new Color(37, 99, 235));
        list.setSelectionForeground(Color.WHITE);

        // Doble clic para abrir el detalle completo del PCB
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ProcesoItem item = list.getSelectedValue();
                    if (item != null && item.proceso != null) {
                        new DialogoDetallePCB(SwingUtilities.getWindowAncestor(PanelComputador.this), item.proceso.getPcb()).setVisible(true);
                    }
                }
            }
        });

        return list;
    }

    private JPanel crearCajaDato(String etiqueta, JLabel lblValor) {
        JPanel caja = new JPanel(new BorderLayout(1, 1));
        caja.setOpaque(false);

        JLabel lblEtiq = new JLabel(etiqueta);
        lblEtiq.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblEtiq.setForeground(COLOR_TEXTO_SECUNDARIO);

        caja.add(lblEtiq, BorderLayout.NORTH);
        caja.add(lblValor, BorderLayout.CENTER);
        return caja;
    }

    private JLabel crearLabelValor(String textoInicial) {
        JLabel lbl = new JLabel(textoInicial);
        lbl.setFont(new Font("Consolas", Font.BOLD, 12));
        lbl.setForeground(COLOR_TEXTO_VALOR);
        return lbl;
    }

    /**
     * Sincroniza y refresca todos los componentes visuales con el estado actual del Computador.
     */
    public void actualizarVista() {
        CPU cpu = computador.getCpu();
        Proceso p = cpu.getProcesoActual();
        ModoEjecucion modo = cpu.getModoEjecucion();
        SistemaOperativo so = computador.getSo();
        MemoriaPrincipal ram = computador.getMemoria();

        // 1. Porcentaje de utilización de CPU con badge de alto contraste
        lblUtilizacionCpu.setText(String.format("  CPU: %.1f%%  ", cpu.getPorcentajeUtilizacion()));

        // 2. Modo de ejecución (Usuario vs Kernel / SO) con badges sólidos
        if (modo == ModoEjecucion.USUARIO) {
            lblModoEjecucion.setText("  MODO: USUARIO  ");
            lblModoEjecucion.setBackground(new Color(14, 116, 144));
            lblModoEjecucion.setForeground(new Color(207, 250, 254));
            lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(6, 182, 212), 1, true),
                    new EmptyBorder(3, 6, 3, 6)
            ));
        } else {
            lblModoEjecucion.setText("  MODO: SISTEMA OPERATIVO  ");
            lblModoEjecucion.setBackground(new Color(146, 64, 14));
            lblModoEjecucion.setForeground(new Color(254, 240, 138));
            lblModoEjecucion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(245, 158, 11), 1, true),
                    new EmptyBorder(3, 6, 3, 6)
            ));
        }

        // 3. Proceso en CPU
        if (p != null) {
            PCB pcb = p.getPcb();
            lblEstadoCpu.setText("  EJECUTANDO  ");
            lblEstadoCpu.setBackground(new Color(20, 83, 45));
            lblEstadoCpu.setForeground(new Color(187, 247, 208));
            lblEstadoCpu.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(34, 197, 94), 1, true),
                    new EmptyBorder(3, 6, 3, 6)
            ));

            lblProcesoNombre.setText("[" + pcb.getId() + "] " + pcb.getNombre());
            lblProcesoTipo.setText("Tipo: " + pcb.getTipo().getNombreLegible() + " | Estado: " + pcb.getEstado().getNombreLegible());
            btnVerPcbCpu.setEnabled(true);

            lblProgramCounter.setText(String.format("PC:%d (MAR:%d)", cpu.getProgramCounter(), cpu.getMemoryAddressRegister()));
            lblPrioridad.setText("Prio " + pcb.getPrioridad());

            int dlRestante = pcb.getDeadlineRestante();
            int dlInicial = Math.max(1, pcb.getDeadlineInicial());
            lblDeadline.setText(String.format("%d/%d c", dlRestante, dlInicial));

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

            int restante = pcb.getTiempoRestanteEjecucion();
            int total = Math.max(1, pcb.getTiempoEjecucionTotal());
            int completadas = total - restante;
            lblTiempoRestante.setText(String.format("%d rest (%d/%d)", restante, completadas, total));

            int pctInstrucciones = Math.min(100, (int) ((completadas / (double) total) * 100));
            barraProgresoInstrucciones.setValue(pctInstrucciones);
            barraProgresoInstrucciones.setString(pctInstrucciones + "% ejecutado");

            lblMemoriaProceso.setText(pcb.getMemoriaAsignada() + " MB");
            lblTiempoEnCpu.setText(pcb.getTiempoEnCpu() + " c");

        } else {
            lblEstadoCpu.setText("  CPU OCIOSA  ");
            lblEstadoCpu.setBackground(new Color(40, 48, 62));
            lblEstadoCpu.setForeground(new Color(175, 190, 215));
            lblEstadoCpu.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(75, 85, 99), 1, true),
                    new EmptyBorder(3, 6, 3, 6)
            ));

            lblProcesoNombre.setText("Sin proceso en CPU");
            lblProcesoTipo.setText("En espera de procesos en cola de listos");
            btnVerPcbCpu.setEnabled(false);

            lblProgramCounter.setText("PC: 0");
            lblPrioridad.setText("-");
            lblDeadline.setText("-");
            lblDeadline.setForeground(COLOR_TEXTO_VALOR);
            lblTiempoRestante.setText("-");
            lblMemoriaProceso.setText("-");
            lblTiempoEnCpu.setText("-");

            barraProgresoInstrucciones.setValue(0);
            barraProgresoInstrucciones.setString("Inactiva");
            barraDeadline.setValue(0);
            barraDeadline.setString("-");
        }

        // 4. Memoria RAM
        int totalRam = ram.getCapacidadTotal();
        int usadaRam = ram.getMemoriaUsada();
        int libreRam = ram.getMemoriaLibre();
        double pctRam = ram.getPorcentajeOcupacion();

        lblMemoriaInfo.setText(String.format("%d / %d MB (%d MB libre)", usadaRam, totalRam, libreRam));
        barraMemoriaRam.setValue((int) pctRam);
        barraMemoriaRam.setString(String.format("%.1f%% ocupada", pctRam));
        if (pctRam > 85.0) {
            barraMemoriaRam.setForeground(COLOR_DEADLINE_ALERTA);
        } else if (pctRam > 60.0) {
            barraMemoriaRam.setForeground(new Color(245, 158, 11));
        } else {
            barraMemoriaRam.setForeground(COLOR_AZUL);
        }

        // 5. Política de planificación en el combo
        actualizandoPoliticaInternamente = true;
        if (comboPolitica.getSelectedItem() != so.getPolitica().getTipo()) {
            comboPolitica.setSelectedItem(so.getPolitica().getTipo());
        }
        spinQuantum.setValue(so.getQuantum());
        spinQuantum.setEnabled(so.getPolitica().getTipo() == PoliticaPlanificacion.ROUND_ROBIN);
        lblQuantum.setEnabled(spinQuantum.isEnabled());
        actualizandoPoliticaInternamente = false;

        // 6. Actualizar las 4 Colas del Sistema Operativo
        actualizarColaListos(so.getColaListos());
        actualizarColaNuevos(so.getColaNuevos());
        actualizarColaBloqueados(so.getColaBloqueados());
        actualizarColaTerminados(so.getColaTerminados());
    }

    private void actualizarColaListos(ListaEnlazada<Proceso> cola) {
        modelListos.clear();
        int tam = cola.obtenerTamano();
        tabbedColas.setTitleAt(0, "Listos (" + tam + ")");
        for (int i = 0; i < tam; i++) {
            Proceso p = cola.obtener(i);
            if (p != null) {
                PCB pcb = p.getPcb();
                String txt = String.format("[%s] %s | Prio:%d | DL:%d | Rest:%d",
                        pcb.getId(), pcb.getNombre(), pcb.getPrioridad(),
                        pcb.getDeadlineRestante(), pcb.getTiempoRestanteEjecucion());
                modelListos.addElement(new ProcesoItem(p, txt));
            }
        }
    }

    private void actualizarColaNuevos(ListaEnlazada<Proceso> cola) {
        modelNuevos.clear();
        int tam = cola.obtenerTamano();
        tabbedColas.setTitleAt(1, "Nuevos (" + tam + ")");
        for (int i = 0; i < tam; i++) {
            Proceso p = cola.obtener(i);
            if (p != null) {
                PCB pcb = p.getPcb();
                String txt = String.format("[%s] %s | RAM:%d MB | Prio:%d",
                        pcb.getId(), pcb.getNombre(), pcb.getMemoriaAsignada(), pcb.getPrioridad());
                modelNuevos.addElement(new ProcesoItem(p, txt));
            }
        }
    }

    private void actualizarColaBloqueados(ListaEnlazada<Proceso> cola) {
        modelBloqueados.clear();
        int tam = cola.obtenerTamano();
        tabbedColas.setTitleAt(2, "Bloqueados (" + tam + ")");
        for (int i = 0; i < tam; i++) {
            Proceso p = cola.obtener(i);
            if (p != null) {
                PCB pcb = p.getPcb();
                String motivo = pcb.getMotivoBloqueo() != null ? pcb.getMotivoBloqueo().getEtiqueta() : "Desconocido";
                if (pcb.getMotivoBloqueo() == avilaos.enums.MotivoBloqueo.LATENCIA_RED) {
                    motivo = "Latencia Red (" + pcb.getCiclosLatenciaRedRestante() + "c)";
                }
                String txt = String.format("[%s] %s | %s",
                        pcb.getId(), pcb.getNombre(), motivo);
                modelBloqueados.addElement(new ProcesoItem(p, txt));
            }
        }
    }

    private void actualizarColaTerminados(ListaEnlazada<Proceso> cola) {
        modelTerminados.clear();
        int tam = cola.obtenerTamano();
        tabbedColas.setTitleAt(3, "Terminados (" + tam + ")");
        for (int i = 0; i < tam; i++) {
            Proceso p = cola.obtener(i);
            if (p != null) {
                PCB pcb = p.getPcb();
                String resultado = pcb.isTerminoPorDeadline() ? "ABORTADO DEADLINE" : "EXITOSO";
                String txt = String.format("[%s] %s | %s (@c%d)",
                        pcb.getId(), pcb.getNombre(), resultado, pcb.getCicloTerminacion());
                modelTerminados.addElement(new ProcesoItem(p, txt));
            }
        }
    }

    public Computador getComputador() {
        return computador;
    }

    /**
     * Estiliza un JComboBox con tema oscuro sin clases de componentes separadas.
     */
    public static void estilizarComboBox(JComboBox<?> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton() {
                    @Override
                    public void paint(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(28, 35, 48));
                        g2.fillRect(0, 0, getWidth(), getHeight());

                        g2.setColor(new Color(185, 198, 218));
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        int[] xPoints = {cx - 4, cx + 4, cx};
                        int[] yPoints = {cy - 2, cy - 2, cy + 3};
                        g2.fillPolygon(xPoints, yPoints, 3);
                        g2.dispose();
                    }
                };
                btn.setBorder(BorderFactory.createEmptyBorder());
                btn.setContentAreaFilled(false);
                btn.setFocusPainted(false);
                btn.setOpaque(false);
                return btn;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(28, 35, 48));
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.dispose();
            }

            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox) {
                    @Override
                    protected JScrollPane createScroller() {
                        JScrollPane scroller = super.createScroller();
                        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                        scroller.setBorder(new LineBorder(new Color(55, 68, 88), 1));
                        return scroller;
                    }
                };
                popup.setBorder(new LineBorder(new Color(55, 68, 88), 1));
                return popup;
            }
        });

        combo.setBackground(new Color(28, 35, 48));
        combo.setForeground(Color.WHITE);
        combo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        combo.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(55, 68, 88), 1, true),
                new EmptyBorder(3, 8, 3, 4)
        ));

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lbl.setBorder(new EmptyBorder(5, 8, 5, 8));

                if (isSelected) {
                    lbl.setBackground(new Color(37, 99, 235));
                    lbl.setForeground(Color.WHITE);
                } else {
                    lbl.setBackground(new Color(28, 35, 48));
                    lbl.setForeground(Color.WHITE);
                }
                return lbl;
            }
        });
    }
}
