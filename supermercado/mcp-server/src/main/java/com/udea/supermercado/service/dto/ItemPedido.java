package com.udea.supermercado.service.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ItemPedido(
        @JsonPropertyDescription("Id del producto, obtenido con buscarProductos") Long productoId,
        @JsonPropertyDescription("Cantidad de unidades a comprar, entre 1 y 50") Integer cantidad) {
}
