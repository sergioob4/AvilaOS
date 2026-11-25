package avilaos.gui;

import avilaos.estructuras.ListaEnlazada;
import avilaos.sistemaarchivos.Archivo;
import avilaos.sistemaarchivos.Directorio;
import avilaos.sistemaarchivos.NodoSistemaArchivos;
import avilaos.sistemaarchivos.SistemaArchivos;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Observador Swing del sistema de archivos jerárquico de ÁvilaOS.
 *
 * <p>Presenta en tiempo real:
 * <ul>
 *   <li>Un {@link JTree} con la jerarquía de directorios y archivos, junto al detalle
 *       del elemento seleccionado (nombre, tamaño en bloques, dueño, permisos y primer bloque).</li>
 *   <li>Una {@link JTable} de asignación de archivos con color compartido con {@link PanelDisco},
 *       nombre, ruta, bloques asignados, dirección del primer bloque, dueño, permisos y proceso creador.</li>
 * </ul>
 * No modifica el sistema de archivos: únicamente consulta instantáneas seguras bajo semáforo.</p>
 */
final class PanelSistemaArchivos extends JPanel {

    private static final Color COLOR_BG = new Color(21, 25, 32);
    private static final Color COLOR_PANEL = new Color(28, 34, 44);
    private static final Color COLOR_SURFACE = new Color(16, 20, 26);
    private static final Color COLOR_FILA_ALT = new Color(22, 28, 37);
    private static final Color COLOR_BORDE = new Color(55, 68, 88);
    private static final Color COLOR_SELECCION = new Color(37, 99, 235);
    private static final Color COLOR_TEXTO = new Color(245, 248, 252);
    private static final Color COLOR_MUTED = new Color(175, 190, 218);
    private static final Color COLOR_DIR = new Color(96, 165, 250);

    private final SistemaArchivos sistemaArchivos;
    private final Consumer<String> alElegirRuta;

    private final ModeloArbolArchivos modeloArbol;
    private final JTree arbol;

    private final ModeloTablaAsignacion modeloTabla;
    private final JTable tablaAsignacion;
    private final JLabel lblResumenTabla;

    // Etiquetas de detalle del nodo seleccionado
    private final JLabel lblDetNombre = crearValorDetalle("-");
    private final JLabel lblDetRuta = crearValorDetalle("-");
    private final JLabel lblDetTipo = crearValorDetalle("-");
    private final JLabel lblDetBloques = crearValorDetalle("-");
    private final JLabel lblDetDueno = crearValorDetalle("-");
    private final JLabel lblDetPermisos = crearValorDetalle("-");
    private final JLabel lblDetPrimerBloque = crearValorDetalle("-");
    private final JLabel lblDetCreador = crearValorDetalle("-");

    private String firmaAnterior = "";
    private boolean sincronizandoSeleccion;

