package avilaos.gui;

import avilaos.enums.TipoProceso;
import avilaos.proceso.Proceso;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Diálogo modal para la creación de nuevos procesos en tiempo de ejecución ("Vista 1").
 *
 * <p>Implementa validaciones estrictas de tipo de dato y rango en todos los campos de entrada
 * según lo exigido por las especificaciones de ÁvilaOS (página 10 del documento oficial).</p>
 */
public class DialogoCrearProceso extends JDialog {

    private final ClusterDistribuido cluster;
    private final VentanaPrincipal ventanaPrincipal;

    private JTextField txtNombre;
    private JComboBox<TipoProceso> comboTipo;
    private JSpinner spinInstrucciones;
    private JSpinner spinMemoria;
    private JSpinner spinPrioridad;
    private JSpinner spinDeadline;
    private JComboBox<String> comboComputador;

    // Campos condicionales para I/O Bound
    private JPanel panelOpcionesIO;
    private JSpinner spinCiclosEntreIO;
    private JSpinner spinDuracionIO;

    // Colores Dark Theme
    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_INPUT = new Color(25, 30, 40);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_LABEL = new Color(180, 195, 218); // Alto contraste
    private static final Color COLOR_PRIMARIO = new Color(37, 99, 235);
    private static final Color COLOR_VERDE = new Color(22, 163, 74);

