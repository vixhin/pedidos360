package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.entity.CoberturaComuna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoberturaComunaRepository extends JpaRepository<CoberturaComuna, Long> {
    List<CoberturaComuna> findByActivaTrue();
    Optional<CoberturaComuna> findByNombreIgnoreCaseAndActivaTrue(String nombre);
}

