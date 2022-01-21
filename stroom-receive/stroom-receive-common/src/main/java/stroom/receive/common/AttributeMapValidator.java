package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class AttributeMapValidator {

    private AttributeMapValidator() {
    }

    public static void validate(final AttributeMap attributeMap,
                                final Supplier<Set<String>> validTypeNamesSupplier) {
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
        if (feedName == null || feedName.trim().isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        // Get the type name from the header arguments if supplied.
        final String typeName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.TYPE))
                .map(String::trim)
                .orElse("");
        final Set<String> validTypeNames = validTypeNamesSupplier.get();
        if (!typeName.isEmpty() && !validTypeNames.contains(typeName)) {
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }
    }
}
