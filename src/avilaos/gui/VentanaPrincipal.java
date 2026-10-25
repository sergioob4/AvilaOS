package avilaos.gui;

import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.RelojGlobal;
import avilaos.interfaces.IComponenteReloj;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;
import avilaos.sincronizacion.BufferAcotado;
import avilaos.sincronizacion.ElementoBuffer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Ventana Principal de ÁvilaOS para la supervisión y control del Clúster Distribuido.
 *
 * <p>Implementa {@link IComponenteReloj} para responder automáticamente a los pulsos
 * del {@link RelojGlobal}, garantizando que la interfaz gráfica sea un observador desacoplado
 * de la lógica del sistema operativo y actualice la CPU, PC, prioridades y deadlines en tiempo real.</p>
 */
public class VentanaPrincipal extends JFrame implements IComponenteReloj {

    private final ClusterDistribuido cluster;
    private final RelojGlobal reloj;

    // Componentes de control superior
    private JLabel lblCicloGlobal;
    private JLabel lblEstadoSimulacion;
    private BotonEstilizado btnIniciar;
    private BotonEstilizado btnPausar;
    private BotonEstilizado btnAvanzarPaso;
    private BotonEstilizado btnNuevoProceso;
    private BotonEstilizado btnMetricas;
    private BotonEstilizado btnGraficoCpu;
    private BotonEstilizado btnGuardar;
    private BotonEstilizado btnCargar;
    private BotonEstilizado btnCargarPrueba;
    private JSlider sliderVelocidad;
    private JLabel lblValorVelocidad;

    // Contenedor de los paneles de cada computador
    private JPanel panelComputadores;
    private final ListaEnlazada<PanelComputador> listaPaneles;

    // Panel de Logs Globales
    private JTabbedPane tabbedLogs;
    private JTextPane logEventos;
    private JTextPane logCiclos;
    private JTextPane logPCB;
    private JTextPane logBuffers;
    private StyledDocument docEventos;
    private StyledDocument docCiclos;
    private StyledDocument docPCB;
    private StyledDocument docBuffers;
    private JLabel lblInfoCluster;

    // Paleta visual Dark Theme con alto contraste
    private static final Color COLOR_BG_VENTANA = new Color(21, 25, 32);
    private static final Color COLOR_BG_HEADER = new Color(28, 34, 44);
    private static final Color COLOR_TEXTO_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_TEXTO_MUTED = new Color(175, 190, 218);
    private static final Color COLOR_PRIMARIO = new Color(37, 99, 235);
    private static final Color COLOR_VERDE = new Color(22, 163, 74);
    private static final Color COLOR_AMBAR = new Color(217, 119, 6);
    private static final Color COLOR_ROJO = new Color(239, 83, 80);
    private static final Color COLOR_MORADO = new Color(124, 58, 237);
    private static final Color COLOR_CYAN = new Color(13, 148, 136);
    private static final Color COLOR_GRIS_BOTON = new Color(51, 65, 85);
    private static final Color COLOR_BG_LOG = new Color(16, 20, 26);
    private static final Color COLOR_BORDE_LOG = new Color(45, 55, 72);

    // Formato de tiempo para timestamps en los logs
    private final SimpleDateFormat sdfLog = new SimpleDateFormat("HH:mm:ss");

