package stroom.state.impl;

import stroom.util.shared.EntityServiceException;

import java.util.regex.Pattern;

public class KeyspaceNameValidator {
    // Deliberately forces lower case naming.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_0-9]{1,48}$");

    private KeyspaceNameValidator() {
        // Utility class.
    }

    public static void validateName(final String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new EntityServiceException("A state store name must match the pattern '" +
                    NAME_PATTERN.pattern() +
                    "'");
        }
    }
}
