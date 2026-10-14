# ÁvilaOS — Simulador de Sistema Operativo Distribuido

**Universidad Metropolitana**  
**Departamento de Gestión de Proyectos y Sistemas**  
**Asignatura:** Sistemas Operativos (Trimestre 2627-1)  
**Proyecto 1:** Diseño de un Sistema Operativo Distribuido — Procesos y sincronización con semáforos  

---

## 📌 Descripción del Proyecto

**ÁvilaOS** es un simulador modular y concurrente que modela la operación y coordinación de un sistema operativo distribuido sobre un clúster de computadores homogéneos. 

El sistema recrea la interacción entre capas de hardware y software:
* **Hardware:** Reloj global sincronizado, procesadores (CPU monohilo con registros visibles `PC` y `MAR`), y memoria principal (RAM) de capacidad limitada.
* **Núcleo del Sistema Operativo:** Planificación a largo plazo (admisión por disponibilidad de memoria), planificación a corto plazo (despacho hacia CPU y desalojo preventivo), supervisión estricta de tiempos límites (*deadlines*) y gestión de colas de procesos.
* **Sincronización Distribuida:** Problema del productor-consumidor con buffer acotado mediante semáforos de conteo y cerrojos mutex propios, soportando acceso concurrente local y remoto sujeto a latencia de red.

---

## 🏗️ Arquitectura y Principios de Diseño

El diseño de ÁvilaOS sigue un modelo por capas desacoplado y estrictamente orientado a objetos:

```
+-------------------------------------------------------------------+
|               CAPA 4: INTERFAZ GRÁFICA / OBSERVADOR               |
|      (Vistas de monitoreo, configuración y métricas de clúster)   |
+-------------------------------------------------------------------+
                                  │ (Observa sin acoplar lógica)
+-------------------------------------------------------------------+
|               CAPA 3: SISTEMA OPERATIVO Y PLANIFICACIÓN           |
| (SO, Políticas FCFS/Round Robin/EDF/Prioridades, Semáforos, Colas)|
+-------------------------------------------------------------------+
                                  │ (Administra y despacha)
+-------------------------------------------------------------------+
|               CAPA 2: PROCESOS Y ESTRUCTURAS DE CONTROL           |
|         (PCB 1:1 con hilo único, registros PC/MAR, métricas)      |
+-------------------------------------------------------------------+
                                  │ (Opera sobre)
+-------------------------------------------------------------------+
|               CAPA 1: HARDWARE Y SINCRONIZACIÓN                   |
|        (Reloj Global, CPUs, Memoria RAM, Red de Comunicación)     |
+-------------------------------------------------------------------+
```

### Reglas Críticas de Diseño
1. **Estructuras de Datos Propias:** Conforme a la normativa del curso, está **terminantemente prohibido** el uso de colecciones nativas de Java (`java.util.ArrayList`, `Queue`, `Vector`, `Stack`, etc.). Todas las colas y listas se implementan sobre `ListaEnlazada<T>` y `Cola<T>` con punteros manuales.
2. **Concurrencia y Exclusión Mutua:** Las estructuras compartidas integran `java.util.concurrent.Semaphore` para garantizar seguridad entre hilos concurrentes.
3. **Uso de Enumeraciones (`enum`):**
   * `EstadoProceso`: `NUEVO`, `LISTO`, `EJECUCION`, `BLOQUEADO`, `TERMINADO`.
   * `ModoEjecucion`: `USUARIO`, `SISTEMA_OPERATIVO` (Kernel).
   * `MotivoBloqueo`: `SEMAFORO_MUTEX`, `SEMAFORO_LLENO`, `SEMAFORO_VACIO`, `LATENCIA_RED`, `ENTRADA_SALIDA`, `NINGUNO`.
   * `PoliticaPlanificacion`: `FCFS`, `ROUND_ROBIN`, `EDF`, `PRIORIDADES_APROPIATIVAS`.
   * `TipoProceso`: `CPU_BOUND`, `IO_BOUND`, `PRODUCTOR`, `CONSUMIDOR`.
   * `TipoSemaforo`: `BINARIO_MUTEX`, `CONTEO`.
4. **Interfaces Intercambiables:**
   * `IComponenteReloj`: Implementada por cualquier elemento de hardware que responda al pulso del reloj (`Computador`, `CPU`).
   * `IPoliticaPlanificacion`: Permite agregar o intercambiar algoritmos de planificación en caliente sin modificar el núcleo del SO.
