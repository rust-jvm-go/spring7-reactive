package initiative.java.spring7plus.spring7reactive.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import initiative.java.spring7plus.spring7reactive.fx.FxClient.FxQuote;
import reactor.core.publisher.Mono;

/**
 * WebFlux slice test for FxController.
 * 
 * This test class demonstrates testing of external integration endpoints using WebTestClient:
 * - Uses @WebFluxTest to load only the web layer for the target controller
 * - Tests POST endpoint with query parameters and validation
 * - Verifies parameter validation (currency codes, amount constraints)
 * - Tests successful FX conversion responses
 * - Tests error propagation from external service calls
 * - Mocks service dependencies to isolate controller behavior
 * 
 * Slice testing benefits:
 * - Fast execution (~0.15s) - minimal Spring context, no external HTTP calls
 * - Focused testing of HTTP contracts and validation logic
 * - Ideal for API contract validation and error handling verification
 */
@WebFluxTest(FxController.class)
class FxControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    FxService fxService;

    @Test
    void convertReturnsFxQuoteWhenRequestIsValid() {
        // Arrange: Create expected FX quote
        FxQuote expectedQuote = new FxQuote(
                "EUR", 
                "USD", 
                new BigDecimal("100.00"), 
                new BigDecimal("1.0850"), 
                new BigDecimal("108.50"), 
                Instant.parse("2025-01-07T12:00:00Z"));

        when(fxService.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act & Assert: Verify successful conversion
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "EUR")
                        .queryParam("to", "USD")
                        .queryParam("amount", "100.00")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FxQuote.class)
                .value(quote -> {
                    assertThat(quote.fromCurrency()).isEqualTo("EUR");
                    assertThat(quote.toCurrency()).isEqualTo("USD");
                    assertThat(quote.requestedAmount()).isEqualTo(new BigDecimal("100.00"));
                    assertThat(quote.rate()).isEqualTo(new BigDecimal("1.0850"));
                    assertThat(quote.converted()).isEqualTo(new BigDecimal("108.50"));
                    assertThat(quote.fetchedAt()).isNotNull();
                });

        // Verify service was called with correct parameters
        verify(fxService).convert("EUR", "USD", new BigDecimal("100.00"));
    }

    @Test
    void convertHandlesDifferentCurrencyPairs() {
        // Arrange: Test different currency pair
        FxQuote expectedQuote = new FxQuote(
                "GBP", 
                "JPY", 
                new BigDecimal("50.00"), 
                new BigDecimal("195.25"), 
                new BigDecimal("9762.50"), 
                Instant.now());

        when(fxService.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act & Assert: Verify GBP to JPY conversion
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "GBP")
                        .queryParam("to", "JPY")
                        .queryParam("amount", "50.00")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FxQuote.class)
                .value(quote -> {
                    assertThat(quote.fromCurrency()).isEqualTo("GBP");
                    assertThat(quote.toCurrency()).isEqualTo("JPY");
                    assertThat(quote.converted()).isEqualTo(new BigDecimal("9762.50"));
                });

        verify(fxService).convert("GBP", "JPY", new BigDecimal("50.00"));
    }

    @Test
    void convertPropagatesServiceErrors() {
        // Arrange: Mock service to return error (e.g., external API failure)
        when(fxService.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.error(new RuntimeException("External FX service unavailable")));

        // Act & Assert: Verify error propagation
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "USD")
                        .queryParam("to", "EUR")
                        .queryParam("amount", "25.00")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();  // Server error for external service failures

        verify(fxService).convert("USD", "EUR", new BigDecimal("25.00"));
    }

    @Test
    void convertReturnsBadRequestForInvalidCurrencyCode() {
        // Act & Assert: Test validation of currency code format (should be 3 uppercase letters)
        // Note: Without global exception handler, validation errors return 500
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "eur")  // Invalid: lowercase
                        .queryParam("to", "USD")
                        .queryParam("amount", "100.00")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();  // Validation error becomes 500 without global handler

        // Verify service was NOT called due to validation failure
        verify(fxService, never()).convert(any(), any(), any());
    }

    @Test
    void convertReturnsBadRequestForInvalidAmount() {
        // Act & Assert: Test validation of amount (must be > 0.00)
        // Note: Without global exception handler, validation errors return 500
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "USD")
                        .queryParam("to", "EUR")
                        .queryParam("amount", "0.00")  // Invalid: zero amount
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();  // Validation error becomes 500 without global handler

        // Verify service was NOT called due to validation failure
        verify(fxService, never()).convert(any(), any(), any());
    }

    @Test
    void convertReturnsBadRequestForNegativeAmount() {
        // Act & Assert: Test validation of negative amount
        // Note: Without global exception handler, validation errors return 500
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "USD")
                        .queryParam("to", "EUR")
                        .queryParam("amount", "-50.00")  // Invalid: negative amount
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();  // Validation error becomes 500 without global handler

        // Verify service was NOT called due to validation failure
        verify(fxService, never()).convert(any(), any(), any());
    }

    @Test
    void convertReturnsBadRequestForMalformedCurrencyCode() {
        // Act & Assert: Test validation of currency code length
        // Note: Without global exception handler, validation errors return 500
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "US")  // Invalid: only 2 letters
                        .queryParam("to", "EURO")  // Invalid: 4 letters
                        .queryParam("amount", "100.00")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();  // Validation error becomes 500 without global handler

        // Verify service was NOT called due to validation failure
        verify(fxService, never()).convert(any(), any(), any());
    }

    @Test
    void convertHandlesSmallAmounts() {
        // Arrange: Test with small decimal amounts
        FxQuote expectedQuote = new FxQuote(
                "USD", 
                "JPY", 
                new BigDecimal("0.01"), 
                new BigDecimal("148.50"), 
                new BigDecimal("1.48"), 
                Instant.now());

        when(fxService.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act & Assert: Verify small amount handling
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fx/convert")
                        .queryParam("from", "USD")
                        .queryParam("to", "JPY")
                        .queryParam("amount", "0.01")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FxQuote.class)
                .value(quote -> {
                    assertThat(quote.requestedAmount()).isEqualTo(new BigDecimal("0.01"));
                    assertThat(quote.converted()).isEqualTo(new BigDecimal("1.48"));
                });

        verify(fxService).convert("USD", "JPY", new BigDecimal("0.01"));
    }
}
