package stroom.docstore.api;

import java.util.Set;

public final class UniqueNameUtil {
    public static String getCopyName(final String name,
                                     final Set<String> existingNames) {
        return getCopyName(name, existingNames, "- Copy", " ");
    }

    public static String getCopyName(final String name,
                                     final Set<String> existingNames,
                                     final String suffix,
                                     final String delimiter) {
        if (existingNames == null || !existingNames.contains(name)) {
            return name;
        }

        String copyName = name + delimiter + suffix;

        int copyIndex = 2;
        while (existingNames.contains(copyName)) {
            copyName = name + delimiter + suffix + delimiter + copyIndex;
            copyIndex++;
        }

        return copyName;
    }
}
