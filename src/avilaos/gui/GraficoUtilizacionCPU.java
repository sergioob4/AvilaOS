package avilaos.gui;

import avilaos.estructuras.ListaEnlazada;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Gráfico de líneas propio (sin librerías externas) con la utilización de CPU
 * de todos los computadores del clúster respecto al tiempo (Requerimiento 8).
 *
 * <p>Es un observador: lee el historial que mantiene cada {@code CPU} y solo dibuja.</p>
 */
public class GraficoUtilizacionCPU extends JPanel {

    private static final Color COLOR_FONDO = new Color(16, 20, 26);
    private static final Color COLOR_GRILLA = new Color(45, 55, 72);
    private static final Color COLOR_TEXTO = new Color(175, 190, 218);
    private static final Color[] PALETA = {
        new Color(96, 165, 250), new Color(34, 197, 94), new Color(245, 158, 11),
        new Color(239, 83, 80), new Color(168, 85, 247), new Color(13, 148, 136)
    };

    private final ClusterDistribuido cluster;

    public GraficoUtilizacionCPU(ClusterDistribuido cluster) {
        this.cluster = cluster;
        setBackground(COLOR_FONDO);
        setPreferredSize(new Dimension(760, 400));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();

        int izq = 50, der = 20, arr = 20, abj = 60;
        int ancho = getWidth() - izq - der;
        int alto = getHeight() - arr - abj;
        if (ancho <= 0 || alto <= 0) {
            g2.dispose();
            return;
        }

        // Rango de ciclos: unión de los historiales de todos los CPUs
        ListaEnlazada<Computador> comps = cluster.getComputadores();
        int total = comps.obtenerTamano();
        long cicloMin = Long.MAX_VALUE;
        long cicloMax = 0;
        for (int i = 0; i < total; i++) {
            Computador c = comps.obtener(i);
            double[] h = c.getCpu().getHistorialUtilizacion();
            if (h.length > 0) {
                long ini = c.getCpu().getCicloInicialHistorial();
                cicloMin = Math.min(cicloMin, ini);
                cicloMax = Math.max(cicloMax, ini + h.length - 1);
            }
        }
        boolean hayDatos = cicloMax > 0;
        long rango = hayDatos ? Math.max(1, cicloMax - cicloMin) : 1;

        // Grilla y eje Y (0-100 %)
        g2.setStroke(new BasicStroke(1f));
        for (int p = 0; p <= 100; p += 20) {
            int y = arr + alto - (int) (alto * p / 100.0);
            g2.setColor(COLOR_GRILLA);
            g2.drawLine(izq, y, izq + ancho, y);
            g2.setColor(COLOR_TEXTO);
            String s = p + "%";
            g2.drawString(s, izq - 8 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
        }
        g2.setColor(COLOR_TEXTO);
        g2.drawRect(izq, arr, ancho, alto);

        // Eje X (ciclos globales)
        if (hayDatos) {
            for (int k = 0; k <= 5; k++) {
                long ciclo = cicloMin + rango * k / 5;
                int x = izq + ancho * k / 5;
                g2.setColor(COLOR_GRILLA);
                g2.drawLine(x, arr, x, arr + alto);
                g2.setColor(COLOR_TEXTO);
                String s = String.valueOf(ciclo);
                g2.drawString(s, x - fm.stringWidth(s) / 2, arr + alto + 15);
            }
        } else {
            g2.drawString("Sin datos: inicie la simulación para ver la utilización.", izq + 20, arr + alto / 2);
        }
        g2.drawString("Ciclo de reloj", izq + ancho / 2 - 30, arr + alto + 32);

        // Series y leyenda
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int xLeyenda = izq;
        for (int i = 0; i < total; i++) {
            Computador c = comps.obtener(i);
            Color color = PALETA[i % PALETA.length];
            double[] h = c.getCpu().getHistorialUtilizacion();
            long ini = c.getCpu().getCicloInicialHistorial();

            g2.setColor(color);
            for (int j = 1; j < h.length; j++) {
                int x1 = izq + (int) ((ini + j - 1 - cicloMin) * ancho / rango);
                int x2 = izq + (int) ((ini + j - cicloMin) * ancho / rango);
                int y1 = arr + alto - (int) (alto * h[j - 1] / 100.0);
                int y2 = arr + alto - (int) (alto * h[j] / 100.0);
                g2.drawLine(x1, y1, x2, y2);
            }

            double actual = h.length > 0 ? h[h.length - 1] : 0.0;
            String etiqueta = String.format("%s (%.1f%%)", c.getId(), actual);
            g2.fillRect(xLeyenda, arr + alto + 40, 14, 4);
            g2.setColor(COLOR_TEXTO);
            g2.drawString(etiqueta, xLeyenda + 20, arr + alto + 46);
            xLeyenda += 20 + fm.stringWidth(etiqueta) + 24;
        }
        g2.dispose();
    }
}