    public DialogoCrearProceso(VentanaPrincipal ventanaPrincipal, ClusterDistribuido cluster) {
        super(ventanaPrincipal, "ÁvilaOS — Crear Nuevo Proceso (Vista 1)", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;
        this.ventanaPrincipal = ventanaPrincipal;

        inicializarUI();
    }

    private void inicializarUI() {
        setSize(520, 640);
        setMinimumSize(new Dimension(460, 540));
        setLocationRelativeTo(ventanaPrincipal);
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // Header
        JPanel panelHeader = new JPanel(new BorderLayout(8, 4));
        panelHeader.setBackground(COLOR_CARD);
        panelHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE),
                new EmptyBorder(16, 22, 16, 22)
        ));

        JLabel lblTitulo = new JLabel("Crear Nuevo Proceso");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(COLOR_TITULO);

        JLabel lblSub = new JLabel("Defina los parámetros del proceso y asígnelo al clúster");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(COLOR_LABEL);

        panelHeader.add(lblTitulo, BorderLayout.NORTH);
        panelHeader.add(lblSub, BorderLayout.SOUTH);
        add(panelHeader, BorderLayout.NORTH);

        // Formulario central
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout(panelForm, BoxLayout.Y_AXIS));
        panelForm.setBackground(COLOR_BG);
        panelForm.setBorder(new EmptyBorder(16, 22, 16, 22));

        // 1. Nombre
        txtNombre = new JTextField("Proceso-Trabajo");
        estilizarCampoTexto(txtNombre);
        panelForm.add(crearFilaCampo("Nombre del Proceso:", txtNombre));
        panelForm.add(Box.createVerticalStrut(10));

        // 2. Tipo de Proceso
        comboTipo = new JComboBox<>(TipoProceso.values());
        estilizarCombo(comboTipo);
        panelForm.add(crearFilaCampo("Tipo de Proceso:", comboTipo));
        panelForm.add(Box.createVerticalStrut(10));

        // 3. Instrucciones Totales (Tiempo de Ejecución)
        spinInstrucciones = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
        estilizarSpinner(spinInstrucciones);
        panelForm.add(crearFilaCampo("Cantidad de Instrucciones (Ciclos):", spinInstrucciones));
        panelForm.add(Box.createVerticalStrut(10));

        // 4. Memoria RAM requerida (MB)
        spinMemoria = new JSpinner(new SpinnerNumberModel(256, 16, 8192, 64));
        estilizarSpinner(spinMemoria);
        panelForm.add(crearFilaCampo("Memoria RAM Requerida (MB):", spinMemoria));
        panelForm.add(Box.createVerticalStrut(10));

        // 5. Nivel de Prioridad (1 a 10)
        spinPrioridad = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        estilizarSpinner(spinPrioridad);
        panelForm.add(crearFilaCampo("Nivel de Prioridad (1 menor, 10 mayor):", spinPrioridad));
        panelForm.add(Box.createVerticalStrut(10));

        // 6. Deadline (Tiempo Límite)
        spinDeadline = new JSpinner(new SpinnerNumberModel(25, 1, 5000, 5));
        estilizarSpinner(spinDeadline);
        panelForm.add(crearFilaCampo("Deadline / Tiempo Límite (Ciclos):", spinDeadline));
        panelForm.add(Box.createVerticalStrut(10));

        // 7. Asignación de Computador (Manual vs Automático)
        comboComputador = new JComboBox<>();
        comboComputador.addItem("Automático (Balanceo ÁvilaOS)");
        for (int i = 0; i < cluster.getCantidadComputadores(); i++) {
            Computador comp = cluster.getComputadores().obtener(i);
            comboComputador.addItem(comp.getId() + " - " + comp.getNombre());
        }
        estilizarCombo(comboComputador);
        panelForm.add(crearFilaCampo("Asignación de Computador:", comboComputador));
        panelForm.add(Box.createVerticalStrut(10));

        // 8. Opciones para I/O Bound
        panelOpcionesIO = new JPanel(new GridLayout(2, 2, 8, 8));
        panelOpcionesIO.setBackground(COLOR_CARD);
        panelOpcionesIO.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel lblCiclosIo = new JLabel("Ciclos entre E/S:");
        lblCiclosIo.setForeground(COLOR_LABEL);
        lblCiclosIo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        spinCiclosEntreIO = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
        estilizarSpinner(spinCiclosEntreIO);

        JLabel lblDuracionIo = new JLabel("Duración bloqueo E/S:");
        lblDuracionIo.setForeground(COLOR_LABEL);
        lblDuracionIo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        spinDuracionIO = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));
        estilizarSpinner(spinDuracionIO);

        panelOpcionesIO.add(lblCiclosIo);
        panelOpcionesIO.add(spinCiclosEntreIO);
        panelOpcionesIO.add(lblDuracionIo);
        panelOpcionesIO.add(spinDuracionIO);
        panelOpcionesIO.setVisible(false);

        panelForm.add(panelOpcionesIO);

        // Listener para mostrar/ocultar opciones según el tipo
        comboTipo.addActionListener(e -> {
            TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
            panelOpcionesIO.setVisible(tipo == TipoProceso.IO_BOUND);
            revalidate();
            repaint();
        });

        JScrollPane scroll = new JScrollPane(panelForm);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(COLOR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);

        // Botones inferiores estilizados con BotonModerno
        JPanel panelBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        panelBottom.setBackground(COLOR_CARD);
        panelBottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));

        VentanaPrincipal.BotonEstilizado btnCancelar = new VentanaPrincipal.BotonEstilizado("Cancelar", new Color(55, 68, 88), Color.WHITE);
        btnCancelar.setPreferredSize(new Dimension(100, 34));
        btnCancelar.addActionListener(e -> dispose());

        VentanaPrincipal.BotonEstilizado btnCrear = new VentanaPrincipal.BotonEstilizado("Crear y Admitir", COLOR_VERDE, Color.WHITE);
        btnCrear.setPreferredSize(new Dimension(140, 34));
        btnCrear.addActionListener(e -> procesarCreacionProceso());

        panelBottom.add(btnCancelar);
        panelBottom.add(btnCrear);
        add(panelBottom, BorderLayout.SOUTH);
    }

    private void procesarCreacionProceso() {
        // Validaciones estrictas de tipo de dato y rango
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            mostrarError("El nombre del proceso no puede estar vacío.");
            txtNombre.requestFocus();
            return;
        }

        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        int instrucciones = (int) spinInstrucciones.getValue();
        int memoria = (int) spinMemoria.getValue();
        int prioridad = (int) spinPrioridad.getValue();
        int deadline = (int) spinDeadline.getValue();

        if (instrucciones <= 0) {
            mostrarError("La cantidad de instrucciones debe ser mayor a 0.");
            return;
        }

        if (memoria <= 0) {
            mostrarError("La memoria RAM solicitada debe ser mayor a 0 MB.");
            return;
        }

        if (prioridad <= 0) {
            mostrarError("La prioridad debe ser un número positivo.");
            return;
        }

        if (deadline < instrucciones) {
            int opc = JOptionPane.showConfirmDialog(this,
                    "El deadline (" + deadline + " ciclos) es menor a la cantidad de instrucciones (" + instrucciones + ").\n"
                    + "El proceso probablemente expirará antes de completar. ¿Desea crearlo de todas formas?",
                    "Advertencia de Deadline Estricto",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opc != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Determinar destino
        int seleccionComp = comboComputador.getSelectedIndex();
        long cicloActual = cluster.getRelojGlobal().getCicloActual();

        Proceso proceso;
        if (seleccionComp == 0) {
            // Asignación automática por ÁvilaOS (balanceo de carga)
            proceso = new Proceso(nombre, "Cluster", tipo, prioridad, memoria, deadline, instrucciones);
            if (tipo == TipoProceso.IO_BOUND) {
                proceso.getPcb().setCiclosEntreIO((int) spinCiclosEntreIO.getValue());
                proceso.getPcb().setDuracionBloqueoIO((int) spinDuracionIO.getValue());
            }
            cluster.asignarProcesoAutomatico(proceso);
            ventanaPrincipal.actualizarTodo();
            JOptionPane.showMessageDialog(this,
                    "Proceso [" + proceso.getPcb().getId() + "] asignado automáticamente por ÁvilaOS al computador: "
                    + proceso.getPcb().getComputadorAsignado(),
                    "Proceso Creado", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Asignación manual
            Computador comp = cluster.getComputadores().obtener(seleccionComp - 1);
            if (comp == null) {
                mostrarError("El computador seleccionado no está disponible.");
                return;
            }

            // Validar que la memoria no exceda la RAM total del computador
            if (memoria > comp.getMemoria().getCapacidadTotal()) {
                mostrarError("La memoria solicitada (" + memoria + " MB) supera la capacidad máxima de la RAM de "
                        + comp.getNombre() + " (" + comp.getMemoria().getCapacidadTotal() + " MB).\n"
                        + "Este proceso nunca podrá ser admitido.");
                return;
            }

            proceso = new Proceso(nombre, comp.getId(), tipo, prioridad, memoria, deadline, instrucciones);
            if (tipo == TipoProceso.IO_BOUND) {
                proceso.getPcb().setCiclosEntreIO((int) spinCiclosEntreIO.getValue());
                proceso.getPcb().setDuracionBloqueoIO((int) spinDuracionIO.getValue());
            }
            comp.admitirProceso(proceso, cicloActual);
            ventanaPrincipal.actualizarTodo();
            JOptionPane.showMessageDialog(this,
                    "Proceso [" + proceso.getPcb().getId() + "] ingresado a la cola de " + comp.getNombre() + ".",
                    "Proceso Creado", JOptionPane.INFORMATION_MESSAGE);
        }

        dispose();
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error de Validación", JOptionPane.ERROR_MESSAGE);
    }

    private JPanel crearFilaCampo(String etiqueta, JComponent componente) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(COLOR_BG);

        JLabel lbl = new JLabel(etiqueta);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(COLOR_LABEL);

        p.add(lbl, BorderLayout.NORTH);
        p.add(componente, BorderLayout.CENTER);
        return p;
    }

    private void estilizarCampoTexto(JTextField txt) {
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBackground(COLOR_INPUT);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(COLOR_PRIMARIO);
        txt.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private void estilizarCombo(JComboBox<?> combo) {
        PanelComputador.estilizarComboBox(combo);
    }

    private void estilizarSpinner(JSpinner spin) {
        spin.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JComponent editor = spin.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setBackground(COLOR_INPUT);
            ((JSpinner.DefaultEditor) editor).getTextField().setForeground(Color.WHITE);
            ((JSpinner.DefaultEditor) editor).getTextField().setCaretColor(COLOR_PRIMARIO);
        }
    }
}
