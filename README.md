# ÁvilaOS — Simulador de Sistema Operativo Distribuido

**Universidad Metropolitana**  
**Departamento de Gestión de Proyectos y Sistemas**  
**Asignatura:** Sistemas Operativos (Trimestre 2627-1)
**Integrantes:** Sergio Barrios, Carlos Moreno, Adrián Rondón
**Proyecto 1:** Diseño de un Sistema Operativo Distribuido — Procesos y sincronización con semáforos

---

## Descripción del Proyecto

**ÁvilaOS** es un simulador modular y concurrente que modela la operación y coordinación de un sistema operativo distribuido sobre un clúster de computadores homogéneos. 

El sistema recrea la interacción entre capas de hardware y software:
* **Hardware:** Reloj global sincronizado, procesadores (CPU monohilo con registros visibles `PC` y `MAR`), y memoria principal (RAM) de capacidad limitada.
* **Núcleo del Sistema Operativo:** Planificación a largo plazo (admisión por disponibilidad de memoria), planificación a corto plazo (despacho hacia CPU y desalojo preventivo), supervisión estricta de tiempos límites (*deadlines*) y gestión de colas de procesos.
* **Sincronización Distribuida:** Problema del productor-consumidor con buffer acotado mediante semáforos de conteo y cerrojos mutex propios, soportando acceso concurrente local y remoto sujeto a latencia de red.

---

## Arquitectura y Principios de Diseño

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
1. **Estructuras de Datos Propias:** Todas las colas y listas están implementadas de cero sobre `ListaEnlazada<T>` y `Cola<T>` con punteros manuales.
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

## Estado Actual del Proyecto

| Módulo / Requerimiento | Detalle de Implementación |
| :--- | :--- |
| **1. Arquitectura del sistema distribuido** | Clúster de al menos dos computadores de una misma clase, cada uno con CPU, RAM y SO; reloj global, hilos, semáforos de Java, enums e interfaces requeridas. |
| **2. Procesos y PCB** | Estados requeridos, motivos de bloqueo e identificación global única. El PCB incluye contexto PC/MAR, computador, tipo, prioridad, memoria, deadline, tiempo restante y métricas; al vencer el deadline, el proceso termina. |
| **3. Planificación** | FCFS, EDF, Round Robin con quantum configurable y prioridades apropiativas. La política y el quantum se pueden cambiar por computador durante la simulación; se implementa asignación manual o automática sin migración. |
| **4. Sincronización productor-consumidor** | Semáforos propios de ÁvilaOS (mutex y conteo), buffers acotados con varios productores/consumidores, bloqueo y reanudación, ubicación anfitriona en RAM y latencia configurable para accesos remotos. |
| **5. Memoria principal** | RAM configurable con admisión de procesos según disponibilidad, liberación al terminar y revisión de la cola de nuevos. La RAM del anfitrión también descuenta la reservada por cada buffer. |
| **6. Interfaz gráfica y configuración** | Vistas Swing de configuración inicial y monitoreo: CPU, modo, PCB, colas y motivos de bloqueo, RAM, políticas y quantum. Permite crear procesos y buffers, controlar la simulación y consultar el ciclo, los semáforos, los elementos de los buffers y los logs. |
| **7. Persistencia de escenarios** | **Parcial** | Hay exportación e importación CSV de parámetros globales y procesos básicos. Falta restaurar la configuración completa de computadores (cantidad, RAM, políticas y quantum) y guardar/cargar buffers (capacidad, anfitrión y memoria), además de todos los atributos específicos de procesos. |
| **8. Métricas y gráficos** | **Parcial** | Se muestran métricas globales de throughput, uso de CPU, respuesta promedio, cumplimiento de deadlines y equidad; también métricas por buffer y un gráfico de utilización de todas las CPU. Falta completar throughput, respuesta promedio y tasa de cumplimiento de deadlines para cada computador. |

---

## Estructura del Código Fuente

