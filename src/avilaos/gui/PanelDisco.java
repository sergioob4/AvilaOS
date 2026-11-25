package avilaos.gui;

import avilaos.hardware.DiscoSimulado;
import avilaos.hardware.SolicitudDisco;
import avilaos.enums.PoliticaDisco;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

/** Observador Swing del disco. Los colores y la selección describen una misma instantánea. */
final class PanelDisco extends JPanel {
    private final DiscoSimulado disco;
    private final JLabel estado = new JLabel();
    private final JLabel detalle = new JLabel("Seleccione un bloque para inspeccionarlo.");
    private final JButton[] celdas;
    private final JComboBox<PoliticaDisco> politica = new JComboBox<>(PoliticaDisco.values());
    private final JList<SolicitudDisco> pendientes = new JList<>();
    private final JLabel activa = new JLabel();
    private final JTabbedPane vistas = new JTabbedPane();
    private int seleccionado = -1;
    private static final Color SWAP_LIBRE = new Color(190, 132, 255);
    private static final Color TEXTO_SWAP_LIBRE = new Color(29, 11, 49);
    private static final Color[] PALETA_ARCHIVOS = {
        new Color(25, 102, 147),
        new Color(22, 138, 107),
        new Color(14, 116, 144),
        new Color(79, 70, 229),
        new Color(147, 51, 234),
        new Color(190, 24, 93),
        new Color(180, 83, 9),
        new Color(21, 128, 61),
        new Color(3, 105, 161),
        new Color(109, 40, 217)
    };
    private static final Border CABEZAL = BorderFactory.createLineBorder(new Color(255, 194, 60), 3);
    private static final Border NORMAL = BorderFactory.createEmptyBorder(3, 3, 3, 3);

    /** Color determinista compartido entre el mapa del disco y la tabla de asignación. */
    static Color colorArchivo(String ruta) {
        if (ruta == null || ruta.isEmpty()) {
            return PALETA_ARCHIVOS[0];
        }
        return PALETA_ARCHIVOS[Math.floorMod(ruta.hashCode(), PALETA_ARCHIVOS.length)];
    }

    PanelDisco(DiscoSimulado disco) {
        super(new BorderLayout(4, 4));
        this.disco = disco;
        celdas = new JButton[disco.vista().bloques().length];
        JPanel mapa = new JPanel(new GridLayout(0, 10, 2, 2));
        for (int i = 0; i < celdas.length; i++) {
            final int numero = i;
            JButton boton = new JButton(String.valueOf(i));
            boton.setContentAreaFilled(false);
            boton.setOpaque(true);
            boton.setForeground(Color.WHITE);
            boton.setPreferredSize(new Dimension(52, 27));
            boton.addActionListener(e -> { seleccionado = numero; actualizar(); });
            celdas[i] = boton;
            mapa.add(boton);
        }
        JPanel cabecera = new JPanel(new BorderLayout());
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controles.add(new JLabel("Política de disco"));
        politica.setSelectedItem(disco.vista().politica());
        politica.addActionListener(e -> disco.cambiarPolitica((PoliticaDisco) politica.getSelectedItem()));
        controles.add(politica);
        controles.add(activa);
        cabecera.add(controles, BorderLayout.NORTH);
        cabecera.add(estado, BorderLayout.SOUTH);
        add(cabecera, BorderLayout.NORTH);
        vistas.addTab("Mapa", new JScrollPane(mapa));
        vistas.addTab("Cola de E/S", new JScrollPane(pendientes));
        add(vistas, BorderLayout.CENTER);
        add(detalle, BorderLayout.SOUTH);
        actualizar();
    }

    void actualizar() {
        DiscoSimulado.Vista vista = disco.vista();
        pendientes.setListData(vista.pendientes());
        vistas.setTitleAt(1, "Cola de E/S (" + vista.pendientes().length + ")");
        activa.setText("En servicio: " + (vista.activa() == null ? "—" : vista.activa())
                + "   ·   Recorrido: " + vista.desplazamiento());
        estado.setText("Cabezal " + vista.cabezal() + " · " + vista.direccion()
                + " · " + (vista.operacion() == null ? "En reposo" : vista.operacion())
                + "     Gris: libre   Azul: archivo   Violeta: swap libre   Naranja: proceso");
        for (DiscoSimulado.Bloque b : vista.bloques()) {
            JButton celda = celdas[b.numero()];
            String uso = b.swap() ? "Swap libre" : "Libre";
            Color color = b.swap() ? SWAP_LIBRE : new Color(65, 77, 91);
            if (b.ocupante() != null) {
                uso = (b.swap() ? "Proceso " : "Archivo ") + b.ocupante()
                        + (b.siguiente() >= 0 ? " → " + b.siguiente() : " · Fin de cadena");
                color = b.swap() ? new Color(151, 78, 24) : colorArchivo(b.ocupante());
            }
            String texto = "Bloque " + b.numero() + " · " + uso;
            celda.setBackground(color);
            celda.setForeground(b.swap() && b.ocupante() == null ? TEXTO_SWAP_LIBRE : Color.WHITE);
            celda.setBorder(b.numero() == vista.cabezal() ? CABEZAL : NORMAL);
            celda.setToolTipText(texto);
            if (seleccionado == b.numero()) detalle.setText(texto);
        }
    }
}
