package com.udea.supermercado.repository;

import com.udea.supermercado.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByIdAndTelefono(Long id, String telefono);

    List<Pedido> findTop10ByTelefonoOrderByCreadoEnDesc(String telefono);
}
