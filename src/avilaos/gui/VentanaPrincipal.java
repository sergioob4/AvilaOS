package avilaos.gui;

import avilaos.persistencia.GestorPersistencia;

import avilaos.enums.ModoUsuario;
import avilaos.enums.MotivoBloqueo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.RelojGlobal;
import avilaos.interfaces.IComponenteReloj;
import avilaos.proceso.PCB;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;
import avilaos.sistema.SistemaOperativo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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

    private final JComboBox<ModoUsuario> modo = new JComboBox<>(ModoUsuario.values());
    private final JTextField identidadUsuario = new JTextField("user1", 10);
    private final JLabel avisoModo = new JLabel();
    private JSplitPane splitVertical;
    private JScrollPane scrollComputadores;
    private int posicionDivisor;
    private PanelDisco panelDisco;
    private PanelSistemaArchivos panelSistemaArchivos;
    private PanelOperacionesArchivo panelArchivos;
    private final ClusterDistribuido cluster;
    private final RelojGlobal reloj;

    // Componentes de control superior
    private JLabel lblCicloGlobal;
    private JLabel lblEstadoSimulacion;
    private JLabel lblEstadoFsJson;
    private JLabel lblEstadoProcJson;
    private boolean fsJsonCargado = false;
    private boolean procesosJsonCargado = false;
    private File archivoProcesosSeleccionado;
    private BotonEstilizado btnIniciar;
    private BotonEstilizado btnPausar;
    private BotonEstilizado btnAvanzarPaso;
    private BotonEstilizado btnNuevoProceso;
    private BotonEstilizado btnMetricas;
    private BotonEstilizado btnGraficoCpu;
    private BotonEstilizado btnGraficoDisco;
    private BotonEstilizado btnCompararDisco;
    private BotonEstilizado btnCargarFsJson;
    private BotonEstilizado btnGuardarFsJson;
    private BotonEstilizado btnCargarProcesosJson;
    private BotonEstilizado btnGuardar;
    private BotonEstilizado btnCargar;
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
    private StyledDocument docEventos;
    private StyledDocument docCiclos;
    private StyledDocument docPCB;

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
        super("ÁvilaOS — Monitor del computador (Sistemas Operativos)");
        this.cluster = cluster;
        this.reloj = cluster.getRelojGlobal();
        this.listaPaneles = new ListaEnlazada<>();

        configurarVentana();
        inicializarUI();
        aplicarModo();

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
        panelTop.setLayout(new GridBagLayout());
        panelTop.setBackground(COLOR_BG_HEADER);
        panelTop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 68, 88)),
                new EmptyBorder(10, 20, 10, 20)
        ));

        // ==================== FILA 1: Reloj y Control de Simulación ====================
        JPanel fila1 = crearFilaHerramientas();

        JLabel lblTitulo = new JLabel("ÁVILA-OS");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitulo.setForeground(new Color(96, 165, 250));

        lblCicloGlobal = new JLabel("  Ciclo Global: 0  ");
        lblCicloGlobal.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblCicloGlobal.setVerticalAlignment(SwingConstants.CENTER);
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

        lblEstadoFsJson = crearBadgeCarga("  FS JSON: Pendiente  ", false);
        lblEstadoProcJson = crearBadgeCarga("  Procesos JSON: Pendiente  ", false);

        fila1.add(lblTitulo);
        fila1.add(lblCicloGlobal);
        fila1.add(lblEstadoSimulacion);
        fila1.add(lblEstadoFsJson);
        fila1.add(lblEstadoProcJson);

        // Derecha Fila 1: Velocidad, Paso, Iniciar, Pausar

        JPanel panelVelocidad = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panelVelocidad.setOpaque(false);
        JLabel lblVel = new JLabel("Velocidad:");
        lblVel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblVel.setForeground(COLOR_TEXTO_MUTED);

        sliderVelocidad = new JSlider(100, 2500, (int) reloj.getDuracionCicloMs());
        sliderVelocidad.setOpaque(false);
        sliderVelocidad.setPreferredSize(new Dimension(110, 26));

        lblValorVelocidad = new JLabel(sliderVelocidad.getValue() + " ms");
        lblValorVelocidad.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblValorVelocidad.setVerticalAlignment(SwingConstants.CENTER);
        lblValorVelocidad.setForeground(COLOR_TEXTO_TITULO);
        lblValorVelocidad.setPreferredSize(new Dimension(64, 26));

        sliderVelocidad.addChangeListener(e -> {
            int ms = sliderVelocidad.getValue();
            reloj.setDuracionCicloMs(ms);
            lblValorVelocidad.setText(ms + " ms");
        });

        panelVelocidad.add(lblVel);
        panelVelocidad.add(sliderVelocidad);
        panelVelocidad.add(lblValorVelocidad);

        btnAvanzarPaso = crearBoton("Paso (+1)", COLOR_PRIMARIO);
        btnIniciar = crearBoton("Iniciar / Reanudar", COLOR_VERDE);
        btnPausar = crearBoton("Pausar", COLOR_AMBAR);

        btnIniciar.addActionListener(e -> {
            if (!validarCargaJsonObligatoria()) {
                return;
            }
            iniciarSimulacion();
        });

        btnPausar.addActionListener(e -> {
            pausarReloj();
            agregarLogEvento("Simulación PAUSADA por el usuario.", COLOR_AMBAR);
        });

        btnAvanzarPaso.addActionListener(e -> {
            if (!validarCargaJsonObligatoria()) {
                return;
            }
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

        fila1.add(panelVelocidad);
        fila1.add(btnAvanzarPaso);
        fila1.add(btnIniciar);
        fila1.add(btnPausar);

        JPanel panelAcciones = crearFilaHerramientas();

        btnNuevoProceso = crearBoton("Nuevo Proceso", COLOR_CYAN);
        btnCargarFsJson = crearBoton("Cargar FS JSON", COLOR_PRIMARIO);
        btnGuardarFsJson = crearBoton("Guardar FS JSON", COLOR_GRIS_BOTON);
        btnCargarProcesosJson = crearBoton("Cargar Procesos JSON", COLOR_VERDE);
        btnMetricas = crearBoton("Metricas", COLOR_MORADO);
        btnGraficoCpu = crearBoton("Grafico CPU", COLOR_PRIMARIO);
        btnGraficoDisco = crearBoton("Grafico Disco", COLOR_AMBAR);
        btnCompararDisco = crearBoton("Comparar políticas de disco", COLOR_AMBAR);
        btnGuardar = crearBoton("Exportar Escenario", COLOR_GRIS_BOTON);
        btnCargar = crearBoton("Importar CSV", COLOR_GRIS_BOTON);

        btnNuevoProceso.addActionListener(e -> {
            DialogoCrearProceso dlg = new DialogoCrearProceso(this, cluster);
            dlg.setVisible(true);
        });

        btnCargarFsJson.addActionListener(e -> cargarSistemaArchivosJson());
        btnGuardarFsJson.addActionListener(e -> guardarSistemaArchivosJson());
        btnCargarProcesosJson.addActionListener(e -> cargarProcesosConfigJson());

        btnMetricas.addActionListener(e -> {
            DialogoMetricas dlg = new DialogoMetricas(this, cluster);
            dlg.setVisible(true);
        });

        btnGraficoCpu.addActionListener(e -> {
            DialogoGraficoCPU dlg = new DialogoGraficoCPU(this, cluster);
            dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dlg.setVisible(true);
        });

        btnGraficoDisco.addActionListener(e -> {
            DialogoGraficoDisco dlg = new DialogoGraficoDisco(this, cluster);
            dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dlg.setVisible(true);
        });
        btnCompararDisco.addActionListener(e -> compararPlanificacionDisco());

        btnGuardar.addActionListener(e -> guardarEscenarioCsv());
        btnCargar.addActionListener(e -> cargarEscenarioCsv());

        panelAcciones.add(btnCargarFsJson);
        panelAcciones.add(btnGuardarFsJson);
        panelAcciones.add(btnCargarProcesosJson);
        panelAcciones.add(btnNuevoProceso);
        panelAcciones.add(btnMetricas);
        panelAcciones.add(btnGraficoCpu);
        panelAcciones.add(btnGraficoDisco);
        panelAcciones.add(btnCompararDisco);
        panelAcciones.add(btnGuardar);
        panelAcciones.add(btnCargar);

        GridBagConstraints fila = new GridBagConstraints();
        fila.gridx = 0;
        fila.weightx = 1;
        fila.fill = GridBagConstraints.HORIZONTAL;
        panelTop.add(fila1, fila);
        panelTop.add(panelAcciones, fila);
        JPanel sesion = crearFilaHerramientas();
        JLabel etiquetaModo = new JLabel("Modo activo");
        etiquetaModo.setForeground(COLOR_TEXTO_TITULO);
        etiquetaModo.setLabelFor(modo);
        sesion.add(etiquetaModo);
        sesion.add(modo);
        JLabel etiquetaIdentidad = new JLabel("Usuario simulado");
        etiquetaIdentidad.setForeground(COLOR_TEXTO_TITULO);
        etiquetaIdentidad.setLabelFor(identidadUsuario);
        sesion.add(etiquetaIdentidad);
        sesion.add(identidadUsuario);
        avisoModo.setForeground(COLOR_TEXTO_MUTED);
        sesion.add(avisoModo);
        panelTop.add(sesion, fila);
        modo.addActionListener(e -> aplicarModo());
        add(panelTop, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // 2. Panel Central con Split: Computadores (arriba) y Logs (abajo)
        // ---------------------------------------------------------------------
        splitVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT) {
            private boolean ajustarAlAbrir = true;

            @Override
            public void doLayout() {
                if (ajustarAlAbrir && getHeight() > getDividerSize()) {
                    ajustarAlAbrir = false;
                    setDividerLocation(0.60);
                }
                super.doLayout();
            }
        };
        splitVertical.setBackground(COLOR_BG_VENTANA);
        splitVertical.setBorder(null);
        splitVertical.setDividerSize(6);
        splitVertical.setResizeWeight(0.65);
        splitVertical.setContinuousLayout(true);

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

        scrollComputadores = new JScrollPane(panelComputadores);
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

        JLabel lblInfoCluster = new JLabel("Computador 1 · Disco local");
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
        tabbedLogs.addTab("Eventos del sistema", scrollEventos);

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

        Computador primerComp = cluster.getComputadores().obtener(0);
        panelDisco = new PanelDisco(primerComp.getDisco());
        tabbedLogs.insertTab("Disco local", null, panelDisco, null, 0);
        panelSistemaArchivos = new PanelSistemaArchivos(primerComp.getSistemaArchivos(), ruta -> {
            if (panelArchivos != null) {
                panelArchivos.seleccionarRuta(ruta);
                tabbedLogs.setSelectedComponent(panelArchivos);
            }
        });
        tabbedLogs.insertTab("Sistema de archivos", null, panelSistemaArchivos, null, 1);
        if (cluster.getCantidadComputadores() == 1) {
            SistemaOperativo so = primerComp.getSo();
            panelArchivos = new PanelOperacionesArchivo(this::modoActivo, this::usuarioActivo, peticion -> {
                primerComp.getSistemaArchivos().validarPermiso(peticion);
                Proceso creado = so.crearProcesoArchivo(peticion, reloj.getCicloActual());
                actualizarTodo();
                return creado;
            });
            tabbedLogs.insertTab("Operaciones de archivo", null, panelArchivos, null, 2);
        }
        tabbedLogs.setSelectedComponent(panelDisco);
        contenedor.add(tabbedLogs, BorderLayout.CENTER);
        return contenedor;
    }

    private ModoUsuario modoActivo() {
        return (ModoUsuario) modo.getSelectedItem();
    }

    private void exigirModoAdministrador() {
        if (!modoActivo().esAdministrador()) throw new SecurityException("Se requiere el modo Administrador.");
    }

    private String usuarioActivo() {
        return modoActivo().esAdministrador() ? "admin" : identidadUsuario.getText().trim();
    }

    private void aplicarModo() {
        boolean admin = modoActivo().esAdministrador();
        if (!admin && (usuarioActivo().isBlank() || usuarioActivo().equalsIgnoreCase("admin"))) {
            modo.setSelectedItem(ModoUsuario.ADMINISTRADOR);
            avisoModo.setText("Indique un usuario distinto de admin antes de cambiar de modo.");
            return;
        }
        identidadUsuario.setEditable(admin);
        identidadUsuario.setToolTipText("El administrador elige la identidad para el modo Usuario.");
        String motivo = "Se requiere el modo Administrador.";
        avisoModo.setText(admin ? "Acceso total · Identidad administrativa: admin"
                : "Solo lectura propia o pública y creación en sus directorios. Los controles del sistema requieren Administrador.");
        for (JComponent control : new JComponent[]{btnNuevoProceso, btnCargarFsJson, btnGuardarFsJson,
                btnCargarProcesosJson, btnMetricas, btnGraficoCpu, btnGraficoDisco, btnCompararDisco, btnGuardar, btnCargar,
                btnIniciar, btnPausar, btnAvanzarPaso, sliderVelocidad}) {
            control.setEnabled(admin);
            control.setToolTipText(admin ? null : motivo);
        }
        if (!admin) {
            for (Window ventana : getOwnedWindows()) {
                if (ventana instanceof DialogoGraficoCPU || ventana instanceof DialogoGraficoDisco) ventana.dispose();
            }
            posicionDivisor = splitVertical.getDividerLocation();
            splitVertical.setTopComponent(null);
            tabbedLogs.setSelectedComponent(panelSistemaArchivos);
        } else if (splitVertical.getTopComponent() == null) {
            splitVertical.setTopComponent(scrollComputadores);
            splitVertical.setDividerLocation(posicionDivisor);
        }
        for (int i = 0; i < tabbedLogs.getTabCount(); i++) {
            Component panel = tabbedLogs.getComponentAt(i);
            boolean visible = admin || panel == panelSistemaArchivos || panel == panelArchivos;
            tabbedLogs.setEnabledAt(i, visible);
            tabbedLogs.setToolTipTextAt(i, visible ? null : motivo);
        }
        panelSistemaArchivos.mostrarAsignacion(admin);
        if (panelArchivos != null) panelArchivos.cambiarSesion();
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
    /** FlowLayout envuelve los controles, pero por defecto reserva altura para una sola línea. */
    private JPanel crearFilaHerramientas() {
        final int margenX = 5;
        final int margenY = 3;
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEADING, margenX, margenY)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension preferido = super.getPreferredSize();
                Container cabecera = getParent();
                if (cabecera == null || getContentPane().getWidth() <= 0) return preferido;
                Insets borde = cabecera.getInsets();
                int disponible = getContentPane().getWidth() - borde.left - borde.right - 2 * margenX;
                int ancho = 0;
                int altoLinea = 0;
                int alto = 2 * margenY;
                for (Component control : getComponents()) {
                    if (!control.isVisible()) continue;
                    Dimension tam = control.getPreferredSize();
                    if (ancho > 0 && ancho + margenX + tam.width > disponible) {
                        alto += altoLinea + margenY;
                        ancho = 0;
                        altoLinea = 0;
                    }
                    ancho += (ancho == 0 ? 0 : margenX) + tam.width;
                    altoLinea = Math.max(altoLinea, tam.height);
                }
                return new Dimension(Math.min(preferido.width, Math.max(1, disponible)), alto + altoLinea);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };
        fila.setOpaque(false);
        return fila;
    }

    private BotonEstilizado crearBoton(String texto, Color colorFondo) {
        BotonEstilizado btn = new BotonEstilizado(texto, colorFondo, Color.WHITE);
        btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        return btn;
    }

    private JLabel crearBadgeCarga(String texto, boolean cargado) {
        JLabel badge = new JLabel(texto);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setOpaque(true);
        estilizarBadgeCarga(badge, cargado);
        return badge;
    }

    private void estilizarBadgeCarga(JLabel badge, boolean cargado) {
        if (cargado) {
            badge.setBackground(new Color(20, 83, 45));
            badge.setForeground(new Color(187, 247, 208));
            badge.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(34, 197, 94), 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));
        } else {
            badge.setBackground(new Color(120, 53, 15));
            badge.setForeground(new Color(254, 240, 138));
            badge.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(245, 158, 11), 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));
        }
    }

    /** Verifica la carga obligatoria de ambos archivos JSON antes de iniciar el reloj global. */
    private boolean validarCargaJsonObligatoria() {
        if (fsJsonCargado && procesosJsonCargado) {
            return true;
        }
        StringBuilder faltantes = new StringBuilder();
        if (!fsJsonCargado) faltantes.append("\n - Archivo JSON del Sistema de Archivos (Cargar FS JSON)");
        if (!procesosJsonCargado) faltantes.append("\n - Archivo JSON de Procesos y Configuración (Cargar Procesos JSON)");
        JOptionPane.showMessageDialog(this,
                "Debe cargar obligatoriamente ambos archivos JSON antes de iniciar el reloj global:" + faltantes,
                "Carga obligatoria pendiente", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private void pausarReloj() {
        reloj.pausar();
        lblEstadoSimulacion.setText("  PAUSADO  ");
        lblEstadoSimulacion.setBackground(new Color(120, 53, 15));
        lblEstadoSimulacion.setForeground(new Color(254, 240, 138));
        lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(245, 158, 11), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));
    }

    private void iniciarSimulacion() {
        reloj.iniciar();
        lblEstadoSimulacion.setText("  EN EJECUCIÓN  ");
        lblEstadoSimulacion.setBackground(new Color(20, 83, 45));
        lblEstadoSimulacion.setForeground(new Color(187, 247, 208));
        lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(34, 197, 94), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));
        agregarLogEvento("Simulación INICIADA (ejecución automática en tiempo real).", COLOR_VERDE);
    }

    public boolean estaListaParaIniciar() {
        return fsJsonCargado && procesosJsonCargado;
    }

    private JFileChooser crearSelectorJson(String titulo) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(titulo);
        File dirEjemplos = new File("ejemplos");
        if (dirEjemplos.isDirectory()) {
            chooser.setCurrentDirectory(dirEjemplos);
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        return chooser;
    }

    private void cargarSistemaArchivosJson() {
        JFileChooser chooser = crearSelectorJson("Cargar JSON del Sistema de Archivos");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            cargarSistemaArchivosJsonDesdeArchivo(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar JSON del sistema de archivos:\n" + ex.getMessage(),
                    "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void compararPlanificacionDisco() {
        exigirModoAdministrador();
        JFileChooser selector = crearSelectorJson("Seleccionar JSON del sistema de archivos");
        selector.setSelectedFile(new File(selector.getCurrentDirectory(), "sistema_archivos.json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File archivoFs = selector.getSelectedFile();
        selector.setDialogTitle("Seleccionar JSON de procesos");
        selector.setSelectedFile(new File(selector.getCurrentDirectory(), "procesos_config.json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            var resultados = GestorPersistencia.compararPlanificacionJson(archivoFs, selector.getSelectedFile());
            Object[][] filas = new Object[resultados.length][4];
            for (int i = 0; i < resultados.length; i++) {
                var resultado = resultados[i];
                var orden = new StringBuilder();
                for (int bloque : resultado.orden()) {
                    if (!orden.isEmpty()) orden.append(" → ");
                    orden.append(bloque);
                }
                filas[i] = new Object[]{resultado.politica(), orden.toString(), resultado.desplazamiento(),
                        String.format("%.2f", resultado.esperaPromedio())};
            }
            JTable tabla = new JTable(filas, new String[]{"Política", "Orden de atención",
                    "Desplazamiento (bloques)", "Espera media (ciclos)"});
            tabla.setDefaultEditor(Object.class, null);
            tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            int[] anchos = {75, 300, 170, 150};
            for (int i = 0; i < anchos.length; i++) {
                tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
            }
            tabla.setPreferredScrollableViewportSize(new Dimension(695, tabla.getRowHeight() * filas.length));
            JOptionPane.showMessageDialog(this, new Object[]{
                    "Todas las solicitudes parten en cola. Cada acceso usa el primer bloque de su archivo.",
                    new JScrollPane(tabla)
            }, "Comparación de políticas de disco", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error al comparar políticas", JOptionPane.ERROR_MESSAGE);
        }
    }

    public int cargarSistemaArchivosJsonDesdeArchivo(File file) throws Exception {
        exigirModoAdministrador();
        int cargados;
        synchronized (reloj) {
            cargados = GestorPersistencia.importarSistemaArchivosJson(file, archivoProcesosSeleccionado, cluster);
            pausarReloj();
        }
        fsJsonCargado = true;
        lblEstadoFsJson.setText("  FS JSON: Cargado  ");
        estilizarBadgeCarga(lblEstadoFsJson, true);
        agregarLogEvento("Sistema de archivos cargado desde " + file.getName() + " (" + cargados + " elementos).", COLOR_VERDE);
        actualizarCargaJson();
        return cargados;
    }

    private void guardarSistemaArchivosJson() {
        JFileChooser chooser = crearSelectorJson("Guardar Estado del Sistema de Archivos (JSON)");
        chooser.setSelectedFile(new File("sistema_archivos.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }
        try {
            Computador comp = cluster.getComputadores().obtener(0);
            GestorPersistencia.exportarSistemaArchivosJson(file, comp);
            JOptionPane.showMessageDialog(this,
                    "Estado del sistema de archivos guardado en:\n" + file.getAbsolutePath(),
                    "Guardado Exitoso", JOptionPane.INFORMATION_MESSAGE);
            agregarLogEvento("Sistema de archivos guardado en " + file.getName(), COLOR_VERDE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al guardar JSON del sistema de archivos:\n" + ex.getMessage(),
                    "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarProcesosConfigJson() {
        JFileChooser chooser = crearSelectorJson("Cargar JSON de Procesos y Configuración");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            cargarProcesosConfigJsonDesdeArchivo(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar JSON de procesos y configuración:\n" + ex.getMessage(),
                    "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
        }
    }

    public int cargarProcesosConfigJsonDesdeArchivo(File file) throws Exception {
        exigirModoAdministrador();
        int cargados;
        synchronized (reloj) {
            cargados = GestorPersistencia.importarProcesosConfigJson(file, cluster);
            pausarReloj();
        }
        archivoProcesosSeleccionado = file;
        procesosJsonCargado = true;
        lblEstadoProcJson.setText("  Procesos JSON: Cargado  ");
        estilizarBadgeCarga(lblEstadoProcJson, true);
        agregarLogEvento("Procesos y configuración cargados desde " + file.getName() + " (" + cargados + " procesos).", COLOR_VERDE);
        actualizarCargaJson();
        return cargados;
    }

    private void actualizarCargaJson() {
        int duracion = (int) Math.max(sliderVelocidad.getMinimum(),
                Math.min(sliderVelocidad.getMaximum(), reloj.getDuracionCicloMs()));
        sliderVelocidad.setValue(duracion);
        lblValorVelocidad.setText(reloj.getDuracionCicloMs() + " ms");
        actualizarTodo();
        if (estaListaParaIniciar()) iniciarSimulacion();
    }

    /**
     * Exporta el estado del clúster y procesos a un archivo CSV.
     */
    private void guardarEscenarioCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Configuración de Simulación (CSV o JSON)");
        chooser.setSelectedFile(new File("escenario_cluster.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos de Escenario (*.csv, *.json)", "csv", "json"));

        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String nombre = file.getName().toLowerCase();
            if (!nombre.endsWith(".csv") && !nombre.endsWith(".json")) {
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
     * Carga procesos y configuración desde un archivo CSV o JSON.
     */
    private void cargarEscenarioCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Cargar Configuración de Simulación (CSV o JSON)");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos de Escenario (*.csv, *.json)", "csv", "json"));

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
        exigirModoAdministrador();
        boolean cargado = avilaos.sistema.EscenarioDisco.cargar(cluster.getComputadores().obtener(0));
        agregarLogEvento(cargado ? "Ejemplo de disco cargado. Avance el reloj para observar la lectura."
                : "La demostración requiere un disco vacío y las rutas de ejemplo disponibles.", COLOR_MORADO);
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
        panelDisco.actualizar();
        if (panelSistemaArchivos != null) panelSistemaArchivos.actualizar();
        if (panelArchivos != null) panelArchivos.actualizar();
        lblCicloGlobal.setText("  Ciclo Global: " + reloj.getCicloActual() + "  ");

        int total = listaPaneles.obtenerTamano();
        for (int i = 0; i < total; i++) {
            PanelComputador panel = listaPaneles.obtener(i);
            if (panel != null) {
                panel.actualizarVista();
            }
        }

        drenarEventosSistema();
    }

    private void drenarEventosSistema() {
        ListaEnlazada<String> eventos = cluster.drenarEventos();
        int total = eventos.obtenerTamano();
        for (int i = 0; i < total; i++) {
            String evento = eventos.obtener(i);
            if (evento == null) {
                continue;
            }

            Color color = COLOR_PRIMARIO;
            if (evento.contains("deadline") || evento.contains("se bloquea")) {
                color = COLOR_ROJO;
            } else if (evento.contains("selecciona") || evento.contains("produce")
                    || evento.contains("consume") || evento.contains("finaliza")) {
                color = COLOR_VERDE;
            } else if (evento.contains("remota") || evento.contains("Latencia")) {
                color = COLOR_CYAN;
            } else if (evento.contains("desbloquea") || evento.contains("despertado")) {
                color = COLOR_AMBAR;
            } else if (evento.contains("Buffer")) {
                color = COLOR_MORADO;
            }
            agregarLogEvento(evento, color);
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

        agregarLogCiclo(sb.toString(), COLOR_TEXTO_TITULO);

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
            ClusterDistribuido cluster = new ClusterDistribuido(1, 2048);
            VentanaPrincipal ventana = new VentanaPrincipal(cluster);
            ventana.setVisible(true);

            ventana.inyectarCargaPrueba();
        });
    }
}
