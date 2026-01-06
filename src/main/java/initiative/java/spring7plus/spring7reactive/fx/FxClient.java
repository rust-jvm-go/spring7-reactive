package initiative.java.spring7plus.spring7reactive.fx;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Thin client around exchangerate.host /convert endpoint.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Build the outbound HTTP call using the shared WebClient bean.</li>
 *     <li>Apply Reactor resilience operators (timeout + retry) so transient hiccups do not bubble up immediately.</li>
 *     <li>Map the JSON payload into a small record that the rest of the app can consume.</li>
 * </ul>
 */
@Component
public class FxClient {

    private static final Logger log = LoggerFactory.getLogger(FxClient.class);
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(2);
    private static final Retry RETRY_POLICY = Retry.backoff(2, Duration.ofMillis(200))
            .filter(ex -> !(ex instanceof FxClientException));

    private final WebClient exchangerateHostWebClient;
    private final String accessKey;

    public FxClient(WebClient exchangerateHostWebClient, FxProperties fxProperties) {
        this.exchangerateHostWebClient = exchangerateHostWebClient;
        this.accessKey = fxProperties.accessKey();
    }

    /**
     * Converts the given amount from source currency to target currency using exchangerate.host.
     *
     * @param from   ISO currency code (e.g. "USD")
     * @param to     ISO currency code (e.g. "PHP")
     * @param amount Amount to convert
     * @return Mono emitting the FX quote (rate and converted result)
     */
    public Mono<FxQuote> convert(String from, String to, BigDecimal amount) {
        return exchangerateHostWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/convert")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("amount", amount)
                        .queryParam("access_key", requireAccessKey())
                        .build())
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(ErrorInfo.class)
                                .defaultIfEmpty(new ErrorInfo("HTTP_" + response.statusCode(), "http_error", "No body"))
                                .flatMap(body -> Mono.error(new FxClientException("Remote error: " + body))))
                .bodyToMono(ConvertResponse.class)
                .timeout(CLIENT_TIMEOUT)
                .retryWhen(RETRY_POLICY)
                .flatMap(resp -> {
                    if (!resp.success()) {
                        return Mono.error(new FxClientException("Conversion failed: " + resp.error()));
                    }

                    QueryInfo query = resp.query();
                    BigDecimal rate = resp.info() != null ? resp.info().get("rate") : null;
                    BigDecimal converted = resp.result();
                    BigDecimal effectiveRate = rate;

                    if (effectiveRate == null) {
                        if (converted != null && query.amount() != null && query.amount().compareTo(BigDecimal.ZERO) != 0) {
                            effectiveRate = converted.divide(query.amount(), 8, RoundingMode.HALF_UP);
                            log.warn("exchangerate.host omitted info.rate for {} -> {}; derived {} instead",
                                    query.from(), query.to(), effectiveRate);
                        } else {
                            log.warn("exchangerate.host omitted info.rate and derivation failed (converted={}, amount={})",
                                    converted, query.amount());
                        }
                    }

                    return Mono.just(new FxQuote(
                            query.from(),
                            query.to(),
                            query.amount(),
                            effectiveRate,
                            converted,
                            resp.date() != null ? resp.date().atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : Instant.now()));
                });
    }

    /**
     * Domain-facing FX quote result.
     *
     * @param rate       Unit rate (target per 1 unit source)
     * @param converted  Converted amount for the requested source amount
     * @param fetchedAt  Timestamp when the quote was fetched
     */
    public record FxQuote(String fromCurrency,
                          String toCurrency,
                          BigDecimal requestedAmount,
                          BigDecimal rate,
                          BigDecimal converted,
                          Instant fetchedAt) {
    }

    /**
     * Shape of exchangerate.host /convert JSON payload.
     */
    private record ConvertResponse(boolean success,
                                   QueryInfo query,
                                   BigDecimal result,
                                   Map<String, BigDecimal> info,
                                   java.time.LocalDate date,
                                   ErrorInfo error) {
    }

    private record QueryInfo(String from, String to, BigDecimal amount) {
    }

    private record ErrorInfo(String code, String type, String info) {
    }

    public static class FxClientException extends RuntimeException {
        public FxClientException(String message) {
            super(message);
        }
    }

    private String requireAccessKey() {
        if (accessKey == null || accessKey.isBlank()) {
            throw new FxClientException("FX access key missing. Configure app.fx.access-key in application.yaml or environment.");
        }
        return accessKey;
    }
}
