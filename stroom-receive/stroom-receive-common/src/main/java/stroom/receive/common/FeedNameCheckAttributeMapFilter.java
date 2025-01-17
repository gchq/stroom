package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the Feed and Type attributes are supplied. If auto content creation is enabled
 * then the Feed can be derived from AccountId|Format|SchemaName|Type.
 */
public class FeedNameCheckAttributeMapFilter implements AttributeMapFilter {

    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_]");
    public static final String NAME_PART_DELIMITER = "-";

    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    @Inject
    public FeedNameCheckAttributeMapFilter(
            final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
            final Provider<ReceiveDataConfig> receiveDataConfigProvider) {

        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        // Get the type name from the header arguments if supplied.
        final String type = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));
        if (!type.isEmpty() && !getValidStreamTypes().contains(type)) {
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }

        final boolean isAutoContentEnabled = autoContentCreationConfigProvider.get()
                .isEnabled();

        String feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));
        if (isAutoContentEnabled) {
            // If they supply a feed then go with that
            if (feedName.isEmpty()) {
                final String accountName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_NAME));
                if (accountName.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.ACCOUNT_ID_MUST_BE_SPECIFIED, attributeMap);
                }

                final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
                if (component.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.COMPONENT_MUST_BE_SPECIFIED, attributeMap);
                }
                feedName = deriveFeedName(attributeMap, type);
                // Add the derived feed name as everything else depends on the feed name
                attributeMap.put(StandardHeaderArguments.FEED, feedName);
            }
        } else {
            if (feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }
        }

        return true;
    }

    private Set<String> getValidStreamTypes() {
        return NullSafe.set(receiveDataConfigProvider.get().getMetaTypes());
    }

    //pkg private for testing
    static String deriveFeedName(final AttributeMap attributeMap,
                                 final String type) {

        final String accountId = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_ID));
        if (accountId.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.ACCOUNT_ID_MUST_BE_SPECIFIED, attributeMap);
        }

        final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
        final String format = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FORMAT));
        final String schema = NullSafe.trim(attributeMap.get(StandardHeaderArguments.SCHEMA));
        String effectiveType = type.isBlank()
                ? StreamTypeNames.EVENTS
                : type;

        final List<String> nameParts = List.of(
                accountId,
                component,
                schema,
                format,
                effectiveType);

        return buildName(nameParts);
    }

    private static String buildName(final List<String> parts) {
        return parts.stream()
                .filter(Predicate.not(String::isBlank))
                .map(FeedNameCheckAttributeMapFilter::normaliseNamePart)
                .collect(Collectors.joining(NAME_PART_DELIMITER));
    }

    private static String normaliseNamePart(final String name) {
        String result = NullSafe.trim(name);
        result = result.toUpperCase();
        result = REPLACE_PATTERN.matcher(result).replaceAll("_");
        return result;
    }
}
