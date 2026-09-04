package cl.duoc.pedidos360.carrito;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CarritoApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CarritoApplication.class);

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/carrito_db}")
    private String dbUrl;

    public static void main(String[] args) {
        SpringApplication.run(CarritoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("[CARRITO-SERVICE] Connected to PostgreSQL | Target: {}", dbUrl);
    }
}
