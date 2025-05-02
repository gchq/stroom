package stroom.receive.common;

import stroom.docref.DocRef;
import stroom.meta.api.AttributeMap;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds a chain of filters for checking and possibly adding to the contents
 * of the {@link stroom.meta.api.AttributeMap}
 */
@Singleton
public class AttributeMapFilterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFilterFactory.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider;
    private final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;

    private final CachedValue<AttributeMapFilter, ConfigState> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.feedStatusAttributeMapFilterProvider = feedStatusAttributeMapFilterProvider;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;

        // Every 60s, see if config has changed and if so create a new filter
        this.updatableAttributeMapFilter = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withStateSupplier(() -> ConfigState.fromConfig(receiveDataConfigProvider.get()))
                .withValueFunction(this::create)
                .build();
    }

    private AttributeMapFilter create(final ConfigState configState) {
        final List<AttributeMapFilter> filters = new ArrayList<>();

        // Filters will be run in the order they appear in the list,
        // so consider this when adding filters if a filter needs to run
        // before or after other filters.

        // This one adds the feed attr if it is not there (and config allows
        // so needs to go before most others.
        filters.add(feedNameCheckAttributeMapFilterProvider.get());

        if (NullSafe.isNonBlankString(configState.receiptPolicyUuid)) {
            LOGGER.info("Using data receipt policy to filter received data");
            filters.add(dataReceiptPolicyAttributeMapFilterFactory.create(
                    new DocRef(ReceiveDataRules.TYPE, configState.receiptPolicyUuid)));
        }

        if (MetaKeyValuePatternFilter.hasMetKeyValuePatterns(configState.metaKeyValuePatterns)) {

        }

        // The feed status filter will determine if the feed status check needs
        // to happen as the config for it is different between proxy and stroom.
        // Proxy and stroom each have a different FeedStatusService impl bound.
        filters.add(feedStatusAttributeMapFilterProvider.get());

        return AttributeMapFilter.wrap(filters);
    }


    public AttributeMapFilter create() {
        return updatableAttributeMapFilter.getValue();
    }

    // --------------------------------------------------------------------------------


    private record ConfigState(
            String receiptPolicyUuid,
            Map<String, String> metaKeyValuePatterns) {

        public static ConfigState fromConfig(
                final ReceiveDataConfig receiveDataConfig) {

            return new ConfigState(
                    receiveDataConfig.getReceiptPolicyUuid(),
                    receiveDataConfig.getMetaKeyValuePatterns());
        }
    }


    // --------------------------------------------------------------------------------


    private static class MetaKeyValuePatternFilter implements AttributeMapFilter {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaKeyValuePatternFilter.class);

        private final Map<String, Pattern> keyToValuePatternMap;

        MetaKeyValuePatternFilter(final Map<String, String> keyToValuePatternMap) {
            this.keyToValuePatternMap = NullSafe.map(keyToValuePatternMap)
                    .entrySet()
                    .stream()
                    .filter(entry -> NullSafe.isNonEmptyString(entry.getKey()))
                    .filter(entry -> NullSafe.isNonEmptyString(entry.getValue()))
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> Pattern.compile(entry.getValue())));
        }

        static boolean hasMetKeyValuePatterns(final Map<String, String> metaKeyValuePatterns) {
            return NullSafe.map(metaKeyValuePatterns)
                    .entrySet()
                    .stream()
                    .anyMatch(entry ->
                            NullSafe.isNonEmptyString(entry.getKey())
                            && NullSafe.isNonEmptyString(entry.getValue()));
        }

        @Override
        public boolean filter(final AttributeMap attributeMap) {
            return keyToValuePatternMap.entrySet()
                    .stream()
                    .allMatch(entry -> {
                        final String key = entry.getKey();
                        final Pattern pattern = entry.getValue();
                        if (attributeMap.containsKey(key)) {
                            final String attrMapValue = attributeMap.get(key);
                            if (attrMapValue == null) {
                                // If there is a pattern we can't allow a null value
                                return false;
                            } else {
                                final boolean matches = pattern.matcher(attrMapValue).matches();
                                LOGGER.trace("filter() - key: '{}', value: '{}', pattern: '{}', matches: '{}'",
                                        key, attrMapValue, pattern, matches);
                                return matches;
                            }
                        } else {
                            //
                            return true;
                        }
                    });
        }
    }
}
