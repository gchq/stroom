package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.NullSafe;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the Feed and Type attributes are supplied. If auto content creation is enabled
 * then the Feed can be derived from AccountId|Format|SchemaName|Type.
 */
public class FeedNameCheckAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedNameCheckAttributeMapFilter.class);

    private static final Pattern PARAM_REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_]");
    private static final Pattern STATIC_REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_-]");
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

        private final ConfigState configState;
        private final List<Function<AttributeMap, String>> partExtractors;

        public FeedNameGenerator(final ConfigState configState) {
            this.configState = configState;
            if (configState.feedNameGenerationEnabled) {
                try {
                    partExtractors = parseTemplate(configState.feedNameTemplate);
                } catch (Exception e) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Error parsing feed name template '{}'", configState.feedNameTemplate));
                }
            } else {
                // Feed name gen not enabled so just get the feed name from the attr map
                partExtractors = List.of(attributeMap ->
                        NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED)));
            }
        }

        public String generateName(final AttributeMap attributeMap) {
            final String name = partExtractors.stream()
                    .map(func -> func.apply(attributeMap))
                    .collect(Collectors.joining());
            LOGGER.debug("Generated name '{}' from attributeMap: {}", name, attributeMap);
            return name;
        }

        /**
         * Compile the template into a list of functions that convert an {@link AttributeMap} into
         * a name part. On generation of a name, each function is called in turned and the outputs
         * concatenated together.
         */
        private List<Function<AttributeMap, String>> parseTemplate(final String template) {
            if (NullSafe.isEmptyString(template)) {
                return Collections.emptyList();
            } else {
                final List<Function<AttributeMap, String>> funcList = new ArrayList<>();
                final StringBuilder sb = new StringBuilder();
                char lastChar = 0;
                boolean inVariable = false;
                for (final char chr : template.toCharArray()) {
                    if (chr == '{' && lastChar == '$') {
                        inVariable = true;
                        if (!sb.isEmpty()) {
                            // Stuff before must be static text
                            final String staticText = normaliseStaticText(sb.toString());
                            funcList.add(attributeMap -> staticText);
                            LOGGER.debug("Adding static text func for '{}'", staticText);
                            sb.setLength(0);
                        }
                    } else if (inVariable && chr == '}') {
                        inVariable = false;
                        final String key = sb.toString();
                        funcList.add(attributeMap ->
                                NullSafe.hasEntries(attributeMap)
                                        ? normaliseParam(attributeMap.get(key))
                                        : "");
                        LOGGER.debug("Adding header attributeMap value func for key '{}'", key);
                        sb.setLength(0);
                    } else if (chr != '$') {
                        // might be static text or the name of the key
                        sb.append(chr);
                    }
                    lastChar = chr;
                }
                if (inVariable) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Unclosed variable in template '{}'", template));
                }

                // Pick up any trailing static text
                if (!sb.isEmpty()) {
                    // Stuff before must be static text
                    final String staticText = normaliseStaticText(sb.toString());
                    funcList.add(attributeMap -> staticText);
                    sb.setLength(0);
                }
                return funcList;
            }
        }

        @Override
        public String toString() {
            return "FeedNameGenerator{" +
                   "configState=" + configState +
                   '}';
        }
    }
}
