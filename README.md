# Laboratorio MCP: Spring Boot, Spring AI, Claude Desktop y Google ADK

| Carpeta | Contenido | Puerto |
|---|---|---|
| `/` (raíz) | Servidor MCP de tareas con Spring Boot, Spring AI y MySQL (partes A a D de la guía) | 8088 |
| `agente-mcp/` | Agente Google ADK que consume el servidor de tareas (parte E) | 8080 |
| `supermercado/mcp-server/` | Ejercicio propuesto: servidor MCP de pedidos a domicilio | 8090 |
| `supermercado/chatbot/` | Ejercicio propuesto: chatbot web, cliente del servidor anterior | 8081 |
| `docs/actividades.md` | Respuestas a las actividades 10 a 16 | |
| `supermercado/README.md` | Explicación paso a paso del ejercicio propuesto | |

## Requisitos

JDK 21, MySQL 8, Node.js (para MCP Inspector y `mcp-remote`) y una clave de Gemini.

Maven usa `JAVA_HOME`, que debe apuntar al JDK 21. En cada ventana de PowerShell donde se ejecute un proyecto:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

## Configuración de claves

Después de clonar, cree el archivo `.env` en la raíz del repositorio a partir de la plantilla:

```powershell
Copy-Item .env.example .env
```

Complete `PWD_UDEA` (contraseña de `root` en MySQL) y `GOOGLE_API_KEY` (clave de Gemini). Los cuatro proyectos leen ese único archivo, que git ignora. Si una variable también existe en el entorno del sistema, tiene prioridad sobre `.env`.

## Parte A: base de datos

Si `mysql` no está en el PATH, use la ruta completa del cliente:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "source C:/ruta/al/proyecto/src/main/resources/schema.sql"
```

El script de tareas inserta sus datos de ejemplo cada vez que se ejecuta, así que debe correrse una sola vez.

## Parte B: servidor de tareas

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Las pruebas usan H2 en memoria, por lo que no necesitan MySQL. El arranque correcto registra `Registered tools: 9`: las 7 herramientas de la guía más `contarTareas` y `listarCompletadas` de las actividades 10 y 11.

## Parte C: probar el protocolo

```powershell
curl.exe -X POST http://localhost:8088/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"curl\",\"version\":\"1\"}}}'
npx @modelcontextprotocol/inspector@0.12.0
```

En Inspector seleccione Streamable HTTP, use `http://127.0.0.1:8088/mcp` y conecte.

## Parte D: Claude Desktop

En Claude Desktop abra Desarrollador > Abrir archivo de configuración y agregue:

```json
{
  "mcpServers": {
    "tareas-mcp": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8088/mcp"]
    }
  }
}
```

Si el archivo ya tiene otros servidores, agregue solo la entrada `tareas-mcp`. Cierre Claude Desktop desde la bandeja del sistema, ábralo de nuevo y pida: "Lista mis tareas pendientes".

## Parte E: agente Google ADK

Con el servidor de tareas en ejecución, en otra ventana:

```powershell
cd agente-mcp
.\mvnw.cmd clean install
.\mvnw.cmd exec:java@runAdkWebServer
```

La consola debe mostrar `Herramientas MCP descubiertas: 9`. La interfaz queda en http://localhost:8080.

## Diferencias entre la guía y este proyecto

| Tema | Guía | Este proyecto | Motivo |
|---|---|---|---|
| Puerto MCP | Menciona 8088 y 8888 | 8088 | Es el valor configurado en el proyecto base y en la mayoría de los pasos de la guía |
| Paquete y base de datos | `com.udea.tarea_mcp`, `tareas_mcp` | `com.example.labmcp`, `labmcp` | Se conservan los nombres del proyecto base |
| Nombres de herramientas | `listarTareas`, `marcarCompletada`, etc. | Iguales a la guía | El proyecto base usaba `listTareas`, `markComplete`, etc. y se alineó con las pruebas de la sección 8.3 |
| POM del agente | Sin clase principal | `AdkWebServer` como `mainClass` | Sin ella, `clean install` falla con "Unable to find main class" |
| Modelo Gemini | `gemini-2.5-flash` | `gemini-3.5-flash`, configurable con `GEMINI_MODEL` en `.env` | Google responde 404 "no longer available to new users" para `gemini-2.5-flash` |

Las claves nunca se escriben en archivos versionados: se leen del archivo local `.env` o de variables de entorno.
