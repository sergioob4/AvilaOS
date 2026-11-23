package avilaos.gui;

import avilaos.hardware.DiscoSimulado;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

/** Observador Swing del disco. Los colores y la selección describen una misma instantánea. */
final class PanelDisco extends JPanel {
    private final DiscoSimulado disco;
    private final JLabel estado = new JLabel();
    private final JLabel detalle = new JLabel("Seleccione un bloque para inspeccionarlo.");
    private final JButton[] celdas;
    private int seleccionado = -1;
    private static final Border CABEZAL = BorderFactory.createLineBorder(new Color(255, 194, 60), 3);
    private static final Border NORMAL = BorderFactory.createEmptyBorder(3, 3, 3, 3);

    PanelDisco(DiscoSimulado disco) {
        super(new BorderLayout(4, 4));
        this.disco = disco;
        celdas = new JButton[disco.vista().bloques().length];
        JPanel mapa = new JPanel(new GridLayout(0, 10, 2, 2));
        for (int i = 0; i < celdas.length; i++) {
            final int numero = i;
            JButton boton = new JButton(String.valueOf(i));
            boton.setOpaque(true);
            boton.setForeground(Color.WHITE);
            boton.setPreferredSize(new Dimension(52, 27));
            boton.addActionListener(e -> { seleccionado = numero; actualizar(); });
            celdas[i] = boton;
            mapa.add(boton);
        }
        add(estado, BorderLayout.NORTH);
        add(new JScrollPane(mapa), BorderLayout.CENTER);
        add(detalle, BorderLayout.SOUTH);
        actualizar();
    }

    void actualizar() {
        DiscoSimulado.Vista vista = disco.vista();
        estado.setText("Cabezal " + vista.cabezal() + " · " + vista.direccion()
                + " · " + (vista.operacion() == null ? "En reposo" : vista.operacion())
                + "     Gris: libre   Azul: archivo   Violeta: swap libre   Naranja: proceso");
        for (DiscoSimulado.Bloque b : vista.bloques()) {
            JButton celda = celdas[b.numero()];
            String uso = b.swap() ? "Swap libre" : "Libre";
            Color color = b.swap() ? new Color(99, 77, 130) : new Color(65, 77, 91);
            if (b.ocupante() != null) {
                uso = (b.swap() ? "Proceso " : "Archivo ") + b.ocupante()
                        + (b.siguiente() >= 0 ? " → " + b.siguiente() : " · Fin de cadena");
                color = b.swap() ? new Color(151, 78, 24) : new Color(25, 102, 147);
            }
            String texto = "Bloque " + b.numero() + " · " + uso;
            celda.setBackground(color);
            celda.setBorder(b.numero() == vista.cabezal() ? CABEZAL : NORMAL);
            celda.setToolTipText(texto);
            if (seleccionado == b.numero()) detalle.setText(texto);
        }
    }
}
