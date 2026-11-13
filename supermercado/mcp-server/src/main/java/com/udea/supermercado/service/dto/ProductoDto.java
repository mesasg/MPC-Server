package com.udea.supermercado.service.dto;

import com.udea.supermercado.model.Producto;

import java.math.BigDecimal;

public record ProductoDto(Long id, String nombre, String categoria, String unidad, BigDecimal precio, int stock) {

    public static ProductoDto de(Producto p) {
        return new ProductoDto(p.getId(), p.getNombre(), p.getCategoria(), p.getUnidad(), p.getPrecio(), p.getStock());
    }
}
