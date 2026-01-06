package initiative.java.spring7plus.spring7reactive.fx;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Use-case façade that keeps FX-specific business rules (validation, caching, etc.)
 * in one place instead of scattering WebClient calls across controllers.
 */
@Service
public class FxService {

    private final FxClient fxClient;

    public FxService(FxClient fxClient) {
        this.fxClient = fxClient;
    }

    public Mono<FxClient.FxQuote> convert(String from, String to, BigDecimal amount) {
        return fxClient.convert(from, to, amount);
    }
}
