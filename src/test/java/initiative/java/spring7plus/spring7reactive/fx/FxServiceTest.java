package initiative.java.spring7plus.spring7reactive.fx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import initiative.java.spring7plus.spring7reactive.fx.FxClient.FxQuote;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pure unit tests for FxService.
 * 
 * This test class demonstrates testing of service façade layers that delegate to external clients:
 * - Uses @ExtendWith(MockitoExtension.class) to avoid loading Spring context (fast execution)
 * - Tests business logic delegation and parameter passing
 * - Uses StepVerifier for reactive stream testing
 * - Verifies both successful delegation and error propagation
 * - Validates that service properly forwards calls to client
 * - Tests error handling from external service calls
 * - No database, Spring context, or external HTTP calls required
 */
@ExtendWith(MockitoExtension.class)
class FxServiceTest {

    // Mock dependencies - these will be injected into the service under test
    @Mock
    private FxClient fxClient;

    // Service under test - mocks will be automatically injected into this instance
    @InjectMocks
    private FxService fxService;

    @BeforeEach
    void setUp() {
        // Common setup can go here if needed
        // Currently empty as we use specific mocks per test for clarity
    }

    @Test
    void convertDelegatesToFxClientAndReturnsQuote() {
        // Arrange: Create expected FX quote
        FxQuote expectedQuote = new FxQuote(
                "EUR", 
                "USD", 
                new BigDecimal("100.00"), 
                new BigDecimal("1.0850"), 
                new BigDecimal("108.50"), 
                Instant.parse("2025-01-07T12:00:00Z"));

        // Mock the client response
        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("EUR", "USD", new BigDecimal("100.00"));

        // Assert: Verify the reactive stream returns expected quote
        StepVerifier.create(result)
                .expectNextMatches(quote -> 
                    quote.fromCurrency().equals("EUR") &&
                    quote.toCurrency().equals("USD") &&
                    quote.requestedAmount().equals(new BigDecimal("100.00")) &&
                    quote.rate().equals(new BigDecimal("1.0850")) &&
                    quote.converted().equals(new BigDecimal("108.50")) &&
                    quote.fetchedAt() != null)
                .verifyComplete();

        // Verify client was called with correct parameters
        verify(fxClient).convert("EUR", "USD", new BigDecimal("100.00"));
    }

    @Test
    void convertPropagatesFxClientErrors() {
        // Arrange: Mock client to return error (e.g., external API failure)
        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.error(new FxClient.FxClientException("External FX service unavailable")));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("USD", "JPY", new BigDecimal("50.00"));

        // Assert: Verify the reactive stream emits the error
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> 
                    throwable instanceof FxClient.FxClientException &&
                    throwable.getMessage().contains("External FX service unavailable"))
                .verify();

        // Verify client was called with correct parameters
        verify(fxClient).convert("USD", "JPY", new BigDecimal("50.00"));
    }

    @Test
    void convertHandlesDifferentCurrencyPairs() {
        // Arrange: Test with different currency pair
        FxQuote expectedQuote = new FxQuote(
                "GBP", 
                "JPY", 
                new BigDecimal("25.00"), 
                new BigDecimal("195.25"), 
                new BigDecimal("4881.25"), 
                Instant.now());

        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("GBP", "JPY", new BigDecimal("25.00"));

        // Assert: Verify the reactive stream returns expected quote
        StepVerifier.create(result)
                .expectNextMatches(quote -> 
                    quote.fromCurrency().equals("GBP") &&
                    quote.toCurrency().equals("JPY") &&
                    quote.requestedAmount().equals(new BigDecimal("25.00")) &&
                    quote.converted().equals(new BigDecimal("4881.25")))
                .verifyComplete();

        // Verify client was called with correct parameters
        verify(fxClient).convert("GBP", "JPY", new BigDecimal("25.00"));
    }

    @Test
    void convertHandlesSmallAmounts() {
        // Arrange: Test with small decimal amounts
        FxQuote expectedQuote = new FxQuote(
                "USD", 
                "EUR", 
                new BigDecimal("0.01"), 
                new BigDecimal("0.9250"), 
                new BigDecimal("0.00925"), 
                Instant.now());

        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("USD", "EUR", new BigDecimal("0.01"));

        // Assert: Verify the reactive stream handles small amounts correctly
        StepVerifier.create(result)
                .expectNextMatches(quote -> 
                    quote.requestedAmount().equals(new BigDecimal("0.01")) &&
                    quote.rate().equals(new BigDecimal("0.9250")) &&
                    quote.converted().equals(new BigDecimal("0.00925")))
                .verifyComplete();

        // Verify client was called with correct parameters
        verify(fxClient).convert("USD", "EUR", new BigDecimal("0.01"));
    }

    @Test
    void convertHandlesLargeAmounts() {
        // Arrange: Test with large amounts
        FxQuote expectedQuote = new FxQuote(
                "USD", 
                "CAD", 
                new BigDecimal("1000000.00"), 
                new BigDecimal("1.3650"), 
                new BigDecimal("1365000.00"), 
                Instant.now());

        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("USD", "CAD", new BigDecimal("1000000.00"));

        // Assert: Verify the reactive stream handles large amounts correctly
        StepVerifier.create(result)
                .expectNextMatches(quote -> 
                    quote.requestedAmount().equals(new BigDecimal("1000000.00")) &&
                    quote.rate().equals(new BigDecimal("1.3650")) &&
                    quote.converted().equals(new BigDecimal("1365000.00")))
                .verifyComplete();

        // Verify client was called with correct parameters
        verify(fxClient).convert("USD", "CAD", new BigDecimal("1000000.00"));
    }

    @Test
    void convertPropagatesRuntimeExceptions() {
        // Arrange: Mock client to return generic runtime error
        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.error(new RuntimeException("Network timeout")));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert("AUD", "NZD", new BigDecimal("100.00"));

        // Assert: Verify the reactive stream emits the error
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> 
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Network timeout"))
                .verify();

        // Verify client was called with correct parameters
        verify(fxClient).convert("AUD", "NZD", new BigDecimal("100.00"));
    }

    @Test
    void convertVerifiesParameterPassing() {
        // Arrange: Create test parameters
        String fromCurrency = "CHF";
        String toCurrency = "SEK";
        BigDecimal amount = new BigDecimal("750.50");

        FxQuote expectedQuote = new FxQuote(
                fromCurrency, 
                toCurrency, 
                amount, 
                new BigDecimal("10.1250"), 
                new BigDecimal("7593.81"), 
                Instant.now());

        when(fxClient.convert(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(expectedQuote));

        // Act: Call the service method
        Mono<FxQuote> result = fxService.convert(fromCurrency, toCurrency, amount);

        // Assert: Verify the reactive stream returns expected quote
        StepVerifier.create(result)
                .expectNextMatches(quote -> 
                    quote.fromCurrency().equals(fromCurrency) &&
                    quote.toCurrency().equals(toCurrency) &&
                    quote.requestedAmount().equals(amount))
                .verifyComplete();

        // Verify client was called with exact same parameters
        verify(fxClient).convert(fromCurrency, toCurrency, amount);
    }
}
