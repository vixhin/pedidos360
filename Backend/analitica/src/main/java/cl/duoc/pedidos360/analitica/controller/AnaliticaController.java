package cl.duoc.pedidos360.analitica.controller;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    private final AnaliticaService analiticaService;

    public AnaliticaController(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @GetMapping
    public ResponseEntity<List<AnaliticaEvento>> listar() {
        return ResponseEntity.ok(analiticaService.obtenerTodos());
    }

    @GetMapping("/tipo/{tipoEvento}")
    public ResponseEntity<List<AnaliticaEvento>> obtenerPorTipo(@PathVariable String tipoEvento) {
        return ResponseEntity.ok(analiticaService.obtenerPorTipo(tipoEvento));
    }

    @PostMapping
    public ResponseEntity<AnaliticaEvento> registrar(@RequestBody AnaliticaEvento evento) {
        return ResponseEntity.ok(analiticaService.registrarEvento(evento));
    }
}
