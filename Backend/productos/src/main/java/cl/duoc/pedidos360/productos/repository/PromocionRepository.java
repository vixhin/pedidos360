package cl.duoc.pedidos360.productos.repository;

import cl.duoc.pedidos360.productos.entity.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromocionRepository extends JpaRepository<Promocion, String> {
    List<Promocion> findByActivaTrue();
}

