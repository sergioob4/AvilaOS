package avilaos.gui;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.sistema.ConfiguracionInicial;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.ParseException;

/**
 * Vista de configuración inicial de ÁvilaOS (Requerimiento 1 y 7): cantidad de computadores,
 * RAM, política y quantum de cada uno, latencia de red y duración del ciclo.
 *
 * <p>Todos los campos son {@link JSpinner} con rango; además se rechaza texto inválido
 * al confirmar, sin cerrar la ventana ni interrumpir el flujo.</p>
 */
public class DialogoConfiguracionInicial extends JDialog {

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_MUTED = new Color(175, 190, 215);
    private static final Color COLOR_INPUT = new Color(24, 28, 36);

    private final JSpinner spinCantidad;
    private final JSpinner spinLatencia;
    private final JSpinner spinCicloMs;
    private final JPanel panelComputadores;

    private JSpinner[] spinRam = new JSpinner[0];
    private JComboBox<PoliticaPlanificacion>[] comboPolitica = nuevoArregloCombos(0);
    private JSpinner[] spinQuantum = new JSpinner[0];

    private ConfiguracionInicial resultado;

    public DialogoConfiguracionInicial(ConfiguracionInicial inicial) {
        super((java.awt.Frame) null, "ÁvilaOS — Configuración Inicial", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // Encabezado
        JLabel titulo = new JLabel("Configuración de la simulación");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titulo.setForeground(COLOR_TITULO);
        JLabel sub = new JLabel("Defina el clúster antes de iniciar. Podrá crear procesos y buffers y cambiar políticas durante la ejecución.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(COLOR_MUTED);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(COLOR_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE), new EmptyBorder(14, 20, 14, 20)));
        header.add(titulo, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Parámetros globales
        spinCantidad = crearSpinner(inicial.getCantidadComputadores(),
                ConfiguracionInicial.MIN_COMPUTADORES, ConfiguracionInicial.MAX_COMPUTADORES, 1);
        spinLatencia = crearSpinner(inicial.getLatenciaRedCiclos(),
                ConfiguracionInicial.MIN_LATENCIA, ConfiguracionInicial.MAX_LATENCIA, 1);
        spinCicloMs = crearSpinner((int) inicial.getDuracionCicloMs(),
                ConfiguracionInicial.MIN_CICLO_MS, ConfiguracionInicial.MAX_CICLO_MS, 100);

        JPanel globales = new JPanel(new GridLayout(0, 2, 12, 10));
        globales.setOpaque(false);
        agregarFila(globales, "Número de computadores (" + ConfiguracionInicial.MIN_COMPUTADORES
                + "–" + ConfiguracionInicial.MAX_COMPUTADORES + "):", spinCantidad);
        agregarFila(globales, "Latencia de red (ciclos):", spinLatencia);
        agregarFila(globales, "Duración del ciclo (ms):", spinCicloMs);

        panelComputadores = new JPanel();
        panelComputadores.setLayout(new BoxLayout(panelComputadores, BoxLayout.Y_AXIS));
        panelComputadores.setOpaque(false);

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setBackground(COLOR_BG);
        centro.setBorder(new EmptyBorder(16, 20, 8, 20));
        centro.add(crearTarjeta("Parámetros globales", globales));
        centro.add(Box.createVerticalStrut(12));
        centro.add(crearTarjeta("Computadores (RAM en MB, política inicial y quantum)", panelComputadores));
        add(centro, BorderLayout.CENTER);

        reconstruirFilasComputadores(inicial);
        spinCantidad.addChangeListener(e -> reconstruirFilasComputadores(null));

        // Botones
        VentanaPrincipal.BotonEstilizado btnIniciar = new VentanaPrincipal.BotonEstilizado(
                "Iniciar simulación", new Color(22, 163, 74), Color.WHITE);
        VentanaPrincipal.BotonEstilizado btnCancelar = new VentanaPrincipal.BotonEstilizado(
                "Cancelar", new Color(51, 65, 85), Color.WHITE);
        btnIniciar.setPreferredSize(new Dimension(160, 34));
        btnCancelar.setPreferredSize(new Dimension(100, 34));
        btnIniciar.addActionListener(e -> aceptar());
        btnCancelar.addActionListener(e -> dispose());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        botones.setBackground(COLOR_CARD);
        botones.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));
        botones.add(btnCancelar);
        botones.add(btnIniciar);
        add(botones, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btnIniciar);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    /**
     * Muestra el diálogo (modal) y devuelve la configuración elegida.
     *
     * @return la configuración, o {@code null} si el usuario canceló
     */
    public ConfiguracionInicial mostrar() {
        setVisible(true);
        return resultado;
    }

    // -------------------------------------------------------------------------
    // Filas dinámicas por computador
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static JComboBox<PoliticaPlanificacion>[] nuevoArregloCombos(int n) {
        return (JComboBox<PoliticaPlanificacion>[]) new JComboBox<?>[n];
    }

    /** Ajusta las filas al número de computadores conservando lo ya digitado (o usando {@code inicial}). */
    private void reconstruirFilasComputadores(ConfiguracionInicial inicial) {
        int n = (Integer) spinCantidad.getValue();
        JSpinner[] ramAnt = spinRam;
        JComboBox<PoliticaPlanificacion>[] polAnt = comboPolitica;
        JSpinner[] quaAnt = spinQuantum;

        spinRam = new JSpinner[n];
        comboPolitica = nuevoArregloCombos(n);
        spinQuantum = new JSpinner[n];

        panelComputadores.removeAll();
        for (int i = 0; i < n; i++) {
            if (i < ramAnt.length) {
                spinRam[i] = ramAnt[i];
                comboPolitica[i] = polAnt[i];
                spinQuantum[i] = quaAnt[i];
            } else {
                boolean hay = inicial != null && i < inicial.getCantidadComputadores();
                spinRam[i] = crearSpinner(hay ? inicial.getRamMb(i) : 2048,
                        ConfiguracionInicial.MIN_RAM_MB, ConfiguracionInicial.MAX_RAM_MB, 128);
                comboPolitica[i] = new JComboBox<>(PoliticaPlanificacion.values());
                comboPolitica[i].setSelectedItem(hay ? inicial.getPolitica(i) : PoliticaPlanificacion.FCFS);
                PanelComputador.estilizarComboBox(comboPolitica[i]);
                spinQuantum[i] = crearSpinner(hay ? inicial.getQuantum(i) : 3,
                        ConfiguracionInicial.MIN_QUANTUM, ConfiguracionInicial.MAX_QUANTUM, 1);
            }

            JPanel fila = new JPanel(new GridLayout(1, 7, 8, 0));
            fila.setOpaque(false);
            fila.setAlignmentX(Component.LEFT_ALIGNMENT);
            fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            fila.add(etiqueta("Computador " + (i + 1) + " (C" + (i + 1) + ")", true));
            fila.add(etiqueta("RAM (MB):", false));
            fila.add(spinRam[i]);
            fila.add(etiqueta("Política:", false));
            fila.add(comboPolitica[i]);
            fila.add(etiqueta("Quantum:", false));
            fila.add(spinQuantum[i]);
            panelComputadores.add(fila);
            panelComputadores.add(Box.createVerticalStrut(8));
        }
        panelComputadores.revalidate();
        panelComputadores.repaint();
        ajustarTamano();
    }

    private void ajustarTamano() {
        if (isDisplayable()) {
            int ancho = Math.max(getWidth(), getPreferredSize().width);
            pack();
            setSize(ancho, getHeight());
        }
    }

    // -------------------------------------------------------------------------
    // Validación y resultado
    // -------------------------------------------------------------------------

    private void aceptar() {
        try {
            int n = (Integer) leer(spinCantidad, "Número de computadores");
            int latencia = (Integer) leer(spinLatencia, "Latencia de red");
            int cicloMs = (Integer) leer(spinCicloMs, "Duración del ciclo");

            int[] ram = new int[n];
            PoliticaPlanificacion[] pol = new PoliticaPlanificacion[n];
            int[] qua = new int[n];
            for (int i = 0; i < n; i++) {
                ram[i] = (Integer) leer(spinRam[i], "RAM del computador " + (i + 1));
                qua[i] = (Integer) leer(spinQuantum[i], "Quantum del computador " + (i + 1));
                pol[i] = (PoliticaPlanificacion) comboPolitica[i].getSelectedItem();
            }
            resultado = new ConfiguracionInicial(ram, pol, qua, latencia, cicloMs);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dato inválido", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Confirma el texto escrito en el spinner; lanza {@link IllegalArgumentException} si no es válido. */
    private Object leer(JSpinner spinner, String nombre) {
        SpinnerNumberModel modelo = (SpinnerNumberModel) spinner.getModel();
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
            throw new IllegalArgumentException(nombre + ": debe ser un número entero entre "
                    + modelo.getMinimum() + " y " + modelo.getMaximum() + ".");
        }
        return spinner.getValue();
    }

    // -------------------------------------------------------------------------
    // Utilidades de interfaz
    // -------------------------------------------------------------------------

    private JSpinner crearSpinner(int valor, int min, int max, int paso) {
        int v = Math.max(min, Math.min(max, valor));
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(v, min, max, paso));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setBackground(COLOR_INPUT);
        editor.getTextField().setForeground(Color.WHITE);
        editor.getTextField().setCaretColor(Color.WHITE);
        if (editor.getTextField().getFormatter() instanceof DefaultFormatter) {
            // Rechaza al teclear caracteres no numéricos o fuera de rango
            ((DefaultFormatter) editor.getTextField().getFormatter()).setAllowsInvalid(false);
        }
        return spinner;
    }

    private JLabel etiqueta(String texto, boolean negrita) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", negrita ? Font.BOLD : Font.PLAIN, 12));
        l.setForeground(negrita ? COLOR_TITULO : COLOR_MUTED);
        return l;
    }

    private void agregarFila(JPanel panel, String texto, JSpinner spinner) {
        panel.add(etiqueta(texto, false));
        panel.add(spinner);
    }

    private JPanel crearTarjeta(String titulo, JPanel contenido) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(COLOR_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true), new EmptyBorder(12, 16, 12, 16)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(titulo);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(96, 165, 250));
        card.add(l, BorderLayout.NORTH);
        card.add(contenido, BorderLayout.CENTER);
        return card;
    }
}
