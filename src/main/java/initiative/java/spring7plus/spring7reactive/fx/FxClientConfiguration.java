package initiative.java.spring7plus.spring7reactive.fx;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

/**
 * Centralized WebClient tuned for exchangerate.host calls.
 * <p>
 * We keep base URL, timeouts, codecs, and logging in one place so every FX call
 * shares the same non-blocking, resilient defaults.
 */
@Configuration
@EnableConfigurationProperties(FxProperties.class)
public class FxClientConfiguration {

    private static final String FX_BASE_URL = "https://api.exchangerate.host";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_IN_MEMORY_BYTES = 256 * 1024; // 256 KB

    @Bean
    public WebClient.Builder webClientBuilder() {
        // Provide a fallback builder in case auto-configuration is disabled.
        return WebClient.builder();
    }

    @Bean
    public WebClient exchangerateHostWebClient(WebClient.Builder builder) {
        // Reactor Netty client with a response timeout; prevents hung connections.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(READ_TIMEOUT);

        // Optional: cap in-memory buffer so huge payloads are rejected early.
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();

        return builder
                .baseUrl(FX_BASE_URL)
                .defaultHeader("Accept", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