    /**
     * Construye la ventana principal para un clúster dado.
     *
     * @param cluster el clúster distribuido a monitorear
     */
    public VentanaPrincipal(ClusterDistribuido cluster) {
        super("ÁvilaOS — Monitor de Clúster Distribuido (Sistemas Operativos)");
        this.cluster = cluster;
        this.reloj = cluster.getRelojGlobal();
        this.listaPaneles = new ListaEnlazada<>();

        configurarVentana();
        inicializarUI();

        // Registrar la GUI como suscriptor del reloj para recibir pulsos síncronos
        reloj.registrarComponente(this);

        actualizarTodo();
        agregarLogEvento("Sistema inicializado con " + cluster.getCantidadComputadores() + " computadores.", new Color(56, 189, 248));
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 920);
        setMinimumSize(new Dimension(1080, 750));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG_VENTANA);
        setLayout(new BorderLayout());
    }

    private void inicializarUI() {
        // ---------------------------------------------------------------------
        // 1. Barra de Control Superior (Diseño en 2 Filas sin superposiciones)
        // ---------------------------------------------------------------------
        JPanel panelTop = new JPanel();
        panelTop.setLayout(new BoxLayout(panelTop, BoxLayout.Y_AXIS));
        panelTop.setBackground(COLOR_BG_HEADER);
        panelTop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 68, 88)),
                new EmptyBorder(10, 20, 10, 20)
        ));

        // ==================== FILA 1: Reloj y Control de Simulación ====================
        JPanel fila1 = new JPanel(new BorderLayout(15, 0));
        fila1.setOpaque(false);

        // Izquierda Fila 1: Logo y Reloj Global
        JPanel panelTituloReloj = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        panelTituloReloj.setOpaque(false);

        JLabel lblTitulo = new JLabel("ÁVILA-OS");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitulo.setForeground(new Color(96, 165, 250));

        lblCicloGlobal = new JLabel("  Ciclo Global: 0  ");
        lblCicloGlobal.setFont(new Font("Consolas", Font.BOLD, 15));
        lblCicloGlobal.setOpaque(true);
        lblCicloGlobal.setBackground(new Color(30, 41, 59));
        lblCicloGlobal.setForeground(Color.WHITE);
        lblCicloGlobal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(59, 130, 246), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        lblEstadoSimulacion = new JLabel("  PAUSADO  ");
        lblEstadoSimulacion.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstadoSimulacion.setOpaque(true);
        lblEstadoSimulacion.setBackground(new Color(120, 53, 15));
        lblEstadoSimulacion.setForeground(new Color(254, 240, 138));
        lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(245, 158, 11), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        panelTituloReloj.add(lblTitulo);
        panelTituloReloj.add(lblCicloGlobal);
        panelTituloReloj.add(lblEstadoSimulacion);

        // Derecha Fila 1: Velocidad, Paso, Iniciar, Pausar
        JPanel panelControlesSim = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelControlesSim.setOpaque(false);

        JPanel panelVelocidad = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panelVelocidad.setOpaque(false);
        JLabel lblVel = new JLabel("Velocidad:");
        lblVel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblVel.setForeground(COLOR_TEXTO_MUTED);

        sliderVelocidad = new JSlider(100, 2500, (int) reloj.getDuracionCicloMs());
        sliderVelocidad.setOpaque(false);
        sliderVelocidad.setPreferredSize(new Dimension(110, 26));

        lblValorVelocidad = new JLabel(sliderVelocidad.getValue() + " ms");
        lblValorVelocidad.setFont(new Font("Consolas", Font.BOLD, 12));
        lblValorVelocidad.setForeground(COLOR_TEXTO_TITULO);
        lblValorVelocidad.setPreferredSize(new Dimension(70, 24));

        sliderVelocidad.addChangeListener(e -> {
            int ms = sliderVelocidad.getValue();
            reloj.setDuracionCicloMs(ms);
            lblValorVelocidad.setText(ms + " ms");
        });

        panelVelocidad.add(lblVel);
        panelVelocidad.add(sliderVelocidad);
        panelVelocidad.add(lblValorVelocidad);

        btnAvanzarPaso = crearBoton("Paso (+1)", COLOR_PRIMARIO);
        btnIniciar = crearBoton("Iniciar", COLOR_VERDE);
        btnPausar = crearBoton("Pausar", COLOR_AMBAR);

        btnIniciar.addActionListener(e -> {
            reloj.iniciar();
            lblEstadoSimulacion.setText("  EN EJECUCIÓN  ");
            lblEstadoSimulacion.setBackground(new Color(20, 83, 45));
            lblEstadoSimulacion.setForeground(new Color(187, 247, 208));
            lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(34, 197, 94), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
            agregarLogEvento("Simulación INICIADA (ejecución automática en tiempo real).", COLOR_VERDE);
        });

        btnPausar.addActionListener(e -> {
            reloj.pausar();
            lblEstadoSimulacion.setText("  PAUSADO  ");
            lblEstadoSimulacion.setBackground(new Color(120, 53, 15));
            lblEstadoSimulacion.setForeground(new Color(254, 240, 138));
            lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(245, 158, 11), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
            agregarLogEvento("Simulación PAUSADA por el usuario.", COLOR_AMBAR);
        });

        btnAvanzarPaso.addActionListener(e -> {
            reloj.pausar();
            lblEstadoSimulacion.setText("  PASO A PASO  ");
            lblEstadoSimulacion.setBackground(new Color(30, 58, 138));
            lblEstadoSimulacion.setForeground(new Color(191, 219, 254));
            lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(59, 130, 246), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
            reloj.avanzarUnCiclo();
            agregarLogEvento("Avance manual: Ciclo " + reloj.getCicloActual(), COLOR_PRIMARIO);
        });

        panelControlesSim.add(panelVelocidad);
        panelControlesSim.add(btnAvanzarPaso);
        panelControlesSim.add(btnIniciar);
        panelControlesSim.add(btnPausar);

        fila1.add(panelTituloReloj, BorderLayout.WEST);
        fila1.add(panelControlesSim, BorderLayout.EAST);

        // ==================== FILA 2: Acciones y Operaciones del Clúster ====================
        JPanel fila2 = new JPanel(new BorderLayout(15, 0));
        fila2.setOpaque(false);

        JLabel lblToolbarInfo = new JLabel("Operaciones y Carga de Procesos:");
        lblToolbarInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblToolbarInfo.setForeground(COLOR_TEXTO_MUTED);

        JPanel panelAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelAcciones.setOpaque(false);

        btnNuevoProceso = crearBoton("Nuevo Proceso", COLOR_CYAN);
        btnMetricas = crearBoton("Metricas Cluster", COLOR_MORADO);
        btnGraficoCpu = crearBoton("Gráfico CPU", COLOR_PRIMARIO);
        btnGuardar = crearBoton("Guardar CSV", COLOR_GRIS_BOTON);
        btnCargar = crearBoton("Cargar CSV", COLOR_GRIS_BOTON);
        btnCargarPrueba = crearBoton("Carga Demo", COLOR_GRIS_BOTON);

        btnNuevoProceso.addActionListener(e -> {
            DialogoCrearProceso dlg = new DialogoCrearProceso(this, cluster);
            dlg.setVisible(true);
        });

        btnMetricas.addActionListener(e -> {
            DialogoMetricas dlg = new DialogoMetricas(this, cluster);
            dlg.setVisible(true);
        });

        btnGraficoCpu.addActionListener(e -> {
            DialogoGraficoCPU dlg = new DialogoGraficoCPU(this, cluster);
            dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dlg.setVisible(true);
        });

        btnGuardar.addActionListener(e -> guardarEscenarioCsv());
        btnCargar.addActionListener(e -> cargarEscenarioCsv());

        btnCargarPrueba.addActionListener(e -> {
            inyectarCargaPrueba();
            agregarLogEvento("Carga de prueba inyectada exitosamente en el clúster.", COLOR_MORADO);
        });

        panelAcciones.add(btnNuevoProceso);
        panelAcciones.add(btnMetricas);
        panelAcciones.add(btnGraficoCpu);
        panelAcciones.add(btnGuardar);
        panelAcciones.add(btnCargar);
        panelAcciones.add(btnCargarPrueba);

        fila2.add(lblToolbarInfo, BorderLayout.WEST);
        fila2.add(panelAcciones, BorderLayout.EAST);

        panelTop.add(fila1);
        panelTop.add(Box.createVerticalStrut(8));
        panelTop.add(fila2);
        add(panelTop, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // 2. Panel Central con Split: Computadores (arriba) y Logs (abajo)
        // ---------------------------------------------------------------------
        JSplitPane splitVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitVertical.setBackground(COLOR_BG_VENTANA);
        splitVertical.setBorder(null);
        splitVertical.setDividerSize(6);
        splitVertical.setResizeWeight(0.65);
        splitVertical.setContinuousLayout(true);
        splitVertical.setDividerLocation(560);

        // 2.1 Contenedor de Computadores
        panelComputadores = new JPanel();
        panelComputadores.setBackground(COLOR_BG_VENTANA);
        panelComputadores.setBorder(new EmptyBorder(16, 16, 8, 16));

        int cantidad = cluster.getCantidadComputadores();
        panelComputadores.setLayout(new GridLayout(1, Math.max(1, cantidad), 16, 16));

        for (int i = 0; i < cantidad; i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            PanelComputador panelComp = new PanelComputador(comp);
            listaPaneles.agregar(panelComp);
            panelComputadores.add(panelComp);
        }

        JScrollPane scrollComputadores = new JScrollPane(panelComputadores);
        scrollComputadores.setBorder(null);
        scrollComputadores.setBackground(COLOR_BG_VENTANA);
        scrollComputadores.getViewport().setBackground(COLOR_BG_VENTANA);
        scrollComputadores.getVerticalScrollBar().setUnitIncrement(14);
        splitVertical.setTopComponent(scrollComputadores);

        // 2.2 Panel de Logs Globales (con pestañas)
        JPanel panelLogsContenedor = crearPanelLogs();
        splitVertical.setBottomComponent(panelLogsContenedor);

        add(splitVertical, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // 3. Barra de Estado Inferior
        // ---------------------------------------------------------------------
        JPanel panelBottom = new JPanel(new BorderLayout());
        panelBottom.setBackground(COLOR_BG_HEADER);
        panelBottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 68, 88)),
                new EmptyBorder(8, 18, 8, 18)
        ));

        lblInfoCluster = new JLabel(String.format("Clúster: %d Computadores Simulados | Latencia Red: %d ciclos | Monitoreo Activo (ÁvilaOS)",
                cluster.getCantidadComputadores(), cluster.getLatenciaRedCiclos()));
        lblInfoCluster.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblInfoCluster.setForeground(COLOR_TEXTO_MUTED);

        panelBottom.add(lblInfoCluster, BorderLayout.WEST);
        add(panelBottom, BorderLayout.SOUTH);
    }

    // =========================================================================
    // Creación del Panel de Logs Globales
    // =========================================================================

    private JPanel crearPanelLogs() {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBackground(COLOR_BG_VENTANA);
        contenedor.setBorder(new EmptyBorder(4, 16, 6, 16));

        // Título del panel de logs
        JLabel lblLogsTitle = new JLabel("  REGISTRO Y LOGS GLOBALES DEL SISTEMA");
        lblLogsTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogsTitle.setForeground(COLOR_TEXTO_MUTED);
        lblLogsTitle.setBorder(new EmptyBorder(4, 0, 6, 0));
        contenedor.add(lblLogsTitle, BorderLayout.NORTH);

        // Tabs: Eventos | Ciclos | PCB
        tabbedLogs = new JTabbedPane(JTabbedPane.TOP);
        tabbedLogs.setUI(new PestanasOscurasUI());
        tabbedLogs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedLogs.setBackground(COLOR_BG_HEADER);
        tabbedLogs.setForeground(COLOR_TEXTO_TITULO);

        // Pestaña 1: Eventos
        logEventos = crearTextPaneLog();
        docEventos = logEventos.getStyledDocument();
        JScrollPane scrollEventos = crearScrollLog(logEventos);
        tabbedLogs.addTab("Eventos del Clúster", scrollEventos);

        // Pestaña 2: Ciclos
        logCiclos = crearTextPaneLog();
        docCiclos = logCiclos.getStyledDocument();
        JScrollPane scrollCiclos = crearScrollLog(logCiclos);
        tabbedLogs.addTab("Trazas por Ciclo", scrollCiclos);

        // Pestaña 3: PCB
        logPCB = crearTextPaneLog();
        docPCB = logPCB.getStyledDocument();
        JScrollPane scrollPCB = crearScrollLog(logPCB);
        tabbedLogs.addTab("Snapshots PCB", scrollPCB);

        // Pestaña 4: Buffers y Sincronización (Requerimiento 4)
        logBuffers = crearTextPaneLog();
        docBuffers = logBuffers.getStyledDocument();
        JScrollPane scrollBuffers = crearScrollLog(logBuffers);
        tabbedLogs.addTab("Buffers y Semáforos", scrollBuffers);

        contenedor.add(tabbedLogs, BorderLayout.CENTER);
        return contenedor;
    }

    private JTextPane crearTextPaneLog() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(COLOR_BG_LOG);
        pane.setForeground(COLOR_TEXTO_TITULO);
        pane.setFont(new Font("Consolas", Font.PLAIN, 12));
        pane.setBorder(new EmptyBorder(6, 8, 6, 8));
        pane.setCaretColor(COLOR_PRIMARIO);
        return pane;
    }

    private JScrollPane crearScrollLog(JTextPane pane) {
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(new LineBorder(COLOR_BORDE_LOG, 1, true));
        scroll.setBackground(COLOR_BG_LOG);
        scroll.getViewport().setBackground(COLOR_BG_LOG);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // =========================================================================
    // Métodos para escribir en los Logs
    // =========================================================================

    private void agregarLogEvento(String mensaje, Color color) {
        agregarLineaLog(docEventos, logEventos, mensaje, color);
    }

    private void agregarLogCiclo(String mensaje, Color color) {
        agregarLineaLog(docCiclos, logCiclos, mensaje, color);
    }

    private void agregarLogPCB(String mensaje, Color color) {
        agregarLineaLog(docPCB, logPCB, mensaje, color);
    }

    private void agregarLineaLog(StyledDocument doc, JTextPane pane, String mensaje, Color color) {
        try {
            String timestamp = "[" + sdfLog.format(new Date()) + "] ";

            SimpleAttributeSet attrTimestamp = new SimpleAttributeSet();
            StyleConstants.setForeground(attrTimestamp, COLOR_TEXTO_MUTED);
            StyleConstants.setFontFamily(attrTimestamp, "Consolas");
            StyleConstants.setFontSize(attrTimestamp, 12);

            SimpleAttributeSet attrMensaje = new SimpleAttributeSet();
            StyleConstants.setForeground(attrMensaje, color);
            StyleConstants.setFontFamily(attrMensaje, "Consolas");
            StyleConstants.setFontSize(attrMensaje, 12);

            doc.insertString(doc.getLength(), timestamp, attrTimestamp);
            doc.insertString(doc.getLength(), mensaje + "\n", attrMensaje);

            limitarLineasLog(doc, 500);
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void limitarLineasLog(StyledDocument doc, int maxLineas) {
        try {
            String texto = doc.getText(0, doc.getLength());
            int count = 0;
            int idx = texto.length();
            while (idx > 0 && count < maxLineas) {
                idx = texto.lastIndexOf('\n', idx - 1);
                count++;
            }
            if (idx > 0 && count >= maxLineas) {
                doc.remove(0, idx + 1);
            }
        } catch (BadLocationException ignored) {
        }
    }

    /**
     * Construye un botón con renderizado antialiasing propio y alto contraste.
     */
    private BotonEstilizado crearBoton(String texto, Color colorFondo) {
        BotonEstilizado btn = new BotonEstilizado(texto, colorFondo, Color.WHITE);
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 14, 34));
        return btn;
    }

    /**
     * Exporta el estado del clúster y procesos a un archivo CSV.
     */
    private void guardarEscenarioCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Configuración de Simulación (CSV)");
        chooser.setSelectedFile(new File("escenario_cluster.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));

        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            try {
                GestorPersistencia.exportarEscenario(file, cluster);
                JOptionPane.showMessageDialog(this,
                        "Escenario guardado exitosamente en:\n" + file.getAbsolutePath(),
                        "Guardado Exitoso", JOptionPane.INFORMATION_MESSAGE);
                agregarLogEvento("Escenario exportado a: " + file.getName(), COLOR_VERDE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error al guardar archivo: " + ex.getMessage(),
                        "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Carga procesos y configuración desde un archivo CSV.
     */
    private void cargarEscenarioCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Cargar Configuración de Simulación (CSV)");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));

        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                int cargados = GestorPersistencia.importarProcesos(file, cluster);
                actualizarTodo();
                JOptionPane.showMessageDialog(this,
                        "Se cargaron exitosamente " + cargados + " procesos desde el archivo.",
                        "Carga Exitosa", JOptionPane.INFORMATION_MESSAGE);
                agregarLogEvento("Se importaron " + cargados + " procesos desde " + file.getName(), COLOR_VERDE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error al cargar archivo: " + ex.getMessage(),
                        "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Inyecta una carga de procesos variados para demostrar de inmediato
     * la visualización del procesador, PC, prioridad y deadlines.
     */
    public void inyectarCargaPrueba() {
        if (cluster.getCantidadComputadores() < 2) {
            return;
        }

        Computador c1 = cluster.getComputadores().obtener(0);
        Computador c2 = cluster.getComputadores().obtener(1);

        c1.cambiarPolitica(PoliticaPlanificacion.ROUND_ROBIN);
        c1.cambiarQuantum(2);

        c2.cambiarPolitica(PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS);

        // Crear Buffer Acotado si aún no existe en el gestor de sincronización
        if (cluster.getGestorSincronizacion().getCantidadBuffers() == 0) {
            cluster.getGestorSincronizacion().crearBuffer("B1", "Buffer Central", c1.getId(), 3, 256);
            agregarLogEvento("Buffer B1 creado en " + c1.getNombre() + " (Capacidad: 3, RAM: 256 MB)", COLOR_AMBAR);
        }

        // Procesos para C1 (Cálculo Matricial y Productor Local)
        Proceso p1 = new Proceso("Cálculo Matricial", c1.getId(), TipoProceso.CPU_BOUND, 2, 512, 18, 6);
        Proceso p2 = new Proceso("Productor Local", c1.getId(), TipoProceso.PRODUCTOR, 3, 256, 35, 8);
        p2.getPcb().setIdBuffer("B1");
        p2.getPcb().setCiclosPorItem(2);
        p2.getPcb().setItemsRequeridos(4);

        c1.admitirProceso(p1, reloj.getCicloActual());
        c1.admitirProceso(p2, reloj.getCicloActual());

        // Procesos para C2 (Indexador Web y Consumidor Remoto con latencia de red)
        Proceso p3 = new Proceso("Indexador Web", c2.getId(), TipoProceso.CPU_BOUND, 1, 512, 22, 7);
        Proceso p4 = new Proceso("Consumidor Remoto", c2.getId(), TipoProceso.CONSUMIDOR, 4, 256, 35, 8);
        p4.getPcb().setIdBuffer("B1");
        p4.getPcb().setCiclosPorItem(2);
        p4.getPcb().setItemsRequeridos(4);

        c2.admitirProceso(p3, reloj.getCicloActual());
        c2.admitirProceso(p4, reloj.getCicloActual());

        actualizarTodo();
    }

    /**
     * Reacciona síncronamente al ciclo de reloj global ({@link IComponenteReloj}).
     * Despacha la actualización visual al Event Dispatch Thread de Swing.
     */
    @Override
    public void ejecutarCiclo(long cicloActual) {
        SwingUtilities.invokeLater(() -> {
            actualizarTodo();
            registrarCicloEnLog(cicloActual);
            registrarPCBsEnLog(cicloActual);
        });
    }

    /**
     * Refresca los datos en pantalla de todos los computadores y componentes globales.
     */
    public void actualizarTodo() {
        lblCicloGlobal.setText("  Ciclo Global: " + reloj.getCicloActual() + "  ");

        int total = listaPaneles.obtenerTamano();
        for (int i = 0; i < total; i++) {
            PanelComputador panel = listaPaneles.obtener(i);
            if (panel != null) {
                panel.actualizarVista();
            }
        }

        actualizarTabBuffers();

        if (lblInfoCluster != null) {
            StringBuilder info = new StringBuilder();
            info.append(String.format("Clúster: %d Computadores Simulados | Latencia Red: %d ciclos",
                    cluster.getCantidadComputadores(), cluster.getLatenciaRedCiclos()));
            int cantBuffers = cluster.getGestorSincronizacion().getCantidadBuffers();
            if (cantBuffers > 0) {
                info.append(" | Buffers: ");
                for (int i = 0; i < cantBuffers; i++) {
                    BufferAcotado b = cluster.getGestorSincronizacion().getBuffers().obtener(i);
                    if (b != null) {
                        info.append(String.format("[%s @ %s: %d/%d (Ocup: %.1f)] ",
                                b.getId(), b.getComputadorAnfitrion(), b.getCantidadElementos(), b.getCapacidad(), b.calcularOcupacionPromedio()));
                    }
                }
            }
            info.append("| Monitoreo Activo (ÁvilaOS)");
            lblInfoCluster.setText(info.toString());
        }
    }

    private void actualizarTabBuffers() {
        if (docBuffers == null) {
            return;
        }
        try {
            docBuffers.remove(0, docBuffers.getLength());
            int total = cluster.getGestorSincronizacion().getCantidadBuffers();
            if (total == 0) {
                docBuffers.insertString(0, "No hay buffers acotados configurados en el clúster.\n(Use 'Carga Demo' para inicializar el buffer B1 o defínalo en el sistema).", null);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("========================================================================================\n");
            sb.append("                 ESTADO DE BUFFERS ACOTADOS Y SEMÁFOROS (REQUERIMIENTO 4)               \n");
            sb.append("========================================================================================\n\n");

            for (int i = 0; i < total; i++) {
                BufferAcotado b = cluster.getGestorSincronizacion().getBuffers().obtener(i);
                if (b == null) continue;

                sb.append(String.format("📌 BUFFER: %s - \"%s\"\n", b.getId(), b.getNombre()));
                sb.append(String.format("   • Computador Anfitrión: %s (Memoria RAM reservada: %d MB)\n", b.getComputadorAnfitrion(), b.getMemoriaReservadaMb()));
                sb.append(String.format("   • Capacidad: %d elementos | Ocupación Actual: %d / %d (%.1f%%)\n",
                        b.getCapacidad(), b.getCantidadElementos(), b.getCapacidad(),
                        ((double) b.getCantidadElementos() / b.getCapacidad()) * 100.0));
                sb.append(String.format("   • Ocupación Promedio: %.2f elementos\n", b.calcularOcupacionPromedio()));
                sb.append(String.format("   • Total Producidos: %d | Total Consumidos: %d\n", b.getTotalProducidos(), b.getTotalConsumidos()));

                sb.append("   • Semáforos de Dijkstra:\n");
                sb.append(String.format("     - Mutex:     Valor=%d | Procesos en espera=%d (%s)\n",
                        b.getSemaforoMutex().getValor(), b.getSemaforoMutex().getCantidadEnEspera(),
                        b.getSemaforoMutex().getColaEspera()));
                sb.append(String.format("     - Espacios:  Valor=%d | Productores bloqueados=%d (%s)\n",
                        b.getSemaforoEspacios().getValor(), b.getSemaforoEspacios().getCantidadEnEspera(),
                        b.getSemaforoEspacios().getColaEspera()));
                sb.append(String.format("     - Elementos: Valor=%d | Consumidores bloqueados=%d (%s)\n",
                        b.getSemaforoElementos().getValor(), b.getSemaforoElementos().getCantidadEnEspera(),
                        b.getSemaforoElementos().getColaEspera()));

                sb.append("   • Elementos Residentes en Buffer:\n");
                if (b.getCantidadElementos() == 0) {
                    sb.append("     (Buffer vacío)\n");
                } else {
                    for (int k = 0; k < b.getCantidadElementos(); k++) {
                        ElementoBuffer item = b.getElementos().obtener(k);
                        if (item != null) {
                            sb.append(String.format("     [%d] %s (Generado por %s en %s @ T=%d)\n",
                                    k + 1, item.getContenido(), item.getIdProcesoProductor(),
                                    item.getIdComputadorOrigen(), item.getCicloProduccion()));
                        }
                    }
                }
                sb.append("----------------------------------------------------------------------------------------\n\n");
            }

            docBuffers.insertString(0, sb.toString(), null);
        } catch (BadLocationException ignored) {
        }
    }

    // =========================================================================
    // Registro automático en los Logs por cada ciclo
    // =========================================================================

    private void registrarCicloEnLog(long cicloActual) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CICLO ").append(cicloActual).append(" ===");

        int totalComp = cluster.getCantidadComputadores();
        for (int i = 0; i < totalComp; i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            SistemaOperativo so = comp.getSo();
            Proceso enCpu = comp.getCpu().getProcesoActual();

            sb.append("  |  ").append(comp.getId()).append(": ");
            if (enCpu != null) {
                PCB pcb = enCpu.getPcb();
                sb.append("CPU=[").append(pcb.getId()).append("] ").append(pcb.getNombre())
                  .append(" PC:").append(comp.getCpu().getProgramCounter())
                  .append(" DL:").append(pcb.getDeadlineRestante()).append("/").append(pcb.getDeadlineInicial());
            } else {
                sb.append("CPU=OCIOSA");
            }
            sb.append(" | Listos:").append(so.getColaListos().obtenerTamano())
              .append(" Bloq:").append(so.getColaBloqueados().obtenerTamano())
              .append(" Term:").append(so.getColaTerminados().obtenerTamano());
        }

        int cantBuffers = cluster.getGestorSincronizacion().getCantidadBuffers();
        if (cantBuffers > 0) {
            sb.append("  |  Buffers: ");
            for (int b = 0; b < cantBuffers; b++) {
                BufferAcotado buf = cluster.getGestorSincronizacion().getBuffers().obtener(b);
                if (buf != null) {
                    sb.append("[").append(buf.getId()).append(": ").append(buf.getCantidadElementos())
                      .append("/").append(buf.getCapacidad()).append("] ");
                }
            }
        }

        agregarLogCiclo(sb.toString(), COLOR_TEXTO_TITULO);

        // Detectar y registrar eventos en este ciclo
        for (int i = 0; i < totalComp; i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            SistemaOperativo so = comp.getSo();

            int terminados = so.getColaTerminados().obtenerTamano();
            for (int j = 0; j < terminados; j++) {
                Proceso p = so.getColaTerminados().obtener(j);
                if (p != null && p.getPcb().getCicloTerminacion() == cicloActual) {
                    if (p.getPcb().isTerminoPorDeadline()) {
                        agregarLogEvento("[" + comp.getId() + "] Proceso " + p.getPcb().getId()
                                + " (" + p.getPcb().getNombre() + ") ABORTADO por deadline expirado.", COLOR_ROJO);
                    } else {
                        agregarLogEvento("[" + comp.getId() + "] Proceso " + p.getPcb().getId()
                                + " (" + p.getPcb().getNombre() + ") COMPLETADO exitosamente.", COLOR_VERDE);
                    }
                }
            }

            // Detectar bloqueos por latencia de red o semáforos recién ocurridos
            int bloqueados = so.getColaBloqueados().obtenerTamano();
            for (int b = 0; b < bloqueados; b++) {
                Proceso pb = so.getColaBloqueados().obtener(b);
                if (pb != null && pb.getPcb().getMotivoBloqueo() != null) {
                    if (pb.getPcb().getMotivoBloqueo() == MotivoBloqueo.LATENCIA_RED
                            && pb.getPcb().getCiclosLatenciaRedRestante() == cluster.getLatenciaRedCiclos()) {
                        agregarLogEvento("[" + comp.getId() + "] " + pb.getId() + " (" + pb.getNombre()
                                + ") en espera de LATENCIA DE RED (" + cluster.getLatenciaRedCiclos() + " ciclos)", COLOR_CYAN);
                    }
                }
            }
        }
    }

    private void registrarPCBsEnLog(long cicloActual) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- PCB Snapshot @ Ciclo ").append(cicloActual).append(" ---\n");

        int totalComp = cluster.getCantidadComputadores();
        for (int ci = 0; ci < totalComp; ci++) {
            Computador comp = cluster.getComputadores().obtener(ci);
            SistemaOperativo so = comp.getSo();

            sb.append("  [").append(comp.getId()).append("] ").append(comp.getNombre())
              .append(" | Política: ").append(so.getPolitica().getTipo().getCodigo()).append("\n");

            // PCB en CPU
            Proceso enCpu = comp.getCpu().getProcesoActual();
            if (enCpu != null) {
                PCB pcb = enCpu.getPcb();
                sb.append("    >> EN CPU: ").append(formatearPCB(pcb, comp)).append("\n");
            } else {
                sb.append("    >> EN CPU: (ociosa)\n");
            }

            // PCBs en cola de listos
            int listos = so.getColaListos().obtenerTamano();
            if (listos > 0) {
                sb.append("    LISTOS (").append(listos).append("):");
                for (int j = 0; j < listos; j++) {
                    Proceso p = so.getColaListos().obtener(j);
                    if (p != null) {
                        sb.append(" [").append(p.getPcb().getId()).append("]");
                    }
                }
                sb.append("\n");
            }

            // PCBs en cola de bloqueados
            int bloqueados = so.getColaBloqueados().obtenerTamano();
            if (bloqueados > 0) {
                sb.append("    BLOQUEADOS (").append(bloqueados).append("):");
                for (int j = 0; j < bloqueados; j++) {
                    Proceso p = so.getColaBloqueados().obtener(j);
                    if (p != null) {
                        sb.append(" [").append(p.getPcb().getId()).append(":").append(p.getPcb().getMotivoBloqueo()).append("]");
                    }
                }
                sb.append("\n");
            }

            // Terminados
            int terminados = so.getColaTerminados().obtenerTamano();
            if (terminados > 0) {
                sb.append("    TERMINADOS: ").append(terminados)
                  .append(" (Exitosos: ").append(so.getProcesosCompletadosExitosos())
                  .append(", Por Deadline: ").append(so.getProcesosTerminadosPorDeadline()).append(")\n");
            }
        }

        agregarLogPCB(sb.toString(), new Color(56, 189, 248));
    }

    private String formatearPCB(PCB pcb, Computador comp) {
        return String.format("%s \"%s\" | Estado:%s | Prio:%d | PC:%d MAR:%d | DL:%d/%d | Rest:%d/%d | RAM:%dMB | Host:%s",
                pcb.getId(), pcb.getNombre(), pcb.getEstado().getNombreLegible(),
                pcb.getPrioridad(), comp.getCpu().getProgramCounter(), comp.getCpu().getMemoryAddressRegister(),
                pcb.getDeadlineRestante(), pcb.getDeadlineInicial(),
                pcb.getTiempoRestanteEjecucion(), pcb.getTiempoEjecucionTotal(),
                pcb.getMemoriaAsignada(), pcb.getComputadorAsignado());
    }

    // =========================================================================
    // Clases Internas de Estilo (Autocontenidas por Ventana)
    // =========================================================================

    /**
     * Botón estilizado autocontenido con renderizado antialiasing de alto contraste.
     */
    public static class BotonEstilizado extends JButton {
        private final Color colorFondoNormal;
        private final Color colorFondoHover;
        private final Color colorFondoPresionado;
        private final Color colorTexto;
        private final Color colorBorde;

        public BotonEstilizado(String texto, Color fondoNormal, Color colorTexto) {
            super(texto);
            this.colorFondoNormal = fondoNormal;
            this.colorTexto = colorTexto != null ? colorTexto : Color.WHITE;
            this.colorFondoHover = new Color(
                    Math.min(255, fondoNormal.getRed() + 25),
                    Math.min(255, fondoNormal.getGreen() + 25),
                    Math.min(255, fondoNormal.getBlue() + 25)
            );
            this.colorFondoPresionado = new Color(
                    Math.max(0, fondoNormal.getRed() - 30),
                    Math.max(0, fondoNormal.getGreen() - 30),
                    Math.max(0, fondoNormal.getBlue() - 30)
            );
            this.colorBorde = new Color(
                    Math.min(255, fondoNormal.getRed() + 45),
                    Math.min(255, fondoNormal.getGreen() + 45),
                    Math.min(255, fondoNormal.getBlue() + 45)
            );
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(this.colorTexto);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setBorder(new EmptyBorder(7, 14, 7, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fondo = colorFondoNormal;
            if (!isEnabled()) {
                fondo = new Color(38, 45, 58);
            } else if (getModel().isPressed()) {
                fondo = colorFondoPresionado;
            } else if (getModel().isRollover()) {
                fondo = colorFondoHover;
            }

            g2.setColor(fondo);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            g2.setColor(isEnabled() ? colorBorde : new Color(55, 65, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2.setColor(isEnabled() ? colorTexto : new Color(110, 125, 145));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), x, y);

            g2.dispose();
        }
    }

    /**
     * UI personalizada de pestañas oscuras para evitar pestañas blancas nativas de Windows.
     */
    public static class PestanasOscurasUI extends BasicTabbedPaneUI {
        private static final Color COLOR_FONDO_ACTIVO = new Color(37, 99, 235);
        private static final Color COLOR_BORDE_ACTIVO = new Color(96, 165, 250);
        private static final Color COLOR_FONDO_INACTIVO = new Color(28, 35, 48);
        private static final Color COLOR_BORDE_INACTIVO = new Color(55, 68, 88);
        private static final Color COLOR_TEXTO_INACTIVO = new Color(185, 198, 218);
        private static final Color COLOR_FONDO_HOVER = new Color(42, 52, 70);

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(6, 16, 6, 16);
            selectedTabPadInsets = new Insets(2, 2, 2, 2);
            tabAreaInsets = new Insets(4, 4, 2, 4);
            contentBorderInsets = new Insets(2, 2, 2, 2);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected) {
                g2.setColor(COLOR_FONDO_ACTIVO);
            } else if (getRolloverTab() == tabIndex) {
                g2.setColor(COLOR_FONDO_HOVER);
            } else {
                g2.setColor(COLOR_FONDO_INACTIVO);
            }
            g2.fillRoundRect(x + 1, y + 1, w - 2, h + 4, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected ? COLOR_BORDE_ACTIVO : COLOR_BORDE_INACTIVO);
            g2.drawRoundRect(x + 1, y + 1, w - 3, h + 4, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(isSelected ? Color.WHITE : COLOR_TEXTO_INACTIVO);
            g2.setFont(font.deriveFont(isSelected ? Font.BOLD : Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            int y = textRect.y + fm.getAscent();
            g2.drawString(title, textRect.x, y);
            g2.dispose();
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            // Sin bordes blancos del sistema
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                           int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // Sin recuadro punteado
        }
    }

    // -------------------------------------------------------------------------
    // Punto de entrada de prueba directa de la Interfaz Gráfica
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            ClusterDistribuido cluster = new ClusterDistribuido(2, 2048);
            VentanaPrincipal ventana = new VentanaPrincipal(cluster);
            ventana.setVisible(true);

            ventana.inyectarCargaPrueba();
        });
    }
}
