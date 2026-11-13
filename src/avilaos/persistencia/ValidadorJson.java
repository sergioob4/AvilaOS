package avilaos.persistencia;

import java.io.IOException;
import avilaos.estructuras.ListaEnlazada;

/** Comprueba la sintaxis JSON antes de leer los datos de un escenario. */
final class ValidadorJson {
    private final String texto;
    private int posicion;

    private ValidadorJson(String texto) {
        this.texto = texto;
    }

    static void validarObjeto(String texto) throws IOException {
        ValidadorJson parser = new ValidadorJson(texto);
        parser.espacios();
        if (parser.actual() != '{') parser.error("se esperaba un objeto raíz");
        parser.valor(0);
        parser.espacios();
        if (parser.posicion != texto.length()) parser.error("contenido después del objeto raíz");
    }

    /** Devuelve únicamente un miembro directo; nunca busca dentro de sus hijos. */
    static String miembro(String texto, String clave) throws IOException {
        ValidadorJson p = new ValidadorJson(texto);
        p.espacios();
        p.exigir('{');
        p.espacios();
        if (p.tomar('}')) return null;
        do {
            p.espacios();
            int inicio = p.posicion;
            p.cadena();
            String nombre = GestorPersistencia.desescaparJson(texto.substring(inicio + 1, p.posicion - 1));
            p.espacios();
            p.exigir(':');
            p.espacios();
            inicio = p.posicion;
            p.valor(0);
            if (nombre.equals(clave)) return texto.substring(inicio, p.posicion);
            p.espacios();
            if (p.tomar('}')) return null;
            p.exigir(',');
        } while (true);
    }

    static ListaEnlazada<String> objetos(String texto) throws IOException {
        ListaEnlazada<String> resultado = new ListaEnlazada<>();
        ValidadorJson p = new ValidadorJson(texto);
        p.espacios();
        p.exigir('[');
        p.espacios();
        if (p.tomar(']')) return resultado;
        do {
            p.espacios();
            if (p.actual() != '{') p.error("se esperaba un objeto en el arreglo");
            int inicio = p.posicion;
            p.valor(0);
            resultado.agregar(texto.substring(inicio, p.posicion));
            p.espacios();
            if (p.tomar(']')) return resultado;
            p.exigir(',');
        } while (true);
    }

    private void valor(int profundidad) throws IOException {
        if (profundidad > 128) error("anidamiento excesivo");
        espacios();
        switch (actual()) {
            case '{' -> objeto(profundidad + 1);
            case '[' -> arreglo(profundidad + 1);
            case '"' -> cadena();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> numero();
        }
    }

    private void objeto(int profundidad) throws IOException {
        ListaEnlazada<String> claves = new ListaEnlazada<>();
        posicion++;
        espacios();
        if (tomar('}')) return;
        do {
            espacios();
            if (actual() != '"') error("se esperaba una clave");
            int inicio = posicion;
            cadena();
            String clave = GestorPersistencia.desescaparJson(texto.substring(inicio + 1, posicion - 1));
            if (claves.contiene(clave)) error("clave duplicada: " + clave);
            claves.agregar(clave);
            espacios();
            exigir(':');
            valor(profundidad);
            espacios();
            if (tomar('}')) return;
            exigir(',');
        } while (true);
    }

    private void arreglo(int profundidad) throws IOException {
        posicion++;
        espacios();
        if (tomar(']')) return;
        do {
            valor(profundidad);
            espacios();
            if (tomar(']')) return;
            exigir(',');
        } while (true);
    }

    private void cadena() throws IOException {
        exigir('"');
        while (posicion < texto.length()) {
            char c = texto.charAt(posicion++);
            if (c == '"') return;
            if (c < 0x20) error("carácter de control en cadena");
            if (c == '\\') {
                if (posicion >= texto.length()) error("escape incompleto");
                char escape = texto.charAt(posicion++);
                if (escape == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (posicion >= texto.length() || Character.digit(texto.charAt(posicion++), 16) < 0)
                            error("escape Unicode inválido");
                    }
                } else if ("\"\\/bfnrt".indexOf(escape) < 0) {
                    error("escape inválido");
                }
            }
        }
        error("cadena sin cerrar");
    }

    private void numero() throws IOException {
        tomar('-');
        if (tomar('0')) {
            if (esDigito(actual())) error("cero inicial en número");
        } else {
            if (actual() < '1' || actual() > '9') error("se esperaba un valor");
            while (esDigito(actual())) posicion++;
        }
        if (tomar('.')) digitos();
        if (tomar('e') || tomar('E')) {
            if (!tomar('+')) tomar('-');
            digitos();
        }
    }

    private void digitos() throws IOException {
        if (!esDigito(actual())) error("se esperaba un dígito");
        while (esDigito(actual())) posicion++;
    }

    private boolean esDigito(char c) {
        return c >= '0' && c <= '9';
    }

    private void literal(String valor) throws IOException {
        if (!texto.startsWith(valor, posicion)) error("literal inválido");
        posicion += valor.length();
    }

    private void espacios() {
        while (posicion < texto.length()) {
            char c = texto.charAt(posicion);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            posicion++;
        }
    }

    private char actual() {
        return posicion < texto.length() ? texto.charAt(posicion) : '\0';
    }

    private boolean tomar(char esperado) {
        if (actual() != esperado || posicion >= texto.length()) return false;
        posicion++;
        return true;
    }

    private void exigir(char esperado) throws IOException {
        if (!tomar(esperado)) error("se esperaba '" + esperado + "'");
    }

    private void error(String mensaje) throws IOException {
        throw new IOException("JSON inválido en posición " + (posicion + 1) + ": " + mensaje);
    }
}
