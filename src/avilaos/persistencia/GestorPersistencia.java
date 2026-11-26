package avilaos.persistencia;

import avilaos.enums.PoliticaDisco;
import avilaos.enums.PoliticaPlanificacion;
import avilaos.enums.TipoProceso;
import avilaos.enums.OperacionArchivo;
import avilaos.enums.ModoUsuario;
import avilaos.enums.Permiso;
import avilaos.hardware.ConfiguracionDisco;
import avilaos.hardware.DiscoSimulado;
import avilaos.proceso.PeticionArchivo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.Proceso;
import avilaos.sincronizacion.BufferAcotado;
import avilaos.sistema.ClusterDistribuido;
import avilaos.sistema.Computador;
import avilaos.sistemaarchivos.Archivo;
import avilaos.sistemaarchivos.Directorio;
import avilaos.sistemaarchivos.NodoSistemaArchivos;
import avilaos.sistemaarchivos.SistemaArchivos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
            writer.write("nombre,computador,tipo,prioridad,memoria,deadline,instrucciones,idBuffer,ciclosPorItem,itemsRequeridos,ciclosEntreIO,duracionBloqueoIO,peticionArchivo\n");

            ListaEnlazada<String> procesosExportados = new ListaEnlazada<>();
            for (int i = 0; i < totalComp; i++) {
                Computador c = cluster.getComputadores().obtener(i);

                // Proceso en CPU
                Proceso enCpu = c.getCpu().getProcesoActual();
                if (enCpu != null && !enCpu.estaTerminado() && agregarSiNoExiste(procesosExportados, enCpu.getId())) {
                    escribirLineaProcesoCsv(writer, enCpu, c.getId());
                }

                // Procesos en cola de listos
                int listos = c.getSo().getColaListos().obtenerTamano();
                for (int j = 0; j < listos; j++) {
                    Proceso p = c.getSo().getColaListos().obtener(j);
                    if (p != null && agregarSiNoExiste(procesosExportados, p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }

                // Procesos en cola de nuevos
                int nuevos = c.getSo().getColaNuevos().obtenerTamano();
                for (int j = 0; j < nuevos; j++) {
                    Proceso p = c.getSo().getColaNuevos().obtener(j);
                    if (p != null && agregarSiNoExiste(procesosExportados, p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }

                // Procesos en cola de bloqueados
                int bloqueados = c.getSo().getColaBloqueados().obtenerTamano();
                for (int j = 0; j < bloqueados; j++) {
                    Proceso p = c.getSo().getColaBloqueados().obtener(j);
                    if (p != null && agregarSiNoExiste(procesosExportados, p.getId())) {
                        escribirLineaProcesoCsv(writer, p, c.getId());
                    }
                }
            }
        }
    }

    private static void escribirLineaProcesoCsv(FileWriter writer, Proceso p, String idComp) throws IOException {
        String idBuffer = p.getPcb().getIdBuffer() != null ? p.getPcb().getIdBuffer() : "";
        PeticionArchivo peticion = p.getPcb().getPeticionArchivo();
        writer.write(String.format("%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%s\n",
                celdaCsv(p.getPcb().getNombre()), celdaCsv(idComp), p.getPcb().getTipo().name(),
                p.getPcb().getPrioridad(), p.getPcb().getMemoriaAsignada(),
                p.getPcb().getDeadlineInicial(), p.getPcb().getTiempoEjecucionTotal(),
                celdaCsv(idBuffer), p.getPcb().getCiclosPorItem(), p.getPcb().getItemsRequeridos(),
                p.getPcb().getCiclosEntreIO(), p.getPcb().getDuracionBloqueoIO(),
                peticion == null ? "" : celdaCsv(jsonPeticion(peticion))));
    }

    private static String celdaCsv(String texto) {
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }

    private static String[] camposProcesoCsv(String linea, BufferedReader reader) throws IOException {
        ListaEnlazada<String> campos = new ListaEnlazada<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        while (true) {
            for (int i = 0; i < linea.length(); i++) {
                char c = linea.charAt(i);
                if (c == '"' && (entreComillas || campo.isEmpty())) {
                    if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = !entreComillas;
                    }
                } else if (c == ',' && !entreComillas) {
                    campos.agregar(campo.toString());
                    campo.setLength(0);
                } else {
                    campo.append(c);
                }
            }
            if (!entreComillas) break;
            linea = reader.readLine();
            if (linea == null) throw new IOException("Campo CSV sin comilla de cierre.");
            campo.append('\n');
        }
        campos.agregar(campo.toString());
        String[] resultado = new String[campos.obtenerTamano()];
        for (int i = 0; i < resultado.length; i++) resultado[i] = campos.obtener(i);
        return resultado;
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
                        String[] partes = camposProcesoCsv(linea, reader);
                        if (partes.length >= 7) {
                            String nombre = partes[0].trim();
                            String idComp = partes[1].trim();
                            TipoProceso tipo = TipoProceso.valueOf(partes[2].trim());
                            int prioridad = Integer.parseInt(partes[3].trim());
                            int memoria = Integer.parseInt(partes[4].trim());
                            int deadline = Integer.parseInt(partes[5].trim());
                            int instrucciones = Integer.parseInt(partes[6].trim());

                            PeticionArchivo peticion = partes.length > 12 && !partes[12].isBlank()
                                    ? leerPeticion(partes[12], tipo) : null;
                            Proceso p = new Proceso(nombre, idComp, tipo, prioridad, memoria, deadline, instrucciones, peticion);

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
            json.append("      \"id\": \"").append(escaparJson(c.getId())).append("\",\n");
            json.append("      \"nombre\": \"").append(escaparJson(c.getNombre())).append("\",\n");
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
            json.append("      \"id\": \"").append(escaparJson(b.getId())).append("\",\n");
            json.append("      \"nombre\": \"").append(escaparJson(b.getNombre())).append("\",\n");
            json.append("      \"computadorAnfitrion\": \"").append(escaparJson(b.getIdComputadorAnfitrion())).append("\",\n");
            json.append("      \"capacidad\": ").append(b.getCapacidad()).append(",\n");
            json.append("      \"memoriaMb\": ").append(b.getMemoriaReservadaMb()).append("\n");
            json.append("    }").append(i < totalBuffers - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");

        // 4. Procesos
        json.append("  \"procesos\": [\n");
        ListaEnlazada<String> lineasProcesos = new ListaEnlazada<>();
        ListaEnlazada<String> exportados = new ListaEnlazada<>();

        for (int i = 0; i < totalComp; i++) {
            Computador c = cluster.getComputadores().obtener(i);

            // Proceso en CPU
            Proceso enCpu = c.getCpu().getProcesoActual();
            if (enCpu != null && !enCpu.estaTerminado() && agregarSiNoExiste(exportados, enCpu.getId())) {
                lineasProcesos.agregar(generarJsonProceso(enCpu, c.getId()));
            }

            // Cola de Listos
            int listos = c.getSo().getColaListos().obtenerTamano();
            for (int j = 0; j < listos; j++) {
                Proceso p = c.getSo().getColaListos().obtener(j);
                if (p != null && agregarSiNoExiste(exportados, p.getId())) {
                    lineasProcesos.agregar(generarJsonProceso(p, c.getId()));
                }
            }

            // Cola de Nuevos
            int nuevos = c.getSo().getColaNuevos().obtenerTamano();
            for (int j = 0; j < nuevos; j++) {
                Proceso p = c.getSo().getColaNuevos().obtener(j);
                if (p != null && agregarSiNoExiste(exportados, p.getId())) {
                    lineasProcesos.agregar(generarJsonProceso(p, c.getId()));
                }
            }

            // Cola de Bloqueados
            int bloqueados = c.getSo().getColaBloqueados().obtenerTamano();
            for (int j = 0; j < bloqueados; j++) {
                Proceso p = c.getSo().getColaBloqueados().obtener(j);
                if (p != null && agregarSiNoExiste(exportados, p.getId())) {
                    lineasProcesos.agregar(generarJsonProceso(p, c.getId()));
                }
            }
        }

        int totalLineas = lineasProcesos.obtenerTamano();
        for (int i = 0; i < totalLineas; i++) {
            json.append(lineasProcesos.obtener(i)).append(i < totalLineas - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        try (FileWriter writer = new FileWriter(archivo)) {
            writer.write(json.toString());
        }
    }

    private static String generarJsonProceso(Proceso p, String idComp) {
        String idBuffer = p.getPcb().getIdBuffer() != null ? p.getPcb().getIdBuffer() : "";
        PeticionArchivo peticion = p.getPcb().getPeticionArchivo();
        return "    {\n"
                + (peticion == null ? "" : "      \"peticionArchivo\": " + jsonPeticion(peticion) + ",\n")
                + "      \"nombre\": \"" + escaparJson(p.getPcb().getNombre()) + "\",\n"
                + "      \"computador\": \"" + escaparJson(idComp) + "\",\n"
                + "      \"tipo\": \"" + p.getPcb().getTipo().name() + "\",\n"
                + "      \"prioridad\": " + p.getPcb().getPrioridad() + ",\n"
                + "      \"memoria\": " + p.getPcb().getMemoriaAsignada() + ",\n"
                + "      \"deadline\": " + p.getPcb().getDeadlineInicial() + ",\n"
                + "      \"instrucciones\": " + p.getPcb().getTiempoEjecucionTotal() + ",\n"
                + "      \"idBuffer\": \"" + escaparJson(idBuffer) + "\",\n"
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
        ValidadorJson.validarObjeto(json);
        synchronized (cluster.getRelojGlobal()) {
            validarCamposEscenarioJson(json);
            validarBuffersEscenarioJson(json, cluster);
            validarReferenciasProcesos(json, cluster);

            // 1. Configuración de reloj y red
            String duracionMs = extraerValorJson(configuracionJson(json), "duracionCicloMs");
            if (!duracionMs.isEmpty()) {
                cluster.getRelojGlobal().setDuracionCicloMs(Long.parseLong(duracionMs));
            }
            String latenciaRed = extraerValorJson(configuracionJson(json), "latenciaRedCiclos");
            if (!latenciaRed.isEmpty()) {
                cluster.setLatenciaRedCiclos(Integer.parseInt(latenciaRed));
            }

            // 2. Computadores
            ListaEnlazada<String> compsJson = extraerObjetosArrayJson(json, "computadores");
            for (int k = 0; k < compsJson.obtenerTamano(); k++) {
                String cj = compsJson.obtener(k);
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
                            comp.cambiarPolitica(PoliticaPlanificacion.valueOf(polStr));
                        }
                        if (!quantumStr.isEmpty()) {
                            comp.cambiarQuantum(Integer.parseInt(quantumStr));
                        }
                    }
                }
            }

            // 3. Buffers
            ListaEnlazada<String> buffersJson = extraerObjetosArrayJson(json, "buffers");
            for (int k = 0; k < buffersJson.obtenerTamano(); k++) {
                String bj = buffersJson.obtener(k);
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
            ListaEnlazada<String> procesosJson = extraerObjetosArrayJson(json, "procesos");
            for (int k = 0; k < procesosJson.obtenerTamano(); k++) {
                String pj = procesosJson.obtener(k);
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

                    Proceso p = new Proceso(nombre, idComp, tipo, prio, mem, deadline, inst,
                            leerPeticion(ValidadorJson.miembro(pj, "peticionArchivo"), tipo));

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
    }

    // -------------------------------------------------------------------------
    // Funciones Auxiliares de Parseo JSON sin Dependencias Externas
    // -------------------------------------------------------------------------

    /**
     * Valida los valores antes de modificar el clúster. La validación estructural
     * completa y la aplicación transaccional se realizan por separado.
     */
    private static String configuracionJson(String json) throws IOException {
        String valor = ValidadorJson.miembro(json, "configuracion");
        return valor == null ? "{}" : valor;
    }

    private static void validarCamposEscenarioJson(String json) throws IOException {
        try {
            validarEnteroJson(configuracionJson(json), "duracionCicloMs", true);
            validarEnteroJson(configuracionJson(json), "latenciaRedCiclos", false);
            ListaEnlazada<String> computadores = extraerObjetosArrayJson(json, "computadores");
            ListaEnlazada<String> ids = new ListaEnlazada<>();
            for (int i = 0; i < computadores.obtenerTamano(); i++) {
                String objeto = computadores.obtener(i);
                validarTextoJson(objeto, "id", true);
                validarTextoJson(objeto, "nombre", false);
                validarTextoJson(objeto, "politica", false);
                String id = extraerValorJson(objeto, "id").toLowerCase(java.util.Locale.ROOT);
                if (ids.contiene(id)) throw new IOException("Computador duplicado: " + id);
                ids.agregar(id);
                if (ValidadorJson.miembro(objeto, "ramTotalMb") == null)
                    throw new IOException("Falta ramTotalMb en computador " + id);
                validarEnteroJson(objeto, "ramTotalMb", false);
                validarEnteroJson(objeto, "quantum", false);
                String ram = extraerValorJson(objeto, "ramTotalMb");
                if (!ram.isEmpty() && Integer.parseInt(ram) < 1) {
                    throw new IllegalArgumentException("ramTotalMb debe ser mayor que cero");
                }
                String politica = extraerValorJson(objeto, "politica");
                if (!politica.isEmpty()) PoliticaPlanificacion.valueOf(politica);
            }
            ListaEnlazada<String> buffers = extraerObjetosArrayJson(json, "buffers");
            for (int i = 0; i < buffers.obtenerTamano(); i++) {
                String objeto = buffers.obtener(i);
                validarTextoJson(objeto, "id", true);
                validarTextoJson(objeto, "nombre", false);
                validarTextoJson(objeto, "computadorAnfitrion", true);
                validarEnteroJson(objeto, "capacidad", false);
                validarEnteroJson(objeto, "memoriaMb", false);
                String capacidad = extraerValorJson(objeto, "capacidad");
                if (!capacidad.isEmpty() && Integer.parseInt(capacidad) < 1) {
                    throw new IllegalArgumentException("capacidad del buffer debe ser mayor que cero");
                }
            }
            ListaEnlazada<String> procesos = extraerObjetosArrayJson(json, "procesos");
            for (int i = 0; i < procesos.obtenerTamano(); i++) {
                String objeto = procesos.obtener(i);
                validarTextoJson(objeto, "nombre", true);
                validarTextoJson(objeto, "tipo", true);
                validarTextoJson(objeto, "computador", false);
                validarTextoJson(objeto, "idBuffer", false);
                String tipo = extraerValorJson(objeto, "tipo");
                leerPeticion(ValidadorJson.miembro(objeto, "peticionArchivo"), TipoProceso.valueOf(tipo));
                String[] campos = {"prioridad", "memoria", "deadline", "instrucciones",
                    "ciclosPorItem", "itemsRequeridos", "ciclosEntreIO", "duracionBloqueoIO"};
                for (String campo : campos) validarEnteroJson(objeto, campo, false);
                for (String campo : new String[]{"memoria", "deadline", "instrucciones", "prioridad"}) {
                    String valor = extraerValorJson(objeto, campo);
                    if (!valor.isEmpty() && Integer.parseInt(valor) == 0)
                        throw new IOException("El campo " + campo + " debe ser positivo.");
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Escenario JSON inválido: " + e.getMessage(), e);
        }
    }

    private static String jsonPeticion(PeticionArchivo p) {
        return "{\"operacion\":\"" + p.operacion().name() + "\",\"ruta\":\"" + escaparJson(p.ruta())
                + "\",\"usuario\":\"" + escaparJson(p.usuario()) + "\",\"modo\":\"" + p.modo().name()
                + "\",\"tamanoBloques\":" + p.tamanoBloques()
                + (p.permiso() == null ? "" : ",\"permiso\":\"" + p.permiso().name() + "\"")
                + (p.nuevoNombre() == null ? "" : ",\"nuevoNombre\":\"" + escaparJson(p.nuevoNombre()) + "\"") + "}";
    }

    private static PeticionArchivo leerPeticion(String json, TipoProceso tipo) throws IOException {
        if (json == null) return null;
        if (tipo != TipoProceso.IO_BOUND) throw new IOException("La petición de archivo requiere un proceso de E/S.");
        ValidadorJson.validarObjeto(json);
        for (String campo : new String[]{"operacion", "ruta", "usuario", "modo"}) validarTextoJson(json, campo, true);
        validarTextoJson(json, "permiso", false);
        validarTextoJson(json, "nuevoNombre", false);
        validarEnteroJson(json, "tamanoBloques", false);
        String permiso = extraerValorJson(json, "permiso");
        String tamano = extraerValorJson(json, "tamanoBloques");
        String nombre = extraerValorJson(json, "nuevoNombre");
        try {
            return new PeticionArchivo(OperacionArchivo.valueOf(extraerValorJson(json, "operacion")),
                    extraerValorJson(json, "ruta"), extraerValorJson(json, "usuario"),
                    ModoUsuario.valueOf(extraerValorJson(json, "modo")), tamano.isEmpty() ? 0 : Integer.parseInt(tamano),
                    permiso.isEmpty() ? null : Permiso.valueOf(permiso), nombre.isEmpty() ? null : nombre);
        } catch (IllegalArgumentException e) {
            throw new IOException("Petición de archivo inválida: " + e.getMessage(), e);
        }
    }

    private static void validarEnteroJson(String objeto, String campo, boolean largo) throws IOException {
        String bruto = ValidadorJson.miembro(objeto, campo);
        if (bruto != null && !bruto.matches("[0-9]+")) {
            throw new IOException("El campo '" + campo + "' debe ser un entero no negativo, sin comillas.");
        }
        String valor = extraerValorJson(objeto, campo);
        if (!valor.isEmpty()) {
            try {
                if (largo) Long.parseLong(valor);
                else Integer.parseInt(valor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("campo '" + campo + "' no es un entero válido", e);
            }
        }
    }

    private static void validarTextoJson(String objeto, String campo, boolean obligatorio) throws IOException {
        String bruto = ValidadorJson.miembro(objeto, campo);
        if (bruto == null) {
            if (obligatorio) throw new IOException("Falta el campo '" + campo + "'.");
            return;
        }
        if (!bruto.startsWith("\"")) throw new IOException("El campo '" + campo + "' debe ser texto.");
        if (obligatorio && extraerValorJson(objeto, campo).isBlank())
            throw new IOException("El campo '" + campo + "' no puede estar vacío.");
    }

    private static void validarBuffersEscenarioJson(String json, ClusterDistribuido cluster) throws IOException {
        ListaEnlazada<String> computadores = extraerObjetosArrayJson(json, "computadores");
        ListaEnlazada<String> buffers = extraerObjetosArrayJson(json, "buffers");
        for (int i = 0; i < buffers.obtenerTamano(); i++) {
            String buffer = buffers.obtener(i);
            String id = extraerValorJson(buffer, "id");
            String host = extraerValorJson(buffer, "computadorAnfitrion");
            if (id.isBlank() || host.isBlank()) {
                throw new IOException("Cada buffer debe tener un ID y un computador anfitrión.");
            }
            String memoriaStr = extraerValorJson(buffer, "memoriaMb");
            int memoria = memoriaStr.isEmpty() ? 256 : Integer.parseInt(memoriaStr);
            if (memoria < 0) {
                throw new IOException("La memoria del buffer " + id + " no puede ser negativa.");
            }
            if (cluster.getGestorSincronizacion().buscarBuffer(id) != null) continue;

            Computador existente = cluster.buscarPorId(host);
            long libre = existente != null ? existente.getMemoria().getMemoriaLibre() : -1;
            if (existente == null) {
                for (int j = 0; j < computadores.obtenerTamano(); j++) {
                    String computador = computadores.obtener(j);
                    if (host.equalsIgnoreCase(extraerValorJson(computador, "id"))) {
                        String ram = extraerValorJson(computador, "ramTotalMb");
                        if (!ram.isEmpty()) libre = Integer.parseInt(ram);
                        break;
                    }
                }
            }
            if (libre < 0) {
                throw new IOException("No existe el computador anfitrión '" + host + "' del buffer " + id + ".");
            }

            long reservado = 0;
            for (int j = 0; j < i; j++) {
                String anterior = buffers.obtener(j);
                String idAnterior = extraerValorJson(anterior, "id");
                if (idAnterior.equalsIgnoreCase(id)) {
                    throw new IOException("ID de buffer duplicado: " + id + ".");
                }
                if (host.equalsIgnoreCase(extraerValorJson(anterior, "computadorAnfitrion"))
                        && cluster.getGestorSincronizacion().buscarBuffer(idAnterior) == null) {
                    String memoriaAnterior = extraerValorJson(anterior, "memoriaMb");
                    reservado += memoriaAnterior.isEmpty() ? 256 : Integer.parseInt(memoriaAnterior);
                }
            }
            if (reservado + memoria > libre) {
                throw new IOException("RAM insuficiente en " + host + " para el buffer " + id + ".");
            }
        }
    }

    private static void validarReferenciasProcesos(String json, ClusterDistribuido cluster) throws IOException {
        ListaEnlazada<String> computadores = extraerObjetosArrayJson(json, "computadores");
        ListaEnlazada<String> buffers = extraerObjetosArrayJson(json, "buffers");
        ListaEnlazada<String> procesos = extraerObjetosArrayJson(json, "procesos");
        for (int i = 0; i < procesos.obtenerTamano(); i++) {
            String p = procesos.obtener(i);
            String host = extraerValorJson(p, "computador");
            if (!host.isEmpty() && cluster.buscarPorId(host) == null && !contieneId(computadores, host))
                throw new IOException("Computador desconocido en proceso: " + host);
            String buffer = extraerValorJson(p, "idBuffer");
            if (!buffer.isEmpty() && cluster.getGestorSincronizacion().buscarBuffer(buffer) == null
                    && !contieneId(buffers, buffer))
                throw new IOException("Buffer desconocido en proceso: " + buffer);
        }
    }

    private static boolean contieneId(ListaEnlazada<String> objetos, String id) throws IOException {
        for (int i = 0; i < objetos.obtenerTamano(); i++)
            if (id.equalsIgnoreCase(extraerValorJson(objetos.obtener(i), "id"))) return true;
        return false;
    }

    private static String escaparJson(String texto) {
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (c < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) c));
                    } else {
                        resultado.append(c);
                    }
                }
            }
        }
        return resultado.toString();
    }

    static String desescaparJson(String texto) {
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c != '\\') {
                resultado.append(c);
                continue;
            }
            if (++i >= texto.length()) {
                throw new IllegalArgumentException("Escape JSON incompleto");
            }
            char escape = texto.charAt(i);
            switch (escape) {
                case '"', '\\', '/' -> resultado.append(escape);
                case 'b' -> resultado.append('\b');
                case 'f' -> resultado.append('\f');
                case 'n' -> resultado.append('\n');
                case 'r' -> resultado.append('\r');
                case 't' -> resultado.append('\t');
                case 'u' -> {
                    if (i + 4 >= texto.length()) {
                        throw new IllegalArgumentException("Escape Unicode JSON incompleto");
                    }
                    resultado.append((char) Integer.parseInt(texto.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Escape JSON inválido");
            }
        }
        return resultado.toString();
    }

    private static String extraerValorJson(String objeto, String clave) throws IOException {
        String valor = ValidadorJson.miembro(objeto, clave);
        if (valor == null) return "";
        if (valor.startsWith("\"")) return desescaparJson(valor.substring(1, valor.length() - 1));
        return valor;
    }

    /**
     * Registra un ID en la lista de exportados si aún no estaba (equivalente a un conjunto).
     *
     * @return {@code true} si el ID era nuevo y se agregó
     */
    private static boolean agregarSiNoExiste(ListaEnlazada<String> lista, String id) {
        if (lista.contiene(id)) {
            return false;
        }
        lista.agregar(id);
        return true;
    }

    private static ListaEnlazada<String> extraerObjetosArrayJson(String json, String nombreArray) throws IOException {
        String valor = ValidadorJson.miembro(json, nombreArray);
        return valor == null ? new ListaEnlazada<>() : ValidadorJson.objetos(valor);
    }

    // =========================================================================
    // Proyecto 2: Persistencia obligatoria de los 2 JSON (Sistema de Archivos y Procesos)
    // =========================================================================

    /**
     * Exporta el estado actual del disco y del sistema de archivos jerárquico
     * en el formato JSON oficial del Proyecto 2 ({@code diskConfig} + {@code fileSystem}).
     */
    public static void exportarSistemaArchivosJson(File archivo, Computador comp) throws IOException {
        if (comp == null) throw new IOException("Computador inválido.");
        var estado = comp.getSistemaArchivos().capturarEstadoPersistente();
        DiscoSimulado.Vista vista = estado.disco();
        DiscoSimulado.Bloque[] bloquesDisco = vista.bloques();
        int totalBlocks = bloquesDisco.length;
        while (totalBlocks > 0 && bloquesDisco[totalBlocks - 1].swap()) totalBlocks--;
        int initialHead = Math.min(vista.cabezal(), Math.max(0, totalBlocks - 1));

        ListaEnlazada<NodoSistemaArchivos> nodos = new ListaEnlazada<>();
        recolectarPreorden(estado.raiz(), nodos);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"diskConfig\": {\n");
        sb.append("    \"totalBlocks\": ").append(totalBlocks).append(",\n");
        sb.append("    \"initialHead\": ").append(initialHead).append("\n");
        sb.append("  },\n");
        sb.append("  \"fileSystem\": [\n");
        int total = nodos.obtenerTamano();
        for (int i = 0; i < total; i++) {
            NodoSistemaArchivos nodo = nodos.obtener(i);
            sb.append("    {\n");
            sb.append("      \"path\": \"").append(escaparJson(nodo.getRuta())).append("\",\n");
            if (nodo instanceof Archivo arch) {
                sb.append("      \"type\": \"FILE\",\n");
                sb.append("      \"startBlock\": ").append(arch.getPrimerBloque()).append(",\n");
                sb.append("      \"blocks\": ").append(arch.getTamanoBloques()).append(",\n");
                sb.append("      \"blockChain\": [");
                int bloque = arch.getPrimerBloque();
                for (int j = 0; j < arch.getTamanoBloques(); j++) {
                    if (bloque < 0 || bloque >= totalBlocks || !arch.getRuta().equals(bloquesDisco[bloque].ocupante())) {
                        throw new IOException("Cadena de bloques inconsistente para " + arch.getRuta() + ".");
                    }
                    if (j > 0) sb.append(", ");
                    sb.append(bloque);
                    bloque = bloquesDisco[bloque].siguiente();
                }
                if (bloque != -1) {
                    throw new IOException("Cadena de bloques demasiado larga para " + arch.getRuta() + ".");
                }
                sb.append("],\n");
            } else {
                sb.append("      \"type\": \"DIRECTORY\",\n");
            }
            sb.append("      \"owner\": \"").append(escaparJson(nodo.getDueno())).append("\",\n");
            String perm = nodo.getPermiso() == Permiso.PUBLICO ? "public" : "private";
            sb.append("      \"permissions\": \"").append(perm).append("\"\n");
            sb.append("    }").append(i + 1 < total ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Files.writeString(archivo.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void recolectarPreorden(Directorio dir, ListaEnlazada<NodoSistemaArchivos> destino) {
        int hijos = dir.getCantidadHijos();
        for (int i = 0; i < hijos; i++) {
            NodoSistemaArchivos hijo = dir.getHijo(i);
            destino.agregar(hijo);
            if (hijo instanceof Directorio sub) {
                recolectarPreorden(sub, destino);
            }
        }
    }

    /**
     * Carga el JSON 1 (Sistema de Archivos: {@code diskConfig} + {@code fileSystem})
     * inicializando el disco, la posición del cabezal y la jerarquía de directorios y archivos.
     *
     * @return cantidad de elementos (directorios + archivos) cargados
     */
    public static int importarSistemaArchivosJson(File archivo, Computador comp) throws IOException {
        if (comp == null) throw new IOException("Computador inválido.");
        synchronized (comp) {
            var carga = prepararSistemaArchivos(archivo, comp.getDisco().getConfiguracion());
            aplicarSistemaArchivos(carga, comp);
            return carga.elementos();
        }
    }

    /** Si ya hay un JSON de procesos seleccionado, ambos archivos se validan antes de aplicar la carga. */
    public static int importarSistemaArchivosJson(File archivo, File archivoProcesos,
                                                 ClusterDistribuido cluster) throws IOException {
        Computador comp = computadorParaImportar(cluster);
        var procesos = archivoProcesos == null ? null : prepararProcesosConfig(archivoProcesos);
        synchronized (cluster.getRelojGlobal()) {
            synchronized (comp) {
                var carga = prepararSistemaArchivos(archivo, comp.getDisco().getConfiguracion());
                if (procesos != null) validarTamanoDisco(carga.bloquesDatos(), procesos);
                aplicarSistemaArchivos(carga, comp);
                if (procesos != null) aplicarProcesosConfig(procesos, cluster, comp);
                return carga.elementos();
            }
        }
    }

    private record CargaSistemaArchivos(SistemaArchivos sistema, int bloquesDatos, int elementos) {}

    private static void aplicarSistemaArchivos(CargaSistemaArchivos carga, Computador comp) {
        comp.getSo().reiniciarProcesos();
        comp.getSistemaArchivos().restaurarDesde(carga.sistema());
    }

    private static CargaSistemaArchivos prepararSistemaArchivos(File archivo, ConfiguracionDisco prevCfg)
            throws IOException {
        String json = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        ValidadorJson.validarObjeto(json);

        String diskConfigStr = ValidadorJson.miembro(json, "diskConfig");
        if (diskConfigStr == null && ValidadorJson.miembro(json, "processes") != null) {
            throw new IOException("Este archivo contiene procesos. Ábralo con el botón «Cargar Procesos JSON».");
        }
        if (diskConfigStr == null || !diskConfigStr.startsWith("{")) {
            throw new IOException("Falta el objeto obligatorio 'diskConfig' en el JSON del sistema de archivos.");
        }
        int totalBlocks = leerEnteroObligatorio(diskConfigStr, "totalBlocks", 1, 500);
        int initialHead = leerEnteroObligatorio(diskConfigStr, "initialHead", 0, totalBlocks - 1);

        String fsArrayStr = ValidadorJson.miembro(json, "fileSystem");
        if (fsArrayStr == null || !fsArrayStr.startsWith("[")) {
            throw new IOException("Falta el arreglo obligatorio 'fileSystem' en el JSON del sistema de archivos.");
        }
        ListaEnlazada<String> entradas = ValidadorJson.objetos(fsArrayStr);
        int[][] cadenas = new int[entradas.obtenerTamano()][];
        boolean[] ocupados = new boolean[totalBlocks];
        for (int i = 0; i < cadenas.length; i++) {
            String item = entradas.obtener(i);
            if ("FILE".equalsIgnoreCase(leerTextoObligatorio(item, "type"))) {
                cadenas[i] = leerCadenaBloques(item, totalBlocks, ocupados);
            }
        }

        // Conservar swap y política previos si ya existían, asegurando capacidad totalBlocks + swap
        int swapActual = prevCfg.swap();
        int totalDisco = totalBlocks + swapActual;
        int velocidad = Math.min(prevCfg.velocidad(), totalDisco);
        ConfiguracionDisco nuevaCfg;
        try {
            nuevaCfg = new ConfiguracionDisco(totalDisco, swapActual, initialHead, velocidad, prevCfg.politica());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Configuración del disco inválida: " + ex.getMessage(), ex);
        }
        SistemaArchivos preparado = new SistemaArchivos(new DiscoSimulado(nuevaCfg));

        // Primera pasada: directorios
        int cargados = 0;
        for (int i = 0; i < entradas.obtenerTamano(); i++) {
            String item = entradas.obtener(i);
            String type = leerTextoObligatorio(item, "type").toUpperCase();
            if ("DIRECTORY".equals(type)) {
                String path = leerTextoObligatorio(item, "path");
                String owner = leerTextoObligatorio(item, "owner");
                Permiso perm = parsearPermisoJson(leerTextoObligatorio(item, "permissions"));
                try {
                    preparado.cargarDirectorioDesdeJson(path, owner, perm);
                    cargados++;
                } catch (RuntimeException ex) {
                    throw new IOException("Error cargando directorio " + path + ": " + ex.getMessage(), ex);
                }
            } else if (!"FILE".equals(type)) {
                throw new IOException("Tipo de nodo inválido en fileSystem: '" + type + "' (use FILE o DIRECTORY).");
            }
        }

        // Segunda pasada: archivos con su startBlock exacto
        for (int i = 0; i < entradas.obtenerTamano(); i++) {
            String item = entradas.obtener(i);
            String type = leerTextoObligatorio(item, "type").toUpperCase();
            if ("FILE".equals(type)) {
                String path = leerTextoObligatorio(item, "path");
                String owner = leerTextoObligatorio(item, "owner");
                Permiso perm = parsearPermisoJson(leerTextoObligatorio(item, "permissions"));
                try {
                    preparado.cargarArchivoDesdeJson(path, owner, perm, cadenas[i]);
                    cargados++;
                } catch (RuntimeException ex) {
                    throw new IOException("Error cargando archivo " + path + ": " + ex.getMessage(), ex);
                }
            }
        }

        return new CargaSistemaArchivos(preparado, totalBlocks, cargados);
    }

    private static int[] leerCadenaBloques(String item, int totalBlocks, boolean[] ocupados) throws IOException {
        String ruta = leerTextoObligatorio(item, "path");
        int inicio = leerEnteroObligatorio(item, "startBlock", 0, totalBlocks - 1);
        int cantidad = leerEnteroObligatorio(item, "blocks", 1, totalBlocks);
        String cadenaJson = ValidadorJson.miembro(item, "blockChain");
        String[] direcciones = null;
        if (cadenaJson != null) {
            if (!cadenaJson.startsWith("[") || !cadenaJson.endsWith("]")) {
                throw new IOException("blockChain debe ser un arreglo para " + ruta + ".");
            }
            direcciones = cadenaJson.substring(1, cadenaJson.length() - 1).split(",", -1);
            if (direcciones.length != cantidad) {
                throw new IOException("El tamaño de blockChain no coincide con blocks para " + ruta + ".");
            }
        }

        int[] cadena = new int[cantidad];
        for (int i = 0; i < cantidad; i++) {
            int bloque;
            try {
                // Los JSON originales solo describen intervalos contiguos.
                bloque = direcciones == null ? inicio + i : Integer.parseInt(direcciones[i].trim());
            } catch (NumberFormatException ex) {
                throw new IOException("blockChain debe contener direcciones enteras para " + ruta + ".", ex);
            }
            if (i == 0 && bloque != inicio) {
                throw new IOException("El primer bloque de blockChain no coincide con startBlock para " + ruta + ".");
            }
            if (bloque < 0 || bloque >= totalBlocks || ocupados[bloque]) {
                throw new IOException("Bloque fuera del área de datos o repetido para " + ruta + ": " + bloque);
            }
            ocupados[bloque] = true;
            cadena[i] = bloque;
        }
        return cadena;
    }

    /**
     * Carga el JSON 2 (Procesos y Configuración: {@code globalConfig} + {@code computer} + {@code processes}).
     * Reinicia procesos y reloj preservando intacto el sistema de archivos cargado en el disco.
     *
     * @return cantidad de procesos de E/S cargados y admitidos
     */
    public static int importarProcesosConfigJson(File archivo, ClusterDistribuido cluster) throws IOException {
        Computador comp = computadorParaImportar(cluster);
        var carga = prepararProcesosConfig(archivo);
        synchronized (cluster.getRelojGlobal()) {
            synchronized (comp) {
                validarTamanoDisco(comp.getDisco().getBloquesDatosTotales(), carga);
                aplicarProcesosConfig(carga, cluster, comp);
            }
        }
        return carga.procesos().obtenerTamano();
    }

    private static Computador computadorParaImportar(ClusterDistribuido cluster) throws IOException {
        if (cluster == null || cluster.getCantidadComputadores() < 1) {
            throw new IOException("No hay computador disponible en el clúster.");
        }
        return cluster.getComputadores().obtener(0);
    }

    private record ProcesoImportado(String nombre, int prioridad, int memoria, int deadline,
                                    int instrucciones, PeticionArchivo peticion) {}

    private record CargaProcesos(int duracionCiclo, int swap, PoliticaDisco politicaDisco, int ram,
                                 PoliticaPlanificacion politicaCpu, int quantum,
                                 ListaEnlazada<ProcesoImportado> procesos) {}

    private static CargaProcesos prepararProcesosConfig(File archivo) throws IOException {
        String json = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        ValidadorJson.validarObjeto(json);

        String globalStr = ValidadorJson.miembro(json, "globalConfig");
        if (globalStr == null && ValidadorJson.miembro(json, "fileSystem") != null) {
            throw new IOException("Este archivo contiene el sistema de archivos. Ábralo con el botón «Cargar FS JSON».");
        }
        if (globalStr == null || !globalStr.startsWith("{")) {
            throw new IOException("Falta el objeto obligatorio 'globalConfig' en el JSON de procesos.");
        }
        int cycleDurationMs = leerEnteroObligatorio(globalStr, "cycleDurationMs", 10, 60000);
        int swapSizeBlocks = leerEnteroObligatorio(globalStr, "swapSizeBlocks", 0, 500);
        PoliticaDisco politicaDisco = parsearPoliticaDiscoJson(leerTextoObligatorio(globalStr, "initialDiskPolicy"));

        String compStr = ValidadorJson.miembro(json, "computer");
        if (compStr == null || !compStr.startsWith("{")) {
            throw new IOException("Falta el objeto obligatorio 'computer' en el JSON de procesos.");
        }
        int ramCapacity = leerEnteroObligatorio(compStr, "ramCapacity", 1, 65536);
        PoliticaPlanificacion politicaCpu = parsearPoliticaCpuJson(leerTextoObligatorio(compStr, "policy"));
        int quantum = leerEnteroObligatorio(compStr, "quantum", 0, 100);

        String procsStr = ValidadorJson.miembro(json, "processes");
        if (procsStr == null || !procsStr.startsWith("[")) {
            throw new IOException("Falta el arreglo obligatorio 'processes' en el JSON de procesos.");
        }
        ListaEnlazada<String> listaProcesos = ValidadorJson.objetos(procsStr);

        ListaEnlazada<ProcesoImportado> preparados = new ListaEnlazada<>();
        for (int i = 0; i < listaProcesos.obtenerTamano(); i++) {
            String obj = listaProcesos.obtener(i);
            String name = leerTextoObligatorio(obj, "name");
            int memory = leerEnteroObligatorio(obj, "memory", 1, ramCapacity);
            int deadline = leerEnteroObligatorio(obj, "deadline", 1, 100000);
            int instructions = leerEnteroObligatorio(obj, "instructions", 1, 100000);
            String targetPath = leerTextoObligatorio(obj, "targetPath");
            if (!targetPath.startsWith("/")) {
                throw new IOException("La ruta targetPath del proceso " + name + " debe iniciar con '/'.");
            }
            OperacionArchivo op = parsearOperacionJson(leerTextoObligatorio(obj, "operation"));
            int prioridad = leerEnteroOpcional(obj, "priority", 1, 1, 10);
            String owner = extraerValorJson(obj, "owner");
            if (owner.isBlank()) owner = SistemaArchivos.ADMINISTRADOR;

            int bloquesCrear = 0;
            Permiso permisoCrear = null;
            String nuevoNombre = null;
            if (op == OperacionArchivo.CREAR || op == OperacionArchivo.CREAR_DIRECTORIO) {
                bloquesCrear = op == OperacionArchivo.CREAR ? leerEnteroOpcional(obj, "blocks", 1, 1, 500)
                        : leerEnteroOpcional(obj, "blocks", 0, 0, 0);
                String permStr = extraerValorJson(obj, "permissions");
                permisoCrear = permStr.isBlank() ? Permiso.PUBLICO : parsearPermisoJson(permStr);
            } else if (op == OperacionArchivo.ACTUALIZAR) {
                String nn = extraerValorJson(obj, "newName");
                if (nn.isBlank()) {
                    int sep = targetPath.lastIndexOf('/');
                    nn = sep >= 0 && sep + 1 < targetPath.length() ? targetPath.substring(sep + 1) : "actualizado";
                }
                nuevoNombre = nn;
            }

            try {
                PeticionArchivo peticion = new PeticionArchivo(op, targetPath, owner,
                        ModoUsuario.ADMINISTRADOR, bloquesCrear, permisoCrear, nuevoNombre);
                preparados.agregar(new ProcesoImportado(name, prioridad, memory, deadline, instructions, peticion));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Proceso " + name + ": " + ex.getMessage(), ex);
            }
        }
        return new CargaProcesos(cycleDurationMs, swapSizeBlocks, politicaDisco, ramCapacity,
                politicaCpu, quantum, preparados);
    }

    private static void validarTamanoDisco(int bloquesDatos, CargaProcesos carga) throws IOException {
        if ((long) bloquesDatos + carga.swap() > 1000) {
            throw new IOException("Los bloques de datos y de swap superan la capacidad máxima del disco.");
        }
    }

    private static void aplicarProcesosConfig(CargaProcesos carga, ClusterDistribuido cluster, Computador comp) {
        cluster.getRelojGlobal().reiniciar();
        cluster.getRelojGlobal().setDuracionCicloMs(carga.duracionCiclo());
        comp.getSo().reiniciarProcesos();
        comp.getMemoria().reconfigurar(carga.ram());
        comp.getDisco().ajustarSwapYPolitica(carga.swap(), carga.politicaDisco());
        comp.cambiarPolitica(carga.politicaCpu());
        if (carga.quantum() > 0) {
            comp.cambiarQuantum(carga.quantum());
        }

        for (int i = 0; i < carga.procesos().obtenerTamano(); i++) {
            ProcesoImportado p = carga.procesos().obtener(i);
            comp.admitirProceso(new Proceso(p.nombre(), comp.getId(), TipoProceso.IO_BOUND, p.prioridad(),
                    p.memoria(), p.deadline(), p.instrucciones(), p.peticion()), 0);
        }
    }

    private static String leerTextoObligatorio(String obj, String campo) throws IOException {
        validarTextoJson(obj, campo, true);
        return extraerValorJson(obj, campo).trim();
    }

    private static int leerEnteroObligatorio(String obj, String campo, int min, int max) throws IOException {
        String bruto = ValidadorJson.miembro(obj, campo);
        if (bruto == null) {
            throw new IOException("Falta el campo numérico obligatorio '" + campo + "'.");
        }
        int valor;
        try {
            valor = Integer.parseInt(bruto.trim());
        } catch (NumberFormatException ex) {
            throw new IOException("El campo '" + campo + "' debe ser un entero válido (recibido: " + bruto + ").");
        }
        if (valor < min || valor > max) {
            throw new IOException("El campo '" + campo + "' (" + valor + ") está fuera del rango [" + min + ", " + max + "].");
        }
        return valor;
    }

    private static int leerEnteroOpcional(String obj, String campo, int defecto, int min, int max) throws IOException {
        String bruto = ValidadorJson.miembro(obj, campo);
        if (bruto == null) return defecto;
        return leerEnteroObligatorio(obj, campo, min, max);
    }

    private static Permiso parsearPermisoJson(String texto) throws IOException {
        return switch (texto.trim().toUpperCase()) {
            case "PUBLIC", "PUBLICO", "PÚBLICO" -> Permiso.PUBLICO;
            case "PRIVATE", "PRIVADO" -> Permiso.PRIVADO;
            default -> throw new IOException("Permiso inválido: '" + texto + "' (use public o private).");
        };
    }

    private static PoliticaDisco parsearPoliticaDiscoJson(String texto) throws IOException {
        return switch (texto.trim().toUpperCase().replace('-', '_')) {
            case "FIFO" -> PoliticaDisco.FIFO;
            case "SSTF" -> PoliticaDisco.SSTF;
            case "SCAN" -> PoliticaDisco.SCAN;
            case "C_SCAN", "CSCAN" -> PoliticaDisco.C_SCAN;
            default -> throw new IOException("Política de disco inválida: '" + texto + "'.");
        };
    }

    private static PoliticaPlanificacion parsearPoliticaCpuJson(String texto) throws IOException {
        return switch (texto.trim().toUpperCase().replace('-', '_')) {
            case "FCFS" -> PoliticaPlanificacion.FCFS;
            case "ROUND_ROBIN", "RR" -> PoliticaPlanificacion.ROUND_ROBIN;
            case "PRIORIDADES_APROPIATIVAS", "PRIORIDADES", "PRIORITY" -> PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS;
            case "EDF" -> PoliticaPlanificacion.EDF;
            default -> throw new IOException("Política de CPU inválida: '" + texto + "'.");
        };
    }

    private static OperacionArchivo parsearOperacionJson(String texto) throws IOException {
        return switch (texto.trim().toUpperCase()) {
            case "CREATE_DIRECTORY", "CREAR_DIRECTORIO" -> OperacionArchivo.CREAR_DIRECTORIO;
            case "READ", "LEER" -> OperacionArchivo.LEER;
            case "CREATE", "CREAR" -> OperacionArchivo.CREAR;
            case "UPDATE", "ACTUALIZAR" -> OperacionArchivo.ACTUALIZAR;
            case "DELETE", "ELIMINAR" -> OperacionArchivo.ELIMINAR;
            default -> throw new IOException("Operación de archivo inválida: '" + texto + "' (use READ, CREATE, UPDATE o DELETE).");
        };
    }
}
