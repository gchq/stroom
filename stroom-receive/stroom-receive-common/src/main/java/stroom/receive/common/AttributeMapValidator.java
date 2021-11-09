package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;

import java.util.Optional;

public class AttributeMapValidator {
    private AttributeMapValidator() {
    }

    public static void validate(final AttributeMap attributeMap) {
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
        if (feedName == null || feedName.trim().isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        // Get the type name from the header arguments if supplied.
        final String typeName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.TYPE))
                .map(String::trim)
                .orElse("");
        if (!typeName.isEmpty() && !StreamTypeNames.VALID_RECEIVE_TYPE_NAMES.contains(typeName)) {
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }
    }
}
