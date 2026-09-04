package cl.duoc.pedidos360.productos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProductosApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductosApplication.class);

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/productos_db}")
    private String dbUrl;

    public static void main(String[] args) {
        SpringApplication.run(ProductosApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("[PRODUCTOS-SERVICE] Connected to PostgreSQL | Target: {}", dbUrl);
    }
}
