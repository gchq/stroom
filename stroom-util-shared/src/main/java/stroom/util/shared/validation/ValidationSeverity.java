package stroom.util.shared.validation;

import javax.validation.Payload;
import java.util.Set;

/**
 * Severities for javax.validation
 */
public enum ValidationSeverity {

    ERROR(Error.class),
    WARNING(Warning.class);

    private final Class<? extends ValidationSeverityPayload> payloadClass;

    ValidationSeverity(final Class<? extends ValidationSeverityPayload> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<? extends Payload> payload() {
        return payloadClass;
    }

    public static ValidationSeverity fromPayloads(Set<Class<? extends Payload>> payloads) {
        ValidationSeverity result;
        if (payloads == null || payloads.isEmpty()) {
            // ERROR is assumed if we have no other info
            result = ERROR;
        } else {
            if (payloads.contains(Error.class)) {
                // ERROR trumps WARNING
                result = ERROR;
            } else if(payloads.contains(Warning.class)) {
                // No errors but a warning
                result = WARNING;
            } else {
                // None of either so assume ERROR
                result = ERROR;
            }
        }
        return result;
    }

    public interface ValidationSeverityPayload extends Payload {

    }

    public class Error implements ValidationSeverityPayload {

    };

    public class Warning implements ValidationSeverityPayload {

    };
}
