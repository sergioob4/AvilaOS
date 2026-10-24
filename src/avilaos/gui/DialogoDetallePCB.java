package avilaos.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.TipoProceso;
import avilaos.proceso.PCB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Diálogo modal para visualizar el detalle completo de un Bloque de Control de Proceso (PCB).
 *
 * <p>Muestra el contexto monohilo (PC, MAR), métricas de ciclo de vida,
 * estado actual, motivo de bloqueo y tiempos límites (deadlines).</p>
 */
public class DialogoDetallePCB extends JDialog {

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_CARD = new Color(32, 38, 48);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_TITULO = new Color(245, 248, 252);
    private static final Color COLOR_LABEL = new Color(175, 190, 215); // Alto contraste
    private static final Color COLOR_VALOR = new Color(255, 255, 255);
    private static final Color COLOR_ACENTO = new Color(37, 99, 235); // Azul vibrante accesible

    public DialogoDetallePCB(Window parent, PCB pcb) {
        super(parent, "Detalle del Bloque de Control de Proceso (PCB) — " + pcb.getId(), ModalityType.APPLICATION_MODAL);
        inicializarUI(pcb);
    }

    private void inicializarUI(PCB pcb) {
        setSize(560, 620);
        setMinimumSize(new Dimension(500, 500));
        setLocationRelativeTo(getParent());
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout(12, 12));

        // 1. Header con ID, Nombre y Badge de Estado con alto contraste
        JPanel panelHeader = new JPanel(new BorderLayout(10, 0));
        panelHeader.setBackground(COLOR_CARD);
        panelHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JLabel lblId = new JLabel("[" + pcb.getId() + "] " + pcb.getNombre());
        lblId.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblId.setForeground(COLOR_TITULO);

        // Badge estilizado con fondo sólido y texto de alto contraste
        JLabel lblEstado = new JLabel("  " + pcb.getEstado().getNombreLegible() + "  ");
        lblEstado.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Color colorTextoEstado = obtenerColorEstado(pcb.getEstado());
        Color fondoEstado = obtenerFondoEstado(pcb.getEstado());
        lblEstado.setOpaque(true);
        lblEstado.setBackground(fondoEstado);
        lblEstado.setForeground(colorTextoEstado);
        lblEstado.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorTextoEstado, 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        panelHeader.add(lblId, BorderLayout.CENTER);
        panelHeader.add(lblEstado, BorderLayout.EAST);
        add(panelHeader, BorderLayout.NORTH);

        // 2. Contenido central con 18 atributos del PCB (cuadrícula 9x2 simétrica)
        JPanel panelCentro = new JPanel(new GridLayout(0, 2, 12, 10));
        panelCentro.setBackground(COLOR_BG);
        panelCentro.setBorder(new EmptyBorder(16, 20, 16, 20));

        panelCentro.add(crearFila("Tipo de Proceso:", pcb.getTipo().getNombreLegible()));
        panelCentro.add(crearFila("Computador Asignado:", pcb.getComputadorAsignado()));
        panelCentro.add(crearFila("Nivel de Prioridad:", String.valueOf(pcb.getPrioridad())));
        panelCentro.add(crearFila("Memoria RAM Asignada:", pcb.getMemoriaAsignada() + " MB"));

        panelCentro.add(crearFila("Program Counter (PC):", String.valueOf(pcb.getProgramCounter())));
        panelCentro.add(crearFila("Memory Address Reg (MAR):", String.valueOf(pcb.getMemoryAddressRegister())));

        panelCentro.add(crearFila("Instrucciones Totales:", String.valueOf(pcb.getTiempoEjecucionTotal())));
        panelCentro.add(crearFila("Instrucciones Restantes:", String.valueOf(pcb.getTiempoRestanteEjecucion())));

        panelCentro.add(crearFila("Deadline Inicial:", pcb.getDeadlineInicial() + " ciclos"));
        panelCentro.add(crearFila("Deadline Restante:", pcb.getDeadlineRestante() + " ciclos"));

        panelCentro.add(crearFila("Motivo de Bloqueo:", pcb.getMotivoBloqueo().getJustificacion()));
        panelCentro.add(crearFila("Tiempo en CPU:", pcb.getTiempoEnCpu() + " ciclos"));

