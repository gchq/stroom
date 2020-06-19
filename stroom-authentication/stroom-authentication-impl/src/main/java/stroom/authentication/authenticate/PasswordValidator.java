package stroom.authentication.authenticate;


import java.util.Optional;


public class PasswordValidator {

    static Optional<PasswordValidationFailureType> validateLength(final String newPassword,
                                                                  int minimumLength) {
        boolean isLengthValid = newPassword != null && (newPassword.length() >= minimumLength);
        return isLengthValid
                ? Optional.empty()
                : Optional.of(PasswordValidationFailureType.LENGTH);
    }

    static Optional<PasswordValidationFailureType> validateComplexity(final String newPassword,
                                                                      final String complexityRegex) {
        boolean isPasswordComplexEnough = newPassword.matches(complexityRegex);
        return isPasswordComplexEnough
                ? Optional.empty()
                : Optional.of(PasswordValidationFailureType.COMPLEXITY);
    }

    static Optional<PasswordValidationFailureType> validateAuthenticity(final CredentialValidationResult result) {
        return result.isAllOk()
                ? Optional.empty()
                : Optional.of(PasswordValidationFailureType.BAD_OLD_PASSWORD);
    }

    static Optional<PasswordValidationFailureType> validateReuse(final String oldPassword,
                                                                 final String newPassword) {
        boolean isPasswordReused = oldPassword.equalsIgnoreCase(newPassword);
        return isPasswordReused
                ? Optional.of(PasswordValidationFailureType.REUSE)
                : Optional.empty();
    }
}
