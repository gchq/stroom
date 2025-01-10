package stroom.planb.impl;

import java.util.regex.Pattern;

public class PlanBNameValidator {

    // Deliberately forces lower case naming.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_0-9]+$");

    private PlanBNameValidator() {
        // Utility class.
    }

    public static boolean isValidName(final String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public static String getPattern() {
        return NAME_PATTERN.pattern();
    }
}