```text
src/
└── avilaos/
    ├── Main.java                          # Punto de entrada; inicia la GUI o el modo consola
    ├── enums/                             # Estados, modos, políticas y tipos del sistema
    │   ├── EstadoProceso.java             # Estados del ciclo de vida de un proceso
    │   ├── ModoEjecucion.java             # Modo usuario o sistema operativo
    │   ├── MotivoBloqueo.java             # Causas de bloqueo: semáforos, red y E/S
    │   ├── PoliticaPlanificacion.java     # Políticas de planificación disponibles
    │   ├── TipoProceso.java               # CPU-bound, I/O-bound, productor y consumidor
    │   └── TipoSemaforo.java              # Semáforos binarios mutex y de conteo
    ├── estructuras/                       # Estructuras enlazadas propias y colas
    │   ├── Nodo.java                      # Nodo genérico
    │   ├── ListaEnlazada.java             # Lista enlazada sincronizada
    │   └── Cola.java                      # Cola FIFO basada en nodos propios
    ├── gui/                               # Interfaz gráfica Swing y persistencia CSV
    │   ├── DialogoConfiguracionInicial.java # Configuración inicial del clúster y el reloj
    │   ├── DialogoCrearBuffer.java        # Creación de buffers y reserva de RAM
    │   ├── DialogoCrearProceso.java       # Creación y asignación de procesos
    │   ├── DialogoDetallePCB.java         # Consulta detallada de un PCB
    │   ├── DialogoGraficoCPU.java         # Ventana del gráfico de utilización de CPU
    │   ├── DialogoMetricas.java           # Métricas globales, por computador y buffer
    │   ├── GestorPersistencia.java        # Exportación e importación de escenarios CSV
    │   ├── GraficoUtilizacionCPU.java     # Gráfico de utilización de todas las CPU
    │   ├── PanelComputador.java           # Monitor de CPU, RAM, política, PCB y colas
    │   └── VentanaPrincipal.java          # Dashboard, controles, logs y ciclo global
    ├── hardware/                          # Componentes de hardware simulados
    │   ├── CPU.java                       # CPU, registros PC/MAR y utilización
    │   ├── MemoriaPrincipal.java          # RAM configurable y control de asignación
    │   └── RelojGlobal.java               # Reloj compartido que avanza el clúster
    ├── interfaces/                        # Contratos para componentes intercambiables
    │   ├── IComponenteReloj.java          # Componente que reacciona a cada ciclo
    │   └── IPoliticaPlanificacion.java    # Interfaz de las políticas de planificación
    ├── planificacion/                     # Algoritmos de planificación de CPU
    │   ├── FabricaPoliticas.java          # Creación de políticas a partir de su tipo
    │   ├── PoliticaEDF.java               # Earliest Deadline First
    │   ├── PoliticaFCFS.java              # First-Come, First-Served
    │   ├── PoliticaPrioridadesApropiativas.java # Prioridades con desalojo
    │   └── PoliticaRoundRobin.java        # Round Robin con quantum configurable
    ├── proceso/                           # Modelo de procesos y su contexto
    │   ├── GeneradorIdProceso.java        # Generación de identificadores únicos
    │   ├── PCB.java                       # Estado, contexto, recursos y métricas del proceso
    │   └── Proceso.java                   # Ciclo de ejecución y transiciones del proceso
    ├── sincronizacion/                    # Productor-consumidor y buffers acotados
    │   ├── BufferAcotado.java             # Buffer con semáforos mutex, espacios y elementos
    │   ├── ElementoBuffer.java            # Elemento producido y consumido
    │   ├── GestorSincronizacion.java      # Buffers, operaciones y latencia de red
    │   └── Semaforo.java                  # Semáforo propio con cola de espera
    └── sistema/                           # Clúster, computadores, SO y métricas
        ├── ClusterDistribuido.java        # Coordinador del clúster, reloj y asignación
        ├── Computador.java                # Nodo con CPU, RAM y sistema operativo
        ├── ConfiguracionInicial.java      # Parámetros iniciales y creación del clúster
        ├── MetricasEquidad.java           # Cálculo del índice de equidad de Jain
        ├── MetricasSincronizacion.java    # Métricas de producción, consumo y bloqueos
        └── SistemaOperativo.java          # Planificación, colas, memoria y deadlines
```

---

## Requisitos del Entorno

* **Java Development Kit (JDK):** Versión 21 o superior (Validado con JDK 22).
* **IDE Oficial:** Apache NetBeans (Requerimiento estricto de cátedra).
* **Herramienta de Construcción:** Ant (`build.xml` integrado para NetBeans).

---

## Compilación y Ejecución

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

## Estándares de Git y Flujo de Trabajo

Para cumplir con la rúbrica de desarrollo colaborativo de la cátedra:
* **Rama Principal (`main`):** Código estable y probado para entregas.
* **Rama de Integración (`develop`):** Integración continua de funcionalidades.
* **Ramas por Funcionalidad:** Ramas cortas para cada tarea (ej: `buffer-semaforos`, `interfaz`, `planificacion`, `feat\MetricasProductorConsumidor`).
* **Commits Semánticos:** Mensajes claros y descriptivos (`feat:`, `fix:`, `refactor:`, `docs:`).
* **Pull Requests & Issues:** Toda integración a `develop` o `main` debe respaldarse mediante un Pull Request con revisión y discusión de código, asociado a su respectivo Issue.
