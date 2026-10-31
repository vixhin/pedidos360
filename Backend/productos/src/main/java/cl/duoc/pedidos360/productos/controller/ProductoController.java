package cl.duoc.pedidos360.productos.controller;

import cl.duoc.pedidos360.productos.dto.ApiResponse;
import cl.duoc.pedidos360.productos.dto.FavoritoRequestDTO;
import cl.duoc.pedidos360.productos.dto.ProductoCreateDTO;
import cl.duoc.pedidos360.productos.dto.ProductoResponseDTO;
import cl.duoc.pedidos360.productos.entity.Promocion;
import cl.duoc.pedidos360.productos.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> listar() {
        List<ProductoResponseDTO> productos = productoService.obtenerTodos();
        return ResponseEntity.ok(ApiResponse.ok("Catálogo de productos obtenido correctamente", productos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoResponseDTO>> obtenerPorId(@PathVariable Long id) {
        ProductoResponseDTO producto = productoService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok("Producto encontrado", producto));
    }

    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> obtenerPorCategoria(@PathVariable String categoria) {
        List<ProductoResponseDTO> productos = productoService.obtenerPorCategoria(categoria);
        return ResponseEntity.ok(ApiResponse.ok("Productos de categoría '" + categoria + "' obtenidos correctamente", productos));
    }

    @GetMapping("/buscar")
    public ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> buscarPorNombre(@RequestParam String nombre) {
        List<ProductoResponseDTO> productos = productoService.buscarPorNombre(nombre);
        return ResponseEntity.ok(ApiResponse.ok("Resultados de búsqueda para '" + nombre + "'", productos));
    }

    @GetMapping("/promociones")
    public ResponseEntity<ApiResponse<List<Promocion>>> listarPromociones() {
        List<Promocion> promociones = productoService.obtenerPromociones();
        return ResponseEntity.ok(ApiResponse.ok("Promociones obtenidas exitosamente", promociones));
    }

    @GetMapping("/favoritos/{usuarioId}")
    public ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> listarFavoritos(@PathVariable Long usuarioId) {
        List<ProductoResponseDTO> favoritos = productoService.obtenerFavoritosDeUsuario(usuarioId);
        return ResponseEntity.ok(ApiResponse.ok("Favoritos del usuario obtenidos exitosamente", favoritos));
    }

    @PostMapping("/favoritos")
    public ResponseEntity<ApiResponse<Void>> agregarFavorito(@Valid @RequestBody FavoritoRequestDTO dto) {
        productoService.agregarFavorito(dto.getUsuarioId(), dto.getProductoId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Producto agregado a favoritos correctamente", null));
    }

    @DeleteMapping("/favoritos/{usuarioId}/{productoId}")
    public ResponseEntity<ApiResponse<Void>> eliminarFavorito(
            @PathVariable Long usuarioId,
            @PathVariable Long productoId) {
        productoService.eliminarFavorito(usuarioId, productoId);
        return ResponseEntity.ok(ApiResponse.ok("Producto eliminado de favoritos correctamente", null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductoResponseDTO>> crear(@Valid @RequestBody ProductoCreateDTO dto) {
        ProductoResponseDTO creado = productoService.crearProducto(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Producto creado correctamente", creado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoResponseDTO>> actualizar(@PathVariable Long id,
                                                                        @Valid @RequestBody ProductoCreateDTO dto) {
        ProductoResponseDTO actualizado = productoService.actualizarProducto(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Producto actualizado correctamente", actualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok("Producto eliminado correctamente", null));
    }
}
