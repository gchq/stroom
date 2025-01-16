package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AttributeMapValidator {

    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_]");
    public static final String NAME_PART_DELIMITER = "-";
    public static final String SUFFIX = "EVENTS";

    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;

    @Inject
    private AttributeMapValidator(final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider) {
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
    }

    public void validate(final AttributeMap attributeMap,
                         final Supplier<Set<String>> validTypeNamesSupplier) {

        final boolean isAutoContentEnabled = autoContentCreationConfigProvider.get()
                .isEnabled();

        String feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));
        if (isAutoContentEnabled) {
            // If they supply a feed then go with that
            if (feedName.isEmpty()) {
                final String accountName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_NAME));
                if (accountName.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.ACCOUNT_NAME_MUST_BE_SPECIFIED, attributeMap);
                }

                final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
                if (component.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.COMPONENT_MUST_BE_SPECIFIED, attributeMap);
                }
                feedName = deriveFeedName(attributeMap);
                attributeMap.put(StandardHeaderArguments.FEED, feedName);
            }
        } else {
            if (feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }
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

    private static String deriveFeedName(final AttributeMap attributeMap) {

        final String accountName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_NAME));
        if (accountName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.ACCOUNT_NAME_MUST_BE_SPECIFIED, attributeMap);
        }
        final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
        final String schema = NullSafe.trim(attributeMap.get(StandardHeaderArguments.SCHEMA));

        // TODO Need to include the strm type but we may not know it at this point

        final List<String> nameParts = new ArrayList<>();
        nameParts.add(accountName);

        if (!component.isEmpty()) {
            nameParts.add(component);
        }
        nameParts.add(SUFFIX);

        return buildName(nameParts);
    }

    private static String buildName(final List<String> parts) {
        return parts.stream()
                .map(AttributeMapValidator::normaliseNamePart)
                .collect(Collectors.joining(NAME_PART_DELIMITER));
    }

    private static String normaliseNamePart(final String name) {
        String result = NullSafe.trim(name);
        result = result.toUpperCase();
        result = REPLACE_PATTERN.matcher(result).replaceAll("_");
        return result;
    }
}
