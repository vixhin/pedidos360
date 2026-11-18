package cl.duoc.pedidos360.carrito.controller;

import cl.duoc.pedidos360.carrito.dto.CantidadRequestDTO;
import cl.duoc.pedidos360.carrito.dto.CarritoItemRequestDTO;
import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.service.CarritoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<CarritoItem>> obtenerPorUsuario(@PathVariable Long usuarioId, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            validarPropietarioOAdmin(request, usuarioId);
        }
        return ResponseEntity.ok(carritoService.obtenerPorUsuario(usuarioId));
    }

    public ResponseEntity<List<CarritoItem>> obtenerPorUsuario(Long usuarioId) {
        return obtenerPorUsuario(usuarioId, null);
    }

    @PostMapping
    public ResponseEntity<CarritoItem> agregarItem(@RequestBody CarritoItemRequestDTO dto, @Autowired(required = false) HttpServletRequest request) {
        CarritoItem item = dto.toEntity();
        if (request != null) {
            Long callerId = (Long) request.getAttribute("p360.userId");
            String role = (String) request.getAttribute("p360.role");
            if (callerId != null && (role == null || !role.contains("ADMIN"))) {
                item.setUsuarioId(callerId);
            }
        }
        return ResponseEntity.ok(carritoService.agregarOActualizar(item));
    }

    public ResponseEntity<CarritoItem> agregarItem(CarritoItemRequestDTO dto) {
        return agregarItem(dto, null);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarritoItem> actualizarCantidad(
            @PathVariable Long id,
            @RequestParam(required = false) Integer cantidad,
            @RequestBody(required = false) CantidadRequestDTO body,
            @Autowired(required = false) HttpServletRequest request) {
        Integer qty = cantidad != null ? cantidad : (body != null ? body.cantidad() : null);
        if (qty == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request != null) {
            validarDuenoDelItem(request, id);
        }
        CarritoItem actualizado = carritoService.actualizarCantidad(id, qty);
        if (actualizado == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarItem(@PathVariable Long id, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            validarDuenoDelItem(request, id);
        }
        carritoService.eliminarItem(id);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<Void> eliminarItem(Long id) {
        return eliminarItem(id, null);
    }

    /** Evita IDOR: sólo el dueño del ítem (o ADMIN/VENDEDOR) puede modificarlo o eliminarlo. */
    private void validarDuenoDelItem(HttpServletRequest request, Long itemId) {
        CarritoItem item = carritoService.obtenerPorId(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ítem de carrito no encontrado."));
        validarPropietarioOAdmin(request, item.getUsuarioId());
    }

    @DeleteMapping("/usuario/{usuarioId}")
    public ResponseEntity<Void> vaciarCarrito(@PathVariable Long usuarioId, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            validarPropietarioOAdmin(request, usuarioId);
        }
        carritoService.vaciarCarrito(usuarioId);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<Void> vaciarCarrito(Long usuarioId) {
        return vaciarCarrito(usuarioId, null);
    }

    private void validarPropietarioOAdmin(HttpServletRequest request, Long targetUsuarioId) {
        Long callerId = (Long) request.getAttribute("p360.userId");
        String role = (String) request.getAttribute("p360.role");

        if (role != null && (role.contains("ADMIN") || role.contains("VENDEDOR"))) {
            return;
        }

        if (callerId == null || !callerId.equals(targetUsuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para consultar o modificar el carrito de otro usuario.");
        }
    }
}
