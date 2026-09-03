package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.repository.AnaliticaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnaliticaService {

    private final AnaliticaRepository analiticaRepository;

    public AnaliticaService(AnaliticaRepository analiticaRepository) {
        this.analiticaRepository = analiticaRepository;
    }

    public List<AnaliticaEvento> obtenerTodos() {
        return analiticaRepository.findAll();
    }

    public List<AnaliticaEvento> obtenerPorTipo(String tipoEvento) {
        return analiticaRepository.findByTipoEvento(tipoEvento);
    }

    public AnaliticaEvento registrarEvento(AnaliticaEvento evento) {
        return analiticaRepository.save(evento);
    }
}
