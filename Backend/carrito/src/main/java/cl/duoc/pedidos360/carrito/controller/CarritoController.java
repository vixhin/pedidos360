package cl.duoc.pedidos360.carrito.controller;

import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.service.CarritoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<CarritoItem>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(carritoService.obtenerPorUsuario(usuarioId));
    }

    @PostMapping
    public ResponseEntity<CarritoItem> agregarItem(@RequestBody CarritoItem item) {
        return ResponseEntity.ok(carritoService.agregarOActualizar(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarritoItem> actualizarCantidad(
            @PathVariable Long id,
            @RequestParam(required = false) Integer cantidad,
            @RequestBody(required = false) CarritoItem body) {
        Integer qty = cantidad != null ? cantidad : (body != null ? body.getCantidad() : null);
        if (qty == null) {
            return ResponseEntity.badRequest().build();
        }
        CarritoItem actualizado = carritoService.actualizarCantidad(id, qty);
        if (actualizado == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarItem(@PathVariable Long id) {
        carritoService.eliminarItem(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuario/{usuarioId}")
    public ResponseEntity<Void> vaciarCarrito(@PathVariable Long usuarioId) {
        carritoService.vaciarCarrito(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
