package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.entity.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Long> {
    Optional<Cupon> findByCodigoIgnoreCase(String codigo);
    Optional<Cupon> findByCodigoIgnoreCaseAndActivoTrue(String codigo);
    List<Cupon> findByActivoTrue();

    @Modifying
    @Query("UPDATE Cupon c SET c.usosActuales = c.usosActuales + 1 WHERE c.id = :id AND (c.usosMaximos IS NULL OR c.usosActuales < c.usosMaximos)")
    int incrementarUsoAtomico(@Param("id") Long id);
}
