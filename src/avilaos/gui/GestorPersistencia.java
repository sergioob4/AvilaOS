package avilaos.gui;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.BufferAcotado;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestor de persistencia para guardar y cargar escenarios de simulación en formato CSV o JSON.
 *
 * <p>Conforme al Requerimiento 7, serializa y deserializa de forma completa:
 * <ul>
 *   <li>Duración del ciclo de ejecución (ms) y latencia de red en ciclos.</li>
 *   <li>Configuración completa de computadores (ID, nombre, RAM total, política y quantum).</li>
 *   <li>Buffers acotados del clúster (ID, nombre, anfitrión, capacidad y reserva de RAM).</li>
 *   <li>Carga de procesos con todos sus atributos específicos: CPU-bound, productor/consumidor
 *       (ID de buffer, ciclos por ítem, ítems requeridos) e I/O-bound (ciclos entre E/S y duración del bloqueo).</li>
 * </ul>
 * Sin depender de librerías externas complejas, asegurando portabilidad directa en NetBeans.
 * </p>
 */
public class GestorPersistencia {

    // -------------------------------------------------------------------------
    // Punto de entrada general de exportación
    // -------------------------------------------------------------------------

    /**
     * Guarda la configuración del clúster, buffers y procesos en un archivo CSV o JSON según la extensión.
     *
     * @param archivo archivo de destino
     * @param cluster clúster a exportar
     * @throws IOException si ocurre un error de escritura
     */
    public static void exportarEscenario(File archivo, ClusterDistribuido cluster) throws IOException {
        if (archivo.getName().toLowerCase().endsWith(".json")) {
            exportarEscenarioJson(archivo, cluster);
        } else {
            exportarEscenarioCsv(archivo, cluster);
        }
    }

