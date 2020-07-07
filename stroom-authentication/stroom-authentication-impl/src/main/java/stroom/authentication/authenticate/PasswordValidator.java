package stroom.authentication.authenticate;

import java.util.Objects;

public class PasswordValidator {
    public static void validateLength(final String newPassword,
                                      int minimumLength) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (newPassword.length() < minimumLength) {
            throw new RuntimeException("Password does not meet the minimum length requirement of " + minimumLength + " characters");
        }
    }

    public static void validateComplexity(final String newPassword,
                                          final String complexityRegex) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (!newPassword.matches(complexityRegex)) {
            throw new RuntimeException("Password does not meet the minimum complexity requirements");
        }
    }

    public static void validateConfirmation(final String password, final String confirmationPassword) {
        if (password == null) {
            throw new RuntimeException("Password is null");
        }
        if (!Objects.equals(password, confirmationPassword)) {
            throw new RuntimeException("The confirmation password does not match");
        }
    }

    public static void validateReuse(final String oldPassword,
                                     final String newPassword) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (oldPassword.equalsIgnoreCase(newPassword)) {
            throw new RuntimeException("You cannot reuse the previous password");
        }
    }

    public static void validateCredentials(final CredentialValidationResult result) {
        if (!result.isAllOk()) {
            throw new RuntimeException("Invalid credentials");
        }
    }
}
