package com.udea.supermercado.service.dto;

import com.udea.supermercado.model.DetallePedido;
import com.udea.supermercado.model.EstadoPedido;
import com.udea.supermercado.model.Pedido;

import java.math.BigDecimal;
import java.util.List;

/** Vista del pedido que recibe el cliente MCP; evita serializar entidades JPA. */
public record PedidoDto(
        Long id,
        String cliente,
        String direccion,
        EstadoPedido estado,
        String creadoEn,
        BigDecimal total,
        List<Linea> lineas) {

    public record Linea(String producto, String unidad, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {

        static Linea de(DetallePedido d) {
            return new Linea(d.getProducto().getNombre(), d.getProducto().getUnidad(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getSubtotal());
        }
    }

    public static PedidoDto de(Pedido p) {
        return new PedidoDto(p.getId(), p.getClienteNombre(), p.getDireccion(), p.getEstado(),
                p.getCreadoEn().toString(), p.getTotal(), p.getDetalles().stream().map(Linea::de).toList());
    }
}
