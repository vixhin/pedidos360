package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.entity.RabbitBinding;
import cl.duoc.pedidos360.usuario.entity.RabbitExchange;
import cl.duoc.pedidos360.usuario.entity.RabbitQueue;
import cl.duoc.pedidos360.usuario.repository.RabbitBindingRepository;
import cl.duoc.pedidos360.usuario.repository.RabbitExchangeRepository;
import cl.duoc.pedidos360.usuario.repository.RabbitQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RabbitTopologyService {

    private static final Logger log = LoggerFactory.getLogger(RabbitTopologyService.class);

    private final RabbitExchangeRepository exchangeRepository;
    private final RabbitQueueRepository queueRepository;
    private final RabbitBindingRepository bindingRepository;
    private final RabbitAdminService adminService;

    public RabbitTopologyService(RabbitExchangeRepository exchangeRepository,
                                 RabbitQueueRepository queueRepository,
                                 RabbitBindingRepository bindingRepository,
                                 RabbitAdminService adminService) {
        this.exchangeRepository = exchangeRepository;
        this.queueRepository = queueRepository;
        this.bindingRepository = bindingRepository;
        this.adminService = adminService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[RABBIT-TOPOLOGY] ApplicationReadyEvent detectado. Sincronizando topología DB → RabbitMQ...");
        resyncTopology();
    }

    public synchronized void resyncTopology() {
        try {
            // 1. Declarar Exchanges activos desde DB
            List<RabbitExchange> activeExchanges = exchangeRepository.findByActiveTrue();
            log.info("[RABBIT-TOPOLOGY] Declarando {} exchanges activos desde PostgreSQL...", activeExchanges.size());
            for (RabbitExchange exchange : activeExchanges) {
                adminService.declareExchangeInRabbitMQ(exchange);
            }

            // 2. Declarar Queues activas desde DB
            List<RabbitQueue> activeQueues = queueRepository.findByActiveTrue();
            log.info("[RABBIT-TOPOLOGY] Declarando {} queues activas desde PostgreSQL...", activeQueues.size());
            for (RabbitQueue queue : activeQueues) {
                adminService.declareQueueInRabbitMQ(queue);
            }

            // 3. Declarar Bindings activos desde DB
            List<RabbitBinding> activeBindings = bindingRepository.findByActiveTrue();
            log.info("[RABBIT-TOPOLOGY] Declarando {} bindings activos desde PostgreSQL...", activeBindings.size());
            for (RabbitBinding binding : activeBindings) {
                adminService.declareBindingInRabbitMQ(binding);
            }

            log.info("[RABBIT-TOPOLOGY] Topología RabbitMQ sincronizada exitosamente desde PostgreSQL.");
        } catch (Exception ex) {
            log.error("[RABBIT-TOPOLOGY] Error durante la sincronización de topología RabbitMQ: {}. " +
                    "La aplicación continúa; la sincronización puede reintentarse con /api/admin/rabbit/sync.", ex.getMessage());
        }
    }
}
