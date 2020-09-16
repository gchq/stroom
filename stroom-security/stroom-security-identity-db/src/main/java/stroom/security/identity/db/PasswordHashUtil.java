package stroom.security.identity.db;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

final class PasswordHashUtil {
    private PasswordHashUtil() {
    }

    static String hash(final String password) {
        Objects.requireNonNull(password, "Null password");
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    static boolean checkPassword(final String password, final String passwordHash) {
        return BCrypt.checkpw(password, passwordHash);
    }
}
