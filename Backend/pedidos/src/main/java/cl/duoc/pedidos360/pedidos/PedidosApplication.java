package cl.duoc.pedidos360.pedidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PedidosApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PedidosApplication.class);

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/pedidos_db}")
    private String dbUrl;

    public static void main(String[] args) {
        SpringApplication.run(PedidosApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("[PEDIDOS-SERVICE] Connected to PostgreSQL | Target: {}", dbUrl);
    }
}
