package initiative.java.spring7plus.spring7reactive.fx;

import java.math.BigDecimal;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import reactor.core.publisher.Mono;

/**
 * REST facade exposing the minimal FX conversion endpoint required for Checkpoint 5.
 * <p>
 * Clients can request {@code /api/fx/convert?from=EUR&to=USD&amount=123.45} and receive
 * a JSON body describing the rate, converted amount, and metadata returned by the FX provider.
 */
@RestController
@RequestMapping(path = "/api/fx", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class FxController {

    private final FxService fxService;

    public FxController(FxService fxService) {
        this.fxService = fxService;
    }

    @GetMapping("/convert")
    public Mono<FxClient.FxQuote> convert(
            @RequestParam @Pattern(regexp = "[A-Z]{3}") String from,
            @RequestParam @Pattern(regexp = "[A-Z]{3}") String to,
            @RequestParam @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount) {
        return fxService.convert(from, to, amount);
    }
}
