package stroom.state.impl;

import java.util.regex.Pattern;

public class ScyllaDbNameValidator {

    // Deliberately forces lower case naming.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_0-9]{1,48}$");

    private ScyllaDbNameValidator() {
        // Utility class.
    }

    public static boolean isValidName(final String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public static String getPattern() {
        return NAME_PATTERN.pattern();
    }
}
