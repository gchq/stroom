package stroom.authentication.authenticate;

public enum PasswordValidationFailureType {
    REUSE,
    LENGTH,
    COMPLEXITY,
    BAD_OLD_PASSWORD
}
