package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.entity.ConfiguracionComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionComercialRepository extends JpaRepository<ConfiguracionComercial, Long> {
    Optional<ConfiguracionComercial> findByClave(String clave);
    Optional<ConfiguracionComercial> findByClaveIgnoreCase(String clave);
}
