package avilaos.gui;

import avilaos.sincronizacion.BufferAcotado;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
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
import java.awt.Window;
import java.text.ParseException;

/**
 * Diálogo para crear un buffer acotado en tiempo de ejecución (Requerimiento 4 y 7).
 *
 * <p>Pide nombre, computador anfitrión, capacidad y la RAM que el buffer ocupará en su
 * anfitrión. Valida tipo de dato, rango y disponibilidad de RAM antes de crearlo.</p>
 */
public class DialogoCrearBuffer extends JDialog {

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_MUTED = new Color(175, 190, 215);
    private static final Color COLOR_INPUT = new Color(24, 28, 36);

    private static final int MAX_CAPACIDAD = 1000;
    private static final int MAX_NOMBRE = 30;

    private final ClusterDistribuido cluster;
    private final Runnable alCrear;
    private final String idBuffer;

    private final JTextField txtNombre;
    private final JComboBox<String> comboAnfitrion;
    private final JSpinner spinCapacidad;
    private final JSpinner spinMemoria;
    private final JLabel lblRamLibre;

    /**
     * @param parent ventana padre
     * @param cluster clúster donde se creará el buffer
     * @param alCrear acción a ejecutar tras crear el buffer (p. ej. refrescar la vista); puede ser {@code null}
     */
    public DialogoCrearBuffer(Window parent, ClusterDistribuido cluster, Runnable alCrear) {
        super(parent, "ÁvilaOS — Nuevo Buffer Acotado", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;
        this.alCrear = alCrear;
        this.idBuffer = generarIdLibre();

        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Crear buffer acotado " + idBuffer);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titulo.setForeground(COLOR_TITULO);
        JLabel sub = new JLabel("Se sincroniza con 3 semáforos (mutex, espacios, elementos) y ocupa RAM en su anfitrión.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(COLOR_MUTED);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(COLOR_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE), new EmptyBorder(14, 20, 14, 20)));
        header.add(titulo, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        txtNombre = new JTextField("Buffer " + idBuffer);
        txtNombre.setBackground(COLOR_INPUT);
        txtNombre.setForeground(Color.WHITE);
        txtNombre.setCaretColor(Color.WHITE);
        txtNombre.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtNombre.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true), new EmptyBorder(4, 8, 4, 8)));

        comboAnfitrion = new JComboBox<>();
        int total = cluster.getCantidadComputadores();
        for (int i = 0; i < total; i++) {
            Computador c = cluster.getComputadores().obtener(i);
            comboAnfitrion.addItem(c.getId() + " - " + c.getNombre());
        }
        PanelComputador.estilizarComboBox(comboAnfitrion);

        spinCapacidad = crearSpinner(5, 1, MAX_CAPACIDAD, 1);
        spinMemoria = crearSpinner(64, 1, 65536, 16);

        lblRamLibre = new JLabel();
        lblRamLibre.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRamLibre.setForeground(COLOR_MUTED);
        comboAnfitrion.addActionListener(e -> actualizarRamLibre());
        actualizarRamLibre();

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(COLOR_BG);
        form.setBorder(new EmptyBorder(16, 20, 8, 20));
        form.add(fila("Nombre del buffer:", txtNombre));
        form.add(fila("Computador anfitrión:", comboAnfitrion));
        form.add(fila("Capacidad (elementos, 1–" + MAX_CAPACIDAD + "):", spinCapacidad));
        form.add(fila("RAM que ocupa en el anfitrión (MB):", spinMemoria));
        form.add(lblRamLibre);
        add(form, BorderLayout.CENTER);

        VentanaPrincipal.BotonEstilizado btnCancelar = new VentanaPrincipal.BotonEstilizado(
                "Cancelar", new Color(55, 68, 88), Color.WHITE);
        VentanaPrincipal.BotonEstilizado btnCrear = new VentanaPrincipal.BotonEstilizado(
                "Crear Buffer", new Color(22, 163, 74), Color.WHITE);
        btnCancelar.setPreferredSize(new Dimension(100, 34));
        btnCrear.setPreferredSize(new Dimension(130, 34));
        btnCancelar.addActionListener(e -> dispose());
        btnCrear.addActionListener(e -> crearBuffer());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        botones.setBackground(COLOR_CARD);
        botones.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));
        botones.add(btnCancelar);
        botones.add(btnCrear);
        add(botones, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btnCrear);

        setSize(480, 400);
        setLocationRelativeTo(parent);
    }

    // -------------------------------------------------------------------------
    // Lógica de creación
    // -------------------------------------------------------------------------

    private void crearBuffer() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            error("El nombre del buffer no puede estar vacío.");
            txtNombre.requestFocus();
            return;
        }
        if (nombre.length() > MAX_NOMBRE) {
            error("El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
            return;
        }

        int capacidad;
        int memoria;
        try {
            capacidad = leer(spinCapacidad, "Capacidad");
            memoria = leer(spinMemoria, "RAM del buffer");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            return;
        }

        Computador host = anfitrionSeleccionado();
        if (host == null) {
            error("Seleccione un computador anfitrión válido.");
            return;
        }
        if (!host.getMemoria().hayEspacioDisponible(memoria)) {
            error("RAM insuficiente en " + host.getNombre() + ": el buffer necesita " + memoria
                    + " MB y hay " + host.getMemoria().getMemoriaLibre() + " MB libres.");
            return;
        }
        if (cluster.getGestorSincronizacion().buscarBuffer(idBuffer) != null) {
            error("Ya existe un buffer con el ID " + idBuffer + ". Cierre y vuelva a abrir el diálogo.");
            return;
        }

        BufferAcotado buffer = cluster.getGestorSincronizacion()
                .crearBuffer(idBuffer, nombre, host.getId(), capacidad, memoria);
        if (buffer == null) {
            error("No se pudo crear el buffer (RAM insuficiente o anfitrión no disponible).");
            return;
        }

        if (alCrear != null) {
            alCrear.run();
        }
        dispose();
    }

    private Computador anfitrionSeleccionado() {
        int idx = comboAnfitrion.getSelectedIndex();
        return idx < 0 ? null : cluster.getComputadores().obtener(idx);
    }

    private void actualizarRamLibre() {
        Computador host = anfitrionSeleccionado();
        if (host != null) {
            lblRamLibre.setText("RAM libre en " + host.getId() + ": " + host.getMemoria().getMemoriaLibre()
                    + " MB de " + host.getMemoria().getCapacidadTotal() + " MB");
        }
    }

    /** Primer ID "Bn" que no esté en uso. */
    private String generarIdLibre() {
        int n = cluster.getGestorSincronizacion().getCantidadBuffers() + 1;
        while (cluster.getGestorSincronizacion().buscarBuffer("B" + n) != null) {
            n++;
        }
        return "B" + n;
    }

    // -------------------------------------------------------------------------
    // Utilidades de interfaz
    // -------------------------------------------------------------------------

    private int leer(JSpinner spinner, String nombre) {
        SpinnerNumberModel m = (SpinnerNumberModel) spinner.getModel();
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
            throw new IllegalArgumentException(nombre + ": debe ser un entero entre "
                    + m.getMinimum() + " y " + m.getMaximum() + ".");
        }
        return (Integer) spinner.getValue();
    }

    private JSpinner crearSpinner(int valor, int min, int max, int paso) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(valor, min, max, paso));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setBackground(COLOR_INPUT);
        editor.getTextField().setForeground(Color.WHITE);
        editor.getTextField().setCaretColor(Color.WHITE);
        if (editor.getTextField().getFormatter() instanceof DefaultFormatter) {
            ((DefaultFormatter) editor.getTextField().getFormatter()).setAllowsInvalid(false);
        }
        return spinner;
    }

    private JPanel fila(String etiqueta, Component campo) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(COLOR_BG);
        p.setBorder(new EmptyBorder(0, 0, 10, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(etiqueta);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(COLOR_MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(campo, BorderLayout.CENTER);
        return p;
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error de Validación", JOptionPane.ERROR_MESSAGE);
    }
}
