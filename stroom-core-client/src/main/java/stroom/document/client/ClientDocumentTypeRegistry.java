package stroom.document.client;

import stroom.svg.shared.SvgImage;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
            return SvgImage.DOCUMENT_SEARCHABLE;
        }
        return documentType.getIcon();
    }

    public static Collection<ClientDocumentType> getTypes() {
        return MAP.values();
    }
}
