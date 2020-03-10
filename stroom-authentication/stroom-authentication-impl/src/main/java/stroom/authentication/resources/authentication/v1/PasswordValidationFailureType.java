package stroom.authentication.resources.authentication.v1;

enum PasswordValidationFailureType {
    REUSE,
    LENGTH,
    COMPLEXITY,
    BAD_OLD_PASSWORD
}