    /**
     * Importa la configuración del clúster, buffers y procesos desde un archivo CSV o JSON.
     *
     * @param archivo archivo a importar
     * @param cluster clúster donde se cargarán los datos
     * @return cantidad de procesos cargados exitosamente
     * @throws IOException si ocurre un error de lectura
     */
    public static int importarProcesos(File archivo, ClusterDistribuido cluster) throws IOException {
        if (archivo.getName().toLowerCase().endsWith(".json")) {
            return importarEscenarioJson(archivo, cluster);
        }

        // Detección automática por contenido inicial si el archivo no tiene extensión .json
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                if (linea.startsWith("{")) {
                    return importarEscenarioJson(archivo, cluster);
                }
                break;
            }
        }

        return importarEscenarioCsv(archivo, cluster);
    }

    // -------------------------------------------------------------------------
    // Exportación e Importación en Formato CSV
    // -------------------------------------------------------------------------

    /**
     * Exporta el escenario completo a un archivo delimitado por comas (CSV).
     *
     * @param archivo destino
     * @param cluster clúster a exportar
     * @throws IOException si ocurre un error de escritura
     */
    public static void exportarEscenarioCsv(File archivo, ClusterDistribuido cluster) throws IOException {
        try (FileWriter writer = new FileWriter(archivo)) {
            // 1. Configuración del Clúster
            writer.write("# CONFIGURACION_CLUSTER\n");
            writer.write("duracionCicloMs," + cluster.getRelojGlobal().getDuracionCicloMs() + "\n");
            writer.write("latenciaRedCiclos," + cluster.getLatenciaRedCiclos() + "\n");
            writer.write("cantidadComputadores," + cluster.getCantidadComputadores() + "\n\n");

            // 2. Configuración de Computadores
            writer.write("# COMPUTADORES\n");
            writer.write("id,nombre,ramTotalMb,politica,quantum\n");
            int totalComp = cluster.getCantidadComputadores();
            for (int i = 0; i < totalComp; i++) {
                Computador c = cluster.getComputadores().obtener(i);
                writer.write(String.format("%s,%s,%d,%s,%d\n",
                        c.getId(), c.getNombre(), c.getMemoria().getCapacidadTotal(),
                        c.getSo().getPolitica().getTipo().name(), c.getSo().getQuantum()));
            }
            writer.write("\n");

            // 3. Configuración de Buffers Acotados
            writer.write("# BUFFERS\n");
            writer.write("id,nombre,computadorAnfitrion,capacidad,memoriaMb\n");
            if (cluster.getGestorSincronizacion() != null) {
                int totalBuffers = cluster.getGestorSincronizacion().getCantidadBuffers();
                for (int i = 0; i < totalBuffers; i++) {
                    BufferAcotado b = cluster.getGestorSincronizacion().getBuffers().obtener(i);
                    if (b != null) {
                        writer.write(String.format("%s,%s,%s,%d,%d\n",
                                b.getId(), b.getNombre(), b.getIdComputadorAnfitrion(),
                                b.getCapacidad(), b.getMemoriaReservadaMb()));
                    }
                }
            }
            writer.write("\n");

            // 4. Procesos con atributos completos
            writer.write("# PROCESOS\n");
            writer.write("nombre,computador,tipo,prioridad,memoria,deadline,instrucciones,idBuffer,ciclosPorItem,itemsRequeridos,ciclosEntreIO,duracionBloqueoIO\n");

            Set<String> procesosExportados = new HashSet<>();
            for (int i = 0; i < totalComp; i++) {
                Computador c = cluster.getComputadores().obtener(i);

                // Proceso en CPU
                Proceso enCpu = c.getCpu().getProcesoActual();
                if (enCpu != null && !enCpu.estaTerminado() && procesosExportados.add(enCpu.getId())) {
                    escribirLineaProcesoCsv(writer, enCpu, c.getId());
                }

                // Procesos en cola de listos
                int listos = c.getSo().getColaListos().obtenerTamano();
                for (int j = 0; j < listos; j++) {
                    Proceso p = c.getSo().getColaListos().obtener(j);
                    if (p != null && procesosExportados.add(p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }

                // Procesos en cola de nuevos
                int nuevos = c.getSo().getColaNuevos().obtenerTamano();
                for (int j = 0; j < nuevos; j++) {
                    Proceso p = c.getSo().getColaNuevos().obtener(j);
                    if (p != null && procesosExportados.add(p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }

                // Procesos en cola de bloqueados
                int bloqueados = c.getSo().getColaBloqueados().obtenerTamano();
                for (int j = 0; j < bloqueados; j++) {
                    Proceso p = c.getSo().getColaBloqueados().obtener(j);
                    if (p != null && procesosExportados.add(p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }
            }
        }
    }

    private static void escribirLineaProcesoCsv(FileWriter writer, Proceso p, String idComp) throws IOException {
        String idBuffer = p.getPcb().getIdBuffer() != null ? p.getPcb().getIdBuffer() : "";
        writer.write(String.format("%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d\n",
                p.getPcb().getNombre(), idComp, p.getPcb().getTipo().name(),
                p.getPcb().getPrioridad(), p.getPcb().getMemoriaAsignada(),
                p.getPcb().getDeadlineInicial(), p.getPcb().getTiempoEjecucionTotal(),
                idBuffer, p.getPcb().getCiclosPorItem(), p.getPcb().getItemsRequeridos(),
                p.getPcb().getCiclosEntreIO(), p.getPcb().getDuracionBloqueoIO()));
    }

    /**
     * Importa el escenario completo desde un archivo CSV.
     *
     * @param archivo archivo a leer
     * @param cluster clúster donde se cargarán los datos
     * @return cantidad de procesos cargados
     * @throws IOException si ocurre un error de lectura
     */
    public static int importarEscenarioCsv(File archivo, ClusterDistribuido cluster) throws IOException {
        int cargados = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            String seccionActual = "";

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                if (linea.startsWith("#")) {
                    seccionActual = linea.substring(1).trim().toUpperCase();
                    continue;
                }

                switch (seccionActual) {
                    case "CONFIGURACION_CLUSTER" -> {
                        String[] partes = linea.split(",");
                        if (partes.length == 2) {
                            String clave = partes[0].trim();
                            String valor = partes[1].trim();
                            if ("duracionCicloMs".equalsIgnoreCase(clave)) {
                                cluster.getRelojGlobal().setDuracionCicloMs(Long.parseLong(valor));
                            } else if ("latenciaRedCiclos".equalsIgnoreCase(clave)) {
                                cluster.setLatenciaRedCiclos(Integer.parseInt(valor));
                            }
                        }
                    }
                    case "COMPUTADORES" -> {
                        if (linea.toLowerCase().startsWith("id,")) continue; // Cabecera
                        String[] partes = linea.split(",");
                        if (partes.length >= 5) {
                            String id = partes[0].trim();
                            String nombre = partes[1].trim();
                            int ram = Integer.parseInt(partes[2].trim());
                            String politica = partes[3].trim();
                            int quantum = Integer.parseInt(partes[4].trim());

                            Computador comp = cluster.buscarPorId(id);
                            if (comp == null) {
                                comp = new Computador(id, nombre, ram);
                                cluster.agregarComputador(comp);
                            }
                            try {
                                comp.cambiarPolitica(PoliticaPlanificacion.valueOf(politica));
                            } catch (Exception ignored) {}
                            comp.cambiarQuantum(quantum);
                        }
                    }
                    case "BUFFERS" -> {
                        if (linea.toLowerCase().startsWith("id,")) continue; // Cabecera
                        String[] partes = linea.split(",");
                        if (partes.length >= 5) {
                            String id = partes[0].trim();
                            String nombre = partes[1].trim();
                            String host = partes[2].trim();
                            int capacidad = Integer.parseInt(partes[3].trim());
                            int memoria = Integer.parseInt(partes[4].trim());

                            if (cluster.getGestorSincronizacion() != null) {
                                if (cluster.getGestorSincronizacion().buscarBuffer(id) == null) {
                                    cluster.getGestorSincronizacion().crearBuffer(id, nombre, host, capacidad, memoria);
                                }
                            }
                        }
                    }
                    case "PROCESOS" -> {
                        if (linea.toLowerCase().startsWith("nombre,")) continue; // Cabecera
                        String[] partes = linea.split(",");
                        if (partes.length >= 7) {
                            String nombre = partes[0].trim();
                            String idComp = partes[1].trim();
                            TipoProceso tipo = TipoProceso.valueOf(partes[2].trim());
                            int prioridad = Integer.parseInt(partes[3].trim());
                            int memoria = Integer.parseInt(partes[4].trim());
                            int deadline = Integer.parseInt(partes[5].trim());
                            int instrucciones = Integer.parseInt(partes[6].trim());

                            Proceso p = new Proceso(nombre, idComp, tipo, prioridad, memoria, deadline, instrucciones);

                            if (partes.length >= 12) {
                                String idBuf = partes[7].trim();
                                if (!idBuf.isEmpty()) {
                                    p.getPcb().setIdBuffer(idBuf);
                                }
                                p.getPcb().setCiclosPorItem(Integer.parseInt(partes[8].trim()));
                                p.getPcb().setItemsRequeridos(Integer.parseInt(partes[9].trim()));
                                p.getPcb().setCiclosEntreIO(Integer.parseInt(partes[10].trim()));
                                p.getPcb().setDuracionBloqueoIO(Integer.parseInt(partes[11].trim()));
                            }

                            Computador comp = cluster.buscarPorId(idComp);
                            if (comp != null) {
                                comp.admitirProceso(p, cluster.getRelojGlobal().getCicloActual());
                            } else {
                                cluster.asignarProcesoAutomatico(p);
                            }
                            cargados++;
                        }
                    }
                }
            }
        }
        return cargados;
    }

    // -------------------------------------------------------------------------
    // Exportación e Importación en Formato JSON
    // -------------------------------------------------------------------------

    /**
     * Exporta el escenario completo a un archivo estructurado en formato JSON.
     *
     * @param archivo destino
     * @param cluster clúster a exportar
     * @throws IOException si ocurre un error de escritura
     */
    public static void exportarEscenarioJson(File archivo, ClusterDistribuido cluster) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // 1. Configuración del Clúster
        json.append("  \"configuracion\": {\n");
        json.append("    \"duracionCicloMs\": ").append(cluster.getRelojGlobal().getDuracionCicloMs()).append(",\n");
        json.append("    \"latenciaRedCiclos\": ").append(cluster.getLatenciaRedCiclos()).append(",\n");
        json.append("    \"cantidadComputadores\": ").append(cluster.getCantidadComputadores()).append("\n");
        json.append("  },\n");

        // 2. Computadores
        json.append("  \"computadores\": [\n");
        int totalComp = cluster.getCantidadComputadores();
        for (int i = 0; i < totalComp; i++) {
            Computador c = cluster.getComputadores().obtener(i);
            json.append("    {\n");
            json.append("      \"id\": \"").append(c.getId()).append("\",\n");
            json.append("      \"nombre\": \"").append(c.getNombre()).append("\",\n");
            json.append("      \"ramTotalMb\": ").append(c.getMemoria().getCapacidadTotal()).append(",\n");
            json.append("      \"politica\": \"").append(c.getSo().getPolitica().getTipo().name()).append("\",\n");
            json.append("      \"quantum\": ").append(c.getSo().getQuantum()).append("\n");
            json.append("    }").append(i < totalComp - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");

        // 3. Buffers
        json.append("  \"buffers\": [\n");
        int totalBuffers = cluster.getGestorSincronizacion() != null ? cluster.getGestorSincronizacion().getCantidadBuffers() : 0;
        for (int i = 0; i < totalBuffers; i++) {
            BufferAcotado b = cluster.getGestorSincronizacion().getBuffers().obtener(i);
            json.append("    {\n");
            json.append("      \"id\": \"").append(b.getId()).append("\",\n");
            json.append("      \"nombre\": \"").append(b.getNombre()).append("\",\n");
            json.append("      \"computadorAnfitrion\": \"").append(b.getIdComputadorAnfitrion()).append("\",\n");
            json.append("      \"capacidad\": ").append(b.getCapacidad()).append(",\n");
            json.append("      \"memoriaMb\": ").append(b.getMemoriaReservadaMb()).append("\n");
            json.append("    }").append(i < totalBuffers - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");

        // 4. Procesos
        json.append("  \"procesos\": [\n");
        List<String> lineasProcesos = new ArrayList<>();
        Set<String> exportados = new HashSet<>();

        for (int i = 0; i < totalComp; i++) {
            Computador c = cluster.getComputadores().obtener(i);

            // Proceso en CPU
            Proceso enCpu = c.getCpu().getProcesoActual();
            if (enCpu != null && !enCpu.estaTerminado() && exportados.add(enCpu.getId())) {
                lineasProcesos.add(generarJsonProceso(enCpu, c.getId()));
            }

            // Cola de Listos
            int listos = c.getSo().getColaListos().obtenerTamano();
            for (int j = 0; j < listos; j++) {
                Proceso p = c.getSo().getColaListos().obtener(j);
                if (p != null && exportados.add(p.getId())) {
                    lineasProcesos.add(generarJsonProceso(p, c.getId()));
                }
            }

            // Cola de Nuevos
            int nuevos = c.getSo().getColaNuevos().obtenerTamano();
            for (int j = 0; j < nuevos; j++) {
                Proceso p = c.getSo().getColaNuevos().obtener(j);
                if (p != null && exportados.add(p.getId())) {
                    lineasProcesos.add(generarJsonProceso(p, c.getId()));
                }
            }

            // Cola de Bloqueados
            int bloqueados = c.getSo().getColaBloqueados().obtenerTamano();
            for (int j = 0; j < bloqueados; j++) {
                Proceso p = c.getSo().getColaBloqueados().obtener(j);
                if (p != null && exportados.add(p.getId())) {
                    lineasProcesos.add(generarJsonProceso(p, c.getId()));
                }
            }
        }

        for (int i = 0; i < lineasProcesos.size(); i++) {
            json.append(lineasProcesos.get(i)).append(i < lineasProcesos.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        try (FileWriter writer = new FileWriter(archivo)) {
            writer.write(json.toString());
        }
    }

    private static String generarJsonProceso(Proceso p, String idComp) {
        String idBuffer = p.getPcb().getIdBuffer() != null ? p.getPcb().getIdBuffer() : "";
        return "    {\n"
                + "      \"nombre\": \"" + p.getPcb().getNombre() + "\",\n"
                + "      \"computador\": \"" + idComp + "\",\n"
                + "      \"tipo\": \"" + p.getPcb().getTipo().name() + "\",\n"
                + "      \"prioridad\": " + p.getPcb().getPrioridad() + ",\n"
                + "      \"memoria\": " + p.getPcb().getMemoriaAsignada() + ",\n"
                + "      \"deadline\": " + p.getPcb().getDeadlineInicial() + ",\n"
                + "      \"instrucciones\": " + p.getPcb().getTiempoEjecucionTotal() + ",\n"
                + "      \"idBuffer\": \"" + idBuffer + "\",\n"
                + "      \"ciclosPorItem\": " + p.getPcb().getCiclosPorItem() + ",\n"
                + "      \"itemsRequeridos\": " + p.getPcb().getItemsRequeridos() + ",\n"
                + "      \"ciclosEntreIO\": " + p.getPcb().getCiclosEntreIO() + ",\n"
                + "      \"duracionBloqueoIO\": " + p.getPcb().getDuracionBloqueoIO() + "\n"
                + "    }";
    }

    /**
     * Importa el escenario completo desde un archivo en formato JSON sin depender de librerías externas.
     *
     * @param archivo archivo JSON
     * @param cluster clúster donde se cargarán los datos
     * @return cantidad de procesos cargados
     * @throws IOException si ocurre un error de lectura
     */
    public static int importarEscenarioJson(File archivo, ClusterDistribuido cluster) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String l;
            while ((l = reader.readLine()) != null) {
                sb.append(l).append("\n");
            }
        }
        String json = sb.toString();

        // 1. Configuración de reloj y red
        String duracionMs = extraerValorJson(json, "duracionCicloMs");
        if (!duracionMs.isEmpty()) {
            try { cluster.getRelojGlobal().setDuracionCicloMs(Long.parseLong(duracionMs)); } catch (Exception ignored) {}
        }
        String latenciaRed = extraerValorJson(json, "latenciaRedCiclos");
        if (!latenciaRed.isEmpty()) {
            try { cluster.setLatenciaRedCiclos(Integer.parseInt(latenciaRed)); } catch (Exception ignored) {}
        }

        // 2. Computadores
        List<String> compsJson = extraerObjetosArrayJson(json, "computadores");
        for (String cj : compsJson) {
            String id = extraerValorJson(cj, "id");
            String nombre = extraerValorJson(cj, "nombre");
            String ramStr = extraerValorJson(cj, "ramTotalMb");
            String polStr = extraerValorJson(cj, "politica");
            String quantumStr = extraerValorJson(cj, "quantum");

            if (!id.isEmpty()) {
                Computador comp = cluster.buscarPorId(id);
                if (comp == null && !ramStr.isEmpty()) {
                    comp = new Computador(id, nombre, Integer.parseInt(ramStr));
                    cluster.agregarComputador(comp);
                }
                if (comp != null) {
                    if (!polStr.isEmpty()) {
                        try { comp.cambiarPolitica(PoliticaPlanificacion.valueOf(polStr)); } catch (Exception ignored) {}
                    }
                    if (!quantumStr.isEmpty()) {
                        try { comp.cambiarQuantum(Integer.parseInt(quantumStr)); } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 3. Buffers
        List<String> buffersJson = extraerObjetosArrayJson(json, "buffers");
        for (String bj : buffersJson) {
            String id = extraerValorJson(bj, "id");
            String nombre = extraerValorJson(bj, "nombre");
            String host = extraerValorJson(bj, "computadorAnfitrion");
            String capStr = extraerValorJson(bj, "capacidad");
            String memStr = extraerValorJson(bj, "memoriaMb");

            if (!id.isEmpty() && cluster.getGestorSincronizacion() != null) {
                if (cluster.getGestorSincronizacion().buscarBuffer(id) == null) {
                    int cap = !capStr.isEmpty() ? Integer.parseInt(capStr) : 3;
                    int mem = !memStr.isEmpty() ? Integer.parseInt(memStr) : 256;
                    cluster.getGestorSincronizacion().crearBuffer(id, nombre, host, cap, mem);
                }
            }
        }

        // 4. Procesos
        int cargados = 0;
        List<String> procesosJson = extraerObjetosArrayJson(json, "procesos");
        for (String pj : procesosJson) {
            String nombre = extraerValorJson(pj, "nombre");
            String idComp = extraerValorJson(pj, "computador");
            String tipoStr = extraerValorJson(pj, "tipo");
            String prioStr = extraerValorJson(pj, "prioridad");
            String memStr = extraerValorJson(pj, "memoria");
            String deadStr = extraerValorJson(pj, "deadline");
            String instStr = extraerValorJson(pj, "instrucciones");

            if (!nombre.isEmpty() && !tipoStr.isEmpty()) {
                TipoProceso tipo = TipoProceso.valueOf(tipoStr);
                int prio = !prioStr.isEmpty() ? Integer.parseInt(prioStr) : 1;
                int mem = !memStr.isEmpty() ? Integer.parseInt(memStr) : 256;
                int deadline = !deadStr.isEmpty() ? Integer.parseInt(deadStr) : 20;
                int inst = !instStr.isEmpty() ? Integer.parseInt(instStr) : 5;

                Proceso p = new Proceso(nombre, idComp, tipo, prio, mem, deadline, inst);

                String idBuf = extraerValorJson(pj, "idBuffer");
                if (!idBuf.isEmpty()) {
                    p.getPcb().setIdBuffer(idBuf);
                }
                String cpiStr = extraerValorJson(pj, "ciclosPorItem");
                if (!cpiStr.isEmpty()) {
                    p.getPcb().setCiclosPorItem(Integer.parseInt(cpiStr));
                }
                String reqStr = extraerValorJson(pj, "itemsRequeridos");
                if (!reqStr.isEmpty()) {
                    p.getPcb().setItemsRequeridos(Integer.parseInt(reqStr));
                }
                String eioStr = extraerValorJson(pj, "ciclosEntreIO");
                if (!eioStr.isEmpty()) {
                    p.getPcb().setCiclosEntreIO(Integer.parseInt(eioStr));
                }
                String dioStr = extraerValorJson(pj, "duracionBloqueoIO");
                if (!dioStr.isEmpty()) {
                    p.getPcb().setDuracionBloqueoIO(Integer.parseInt(dioStr));
                }

                Computador comp = cluster.buscarPorId(idComp);
                if (comp != null) {
                    comp.admitirProceso(p, cluster.getRelojGlobal().getCicloActual());
                } else {
                    cluster.asignarProcesoAutomatico(p);
                }
                cargados++;
            }
        }

        return cargados;
    }

    // -------------------------------------------------------------------------
    // Funciones Auxiliares de Parseo JSON sin Dependencias Externas
    // -------------------------------------------------------------------------

    private static String extraerValorJson(String jsonObjeto, String clave) {
        String patron = "\"" + clave + "\"\\s*:\\s*(\"[^\"]*\"|[^,\\}\\s]+)";
        Matcher m = Pattern.compile(patron).matcher(jsonObjeto);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                return val.substring(1, val.length() - 1);
            }
            return val;
        }
        return "";
    }

    private static List<String> extraerObjetosArrayJson(String json, String nombreArray) {
        List<String> objetos = new ArrayList<>();
        int idx = json.indexOf("\"" + nombreArray + "\"");
        if (idx == -1) return objetos;
        int inicioArray = json.indexOf("[", idx);
        int finArray = json.indexOf("]", inicioArray);
        if (inicioArray == -1 || finArray == -1) return objetos;

        String contenido = json.substring(inicioArray + 1, finArray);
        int profundidad = 0;
        int inicioObj = -1;
        for (int i = 0; i < contenido.length(); i++) {
            char c = contenido.charAt(i);
            if (c == '{') {
                if (profundidad == 0) inicioObj = i;
                profundidad++;
            } else if (c == '}') {
                profundidad--;
                if (profundidad == 0 && inicioObj != -1) {
                    objetos.add(contenido.substring(inicioObj, i + 1));
                    inicioObj = -1;
                }
            }
        }
        return objetos;
    }
}
