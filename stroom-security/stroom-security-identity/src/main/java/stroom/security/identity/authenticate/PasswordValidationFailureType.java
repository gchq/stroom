package stroom.security.identity.authenticate;

public enum PasswordValidationFailureType {
    REUSE,
    LENGTH,
    COMPLEXITY,
    BAD_OLD_PASSWORD
}
