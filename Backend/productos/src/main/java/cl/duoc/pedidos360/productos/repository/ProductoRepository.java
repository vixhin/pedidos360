package cl.duoc.pedidos360.productos.repository;

import cl.duoc.pedidos360.productos.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findByCategoriaIgnoreCase(String categoria);

    Optional<Producto> findBySku(String sku);

    @Query("SELECT p FROM Producto p WHERE " +
           "LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Producto> buscarPorTermino(@Param("term") String term);

    @Modifying
    @Query("UPDATE Producto p SET p.stock = p.stock - :cantidad WHERE p.id = :id AND p.stock >= :cantidad")
    int decrementarStockSiDisponible(@Param("id") Long id, @Param("cantidad") Integer cantidad);

    @Modifying
    @Query("UPDATE Producto p SET p.stock = p.stock + :cantidad WHERE p.id = :id")
    int incrementarStock(@Param("id") Long id, @Param("cantidad") Integer cantidad);
}
