package com.udea.supermercado.service;

import com.udea.supermercado.model.DetallePedido;
import com.udea.supermercado.model.EstadoPedido;
import com.udea.supermercado.model.Pedido;
import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.PedidoRepository;
import com.udea.supermercado.repository.ProductoRepository;
import com.udea.supermercado.service.dto.ItemPedido;
import com.udea.supermercado.service.dto.PedidoDto;
import com.udea.supermercado.service.dto.ProductoDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Operaciones del cliente del supermercado publicadas como herramientas MCP.
 *
 * Toda regla de negocio (precios, stock, totales, propiedad del pedido) se valida aquí:
 * el modelo de lenguaje solo elige la herramienta y propone los argumentos.
 * El cambio de estado de un pedido (preparación, despacho) no se publica porque
 * corresponde al personal de la tienda y no al cliente del chat.
 */
@Component
public class SupermercadoToolsService {

    private static final int MAX_RESULTADOS = 20;
    private static final int MAX_CANTIDAD = 50;

    private final ProductoRepository productos;
    private final PedidoRepository pedidos;

    public SupermercadoToolsService(ProductoRepository productos, PedidoRepository pedidos) {
        this.productos = productos;
        this.pedidos = pedidos;
    }

    // ---------- CATÁLOGO ----------

    @Tool(description = "Devuelve las categorías de productos disponibles en el supermercado.")
    public List<String> listarCategorias() {
        return productos.findCategorias();
    }

    @Tool(description = "Busca productos del catálogo por parte del nombre, por categoría o por ambos. "
            + "Devuelve id, precio en pesos colombianos, unidad de venta y stock disponible, con un máximo de 20 resultados.")
    public List<ProductoDto> buscarProductos(
            @ToolParam(description = "Parte del nombre del producto, por ejemplo arroz; opcional", required = false) String texto,
            @ToolParam(description = "Categoría exacta, por ejemplo Lácteos; opcional", required = false) String categoria) {
        return productos.buscar(limpiar(texto), limpiar(categoria), PageRequest.of(0, MAX_RESULTADOS))
                .stream().map(ProductoDto::de).toList();
    }

    // ---------- PEDIDOS ----------

    @Tool(description = "Registra un pedido a domicilio, descuenta el inventario y calcula el total. "
            + "Invócala una sola vez y únicamente después de que el cliente confirme productos, cantidades, "
            + "total estimado, nombre, teléfono y dirección de entrega.")
    @Transactional
    public PedidoDto crearPedido(
            @ToolParam(description = "Nombre completo de quien recibe el pedido") String cliente,
            @ToolParam(description = "Teléfono de contacto, de 7 a 15 dígitos") String telefono,
            @ToolParam(description = "Dirección completa de entrega") String direccion,
            @ToolParam(description = "Productos del pedido con su cantidad") List<ItemPedido> items) {
        Pedido pedido = new Pedido(
                obligatorio(cliente, "El nombre del cliente", 120),
                normalizarTelefono(telefono),
                obligatorio(direccion, "La dirección de entrega", 255));

        // Se procesa en orden de id para que compras concurrentes no se bloqueen mutuamente.
        consolidar(items).forEach((productoId, cantidad) -> {
            Producto producto = productos.findById(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("No existe el producto " + productoId));
            if (productos.descontarStock(productoId, cantidad) == 0) {
                throw new IllegalArgumentException("Stock insuficiente de " + producto.getNombre()
                        + ": se pidieron " + cantidad + " y hay " + producto.getStock());
            }
            pedido.agregarDetalle(producto, cantidad);
        });

        return PedidoDto.de(pedidos.save(pedido));
    }

    @Tool(description = "Consulta el detalle y el estado de un pedido. Requiere el teléfono con el que se registró.")
    @Transactional(readOnly = true)
    public PedidoDto consultarPedido(
            @ToolParam(description = "Número del pedido") Long pedidoId,
            @ToolParam(description = "Teléfono con el que se registró el pedido") String telefono) {
        return PedidoDto.de(buscarPedido(pedidoId, telefono));
    }

    @Tool(description = "Lista los 10 pedidos más recientes asociados a un teléfono.")
    @Transactional(readOnly = true)
    public List<PedidoDto> listarPedidosCliente(
            @ToolParam(description = "Teléfono con el que se registraron los pedidos") String telefono) {
        return pedidos.findTop10ByTelefonoOrderByCreadoEnDesc(normalizarTelefono(telefono))
                .stream().map(PedidoDto::de).toList();
    }

    @Tool(description = "Cancela un pedido que aún está en estado RECIBIDO y devuelve sus productos al inventario. "
            + "Invócala únicamente después de que el cliente confirme la cancelación.")
    @Transactional
    public PedidoDto cancelarPedido(
            @ToolParam(description = "Número del pedido") Long pedidoId,
            @ToolParam(description = "Teléfono con el que se registró el pedido") String telefono) {
        Pedido pedido = buscarPedido(pedidoId, telefono);
        if (pedido.getEstado() != EstadoPedido.RECIBIDO) {
            throw new IllegalArgumentException("El pedido " + pedidoId + " está en estado " + pedido.getEstado()
                    + " y ya no se puede cancelar");
        }
        for (DetallePedido detalle : pedido.getDetalles()) {
            productos.reponerStock(detalle.getProducto().getId(), detalle.getCantidad());
        }
        pedido.cancelar();
        return PedidoDto.de(pedidos.saveAndFlush(pedido));
    }

    // ---------- VALIDACIÓN ----------

    /** Un teléfono equivocado responde igual que un número inexistente para no revelar qué pedidos existen. */
    private Pedido buscarPedido(Long pedidoId, String telefono) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("El número de pedido es obligatorio");
        }
        return pedidos.findByIdAndTelefono(pedidoId, normalizarTelefono(telefono))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el pedido " + pedidoId + " con ese teléfono"));
    }

    /** Valida cada línea y suma las que repiten producto. */
    private static Map<Long, Integer> consolidar(List<ItemPedido> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        Map<Long, Integer> cantidades = new TreeMap<>();
        for (ItemPedido item : items) {
            if (item == null || item.productoId() == null || item.cantidad() == null) {
                throw new IllegalArgumentException("Cada producto necesita productoId y cantidad");
            }
            if (item.cantidad() < 1 || item.cantidad() > MAX_CANTIDAD) {
                throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + MAX_CANTIDAD);
            }
            if (cantidades.merge(item.productoId(), item.cantidad(), Integer::sum) > MAX_CANTIDAD) {
                throw new IllegalArgumentException("No se pueden pedir más de " + MAX_CANTIDAD
                        + " unidades del producto " + item.productoId());
            }
        }
        return cantidades;
    }

    private static String obligatorio(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        String limpio = valor.trim();
        if (limpio.length() > longitudMaxima) {
            throw new IllegalArgumentException(campo + " admite máximo " + longitudMaxima + " caracteres");
        }
        return limpio;
    }

    private static String normalizarTelefono(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("[\\s()+-]", "");
        if (!digitos.matches("\\d{7,15}")) {
            throw new IllegalArgumentException("El teléfono debe tener entre 7 y 15 dígitos");
        }
        return digitos;
    }

    private static String limpiar(String filtro) {
        return filtro == null ? "" : filtro.trim();
    }
}
