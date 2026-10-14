package avilaos.gui;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.RelojGlobal;
import avilaos.interfaces.IComponenteReloj;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

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
    private JButton btnIniciar;
    private JButton btnPausar;
    private JButton btnAvanzarPaso;
    private JButton btnCargarPrueba;
    private JSlider sliderVelocidad;
    private JLabel lblValorVelocidad;

    // Contenedor de los paneles de cada computador
    private JPanel panelComputadores;
    private final ListaEnlazada<PanelComputador> listaPaneles;

    // Paleta visual Dark Theme
    private static final Color COLOR_BG_VENTANA = new Color(21, 24, 30);
    private static final Color COLOR_BG_HEADER = new Color(28, 33, 41);
    private static final Color COLOR_TEXTO_TITULO = new Color(245, 247, 250);
    private static final Color COLOR_TEXTO_MUTED = new Color(150, 160, 175);
    private static final Color COLOR_PRIMARIO = new Color(59, 130, 246);
    private static final Color COLOR_VERDE = new Color(34, 197, 94);
    private static final Color COLOR_AMBAR = new Color(245, 158, 11);

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
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG_VENTANA);
        setLayout(new BorderLayout());
    }

    private void inicializarUI() {
        // ---------------------------------------------------------------------
        // 1. Barra de Control Superior
        // ---------------------------------------------------------------------
        JPanel panelTop = new JPanel(new BorderLayout(15, 10));
        panelTop.setBackground(COLOR_BG_HEADER);
        panelTop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 60, 75)),
                new EmptyBorder(12, 18, 12, 18)
        ));

        // Título y reloj global
        JPanel panelTituloReloj = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        panelTituloReloj.setOpaque(false);

        JLabel lblTitulo = new JLabel("ÁVILA-OS");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitulo.setForeground(COLOR_PRIMARIO);

        lblCicloGlobal = new JLabel("⏱ Ciclo Global: 0");
        lblCicloGlobal.setFont(new Font("Consolas", Font.BOLD, 16));
        lblCicloGlobal.setForeground(COLOR_TEXTO_TITULO);
        lblCicloGlobal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(59, 130, 246, 120), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        lblEstadoSimulacion = new JLabel("PAUSADO");
        lblEstadoSimulacion.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstadoSimulacion.setForeground(COLOR_AMBAR);
        lblEstadoSimulacion.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(245, 158, 11, 120), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));

        panelTituloReloj.add(lblTitulo);
        panelTituloReloj.add(lblCicloGlobal);
        panelTituloReloj.add(lblEstadoSimulacion);

        // Botones de acción y control
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setOpaque(false);

        btnIniciar = crearBoton("▶ Iniciar", COLOR_VERDE);
        btnPausar = crearBoton("⏸ Pausar", COLOR_AMBAR);
        btnAvanzarPaso = crearBoton("⏭ Siguiente Ciclo (+1)", COLOR_PRIMARIO);
        btnCargarPrueba = crearBoton("⚡ Carga Demo", new Color(139, 92, 246));

        // Slider de velocidad (100 ms a 2500 ms)
        JPanel panelVelocidad = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panelVelocidad.setOpaque(false);
        JLabel lblVel = new JLabel("Velocidad:");
        lblVel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblVel.setForeground(COLOR_TEXTO_MUTED);

        sliderVelocidad = new JSlider(100, 2500, (int) reloj.getDuracionCicloMs());
        sliderVelocidad.setOpaque(false);
        sliderVelocidad.setPreferredSize(new Dimension(120, 26));

        lblValorVelocidad = new JLabel(sliderVelocidad.getValue() + " ms");
        lblValorVelocidad.setFont(new Font("Consolas", Font.BOLD, 12));
        lblValorVelocidad.setForeground(COLOR_TEXTO_TITULO);

        sliderVelocidad.addChangeListener(e -> {
            int ms = sliderVelocidad.getValue();
            reloj.setDuracionCicloMs(ms);
            lblValorVelocidad.setText(ms + " ms");
        });

        panelVelocidad.add(lblVel);
        panelVelocidad.add(sliderVelocidad);
        panelVelocidad.add(lblValorVelocidad);

        // Eventos de botones
        btnIniciar.addActionListener(e -> {
            reloj.iniciar();
            lblEstadoSimulacion.setText("EN EJECUCIÓN");
            lblEstadoSimulacion.setForeground(COLOR_VERDE);
        });

        btnPausar.addActionListener(e -> {
            reloj.pausar();
            lblEstadoSimulacion.setText("PAUSADO");
            lblEstadoSimulacion.setForeground(COLOR_AMBAR);
        });

        btnAvanzarPaso.addActionListener(e -> {
            reloj.pausar();
            lblEstadoSimulacion.setText("PASO A PASO");
            lblEstadoSimulacion.setForeground(COLOR_PRIMARIO);
            reloj.avanzarUnCiclo();
        });

        btnCargarPrueba.addActionListener(e -> inyectarCargaPrueba());

        panelBotones.add(panelVelocidad);
        panelBotones.add(btnAvanzarPaso);
        panelBotones.add(btnIniciar);
        panelBotones.add(btnPausar);
        panelBotones.add(btnCargarPrueba);

        panelTop.add(panelTituloReloj, BorderLayout.WEST);
        panelTop.add(panelBotones, BorderLayout.EAST);
        add(panelTop, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // 2. Contenedor de Computadores (Visualización de cada máquina)
        // ---------------------------------------------------------------------
        panelComputadores = new JPanel();
        panelComputadores.setBackground(COLOR_BG_VENTANA);
        panelComputadores.setBorder(new EmptyBorder(16, 16, 16, 16));

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
        add(scrollComputadores, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // 3. Barra de Estado Inferior
        // ---------------------------------------------------------------------
        JPanel panelBottom = new JPanel(new BorderLayout());
        panelBottom.setBackground(COLOR_BG_HEADER);
        panelBottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 60, 75)),
                new EmptyBorder(8, 16, 8, 16)
        ));

        JLabel lblInfoClúster = new JLabel(String.format("Clúster: %d Computadores | Latencia Red: %d ciclos | Monitoreo Activo",
                cluster.getCantidadComputadores(), cluster.getLatenciaRedCiclos()));
        lblInfoClúster.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblInfoClúster.setForeground(COLOR_TEXTO_MUTED);

        panelBottom.add(lblInfoClúster, BorderLayout.WEST);
        add(panelBottom, BorderLayout.SOUTH);
    }

    private JButton crearBoton(String texto, Color colorBase) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(colorBase);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorBase.darker(), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
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

        // Procesos para C1
        Proceso p1 = new Proceso("Cálculo Matricial", c1.getId(), TipoProceso.CPU_BOUND, 2, 512, 18, 6);
        Proceso p2 = new Proceso("Compresión Datos", c1.getId(), TipoProceso.CPU_BOUND, 3, 512, 14, 4);
        c1.admitirProceso(p1, reloj.getCicloActual());
        c1.admitirProceso(p2, reloj.getCicloActual());

        // Procesos para C2
        Proceso p3 = new Proceso("Indexador Web", c2.getId(), TipoProceso.CPU_BOUND, 1, 512, 22, 7);
        Proceso p4 = new Proceso("Alerta Urgente", c2.getId(), TipoProceso.CPU_BOUND, 5, 256, 10, 3);
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
        SwingUtilities.invokeLater(this::actualizarTodo);
    }

    /**
     * Refresca los datos en pantalla de todos los computadores y componentes globales.
     */
    public void actualizarTodo() {
        lblCicloGlobal.setText("⏱ Ciclo Global: " + reloj.getCicloActual());

        int total = listaPaneles.obtenerTamano();
        for (int i = 0; i < total; i++) {
            PanelComputador panel = listaPaneles.obtener(i);
            if (panel != null) {
                panel.actualizarVista();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Punto de entrada de prueba directa de la Interfaz Gráfica
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        // Look and Feel nativo del sistema operativo si es posible
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            ClusterDistribuido cluster = new ClusterDistribuido(2, 2048);
            VentanaPrincipal ventana = new VentanaPrincipal(cluster);
            ventana.setVisible(true);

            // Cargar datos de prueba por defecto para ver de inmediato las CPUs en acción
            ventana.inyectarCargaPrueba();
        });
    }
}
