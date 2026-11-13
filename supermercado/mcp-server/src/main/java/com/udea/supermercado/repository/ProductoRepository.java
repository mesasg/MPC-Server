package com.udea.supermercado.repository;

import com.udea.supermercado.model.Producto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("select distinct p.categoria from Producto p order by p.categoria")
    List<String> findCategorias();

    /** Un filtro vacío ('') no restringe los resultados. */
    @Query("""
            select p from Producto p
            where (:texto = '' or lower(p.nombre) like lower(concat('%', :texto, '%')))
              and (:categoria = '' or lower(p.categoria) = lower(:categoria))
            order by p.categoria, p.nombre
            """)
    List<Producto> buscar(@Param("texto") String texto, @Param("categoria") String categoria, Pageable pagina);

    /**
     * Descuenta en una sola sentencia: la base de datos serializa las compras concurrentes
     * y la condición stock >= cantidad impide vender más de lo disponible.
     *
     * @return 1 si se descontó, 0 si no había stock suficiente
     */
    @Modifying
    @Query("update Producto p set p.stock = p.stock - :cantidad where p.id = :id and p.stock >= :cantidad")
    int descontarStock(@Param("id") Long id, @Param("cantidad") int cantidad);

    @Modifying
    @Query("update Producto p set p.stock = p.stock + :cantidad where p.id = :id")
    int reponerStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