5. **Reusabilidad del Nodo:** Cada computador del clúster es una instancia independiente de la misma clase `Computador`, garantizando escalabilidad a 2, 3, 4 o más máquinas sin duplicar código.

---

## 🚦 Estado Actual del Proyecto

| Módulo / Requerimiento | Estado | Detalle de Implementación |
| :--- | :---: | :--- |
| **1. Arquitectura Distribuida** | 🟢 **Listo (Backend)** | `ClusterDistribuido`, `Computador`, `CPU`, `MemoriaPrincipal`, `RelojGlobal`, interfaces `IComponenteReloj`. |
| **2. Modelo de Procesos & PCB** | 🟢 **Listo** | `PCB` completo (ID único global, registros PC/MAR, métricas, deadlines), ciclo de instrucción en `Proceso`. |
| **3. Planificación de CPU** | 🟢 **Listo** | Algoritmos **FCFS**, **Round Robin** (quantum configurable), **Prioridades Apropiativas** y **EDF**. Ordenamiento manual de colas y cambio en caliente. |
| **4. Sincronización (Prod-Cons)** | 🟡 **Pendiente** | Enums creados. Falta implementar la clase de Semáforo propio de ÁvilaOS, la clase `BufferAcotado`, la latencia de red y el ciclo paso a paso de producción/consumo. |
| **5. Memoria Principal (RAM)** | 🟢 **Listo (Backend)** | Control de memoria total/usada/libre, planificación a largo plazo por admisión de RAM. Falta descontar la memoria ocupada por los buffers. |
| **6. Interfaz Gráfica (GUI)** | 🔴 **Pendiente** | Requisito indispensable en NetBeans (Swing). Falta Vista 1 (configuración/creación) y Vista 2 (monitoreo del clúster, colas, PCB, buffers y log). |
| **7. Persistencia (JSON/CSV)** | 🔴 **Pendiente** | Guardado y carga de configuración inicial de simulación (computadores, RAM, procesos, buffers, latencia). |
| **8. Métricas y Gráficos** | 🟡 **Parcial** | Cálculos base de CPU y procesos listos. Falta métricas de equidad, tiempos de respuesta, productor-consumidor y gráfico en tiempo real con JFreeChart. |

---

## 📂 Estructura del Código Fuente

```text
src/
└── avilaos/
    ├── Main.java                          # Clase principal de pruebas y demostración de planificación
    ├── enums/                             # Enumeraciones obligatorias del sistema
    │   ├── EstadoProceso.java             # Estados: Nuevo, Listo, Ejecución, Bloqueado, Terminado
    │   ├── ModoEjecucion.java             # Modo Usuario / Sistema Operativo
    │   ├── MotivoBloqueo.java             # Justificación del bloqueo (semáforos, red, E/S)
    │   ├── PoliticaPlanificacion.java     # Políticas de scheduling disponibles
    │   ├── TipoProceso.java               # CPU-bound, I/O-bound, Productor, Consumidor
    │   └── TipoSemaforo.java              # Semáforos Mutex vs Conteo
    ├── estructuras/                       # Estructuras de datos propias (sin java.util.*)
    │   ├── Nodo.java                      # Nodo genérico para estructuras enlazadas
    │   ├── ListaEnlazada.java             # Lista simplemente enlazada con Semaphore(1) para mutex
    │   └── Cola.java                      # Cola FIFO thread-safe
    ├── gui/                               # Interfaz gráfica de usuario en Java Swing
    │   ├── PanelComputador.java           # Visualización de CPU, PC, prioridad, deadline y modo
    │   └── VentanaPrincipal.java          # Dashboard del clúster con controles y ciclo global
    ├── hardware/                          # Componentes de hardware simulados
    │   ├── CPU.java                       # Procesador monohilo, registros PC y MAR, modos de CPU
    │   ├── MemoriaPrincipal.java          # RAM configurable y asignación de bloques
    │   └── RelojGlobal.java               # Hilo del reloj global que sincroniza todo el clúster
    ├── interfaces/                        # Interfaces obligatorias para desacoplamiento
    │   ├── IComponenteReloj.java          # Contrato para responder al avance de cada ciclo
    │   └── IPoliticaPlanificacion.java    # Contrato para algoritmos de planificación
    ├── planificacion/                     # Implementación de los algoritmos de scheduling
    │   ├── FabricaPoliticas.java          # Factory para conmutación dinámica
    │   ├── PoliticaFCFS.java              # First-Come, First-Served
    │   ├── PoliticaRoundRobin.java        # Round Robin con quantum dinámico
    │   ├── PoliticaPrioridadesApropiativas.java # Desalojo por prioridad
    │   └── PoliticaEDF.java               # Earliest Deadline First con ordenamiento manual
    ├── proceso/                           # Representación y control de procesos
    │   ├── GeneradorIdProceso.java        # Generación secuencial de IDs globales únicos
    │   ├── PCB.java                       # Bloque de Control de Proceso (contexto completo)
    │   └── Proceso.java                   # Hilo único de ejecución, incremento de PC y deadline
    └── sistema/                           # Núcleo y distribución
        ├── ClusterDistribuido.java        # Coordinador del clúster de nodos
        ├── Computador.java                # Instancia reutilizable de nodo (CPU + RAM + SO)
        └── SistemaOperativo.java          # Núcleo del SO: largo/corto plazo, colas y deadlines
```

