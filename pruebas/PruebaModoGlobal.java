import avilaos.enums.*;
import avilaos.gui.VentanaPrincipal;
import avilaos.proceso.PeticionArchivo;
import avilaos.sistema.*;
import java.awt.*;
import java.util.function.Predicate;
import javax.swing.*;

/** Ejecutar con escritorio disponible: java -cp dist/AvilaOS.jar pruebas/PruebaModoGlobal.java */
class PruebaModoGlobal {
    static <T> T buscar(Container padre, Class<T> tipo, Predicate<T> coincide) {
        for (Component c : padre.getComponents()) {
            if (tipo.isInstance(c) && coincide.test(tipo.cast(c))) return tipo.cast(c);
            if (c instanceof Container hijo) {
                T resultado = buscar(hijo, tipo, coincide);
                if (resultado != null) return resultado;
            }
        }
        return null;
    }

    static void comprobar(boolean condicion, String detalle) {
        if (!condicion) throw new AssertionError(detalle);
    }

    static PeticionArchivo peticion(OperacionArchivo op, String ruta) {
        boolean crear = op == OperacionArchivo.CREAR;
        return new PeticionArchivo(op, ruta, "user1", ModoUsuario.USUARIO, crear ? 1 : 0,
                crear ? Permiso.PRIVADO : null, op == OperacionArchivo.ACTUALIZAR ? "renombrado" : null);
    }

    static void denegado(Runnable accion) {
        try { accion.run(); }
        catch (SecurityException esperado) { return; }
        throw new AssertionError("Se permitió una operación restringida");
    }

    public static void main(String[] args) throws Exception {
        var cluster = ConfiguracionInicial.porDefecto().crearCluster();
        var reloj = cluster.getRelojGlobal();
        var comp = cluster.getComputadores().obtener(0);
        var fs = comp.getSistemaArchivos();
        var admin = ModoUsuario.ADMINISTRADOR;
        fs.crearDirectorio("/", "ana", "user1", Permiso.PRIVADO, admin);
        fs.crearArchivo("/", "privado", "otro", Permiso.PRIVADO, 1, null, "admin", admin);
        fs.crearArchivo("/", "publico", "otro", Permiso.PUBLICO, 1, null, "admin", admin);
        fs.validarPermiso(peticion(OperacionArchivo.CREAR, "/ana/nuevo"));
        fs.validarPermiso(peticion(OperacionArchivo.LEER, "/publico"));
        denegado(() -> fs.validarPermiso(peticion(OperacionArchivo.CREAR, "/nuevo")));
        denegado(() -> fs.validarPermiso(peticion(OperacionArchivo.ELIMINAR, "/publico")));
        denegado(() -> fs.validarPermiso(peticion(OperacionArchivo.ACTUALIZAR, "/publico")));

        SwingUtilities.invokeAndWait(() -> {
            var ventana = new VentanaPrincipal(cluster);
            try {
                var modo = buscar(ventana, JComboBox.class, c -> c.getSelectedItem() instanceof ModoUsuario);
                var iniciar = buscar(ventana, JButton.class, b -> b.getText().equals("Iniciar / Reanudar"));
                reloj.iniciar();
                long inicial = reloj.getCicloActual();
                modo.setSelectedItem(ModoUsuario.USUARIO);
                comprobar(!iniciar.isEnabled(), "El reloj quedó editable");
                denegado(ventana::inyectarCargaPrueba);
                var tabs = buscar(ventana, JTabbedPane.class, t -> t.indexOfTab("Operaciones de archivo") >= 0);
                comprobar(!tabs.isEnabledAt(tabs.indexOfTab("Disco local")), "Disco visible en modo Usuario");
                var form = (Container) tabs.getComponentAt(tabs.indexOfTab("Operaciones de archivo"));
                var ruta = buscar(form, JTextField.class, f -> f.getColumns() == 24);
                var enviar = buscar(form, JButton.class, b -> "Solicitar operación".equals(b.getText()));
                ruta.setText("/privado");
                enviar.doClick();
                comprobar(buscar(form, JLabel.class, l -> l.getText() != null && l.getText().contains("privado y pertenece")) != null,
                        "Debe explicarse el rechazo");
                comprobar(comp.getMemoria().getMemoriaUsada() == 0, "El rechazo admitió un proceso");
                comprobar(!reloj.estaEnPausa() && reloj.estaEjecutando(), "El rechazo detuvo el reloj");
                ruta.setText("/publico");
                enviar.doClick();
                comprobar(comp.getMemoria().getMemoriaUsada() > 0, "La lectura pública debe admitirse");
                modo.setSelectedItem(admin);
                comprobar(iniciar.isEnabled() && tabs.isEnabledAt(tabs.indexOfTab("Disco local")), "Restaurar controles");
                comprobar(reloj.getCicloActual() >= inicial, "El reloj se reinició");
            } finally {
                reloj.detener();
                ventana.dispose();
            }
        });
        System.out.println("OK: permisos, sesión global, controles y reloj.");
    }
}
