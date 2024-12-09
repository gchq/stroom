package stroom.docstore.shared;

import stroom.docref.DocRef;

import java.util.Locale;
import java.util.UUID;

public class UniqueNameUtil {

    private UniqueNameUtil() {
        // Utility.
    }

    public static String createDefault(DocRef docRef) {
        return createDefault(docRef.getType(), docRef.getName());
    }

    public static String createDefault(final String type, final String name) {
        if (name == null) {
            return cleanName(type) + ":" + UUID.randomUUID();
        }
        return cleanName(type) + ":" + cleanName(name);
    }

    private static String cleanName(final String name) {
        return name
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("--+", "-");
    }
}
