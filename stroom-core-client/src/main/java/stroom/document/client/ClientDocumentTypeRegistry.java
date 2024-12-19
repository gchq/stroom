package stroom.document.client;

import stroom.svg.shared.SvgImage;

import java.util.HashMap;
import java.util.Map;

public class ClientDocumentTypeRegistry {

    private static final Map<String, ClientDocumentType> MAP = new HashMap<>();

    public static void put(final ClientDocumentType documentType) {
        MAP.put(documentType.getType(), documentType);
    }

    public static ClientDocumentType get(final String type) {
        return MAP.get(type);
    }

    public static SvgImage getIcon(final String type) {
        final ClientDocumentType documentType = MAP.get(type);
        if (documentType == null) {
            return null;
        }
        return documentType.getIcon();
    }
}
