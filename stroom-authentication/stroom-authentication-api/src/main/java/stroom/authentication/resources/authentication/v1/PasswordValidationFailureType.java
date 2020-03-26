package stroom.authentication.resources.authentication.v1;

public enum PasswordValidationFailureType {
    REUSE,
    LENGTH,
    COMPLEXITY,
    BAD_OLD_PASSWORD
}
