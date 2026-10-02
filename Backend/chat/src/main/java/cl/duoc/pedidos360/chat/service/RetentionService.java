package cl.duoc.pedidos360.chat.service;

import cl.duoc.pedidos360.chat.entity.Conversacion;
import cl.duoc.pedidos360.chat.repository.ConversacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final ConversacionRepository conversacionRepository;
    private final int retentionDays;

    public RetentionService(ConversacionRepository conversacionRepository,
                            @Value("${chat.retention-days:30}") int retentionDays) {
        this.conversacionRepository = conversacionRepository;
        this.retentionDays = retentionDays;
    }

    /**
     * Tarea programada diaria para depurar conversaciones cerradas más antiguas que CHAT_RETENTION_DAYS.
     * Se ejecuta todos los días a las 03:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void aplicarPoliticaRetencion() {
        log.info("[RETENTION-SERVICE] Executing scheduled chat retention task (Days={})...", retentionDays);
        LocalDateTime limitDate = LocalDateTime.now().minusDays(retentionDays);

        List<Conversacion> vencidas = conversacionRepository.findByEstadoAndFechaCierreBefore("CERRADO", limitDate);
        if (!vencidas.isEmpty()) {
            for (Conversacion conv : vencidas) {
                log.info("[RETENTION-SERVICE] Removing expired chat conversation ID={} (Closed on {})",
                        conv.getId(), conv.getFechaCierre());
                conversacionRepository.delete(conv);
            }
            log.info("[RETENTION-SERVICE] Successfully purged {} expired chat conversations.", vencidas.size());
        } else {
            log.info("[RETENTION-SERVICE] No expired chat conversations found for purging.");
        }
    }
}
