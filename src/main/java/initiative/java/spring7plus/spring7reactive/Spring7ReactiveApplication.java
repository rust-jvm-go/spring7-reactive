package initiative.java.spring7plus.spring7reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point.
 * @SpringBootApplication enables component scanning and autoconfiguration for this application.
 */
@SpringBootApplication
public class Spring7ReactiveApplication {

	public static void main(String[] args) {
		// Bootstraps the Spring ApplicationContext and starts the embedded reactive web server.
		SpringApplication.run(Spring7ReactiveApplication.class, args);
	}
}