    PanelSistemaArchivos(SistemaArchivos sistemaArchivos, Consumer<String> alElegirRuta) {
        super(new BorderLayout(8, 8));
        this.sistemaArchivos = sistemaArchivos;
        this.alElegirRuta = alElegirRuta;

        setBackground(COLOR_BG);
        setBorder(new EmptyBorder(6, 6, 6, 6));

        Directorio raizInicial = sistemaArchivos.getRaiz();
        this.modeloArbol = new ModeloArbolArchivos(raizInicial);
        this.arbol = crearArbol(modeloArbol);

        this.modeloTabla = new ModeloTablaAsignacion(sistemaArchivos.listarArchivos());
        this.tablaAsignacion = crearTabla(modeloTabla);
        this.lblResumenTabla = new JLabel();
        this.lblResumenTabla.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        this.lblResumenTabla.setForeground(COLOR_MUTED);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, crearPanelIzquierdo(), crearPanelDerecho());
        split.setBackground(COLOR_BG);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setResizeWeight(0.38);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        actualizar();
        expandirTodo(new TreePath(modeloArbol.getRoot()));
        arbol.setSelectionRow(0);
    }

    private JTree crearArbol(ModeloArbolArchivos modelo) {
        JTree tree = new JTree(modelo);
        tree.setBackground(COLOR_SURFACE);
        tree.setForeground(COLOR_TEXTO);
        tree.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tree.setRowHeight(24);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new RenderizadorArbol());

        tree.addTreeSelectionListener(e -> {
            Object seleccionado = tree.getLastSelectedPathComponent();
            if (seleccionado instanceof NodoSistemaArchivos nodo) {
                mostrarDetalle(nodo);
                if (!sincronizandoSeleccion && nodo instanceof Archivo archivo) {
                    seleccionarEnTabla(archivo.getRuta());
                }
            } else {
                limpiarDetalle();
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && alElegirRuta != null) {
                    Object seleccionado = tree.getLastSelectedPathComponent();
                    if (seleccionado instanceof NodoSistemaArchivos nodo) {
                        alElegirRuta.accept(nodo.getRuta());
                    }
                }
            }
        });
        return tree;
    }

    private JTable crearTabla(ModeloTablaAsignacion modelo) {
        JTable table = new JTable(modelo);
        table.setBackground(COLOR_SURFACE);
        table.setForeground(COLOR_TEXTO);
        table.setSelectionBackground(COLOR_SELECCION);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(40, 50, 66));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        JTableHeader header = table.getTableHeader();
        header.setBackground(COLOR_PANEL);
        header.setForeground(COLOR_TEXTO);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new RenderizadorCabecera());

        table.setDefaultRenderer(Object.class, new RenderizadorCeldaTexto());
        table.getColumnModel().getColumn(0).setCellRenderer(new RenderizadorColor());
        table.getColumnModel().getColumn(0).setPreferredWidth(54);
        table.getColumnModel().getColumn(0).setMaxWidth(64);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(68);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        table.getColumnModel().getColumn(6).setPreferredWidth(76);
        table.getColumnModel().getColumn(7).setPreferredWidth(110);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || sincronizandoSeleccion) {
                return;
            }
            int fila = table.getSelectedRow();
            Archivo archivo = modelo.obtenerEn(fila);
            if (archivo != null) {
                mostrarDetalle(archivo);
                seleccionarEnArbol(archivo.getRuta());
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && alElegirRuta != null) {
                    Archivo archivo = modelo.obtenerEn(table.getSelectedRow());
                    if (archivo != null) {
                        alElegirRuta.accept(archivo.getRuta());
                    }
                }
            }
        });
        return table;
    }

    private JPanel crearPanelIzquierdo() {
        JPanel contenedor = new JPanel(new BorderLayout(0, 6));
        contenedor.setBackground(COLOR_PANEL);
        contenedor.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel titulo = new JLabel("Jerarquía de Directorios y Archivos");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titulo.setForeground(COLOR_TEXTO);
        contenedor.add(titulo, BorderLayout.NORTH);

        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setBorder(new LineBorder(COLOR_BORDE, 1, false));
        scrollArbol.getViewport().setBackground(COLOR_SURFACE);
        contenedor.add(scrollArbol, BorderLayout.CENTER);

        contenedor.add(crearPanelDetalle(), BorderLayout.SOUTH);
        return contenedor;
    }

    private JPanel crearPanelDetalle() {
        JPanel detalle = new JPanel(new GridLayout(4, 2, 10, 4));
        detalle.setBackground(COLOR_SURFACE);
        detalle.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        detalle.add(crearParDetalle("Nombre:", lblDetNombre));
        detalle.add(crearParDetalle("Tipo:", lblDetTipo));
        detalle.add(crearParDetalle("Tamaño (bloques):", lblDetBloques));
        detalle.add(crearParDetalle("Primer bloque:", lblDetPrimerBloque));
        detalle.add(crearParDetalle("Dueño:", lblDetDueno));
        detalle.add(crearParDetalle("Permisos:", lblDetPermisos));
        detalle.add(crearParDetalle("Ruta:", lblDetRuta));
        detalle.add(crearParDetalle("Proceso creador:", lblDetCreador));
        return detalle;
    }

    private JPanel crearParDetalle(String etiqueta, JLabel valor) {
        JPanel par = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        par.setOpaque(false);
        JLabel lbl = new JLabel(etiqueta);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(COLOR_MUTED);
        par.add(lbl);
        par.add(valor);
        return par;
    }

    private static JLabel crearValorDetalle(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("Consolas", Font.PLAIN, 11));
        lbl.setForeground(COLOR_TEXTO);
        return lbl;
    }

    private JPanel crearPanelDerecho() {
        JPanel contenedor = new JPanel(new BorderLayout(0, 6));
        contenedor.setBackground(COLOR_PANEL);
        contenedor.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JPanel cabecera = new JPanel(new BorderLayout(8, 0));
        cabecera.setOpaque(false);
        JLabel titulo = new JLabel("Tabla de Asignación de Archivos");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titulo.setForeground(COLOR_TEXTO);
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(lblResumenTabla, BorderLayout.EAST);
        contenedor.add(cabecera, BorderLayout.NORTH);

        JScrollPane scrollTabla = new JScrollPane(tablaAsignacion);
        scrollTabla.setBorder(new LineBorder(COLOR_BORDE, 1, false));
        scrollTabla.getViewport().setBackground(COLOR_SURFACE);
        contenedor.add(scrollTabla, BorderLayout.CENTER);

        return contenedor;
    }

    /** Refresca el árbol, la tabla de asignación y el detalle si hubo cambios en el sistema de archivos. */
    void actualizar() {
        Directorio nuevaRaiz = sistemaArchivos.getRaiz();
        String firma = construirFirma(nuevaRaiz);
        if (firma.equals(firmaAnterior)) {
            return;
        }
        firmaAnterior = firma;

        String rutaSeleccionada = obtenerRutaSeleccionadaArbol();
        ListaEnlazada<String> expandidas = obtenerRutasExpandidas();

        ListaEnlazada<Archivo> archivos = new ListaEnlazada<>();
        recolectarArchivos(nuevaRaiz, archivos);

        sincronizandoSeleccion = true;
        try {
            modeloArbol.reemplazarRaiz(nuevaRaiz);
            restaurarRutasExpandidas(nuevaRaiz, expandidas);
            expandirRaiz();

            modeloTabla.actualizar(archivos);
            actualizarResumen(archivos);

            if (rutaSeleccionada != null) {
                TreePath path = construirTreePath(nuevaRaiz, rutaSeleccionada);
                if (path != null) {
                    arbol.setSelectionPath(path);
                    mostrarDetalle((NodoSistemaArchivos) path.getLastPathComponent());
                    if (path.getLastPathComponent() instanceof Archivo arch) {
                        seleccionarEnTabla(arch.getRuta());
                    }
                    return;
                }
            }
            arbol.setSelectionRow(0);
            mostrarDetalle(nuevaRaiz);
        } finally {
            sincronizandoSeleccion = false;
        }
    }

    private void actualizarResumen(ListaEnlazada<Archivo> archivos) {
        int totalArchivos = archivos.obtenerTamano();
        int bloquesOcupados = 0;
        for (int i = 0; i < totalArchivos; i++) {
            bloquesOcupados += archivos.obtener(i).getTamanoBloques();
        }
        lblResumenTabla.setText("Archivos: " + totalArchivos + "  |  Bloques asignados: " + bloquesOcupados);
    }

    private void mostrarDetalle(NodoSistemaArchivos nodo) {
        lblDetNombre.setText(nodo.getNombre());
        lblDetRuta.setText(nodo.getRuta());
        lblDetTipo.setText(nodo.getTipo().toString());
        lblDetDueno.setText(nodo.getDueno());
        lblDetPermisos.setText(nodo.getPermiso().toString());

        if (nodo instanceof Archivo archivo) {
            lblDetBloques.setText(String.valueOf(archivo.getTamanoBloques()));
            int primero = archivo.getPrimerBloque();
            lblDetPrimerBloque.setText(primero == Archivo.SIN_BLOQUES ? "Sin asignar" : String.valueOf(primero));
            lblDetCreador.setText(archivo.getProcesoCreador());
        } else if (nodo instanceof Directorio dir) {
            lblDetBloques.setText("0 (" + dir.getCantidadHijos() + " elementos)");
            lblDetPrimerBloque.setText("N/A");
            lblDetCreador.setText("N/A");
        }
    }

    private void limpiarDetalle() {
        lblDetNombre.setText("-");
        lblDetRuta.setText("-");
        lblDetTipo.setText("-");
        lblDetBloques.setText("-");
        lblDetDueno.setText("-");
        lblDetPermisos.setText("-");
        lblDetPrimerBloque.setText("-");
        lblDetCreador.setText("-");
    }

    private void seleccionarEnTabla(String ruta) {
        int fila = modeloTabla.buscarFilaPorRuta(ruta);
        sincronizandoSeleccion = true;
        try {
            if (fila >= 0) {
                tablaAsignacion.setRowSelectionInterval(fila, fila);
            } else {
                tablaAsignacion.clearSelection();
            }
        } finally {
            sincronizandoSeleccion = false;
        }
    }

    private void seleccionarEnArbol(String ruta) {
        Directorio raiz = (Directorio) modeloArbol.getRoot();
        TreePath path = construirTreePath(raiz, ruta);
        if (path == null) {
            return;
        }
        sincronizandoSeleccion = true;
        try {
            arbol.expandPath(path.getParentPath());
            arbol.setSelectionPath(path);
            arbol.scrollPathToVisible(path);
        } finally {
            sincronizandoSeleccion = false;
        }
    }

    private String obtenerRutaSeleccionadaArbol() {
        Object seleccionado = arbol.getLastSelectedPathComponent();
        return seleccionado instanceof NodoSistemaArchivos nodo ? nodo.getRuta() : null;
    }

    private ListaEnlazada<String> obtenerRutasExpandidas() {
        ListaEnlazada<String> rutas = new ListaEnlazada<>();
        int filas = arbol.getRowCount();
        for (int i = 0; i < filas; i++) {
            if (arbol.isExpanded(i)) {
                TreePath path = arbol.getPathForRow(i);
                if (path != null && path.getLastPathComponent() instanceof NodoSistemaArchivos nodo) {
                    rutas.agregar(nodo.getRuta());
                }
            }
        }
        return rutas;
    }

    private void restaurarRutasExpandidas(Directorio raiz, ListaEnlazada<String> rutas) {
        int total = rutas.obtenerTamano();
        for (int i = 0; i < total; i++) {
            TreePath path = construirTreePath(raiz, rutas.obtener(i));
            if (path != null) {
                arbol.expandPath(path);
            }
        }
    }

    private void expandirRaiz() {
        arbol.expandPath(new TreePath(modeloArbol.getRoot()));
    }

    private void expandirTodo(TreePath ruta) {
        Object nodo = ruta.getLastPathComponent();
        if (nodo instanceof Directorio dir) {
            arbol.expandPath(ruta);
            int hijos = dir.getCantidadHijos();
            for (int i = 0; i < hijos; i++) {
                expandirTodo(ruta.pathByAddingChild(dir.getHijo(i)));
            }
        }
    }

    private static TreePath construirTreePath(Directorio raiz, String ruta) {
        if (ruta == null || !ruta.startsWith(NodoSistemaArchivos.SEPARADOR)) {
            return null;
        }
        if (ruta.equals(NodoSistemaArchivos.SEPARADOR)) {
            return new TreePath(raiz);
        }
        String[] partes = ruta.split(NodoSistemaArchivos.SEPARADOR);
        ListaEnlazada<Object> nodos = new ListaEnlazada<>();
        nodos.agregar(raiz);
        NodoSistemaArchivos actual = raiz;
        for (String parte : partes) {
            if (parte.isEmpty()) {
                continue;
            }
            if (!(actual instanceof Directorio dir)) {
                return null;
            }
            actual = dir.buscarHijo(parte);
            if (actual == null) {
                return null;
            }
            nodos.agregar(actual);
        }
        Object[] arreglo = new Object[nodos.obtenerTamano()];
        for (int i = 0; i < arreglo.length; i++) {
            arreglo[i] = nodos.obtener(i);
        }
        return new TreePath(arreglo);
    }

    private static void recolectarArchivos(Directorio directorio, ListaEnlazada<Archivo> destino) {
        int total = directorio.getCantidadHijos();
        for (int i = 0; i < total; i++) {
            NodoSistemaArchivos hijo = directorio.getHijo(i);
            if (hijo instanceof Archivo archivo) {
                destino.agregar(archivo);
            } else if (hijo instanceof Directorio sub) {
                recolectarArchivos(sub, destino);
            }
        }
    }

    private static String construirFirma(Directorio raiz) {
        StringBuilder sb = new StringBuilder();
        anexarFirma(raiz, sb);
        return sb.toString();
    }

    private static void anexarFirma(NodoSistemaArchivos nodo, StringBuilder sb) {
        sb.append(nodo.getRuta()).append('|')
                .append(nodo.getTipo().name()).append('|')
                .append(nodo.getDueno()).append('|')
                .append(nodo.getPermiso().name());
        if (nodo instanceof Archivo archivo) {
            sb.append('|').append(archivo.getTamanoBloques())
                    .append('|').append(archivo.getPrimerBloque())
                    .append('|').append(archivo.getProcesoCreador());
        }
        sb.append('\n');
        if (nodo instanceof Directorio dir) {
            int hijos = dir.getCantidadHijos();
            for (int i = 0; i < hijos; i++) {
                anexarFirma(dir.getHijo(i), sb);
            }
        }
    }

    // =========================================================================
    // TreeModel propio (sin colecciones de java.util)
    // =========================================================================

    private static final class ModeloArbolArchivos implements TreeModel {
        private Directorio raiz;
        private final ListaEnlazada<TreeModelListener> listeners = new ListaEnlazada<>();

        ModeloArbolArchivos(Directorio raiz) {
            this.raiz = raiz;
        }

        void reemplazarRaiz(Directorio nuevaRaiz) {
            this.raiz = nuevaRaiz;
            TreeModelEvent evento = new TreeModelEvent(this, new Object[]{nuevaRaiz});
            int total = listeners.obtenerTamano();
            for (int i = 0; i < total; i++) {
                listeners.obtener(i).treeStructureChanged(evento);
            }
        }

        @Override
        public Object getRoot() {
            return raiz;
        }

        @Override
        public Object getChild(Object parent, int index) {
            return parent instanceof Directorio dir ? dir.getHijo(index) : null;
        }

        @Override
        public int getChildCount(Object parent) {
            return parent instanceof Directorio dir ? dir.getCantidadHijos() : 0;
        }

        @Override
        public boolean isLeaf(Object node) {
            return !(node instanceof Directorio);
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {
            // Solo lectura: las modificaciones se ejecutan mediante procesos de E/S.
        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            if (parent instanceof Directorio dir && child instanceof NodoSistemaArchivos nodo) {
                return dir.indiceDe(nodo);
            }
            return -1;
        }

        @Override
        public void addTreeModelListener(TreeModelListener l) {
            if (l != null) {
                listeners.agregar(l);
            }
        }

        @Override
        public void removeTreeModelListener(TreeModelListener l) {
            if (l != null) {
                listeners.eliminar(l);
            }
        }
    }

    // =========================================================================
    // TableModel propio (sin colecciones de java.util)
    // =========================================================================

    private static final class ModeloTablaAsignacion extends AbstractTableModel {
        private static final String[] COLUMNAS = {
            "Color", "Nombre", "Ruta", "Bloques", "1er Bloque", "Dueño", "Permisos", "Proceso creador"
        };

        private ListaEnlazada<Archivo> archivos;

        ModeloTablaAsignacion(ListaEnlazada<Archivo> archivos) {
            this.archivos = archivos;
        }

        void actualizar(ListaEnlazada<Archivo> nuevos) {
            this.archivos = nuevos;
            fireTableDataChanged();
        }

        Archivo obtenerEn(int fila) {
            if (fila < 0 || fila >= archivos.obtenerTamano()) {
                return null;
            }
            return archivos.obtener(fila);
        }

        int buscarFilaPorRuta(String ruta) {
            if (ruta == null) {
                return -1;
            }
            int total = archivos.obtenerTamano();
            for (int i = 0; i < total; i++) {
                if (ruta.equals(archivos.obtener(i).getRuta())) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return archivos.obtenerTamano();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNAS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Archivo archivo = archivos.obtener(rowIndex);
            return switch (columnIndex) {
                case 0 -> PanelDisco.colorArchivo(archivo.getRuta());
                case 1 -> archivo.getNombre();
                case 2 -> archivo.getRuta();
                case 3 -> archivo.getTamanoBloques();
                case 4 -> archivo.getPrimerBloque() == Archivo.SIN_BLOQUES ? "-" : archivo.getPrimerBloque();
                case 5 -> archivo.getDueno();
                case 6 -> archivo.getPermiso().toString();
                case 7 -> archivo.getProcesoCreador();
                default -> "";
            };
        }
    }

    // =========================================================================
    // Renderizadores oscuros de alto contraste
    // =========================================================================

    private static final class RenderizadorArbol extends DefaultTreeCellRenderer {
        RenderizadorArbol() {
            setBackgroundNonSelectionColor(COLOR_SURFACE);
            setBackgroundSelectionColor(COLOR_SELECCION);
            setBorderSelectionColor(COLOR_SELECCION);
            setTextNonSelectionColor(COLOR_TEXTO);
            setTextSelectionColor(Color.WHITE);
            setClosedIcon(null);
            setOpenIcon(null);
            setLeafIcon(null);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setOpaque(true);
            setBackground(sel ? COLOR_SELECCION : COLOR_SURFACE);
            if (value instanceof Directorio dir) {
                String nombre = dir.getPadre() == null ? "/ (raíz)" : dir.getNombre();
                setText("[DIR] " + nombre + " (" + dir.getCantidadHijos() + ")");
                setForeground(sel ? Color.WHITE : COLOR_DIR);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
            } else if (value instanceof Archivo archivo) {
                setText("[ARCH] " + archivo.getNombre() + "  [" + archivo.getTamanoBloques() + " blq]");
                setForeground(sel ? Color.WHITE : COLOR_TEXTO);
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
            }
            return this;
        }
    }

    private static final class RenderizadorCabecera extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setOpaque(true);
            lbl.setBackground(COLOR_PANEL);
            lbl.setForeground(COLOR_TEXTO);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lbl.setHorizontalAlignment(column == 0 || column == 3 || column == 4 ? SwingConstants.CENTER : SwingConstants.LEFT);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, COLOR_BORDE),
                    new EmptyBorder(4, 6, 4, 6)
            ));
            return lbl;
        }
    }

    private static final class RenderizadorCeldaTexto extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setOpaque(true);
            lbl.setBackground(isSelected ? COLOR_SELECCION : (row % 2 == 0 ? COLOR_SURFACE : COLOR_FILA_ALT));
            lbl.setForeground(isSelected ? Color.WHITE : COLOR_TEXTO);
            lbl.setHorizontalAlignment(column == 3 || column == 4 ? SwingConstants.CENTER : SwingConstants.LEFT);
            lbl.setBorder(new EmptyBorder(0, 6, 0, 6));
            return lbl;
        }
    }

    private static final class RenderizadorColor extends DefaultTableCellRenderer {
        private Color muestra = COLOR_DIR;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            setOpaque(true);
            setBackground(isSelected ? COLOR_SELECCION : (row % 2 == 0 ? COLOR_SURFACE : COLOR_FILA_ALT));
            muestra = value instanceof Color color ? color : COLOR_DIR;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = 22;
            int h = 14;
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            g2.setColor(muestra);
            g2.fillRoundRect(x, y, w, h, 4, 4);
            g2.setColor(new Color(220, 230, 245, 160));
            g2.drawRoundRect(x, y, w, h, 4, 4);
            g2.dispose();
        }
    }
}
