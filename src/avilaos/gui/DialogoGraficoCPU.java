package avilaos.gui;

import avilaos.interfaces.IComponenteReloj;
import avilaos.sistema.ClusterDistribuido;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana no modal con el gráfico de utilización de CPU de todos los computadores.
 * Se suscribe al reloj global para refrescarse en cada ciclo y se desuscribe al cerrarse.
 */
public class DialogoGraficoCPU extends JDialog implements IComponenteReloj {

    private final ClusterDistribuido cluster;
    private final GraficoUtilizacionCPU grafico;

    public DialogoGraficoCPU(Window parent, ClusterDistribuido cluster) {
        super(parent, "ÁvilaOS — Utilización de CPU vs. Tiempo", ModalityType.MODELESS);
        this.cluster = cluster;
        this.grafico = new GraficoUtilizacionCPU(cluster);

        getContentPane().setBackground(new Color(21, 25, 32));
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Utilización acumulada del procesador por computador");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titulo.setForeground(new Color(245, 248, 252));
        titulo.setBorder(new EmptyBorder(12, 20, 8, 20));
        add(titulo, BorderLayout.NORTH);
        add(grafico, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(parent);

        cluster.getRelojGlobal().registrarComponente(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                DialogoGraficoCPU.this.cluster.getRelojGlobal().desregistrarComponente(DialogoGraficoCPU.this);
            }
        });
    }

    @Override
    public void ejecutarCiclo(long cicloActual) {
        SwingUtilities.invokeLater(grafico::repaint);
    }
}
