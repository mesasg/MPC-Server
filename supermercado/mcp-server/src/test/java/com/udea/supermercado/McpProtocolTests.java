package com.udea.supermercado;

import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.PedidoRepository;
import com.udea.supermercado.repository.ProductoRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "server.address=127.0.0.1")
class McpProtocolTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private PedidoRepository pedidos;

    @BeforeEach
    void limpiar() {
        pedidos.deleteAll();
        productos.deleteAll();
    }

    @Test
    void publicaLasHerramientasYRegistraUnPedidoPorMcp() {
        Producto arroz = productos.save(
                new Producto("Arroz blanco", "Despensa", "bolsa 1 kg", new BigDecimal("4800.00"), 10));

        var transporte = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build();

        try (McpSyncClient cliente = McpClient.sync(transporte).build()) {
            assertThat(cliente.initialize().serverInfo().name()).isEqualTo("supermercado-mcp-server");

            List<String> nombres = cliente.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            assertThat(nombres).containsExactlyInAnyOrder("listarCategorias", "buscarProductos", "crearPedido",
                    "consultarPedido", "listarPedidosCliente", "cancelarPedido");

            McpSchema.CallToolResult creado = cliente.callTool(new McpSchema.CallToolRequest("crearPedido", Map.of(
                    "cliente", "Ana Pérez",
                    "telefono", "3001234567",
                    "direccion", "Calle 10 # 20-30",
                    "items", List.of(Map.of("productoId", arroz.getId(), "cantidad", 2)))));
            assertThat(creado.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(((McpSchema.TextContent) creado.content().get(0)).text()).contains("9600");

            McpSchema.CallToolResult sinStock = cliente.callTool(new McpSchema.CallToolRequest("crearPedido", Map.of(
                    "cliente", "Ana Pérez",
                    "telefono", "3001234567",
                    "direccion", "Calle 10 # 20-30",
                    "items", List.of(Map.of("productoId", arroz.getId(), "cantidad", 50)))));
            assertThat(sinStock.isError()).isTrue();
            assertThat(((McpSchema.TextContent) sinStock.content().get(0)).text()).contains("Stock insuficiente");
        }
    }
}
