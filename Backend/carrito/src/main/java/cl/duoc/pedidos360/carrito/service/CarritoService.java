package cl.duoc.pedidos360.carrito.service;

import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.repository.CarritoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;

    public CarritoService(CarritoRepository carritoRepository) {
        this.carritoRepository = carritoRepository;
    }

    public List<CarritoItem> obtenerPorUsuario(Long usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId);
    }

    @Transactional
    public CarritoItem agregarOActualizar(CarritoItem item) {
        if (item.getUsuarioId() != null && item.getProductoId() != null) {
            Optional<CarritoItem> existing = carritoRepository.findByUsuarioIdAndProductoId(
                    item.getUsuarioId(), item.getProductoId());
            if (existing.isPresent()) {
                CarritoItem current = existing.get();
                if (item.getCantidad() != null) {
                    current.setCantidad(item.getCantidad());
                }
                if (item.getPrecioUnitario() != null) {
                    current.setPrecioUnitario(item.getPrecioUnitario());
                }
                current.setUltimaActividad(LocalDateTime.now());
                return carritoRepository.save(current);
            }
        }
        item.setUltimaActividad(LocalDateTime.now());
        return carritoRepository.save(item);
    }

    @Transactional
    public CarritoItem actualizarCantidad(Long id, Integer nuevaCantidad) {
        CarritoItem item = carritoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item de carrito no encontrado con ID: " + id));
        if (nuevaCantidad <= 0) {
            carritoRepository.deleteById(id);
            return null;
        }
        item.setCantidad(nuevaCantidad);
        item.setUltimaActividad(LocalDateTime.now());
        return carritoRepository.save(item);
    }

    public void eliminarItem(Long id) {
        carritoRepository.deleteById(id);
    }

    @Transactional
    public void vaciarCarrito(Long usuarioId) {
        carritoRepository.deleteByUsuarioId(usuarioId);
    }
}
