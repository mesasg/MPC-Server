package com.example.labmcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de punta a punta: un cliente MCP real habla con el servidor por Streamable HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpProtocolTests {

    @LocalServerPort
    private int port;

    private McpSyncClient cliente() {
        var transporte = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        return McpClient.sync(transporte).build();
    }

    @Test
    void publicaLasNueveHerramientasYPermiteInvocarlas() {
        try (McpSyncClient cliente = cliente()) {
            McpSchema.InitializeResult init = cliente.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("tarea-mcp-server");

            List<String> nombres = cliente.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            assertThat(nombres).containsExactlyInAnyOrder(
                    "listarTareas", "listarPendientes", "listarCompletadas", "contarTareas", "buscarTareas",
                    "crearTarea", "marcarCompletada", "actualizarTarea", "eliminarTarea");

            McpSchema.CallToolResult ok = cliente.callTool(new McpSchema.CallToolRequest(
                    "crearTarea", Map.of("titulo", "Desde MCP", "fecha", "2026-11-20")));
            assertThat(ok.isError()).isNotEqualTo(Boolean.TRUE);

            McpSchema.CallToolResult invalida = cliente.callTool(new McpSchema.CallToolRequest(
                    "crearTarea", Map.of("titulo", "Fecha mala", "fecha", "20/11/2026")));
            assertThat(invalida.isError()).isTrue();
        }
    }
}
