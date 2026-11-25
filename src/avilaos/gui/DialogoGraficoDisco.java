package avilaos.gui;

import avilaos.enums.PoliticaDisco;
import avilaos.hardware.DiscoSimulado;
import avilaos.interfaces.IComponenteReloj;
import avilaos.sistema.ClusterDistribuido;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana no modal con el gráfico de desplazamiento acumulado del cabezal del disco
 * respecto al tiempo, permitiendo comparar visualmente las políticas de disco
 * (FIFO, SSTF, SCAN y C-SCAN).
 *
 * <p>Es un observador puro: se suscribe al {@code RelojGlobal} para repintarse en cada ciclo
 * leyendo instantáneas seguras de {@link DiscoSimulado#getMetricas()}.</p>
 */
public class DialogoGraficoDisco extends JDialog implements IComponenteReloj {

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_MUTED = new Color(175, 190, 218);

    private final ClusterDistribuido cluster;
    private final DiscoSimulado disco;
    private final PanelGraficoDisco panelGrafico;
    private final JLabel lblResumen;

    public DialogoGraficoDisco(Window parent, ClusterDistribuido cluster) {
        super(parent, "ÁvilaOS — Desplazamiento Acumulado del Cabezal vs. Tiempo", ModalityType.MODELESS);
        this.cluster = cluster;
        this.disco = cluster.getComputadores().obtener(0).getDisco();

        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // Encabezado
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBackground(COLOR_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE),
                new EmptyBorder(12, 18, 12, 18)
        ));

        JLabel titulo = new JLabel("Desplazamiento Acumulado del Cabezal por Política de Disco");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titulo.setForeground(COLOR_TITULO);

        lblResumen = new JLabel();
        lblResumen.setFont(new Font("Consolas", Font.PLAIN, 12));
        lblResumen.setForeground(COLOR_MUTED);

        header.add(titulo, BorderLayout.NORTH);
        header.add(lblResumen, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Gráfico central
        panelGrafico = new PanelGraficoDisco(disco);
        add(panelGrafico, BorderLayout.CENTER);

        // Barra inferior
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        bottom.setBackground(COLOR_CARD);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));

        VentanaPrincipal.BotonEstilizado btnLimpiar = new VentanaPrincipal.BotonEstilizado(
                "Limpiar comparativa", new Color(51, 65, 85), Color.WHITE);
        btnLimpiar.setPreferredSize(new Dimension(165, 32));
        btnLimpiar.addActionListener(e -> {
            disco.limpiarComparativa();
            refrescar();
        });

        VentanaPrincipal.BotonEstilizado btnCerrar = new VentanaPrincipal.BotonEstilizado(
                "Cerrar", new Color(37, 99, 235), Color.WHITE);
        btnCerrar.setPreferredSize(new Dimension(95, 32));
        btnCerrar.addActionListener(e -> dispose());

        bottom.add(btnLimpiar);
        bottom.add(btnCerrar);
        add(bottom, BorderLayout.SOUTH);

        refrescar();
        pack();
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(parent);

        cluster.getRelojGlobal().registrarComponente(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                DialogoGraficoDisco.this.cluster.getRelojGlobal().desregistrarComponente(DialogoGraficoDisco.this);
            }
        });
    }

    private void refrescar() {
        DiscoSimulado.MetricasDisco m = disco.getMetricas();
        DiscoSimulado.Vista v = disco.vista();
        lblResumen.setText(String.format(
                "Política activa: %s  |  Desplazamiento actual: %d bloques  |  Espera prom. E/S: %.2f ciclos  |  Atendidas: %d",
                v.politica(), m.desplazamientoTotal(), m.tiempoEsperaPromedio(), m.totalSolicitudesAtendidas()));
        panelGrafico.repaint();
    }

    @Override
    public void ejecutarCiclo(long cicloActual) {
        SwingUtilities.invokeLater(this::refrescar);
    }

    private static Color colorPolitica(PoliticaDisco p) {
        return switch (p) {
            case FIFO -> new Color(239, 83, 80);    // Rojo coral
            case SSTF -> new Color(34, 197, 94);    // Verde esmeralda
            case SCAN -> new Color(245, 158, 11);   // Ámbar
            case C_SCAN -> new Color(56, 189, 248); // Azul celeste
        };
    }

    // =========================================================================
    // Panel de dibujo propio (sin librerías externas)
    // =========================================================================

    private static final class PanelGraficoDisco extends JPanel {
        private static final Color COLOR_FONDO = new Color(16, 20, 26);
        private static final Color COLOR_GRILLA = new Color(45, 55, 72);
        private static final Color COLOR_TEXTO = new Color(175, 190, 218);

        private final DiscoSimulado disco;

        PanelGraficoDisco(DiscoSimulado disco) {
            this.disco = disco;
            setBackground(COLOR_FONDO);
            setPreferredSize(new Dimension(820, 420));
            setBorder(new LineBorder(COLOR_BORDE, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            int izq = 64, der = 24, arr = 20, abj = 76;
            int ancho = getWidth() - izq - der;
            int alto = getHeight() - arr - abj;
            if (ancho <= 0 || alto <= 0) {
                g2.dispose();
                return;
            }

            DiscoSimulado.MetricasDisco metricas = disco.getMetricas();
            DiscoSimulado.PuntoDesplazamiento[] actual = metricas.serieActual();
            DiscoSimulado.SeriePolitica[] comp = metricas.comparativa();

            long cicloMax = 1;
            long despMax = 10;
            boolean hayDatos = actual.length > 0 || comp.length > 0;

            for (DiscoSimulado.SeriePolitica s : comp) {
                for (DiscoSimulado.PuntoDesplazamiento pt : s.puntos()) {
                    cicloMax = Math.max(cicloMax, pt.ciclo());
                    despMax = Math.max(despMax, pt.desplazamiento());
                }
            }
            for (DiscoSimulado.PuntoDesplazamiento pt : actual) {
                cicloMax = Math.max(cicloMax, pt.ciclo());
                despMax = Math.max(despMax, pt.desplazamiento());
            }
            // Margen superior del 10% en Y
            long techoY = Math.max(10, (long) Math.ceil(despMax * 1.1));

            // Grilla horizontal (eje Y: desplazamiento en bloques)
            g2.setStroke(new BasicStroke(1f));
            for (int k = 0; k <= 5; k++) {
                long valorY = techoY * k / 5;
                int y = arr + alto - (int) (alto * k / 5.0);
                g2.setColor(COLOR_GRILLA);
                g2.drawLine(izq, y, izq + ancho, y);
                g2.setColor(COLOR_TEXTO);
                String s = String.valueOf(valorY);
                g2.drawString(s, izq - 8 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
            }

            // Grilla vertical (eje X: ciclos)
            for (int k = 0; k <= 5; k++) {
                long valorX = cicloMax * k / 5;
                int x = izq + ancho * k / 5;
                g2.setColor(COLOR_GRILLA);
                g2.drawLine(x, arr, x, arr + alto);
                g2.setColor(COLOR_TEXTO);
                String s = String.valueOf(valorX);
                g2.drawString(s, x - fm.stringWidth(s) / 2, arr + alto + 15);
            }

            g2.setColor(COLOR_TEXTO);
            g2.drawRect(izq, arr, ancho, alto);
            g2.drawString("Ciclo de reloj", izq + ancho / 2 - 34, arr + alto + 32);
            g2.drawString("Bloques", 8, arr + 12);

            if (!hayDatos) {
                g2.drawString("Sin datos aún: ejecute la simulación para observar el desplazamiento del cabezal.",
                        izq + 24, arr + alto / 2);
                g2.dispose();
                return;
            }

            // 1. Dibujar series comparativas archivadas (trazo discontinuo suave)
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            for (DiscoSimulado.SeriePolitica serie : comp) {
                g2.setColor(colorPolitica(serie.politica()));
                dibujarSerie(g2, serie.puntos(), izq, arr, ancho, alto, cicloMax, techoY, false);
            }

            // 2. Dibujar serie activa (trazo sólido más grueso, coloreado por política en cada tramo)
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            dibujarSerie(g2, actual, izq, arr, ancho, alto, cicloMax, techoY, true);

            // 3. Leyenda inferior de políticas
            int xLey = izq;
            int yLey = arr + alto + 54;
            for (PoliticaDisco p : PoliticaDisco.values()) {
                g2.setColor(colorPolitica(p));
                g2.fillRect(xLey, yLey - 6, 14, 4);

                String extra = "";
                for (DiscoSimulado.SeriePolitica sp : comp) {
                    if (sp.politica() == p) {
                        extra = " (" + sp.desplazamientoFinal() + " blq)";
                        break;
                    }
                }
                if (actual.length > 0 && actual[actual.length - 1].politica() == p) {
                    extra = " (" + actual[actual.length - 1].desplazamiento() + " blq - activa)";
                }
                String txt = p + extra;
                g2.setColor(COLOR_TEXTO);
                g2.drawString(txt, xLey + 20, yLey);
                xLey += 20 + fm.stringWidth(txt) + 22;
            }

            g2.dispose();
        }

        private static void dibujarSerie(Graphics2D g2, DiscoSimulado.PuntoDesplazamiento[] puntos,
                                         int izq, int arr, int ancho, int alto,
                                         long cicloMax, long techoY, boolean colorearPorTramo) {
            if (puntos.length == 0) return;
            for (int i = 1; i < puntos.length; i++) {
                DiscoSimulado.PuntoDesplazamiento p1 = puntos[i - 1];
                DiscoSimulado.PuntoDesplazamiento p2 = puntos[i];
                if (colorearPorTramo) {
                    g2.setColor(colorPolitica(p2.politica()));
                }
                int x1 = izq + (int) (p1.ciclo() * ancho / Math.max(1L, cicloMax));
                int x2 = izq + (int) (p2.ciclo() * ancho / Math.max(1L, cicloMax));
                int y1 = arr + alto - (int) (p1.desplazamiento() * alto / Math.max(1L, techoY));
                int y2 = arr + alto - (int) (p2.desplazamiento() * alto / Math.max(1L, techoY));
                g2.drawLine(x1, y1, x2, y2);
            }
            DiscoSimulado.PuntoDesplazamiento ultimo = puntos[puntos.length - 1];
            if (colorearPorTramo) {
                g2.setColor(colorPolitica(ultimo.politica()));
            }
            int xu = izq + (int) (ultimo.ciclo() * ancho / Math.max(1L, cicloMax));
            int yu = arr + alto - (int) (ultimo.desplazamiento() * alto / Math.max(1L, techoY));
            g2.fillOval(xu - 4, yu - 4, 8, 8);
        }
    }
}
