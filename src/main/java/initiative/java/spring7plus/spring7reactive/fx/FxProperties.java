package initiative.java.spring7plus.spring7reactive.fx;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holder for exchangerate.host configuration (currently just the access key).
 */
@ConfigurationProperties(prefix = "app.fx")
public record FxProperties(String accessKey) {
}
