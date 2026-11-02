package cl.duoc.pedidos360.productos.repository;

import cl.duoc.pedidos360.productos.entity.StockReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReservaRepository extends JpaRepository<StockReserva, Long> {

    Optional<StockReserva> findByReservaId(String reservaId);
}

