package stroom.util.shared;

import javax.validation.Payload;

/**
 * Severities for javax.validation
 */
public class ValidationSeverity {
    public static class Error implements Payload {};
    public static class Warning implements Payload {};
}
