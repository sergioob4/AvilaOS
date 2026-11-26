package avilaos.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoUsuario;
import avilaos.enums.OperacionArchivo;
import avilaos.enums.Permiso;
import avilaos.proceso.PeticionArchivo;
import avilaos.proceso.Proceso;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Function;
import javax.swing.*;

/** Recoge una petición y muestra el estado del último proceso enviado desde el formulario. */
final class PanelOperacionesArchivo extends JPanel {

    private final JComboBox<OperacionArchivo> operacion = new JComboBox<>(OperacionArchivo.values());
    private final JTextField ruta = new JTextField(24);
    private final JSpinner bloques = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
    private final JComboBox<Permiso> permiso = new JComboBox<>(Permiso.values());
    private final JTextField nuevoNombre = new JTextField(16);
    private final JLabel resultado = new JLabel(" ");
    private Proceso ultimo;

    PanelOperacionesArchivo(java.util.function.Supplier<ModoUsuario> modo,
                            java.util.function.Supplier<String> usuario, Function<PeticionArchivo, Proceso> enviar) {
        super(new BorderLayout());
        JPanel campos = new JPanel();
        campos.setLayout(new BoxLayout(campos, BoxLayout.Y_AXIS));
        JPanel destino = fila(campos);
        campo(destino, "Operación", operacion);
        campo(destino, "Ruta absoluta", ruta);
        JPanel opciones = fila(campos);
        campo(opciones, "Bloques", bloques);
        campo(opciones, "Permiso", permiso);
        campo(opciones, "Nuevo nombre", nuevoNombre);
        JPanel acciones = fila(campos);
        JButton solicitar = new JButton("Solicitar operación");
        acciones.add(solicitar);
        acciones.add(resultado);
        add(new JScrollPane(campos), BorderLayout.CENTER);

        operacion.setSelectedItem(OperacionArchivo.LEER);
        operacion.addActionListener(e -> actualizarCampos());
        actualizarCampos();
        solicitar.addActionListener(e -> {
            try {
                OperacionArchivo tipo = (OperacionArchivo) operacion.getSelectedItem();
                boolean crear = tipo == OperacionArchivo.CREAR;
                if (crear) bloques.commitEdit();
                var peticion = new PeticionArchivo(tipo, ruta.getText().trim(), usuario.get(),
                        modo.get(), crear ? (int) bloques.getValue() : 0,
                        crear ? (Permiso) permiso.getSelectedItem() : null,
                        tipo == OperacionArchivo.ACTUALIZAR ? nuevoNombre.getText() : null);
                ultimo = enviar.apply(peticion);
                actualizar();
            } catch (java.text.ParseException | IllegalArgumentException | SecurityException ex) {
                ultimo = null;
                resultado.setText("Revise la petición: " + ex.getMessage());
            }
        });
    }

    private static JPanel fila(JPanel campos) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEADING));
        campos.add(fila);
        return fila;
    }

    private static void campo(JPanel fila, String nombre, JComponent control) {
        JLabel etiqueta = new JLabel(nombre);
        etiqueta.setLabelFor(control);
        fila.add(etiqueta);
        fila.add(control);
    }

    private void actualizarCampos() {
        boolean crear = operacion.getSelectedItem() == OperacionArchivo.CREAR;
        bloques.setEnabled(crear);
        permiso.setEnabled(crear);
        nuevoNombre.setEnabled(operacion.getSelectedItem() == OperacionArchivo.ACTUALIZAR);
    }

    void cambiarSesion() {
        ultimo = null;
        resultado.setText(" ");
    }

    void seleccionarRuta(String nuevaRuta) {
        if (nuevaRuta != null && !nuevaRuta.isBlank()) {
            ruta.setText(nuevaRuta);
        }
    }

    void actualizar() {
        if (ultimo == null) return;
        var pcb = ultimo.getPcb();
        String estado = ultimo.getEstado().toString();
        if (pcb.getErrorArchivo() != null) estado = pcb.getErrorArchivo();
        else if (pcb.isTerminoPorDeadline()) estado = "Plazo vencido";
        else if (ultimo.getEstado() == EstadoProceso.TERMINADO) estado = "Operación completada";
        resultado.setText(ultimo.getId() + " · " + ultimo.getNombre() + " · " + estado);
    }
}
