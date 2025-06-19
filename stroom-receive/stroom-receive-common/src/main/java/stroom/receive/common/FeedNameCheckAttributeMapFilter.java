package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Templator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the Feed and Type attributes are supplied. If auto content creation is enabled
 * then the Feed can be derived from AccountId|Format|SchemaName|Type.
 */
public class FeedNameCheckAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedNameCheckAttributeMapFilter.class);

//    public static final String NAME_PART_DELIMITER = "-";

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final CachedValue<FeedNameGenerator, ConfigState> cachedFeedNameGenerator;

    @Inject
    public FeedNameCheckAttributeMapFilter(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.cachedFeedNameGenerator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() -> ConfigState.fromConfig(receiveDataConfigProvider.get()))
                .withValueFunction(FeedNameGenerator::new)
                .build();
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        // Get the type name from the header arguments if supplied.
        final String type = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));
        if (!type.isEmpty() && !getValidStreamTypes(receiveDataConfig).contains(type)) {
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }

        String feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));
        if (receiveDataConfig.isFeedNameGenerationEnabled()) {
            LOGGER.debug("feedNameGenerationEnabled");
            // If they supply a feed then go with that
            if (feedName.isEmpty()) {
                LOGGER.debug("No feed name supplied");
                final Set<String> mandatoryHeaders = receiveDataConfig.getFeedNameGenerationMandatoryHeaders();
                if (NullSafe.hasItems(mandatoryHeaders)) {
                    for (final String mandatoryHeader : mandatoryHeaders) {
                        final String mandatoryHeaderValue = attributeMap.get(mandatoryHeader);
                        if (NullSafe.isBlankString(mandatoryHeaderValue)) {
                            throw new StroomStreamException(
                                    StroomStatusCode.MISSING_MANDATORY_HEADER,
                                    attributeMap,
                                    "Mandatory header '" + mandatoryHeader + "' must be provided");
                        }
                    }
                }
                feedName = cachedFeedNameGenerator.getValue()
                        .generateName(attributeMap);
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

    private Set<String> getValidStreamTypes(final ReceiveDataConfig receiveDataConfig) {
        return NullSafe.set(receiveDataConfig.getMetaTypes());
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    record ConfigState(boolean feedNameGenerationEnabled,
                       String feedNameTemplate) {

        static ConfigState fromConfig(final ReceiveDataConfig receiveDataConfig) {
            return new ConfigState(
                    receiveDataConfig.isFeedNameGenerationEnabled(),
                    receiveDataConfig.getFeedNameTemplate());
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    static class FeedNameGenerator {

        private static final Pattern PARAM_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");
        private static final Pattern STATIC_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");

        private static final String FEED_ONLY_TEMPLATE = "${feed}";

        private final Templator templator;

        public FeedNameGenerator(final ConfigState configState) {
            if (configState.feedNameGenerationEnabled) {
                try {
                    this.templator = TemplateUtil.parseTemplate(
                            configState.feedNameTemplate,
                            FeedNameGenerator::normaliseParam,
                            FeedNameGenerator::normaliseStaticText);
                } catch (final Exception e) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Error parsing feed name template '{}'", configState.feedNameTemplate));
                }
            } else {
                // Feed name gen not enabled so just get the feed name from the attr map
                this.templator = TemplateUtil.parseTemplate(
                        FEED_ONLY_TEMPLATE,
                        str -> NullSafe.string(str).toUpperCase());
            }
        }

        public String generateName(final AttributeMap attributeMap) {
            return templator.apply(attributeMap);
        }

        private static String normaliseParam(final String name) {
            String result = NullSafe.trim(name);
            result = result.toUpperCase();
            result = PARAM_REPLACE_PATTERN.matcher(result)
                    .replaceAll("_");
            return result;
        }

        private static String normaliseStaticText(final String name) {
            String result = NullSafe.trim(name);
            result = result.toUpperCase();
            result = STATIC_REPLACE_PATTERN.matcher(result)
                    .replaceAll("_");
            return result;
        }
    }
}