---

## ⚙️ Requisitos del Entorno

* **Java Development Kit (JDK):** Versión 21 o superior (Validado con JDK 22).
* **IDE Oficial:** Apache NetBeans (Requerimiento estricto de cátedra).
* **Herramienta de Construcción:** Ant (`build.xml` integrado para NetBeans).

---

## 🚀 Compilación y Ejecución

Por defecto, el programa inicia directamente con la **Interfaz Gráfica de Usuario (GUI)** en Swing. No obstante, dispone de un modo de consola para pruebas rápidas y depuración.

### 1. Desde Apache NetBeans

#### Modo por Defecto (Interfaz Gráfica Swing):
1. Abrir NetBeans y seleccionar **File > Open Project**.
2. Navegar hasta la carpeta `AvilaOS` y presionar **Open Project**.
3. Hacer clic derecho sobre el proyecto y seleccionar **Clean and Build**.
4. Presionar **Run Project** (`F6`) o el botón verde de Play (▶). Se abrirá la ventana gráfica del simulador.

#### Modo Consola (Sin Interfaz Gráfica):
Para ejecutar la simulación mostrando únicamente la salida detallada en la consola de NetBeans (*Output tab*):
1. En el panel de **Projects**, hacer clic derecho sobre el proyecto **AvilaOS** y seleccionar **Properties** (o presionar `Alt + F7`).
2. En el menú lateral izquierdo, hacer clic en la categoría **Run**.
3. En el campo **Arguments**, escribir:
   ```text
   --console
   ```
   *(También se admite `--cli`).*
4. Presionar **OK** para guardar los cambios.
5. Presionar **Run Project** (`F6`). La simulación se ejecutará de inmediato por la pestaña de salida (*Output*) de NetBeans, mostrando los estados de la CPU, PC, colas y resumen ciclo a ciclo sin abrir ventanas.
6. *(Para regresar al modo de Interfaz Gráfica, simplemente volver a **Properties > Run** y dejar vacío el campo **Arguments**).*

---

### 2. Desde la Línea de Comandos (PowerShell / Terminal)

**Compilar todo el código fuente:**
```powershell
javac -d build/classes (Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```

**Ejecutar con Interfaz Gráfica (por defecto):**
```powershell
java -cp build/classes avilaos.Main
```

**Ejecutar en Modo Consola (sin GUI):**
```powershell
java -cp build/classes avilaos.Main --console
```

---

## 🌿 Estándares de Git y Flujo de Trabajo

Para cumplir con la rúbrica de desarrollo colaborativo de la cátedra:
* **Rama Principal (`main`):** Código estable y probado para entregas.
* **Rama de Integración (`develop`):** Integración continua de funcionalidades.
* **Ramas por Funcionalidad (`feat/*`):** Ramas cortas para cada tarea (ej: `feat/buffer-semaforos`, `feat/gui-dashboard`, `feat/persistencia-json`).
* **Commits Semánticos:** Mensajes claros y descriptivos (`feat:`, `fix:`, `refactor:`, `docs:`).
* **Pull Requests & Issues:** Toda integración a `develop` o `main` debe respaldarse mediante un Pull Request con revisión y discusión de código, asociado a su respectivo Issue.
