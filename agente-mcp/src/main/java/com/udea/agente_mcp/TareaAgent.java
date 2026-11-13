package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Gemini;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class TareaAgent {

    private static final String MCP_URL = "http://localhost:8088/mcp";
    private static final String VARIABLE_CLAVE = "GOOGLE_API_KEY";
    private static final String VARIABLE_MODELO = "GEMINI_MODEL";
    // La guia usa gemini-2.5-flash, pero Google ya no lo ofrece a cuentas nuevas.
    // Se puede cambiar con GEMINI_MODEL en .env.
    private static final String MODELO_PREDETERMINADO = "gemini-3.5-flash";

    public static BaseAgent ROOT_AGENT = buildAgent();

    /** Usa la variable de entorno si existe; si no, el .env de la raiz del repositorio. */
    private static String leerConfiguracion(String nombre) {
        String valor = System.getenv(nombre);
        if (valor != null && !valor.isBlank()) {
            return valor.trim();
        }
        for (Path ruta : List.of(Path.of("../.env"), Path.of(".env"))) {
            if (!Files.isRegularFile(ruta)) {
                continue;
            }
            Properties valores = new Properties();
            try (Reader lector = Files.newBufferedReader(ruta)) {
                valores.load(lector);
            } catch (IOException e) {
                throw new UncheckedIOException("No fue posible leer " + ruta, e);
            }
            valor = valores.getProperty(nombre, "");
            if (!valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private static String leerClaveGemini() {
        String clave = leerConfiguracion(VARIABLE_CLAVE);
        if (clave != null) {
            return clave;
        }
        String mensaje = "Falta " + VARIABLE_CLAVE
                + ": copie .env.example como .env en la raiz del repositorio y complete la clave.";
        // ADK oculta los errores de inicializacion del agente, por eso se imprime aqui tambien.
        System.err.println(mensaje);
        throw new IllegalStateException(mensaje);
    }

    private static BaseAgent buildAgent() {
        String nombreModelo = leerConfiguracion(VARIABLE_MODELO);
        if (nombreModelo == null) {
            nombreModelo = MODELO_PREDETERMINADO;
        }
        System.out.println("Modelo Gemini: " + nombreModelo);
        Gemini modelo = new Gemini(nombreModelo, leerClaveGemini());

        StreamableHttpServerParameters params =
                StreamableHttpServerParameters.builder()
                        .url(MCP_URL)
                        .timeout(Duration.ofSeconds(30))
                        .build();

        McpToolset mcpToolset = new McpToolset(params);

        try {
            List<BaseTool> mcpTools = mcpToolset.getTools(null)
                    .toList()
                    .blockingGet();

            System.out.println("Herramientas MCP descubiertas: " + mcpTools.size());

            return LlmAgent.builder()
                    .name("TareaAgent")
                    .model(modelo)
                    .description("Agente para administrar tareas")
                    .instruction("Usa las herramientas MCP de tareas para consultar, crear, actualizar y "
                            + "administrar pendientes. Antes de eliminar, confirma el identificador con el usuario.")
                    .tools(mcpTools)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible conectar con el servidor MCP en " + MCP_URL, e);
        }
    }
}