        panelCentro.add(crearFila("Ciclo de Creación:", String.valueOf(pcb.getCicloCreacion())));
        panelCentro.add(crearFila("Ciclo Primer Listo:", pcb.getCicloPrimerListo() >= 0 ? String.valueOf(pcb.getCicloPrimerListo()) : "Pendiente"));

        panelCentro.add(crearFila("Ciclo Primera Ejecución:", pcb.getCicloPrimeraEjecucion() >= 0 ? String.valueOf(pcb.getCicloPrimeraEjecucion()) : "Pendiente"));
        panelCentro.add(crearFila("Ciclo de Terminación:", pcb.getCicloTerminacion() >= 0 ? String.valueOf(pcb.getCicloTerminacion()) : "En curso"));

        String resultado = "En curso";
        if (pcb.isTerminoPorDeadline()) {
            resultado = "Abortado por Deadline";
        } else if (pcb.isCumplioDeadline()) {
            resultado = "Completado a tiempo";
        }
        panelCentro.add(crearFila("Estado Final:", resultado));
        panelCentro.add(crearFila("Tiempo Espera en Listos:", pcb.getTiempoEnEspera() + " ciclos"));

        // Atributos de Sincronización (Requerimiento 4)
        if (pcb.getIdBuffer() != null || pcb.getTipo() == TipoProceso.PRODUCTOR || pcb.getTipo() == TipoProceso.CONSUMIDOR) {
            panelCentro.add(crearFila("Buffer Asociado:", pcb.getIdBuffer() != null ? pcb.getIdBuffer() : "Ninguno"));
            panelCentro.add(crearFila("Progreso de Ítems:", pcb.getItemsProcesados() + " / " + pcb.getItemsRequeridos()));
            panelCentro.add(crearFila("Ciclos por Ítem:", pcb.getCiclosPorItem() + " ciclos"));
            panelCentro.add(crearFila("Latencia Red Restante:", pcb.getCiclosLatenciaRedRestante() + " ciclos"));
        }

        JScrollPane scroll = new JScrollPane(panelCentro);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(COLOR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        add(scroll, BorderLayout.CENTER);

        // 3. Botón inferior de cerrar con alto contraste absoluto (BotonModerno)
        JPanel panelBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        panelBottom.setBackground(COLOR_CARD);
        panelBottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDE));

        VentanaPrincipal.BotonEstilizado btnCerrar = new VentanaPrincipal.BotonEstilizado("Cerrar", COLOR_ACENTO, Color.WHITE);
        btnCerrar.setPreferredSize(new Dimension(100, 34));
        btnCerrar.addActionListener(e -> dispose());
        panelBottom.add(btnCerrar);

        add(panelBottom, BorderLayout.SOUTH);
    }

    private JPanel crearFila(String label, String valor) {
        JPanel p = new JPanel(new BorderLayout(4, 3));
        p.setBackground(COLOR_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(COLOR_LABEL);

        JLabel val = new JLabel(valor);
        val.setFont(new Font("Consolas", Font.BOLD, 12));
        val.setForeground(COLOR_VALOR);

        p.add(lbl, BorderLayout.NORTH);
        p.add(val, BorderLayout.CENTER);
        return p;
    }

    private Color obtenerColorEstado(EstadoProceso estado) {
        if (estado == null) return COLOR_LABEL;
        switch (estado) {
            case NUEVO: return new Color(233, 213, 255);      // Lilac claro
            case LISTO: return new Color(191, 219, 254);      // Azul cielo
            case EJECUCION: return new Color(187, 247, 208);  // Verde menta
            case BLOQUEADO: return new Color(254, 240, 138);  // Amarillo suave
            case TERMINADO: return new Color(241, 245, 249);  // Blanco grisáceo
            default: return Color.WHITE;
        }
    }

    private Color obtenerFondoEstado(EstadoProceso estado) {
        if (estado == null) return new Color(40, 48, 62);
        switch (estado) {
            case NUEVO: return new Color(88, 28, 135);        // Morado profundo
            case LISTO: return new Color(30, 58, 138);        // Azul oscuro
            case EJECUCION: return new Color(20, 83, 45);     // Verde bosque
            case BLOQUEADO: return new Color(120, 53, 15);    // Ámbar profundo
            case TERMINADO: return new Color(51, 65, 85);     // Pizarra neutro
            default: return new Color(40, 48, 62);
        }
    }
}
